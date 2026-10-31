package com.epi.procurement.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

import tools.jackson.databind.json.JsonMapper;

class DashboardIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	@Test
	void pipelineManagementAuditPackAndErpExportRespectVisibility() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = accept(assign(approve(submit(sami, request("Dashboard seals")))));

		signedIn("requester.tn@demo.local").get("/api/v1/dashboards/pipeline").expectStatus(403);
		signedIn("receiver.tn@demo.local").get("/api/v1/dashboards/pipeline").expectStatus(403);

		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		ApiClient.Response pipeline = mehdi.get("/api/v1/dashboards/pipeline").expectStatus(200);
		assertThat((Boolean) pipeline.json("$.groupView")).isFalse();
		assertThat(((Number) pipeline.json("$.byUserStatus.length()")).intValue()).isGreaterThanOrEqualTo(1);

		mehdi.get("/api/v1/dashboards/management?page=0&size=20").expectStatus(200);

		ApiClient hedi = signedIn("ceo@demo.local");
		ApiClient.Response groupPipeline = hedi.get("/api/v1/dashboards/pipeline").expectStatus(200);
		assertThat((Boolean) groupPipeline.json("$.groupView")).isTrue();
		assertThat(((Number) groupPipeline.json("$.byRequestingCompany.length()")).intValue()).isGreaterThanOrEqualTo(1);

		ApiClient.Response pack = mehdi.get("/api/v1/dashboards/requests/" + id + "/audit-pack").expectStatus(200);
		assertThat(pack.header("Content-Type")).contains("application/pdf");
		assertThat(pack.body()).startsWith("%PDF");

		signedIn("requester.fr@demo.local").get("/api/v1/dashboards/requests/" + id + "/audit-pack").expectStatus(404);

		ApiClient.Response reqCsv = mehdi.get("/api/v1/dashboards/exports/requests.csv").expectStatus(200);
		assertThat(reqCsv.body()).contains("number,title,status");
		assertThat(reqCsv.body()).containsIgnoringCase("Dashboard seals");

		ApiClient.Response poCsv = mehdi.get("/api/v1/dashboards/exports/purchase-orders.csv").expectStatus(200);
		assertThat(poCsv.body()).startsWith("poNumber,status,currency");

		signedIn("buyer@demo.local").get("/api/v1/dashboards/exports/requests.csv").expectStatus(403);
		signedIn("manager.tn@demo.local").get("/api/v1/dashboards/pipeline").expectStatus(200);
	}

	@Test
	void openAmountsAreNotMergedAcrossCurrencies() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		accept(assign(approve(submit(sami, request("Currency keep separate")))));
		ApiClient.Response pipeline = signedIn("purchasing.manager@demo.local").get("/api/v1/dashboards/pipeline")
			.expectStatus(200);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> amounts = (List<Map<String, Object>>) pipeline.json("$.openAmountsByCurrency");
		assertThat(amounts).isNotNull();
		long distinct = amounts.stream().map(row -> String.valueOf(row.get("currency"))).distinct().count();
		assertThat(distinct).isEqualTo(amounts.size());
	}

	private long submit(ApiClient client, Map<String, Object> body) {
		ApiClient.Response draft = client.post("/api/v1/requests", json(body)).expectStatus(201);
		return client.post("/api/v1/requests/" + draft.id() + "/submit", "{\"version\":" + draft.version() + "}")
			.expectStatus(200)
			.id();
	}

	private long approve(long requestId) {
		ApiClient karim = signedIn("manager.tn@demo.local");
		ApiClient.Response approval = karim.get("/api/v1/requests/" + requestId + "/approval").expectStatus(200);
		long task = ((Number) approval.json("$.myTask.id")).longValue();
		int version = ((Number) approval.json("$.myTask.version")).intValue();
		karim.post("/api/v1/approvals/tasks/" + task + "/approve", "{\"version\":" + version + "}").expectStatus(200);
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

	private long accept(long requestId) {
		ApiClient ines = signedIn("buyer@demo.local");
		int version = ((Number) ines.get("/api/v1/requests/" + requestId).json("$.version")).intValue();
		ines.post("/api/v1/purchasing/requests/" + requestId + "/accept", "{\"version\":" + version + "}")
			.expectStatus(200);
		return requestId;
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

	private static String json(Object value) {
		return JSON.writeValueAsString(value);
	}

}
