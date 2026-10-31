package com.epi.procurement.sourcing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

import tools.jackson.databind.json.JsonMapper;

@TestPropertySource(properties = {
		"app.sourcing.quote-link-rate-limit-per-minute=3",
		"app.sourcing.quote-link-rate-limit-per-token-per-minute=50"
})
class QuoteLinkSecurityIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	@Test
	void quoteLinkSummaryIsRateLimitedPerClientAddress() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = accept(assign(approve(submit(sami, request("Rate limit link")))));
		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		ApiClient.Response rfq = ines.post("/api/v1/sourcing/requests/" + id + "/rfq",
				"{\"kind\":\"STANDARD\",\"supplierIds\":[" + skf + "]}").expectStatus(200);
		long rfqId = ((Number) rfq.json("$.id")).longValue();
		long rfqSupplierId = ((Number) rfq.json("$.suppliers[0].id")).longValue();
		String token = ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/suppliers/" + rfqSupplierId + "/link", "{}")
			.expectStatus(200)
			.json("$.token");

		ApiClient anon = client();
		anon.csrf();
		String body = "{\"token\":" + JSON.writeValueAsString(token) + "}";
		anon.post("/api/v1/quote-links/summary", body).expectStatus(200);
		anon.post("/api/v1/quote-links/summary", body).expectStatus(200);
		anon.post("/api/v1/quote-links/summary", body).expectStatus(200);
		assertThat(anon.post("/api/v1/quote-links/summary", body).expectStatus(429).code()).isEqualTo("too-many-requests");

		// Spoofed forwarded header from a non-trusted peer must not bypass the address bucket (trusted-proxies empty).
		assertThat(anon.send("POST", "/api/v1/quote-links/summary", body, Map.of("X-Forwarded-For", "198.51.100.77"))
			.expectStatus(429)
			.code()).isEqualTo("too-many-requests");
	}

	private long accept(long requestId) {
		ApiClient ines = signedIn("buyer@demo.local");
		int version = ((Number) ines.get("/api/v1/requests/" + requestId).json("$.version")).intValue();
		ines.post("/api/v1/purchasing/requests/" + requestId + "/accept", "{\"version\":" + version + "}")
			.expectStatus(200);
		return requestId;
	}

	private long assign(long requestId) {
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		int version = ((Number) mehdi.get("/api/v1/requests/" + requestId).json("$.version")).intValue();
		long inesId = userId("buyer@demo.local");
		mehdi.post("/api/v1/purchasing/requests/" + requestId + "/assign",
				"{\"version\":" + version + ",\"buyerUserId\":" + inesId + "}").expectStatus(200);
		return requestId;
	}

	private long approve(long requestId) {
		ApiClient karim = signedIn("manager.tn@demo.local");
		ApiClient.Response approval = karim.get("/api/v1/requests/" + requestId + "/approval").expectStatus(200);
		long task = ((Number) approval.json("$.myTask.id")).longValue();
		int version = ((Number) approval.json("$.myTask.version")).intValue();
		karim.post("/api/v1/approvals/tasks/" + task + "/approve", "{\"version\":" + version + "}").expectStatus(200);
		return requestId;
	}

	private long submit(ApiClient client, Map<String, Object> body) {
		ApiClient.Response draft = client.post("/api/v1/requests", JSON.writeValueAsString(body)).expectStatus(201);
		return client.post("/api/v1/requests/" + draft.id() + "/submit", "{\"version\":" + draft.version() + "}")
			.expectStatus(200)
			.id();
	}

	private Map<String, Object> request(String title) {
		long tn = companyId("DEMO-TN-PLT");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("categoryId", jdbc.queryForObject(
				"SELECT c.id FROM request_category c JOIN organization_group g ON g.id = c.group_id WHERE g.code = 'DEMO-GRP' AND c.code = 'DEMO-SPARE'",
				Long.class));
		body.put("priority", "NORMAL");
		body.put("purchaseType", "LOCAL");
		body.put("currency", "TND");
		body.put("departmentId", jdbc.queryForObject("SELECT id FROM department WHERE company_id = ? AND code = 'DEMO-MAINT'",
				Long.class, tn));
		body.put("requiredDate", LocalDate.now(TUNIS).plusDays(21).toString());
		body.put("lines", new ArrayList<>(List.of(Map.of("description", title, "quantity", 1, "unit", "PCE",
				"estimatedUnitPrice", 20))));
		return body;
	}

}
