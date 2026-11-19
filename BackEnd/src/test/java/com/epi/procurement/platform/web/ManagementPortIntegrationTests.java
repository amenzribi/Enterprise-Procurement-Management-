package com.epi.procurement.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.context.TestPropertySource;

import com.epi.procurement.support.SqlServerTestSupport;

/**
 * Sprint 2, P1.4 (ADR-018): with a separate management port, as in UAT, the monitoring agent reads Prometheus there
 * without signing in, the application port no longer serves the actuator, and health stays on the application port
 * at /healthz for the System status page and the uptime check.
 */
// Spring Boot switches metrics exporters off in tests; this test needs the Prometheus one, as production has it.
// Its own context: it must not clean the shared database again (see SqlServerTestSupport.TestDatabase).
@TestPropertySource(properties = { "management.server.port=0", "management.server.address=127.0.0.1",
		"management.defaults.metrics.export.enabled=true", "management.prometheus.metrics.export.enabled=true",
		"test.database.clean=false" })
class ManagementPortIntegrationTests extends SqlServerTestSupport {

	@LocalManagementPort
	int managementPort;

	private static HttpResponse<String> get(int port, String path) throws Exception {
		return HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void prometheusIsReadOnTheManagementPortWithoutSigningIn() throws Exception {
		assertThat(managementPort).isPositive().isNotEqualTo(port);
		HttpResponse<String> scrape = get(managementPort, "/actuator/prometheus");
		assertThat(scrape.statusCode()).isEqualTo(200);
		assertThat(scrape.body()).contains("jvm_memory_used_bytes").contains("procurement_outbox");
	}

	@Test
	void theApplicationPortServesNoActuatorButKeepsHealth() throws Exception {
		assertThat(get(port, "/actuator/prometheus").statusCode()).isIn(401, 403, 404);
		assertThat(get(port, "/actuator/metrics").statusCode()).isIn(401, 403, 404);
		HttpResponse<String> health = get(port, "/healthz");
		assertThat(health.statusCode()).isEqualTo(200);
		// Anonymous: the status only, no components.
		assertThat(health.body()).contains("\"status\"").doesNotContain("components");
	}

	@Test
	void metricsDetailOnTheManagementPortStillNeedsAGlobalAdministrator() throws Exception {
		assertThat(get(managementPort, "/actuator/metrics").statusCode()).isEqualTo(401);
	}

}
