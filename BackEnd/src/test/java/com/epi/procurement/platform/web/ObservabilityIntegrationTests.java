package com.epi.procurement.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

/**
 * Sprint 1 observability (audit A-2, P-4, D-5): request ids, the access line, metrics and health probes.
 */
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIntegrationTests extends SqlServerTestSupport {

	@Test
	void everyAnswerCarriesAServerGeneratedRequestId() {
		ApiClient anonymous = client();
		ApiClient.Response first = anonymous.get("/api/v1/me");
		ApiClient.Response second = anonymous.send("GET", "/api/v1/me", null, Map.of("X-Request-Id", "chosen-by-client"));
		first.expectStatus(401);
		assertThat(first.header("X-Request-Id")).matches("[0-9a-f]{16}");
		assertThat(second.header("X-Request-Id")).matches("[0-9a-f]{16}").isNotEqualTo(first.header("X-Request-Id"));
		assertThat(signedIn("requester.tn@demo.local").get("/api/v1/me").expectStatus(200).header("X-Request-Id"))
			.matches("[0-9a-f]{16}");
	}

	@Test
	void theIdOfAnErrorAnswerIsFoundInTheLog(CapturedOutput output) {
		ApiClient sami = signedIn("requester.tn@demo.local");
		ApiClient.Response missing = sami.get("/api/v1/requests/999999999").expectStatus(404);
		String id = missing.header("X-Request-Id");
		assertThat(missing.body()).doesNotContain(id);
		assertThat(output.getOut()).contains("[" + id + "," + userId("requester.tn@demo.local") + "]");
	}

	@Test
	void theAccessLineHasMethodPathStatusAndUserButNoQueryString(CapturedOutput output) {
		ApiClient sami = signedIn("requester.tn@demo.local");
		sami.get("/api/v1/requests?q=confidential-search-text").expectStatus(200);
		client().get("/api/v1/requests?q=anonymous-probe").expectStatus(401);

		String log = output.getOut();
		assertThat(log)
			.containsPattern(": GET /api/v1/requests 200 [0-9]+ms user=" + userId("requester.tn@demo.local"));
		assertThat(log).containsPattern(": GET /api/v1/requests 401 [0-9]+ms user=null");
		assertThat(log).doesNotContain("confidential-search-text", "anonymous-probe", SqlServerTestSupport.DEMO_PASSWORD);
	}

	@Test
	@SuppressWarnings("unchecked")
	void metricsAreForGlobalAdministratorsOnly() {
		client().get("/actuator/metrics").expectStatus(401);
		signedIn("requester.tn@demo.local").get("/actuator/metrics").expectStatus(403);
		signedIn("admin@demo.local").get("/actuator/metrics/http.server.requests").expectStatus(403);

		ApiClient global = signedIn("global.admin@demo.local");
		assertThat((List<String>) global.get("/actuator/metrics").expectStatus(200).json("$.names")).contains(
				"http.server.requests", "hikaricp.connections.active", "jvm.memory.used", "procurement.outbox.backlog",
				"procurement.outbox.oldest.seconds", "procurement.outbox.failed", "procurement.messaging.streams.open");
		Number backlog = global.get("/actuator/metrics/procurement.outbox.backlog")
			.expectStatus(200)
			.json("$.measurements[0].value");
		assertThat(backlog.doubleValue()).isGreaterThanOrEqualTo(0);
	}

	/** Sprint 2, P1.4: /healthz answers like the actuator's health, also when the actuator moves to its own port. */
	@Test
	@SuppressWarnings("unchecked")
	void healthzGivesTheSameAnswerAsTheActuator() {
		assertThat(client().get("/healthz").expectStatus(200).body())
			.isEqualTo(client().get("/actuator/health").expectStatus(200).body())
			.isEqualTo("{\"status\":\"UP\"}");
		ApiClient sami = signedIn("requester.tn@demo.local");
		java.util.Map<String, Object> components = sami.get("/healthz").expectStatus(200).json("$.components");
		assertThat(components).containsKey("db");
		assertThat((java.util.Map<String, Object>) components.get("db")).containsOnlyKeys("status");
	}

	@Test
	void healthAnswersAnonymousCallersWithTheStatusOnlyAndNothingElseIsExposed() {
		assertThat(client().get("/actuator/health").expectStatus(200).body()).isEqualTo("{\"status\":\"UP\"}");
		// Other actuator endpoints are not exposed at all.
		ApiClient global = signedIn("global.admin@demo.local");
		for (String path : new String[] { "/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/loggers" }) {
			assertThat(global.get(path).status()).as(path).isEqualTo(404);
		}
	}

}
