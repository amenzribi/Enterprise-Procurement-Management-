package com.epi.procurement.purchasing;

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

class PurchasingIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	@Test
	void approvedRequestEntersTheBuyingCompanyQueueAndAssignmentIsIsolated() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = approve(submit(sami, request("Seals for P-204")));
		assertThat((String) sami.get("/api/v1/requests/" + id).json("$.status")).isEqualTo("IN_PURCHASING_REVIEW");
		assertThat((String) sami.get("/api/v1/requests/" + id).json("$.userStatus")).isEqualTo("APPROVED");

		ApiClient ines = signedIn("buyer@demo.local");
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		assertThat((List<Integer>) mehdi.get("/api/v1/purchasing/queue?size=100").json("$.items[*].requestId"))
			.contains((int) id);
		assertThat((List<Integer>) ines.get("/api/v1/purchasing/queue?size=100").json("$.items[*].requestId"))
			.contains((int) id);

		signedIn("requester.fr@demo.local").get("/api/v1/purchasing/queue").expectStatus(403);
		signedIn("requester.fr@demo.local").get("/api/v1/requests/" + id).expectStatus(404);
		signedIn("admin@demo.local").get("/api/v1/requests/" + id).expectStatus(404);

		int version = ((Number) mehdi.get("/api/v1/requests/" + id).json("$.version")).intValue();
		long inesId = userId("buyer@demo.local");
		mehdi.post("/api/v1/purchasing/requests/" + id + "/assign",
				"{\"version\":" + version + ",\"buyerUserId\":" + inesId + "}").expectStatus(200);
		assertThat((String) sami.get("/api/v1/requests/" + id).json("$.status")).isEqualTo("ASSIGNED_TO_BUYER");
		assertThat((String) sami.get("/api/v1/requests/" + id).json("$.userStatus")).isEqualTo("ASSIGNED");

		version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/purchasing/requests/" + id + "/accept", "{\"version\":" + version + "}").expectStatus(200);
		assertThat((List<String>) mehdi.get("/api/v1/purchasing/requests/" + id + "/assignments")
			.json("$[*].status")).contains("ACCEPTED");

		signedIn("purchasing.manager@demo.local").get("/api/v1/suppliers").expectStatus(200);
		signedIn("requester.tn@demo.local").get("/api/v1/suppliers").expectStatus(403);
	}

	@Test
	void purchaseModeDoesNotChangeChargedCompanyAndCrossGroupOrgIs404() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = approve(submit(sami, request("Mode change")));
		long charged = jdbc.queryForObject("SELECT charged_company_id FROM purchase_request WHERE id = ?", Long.class, id);
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		int version = ((Number) mehdi.get("/api/v1/requests/" + id).json("$.version")).intValue();
		mehdi.post("/api/v1/purchasing/requests/" + id + "/purchase-mode",
				"{\"version\":" + version + ",\"purchaseMode\":\"DIRECT\"}").expectStatus(200);
		assertThat(jdbc.queryForObject("SELECT charged_company_id FROM purchase_request WHERE id = ?", Long.class, id))
			.isEqualTo(charged);
		assertThat(jdbc.queryForObject("SELECT buying_company_id FROM purchase_request WHERE id = ?", Long.class, id))
			.isEqualTo(companyId("DEMO-TN-PLT"));

		signedIn("requester.tn@demo.local").get("/api/v1/admin/organization/companies").expectStatus(403);
		ApiClient admin = signedIn("admin@demo.local");
		admin.get("/api/v1/admin/organization/companies").expectStatus(200);
		admin.get("/api/v1/admin/organization/companies/999999999/sites").expectStatus(404);
	}

	@Test
	void supplierImportIsAllOrNothingAndDuplicatesAreRejected() {
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		String ok = "code,legalName,country,visibility\nDEMO-IMP-1,Imported One (Demo),TN,SHARED\n";
		mehdi.upload("/api/v1/imports?kind=SUPPLIER", "suppliers.csv", "text/csv", ok.getBytes(), null).expectStatus(200);
		String dup = "code,legalName,country,visibility\nDEMO-SKF,Duplicate (Demo),SE,SHARED\n";
		assertThat(mehdi.upload("/api/v1/imports?kind=SUPPLIER", "dup.csv", "text/csv", dup.getBytes(), null)
			.expectStatus(422)
			.code()).isEqualTo("import-rejected");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM supplier WHERE code = 'DEMO-SKF'", Integer.class)).isEqualTo(1);
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
		ApiClient.Response draft = client.post("/api/v1/requests", json(body)).expectStatus(201);
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

	private static String json(Object value) {
		return JSON.writeValueAsString(value);
	}

}
