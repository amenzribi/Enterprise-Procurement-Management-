package com.epi.procurement.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

import tools.jackson.databind.json.JsonMapper;

/**
 * The request API over real HTTP with real sessions against SQL Server: journey, visibility, company isolation,
 * identifier manipulation, validation, totals and list parameters (S1-AC-03 to 08, 11, 14).
 */
class RequestApiIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	@Autowired
	AuditLog auditLog;

	@Test
	void enterOnBehalfEditSubmitAndVisibilityJourney() {
		ApiClient assistant = signedIn("assistant.tn@demo.local");
		ApiClient requester = signedIn("requester.tn@demo.local");
		long requesterId = userId("requester.tn@demo.local");

		ApiClient.Response options = assistant.get("/api/v1/requests/form-options").expectStatus(200);
		assertThat((Boolean) options.json("$.companies[0].canEnterOnBehalf")).isTrue();
		assertThat((List<Integer>) options.json("$.companies[0].requesters[*].id")).contains((int) requesterId);

		Map<String, Object> body = completeTnRequest("Bearings for line 2");
		body.put("requesterId", requesterId);
		ApiClient.Response created = assistant.post("/api/v1/requests", json(body)).expectStatus(201);
		long id = created.id();
		assertThat((String) created.json("$.status")).isEqualTo("DRAFT");
		assertThat((Object) created.json("$.number")).isNull();
		assertThat((String) created.json("$.enteredBy.displayName")).isEqualTo("Amel Trabelsi (Demo)");
		assertThat(((Number) created.json("$.requester.id")).longValue()).isEqualTo(requesterId);
		assertThat(((Number) created.json("$.currentOwner.id")).longValue()).isEqualTo(requesterId);
		assertThat((String) created.json("$.chargedCompany.code")).isEqualTo("DEMO-TN-PLT");
		assertThat((String) created.json("$.buyingCompany.code")).isEqualTo("DEMO-TN-PLT");
		assertThat((Boolean) created.json("$.buyingCompanyProposed")).isTrue();
		assertThat((List<String>) created.json("$.actions")).containsExactly("EDIT", "SUBMIT", "DISCARD");
		assertThat((List<Object>) created.json("$.transitions")).hasSize(1);

		// The requester sees it in "My requests" and can edit it.
		assertThat((List<Integer>) requester.get("/api/v1/requests?scope=mine").expectStatus(200).json("$.items[*].id"))
			.contains((int) id);
		int version = created.version();
		requester.put("/api/v1/requests/" + id, json(withVersion(body, version + 1))).expectStatus(409);

		// A line-only edit still increments the version, so the assistant's older copy is rejected (S1DR-06).
		List<Map<String, Object>> lines = lines(body);
		lines.get(0).put("quantity", 12);
		ApiClient.Response edited = requester.put("/api/v1/requests/" + id, json(withVersion(body, version)))
			.expectStatus(200);
		assertThat(edited.version()).isGreaterThan(version);
		ApiClient.Response lost = assistant.put("/api/v1/requests/" + id, json(withVersion(body, version)))
			.expectStatus(409);
		assertThat(lost.code()).isEqualTo("stale-version");

		ApiClient.Response submitted = requester
			.post("/api/v1/requests/" + id + "/submit", "{\"version\":" + edited.version() + "}")
			.expectStatus(200);
		// Sprint 2: a submission enters the approval circuit (shown to users as "Submitted").
		assertThat((String) submitted.json("$.status")).isEqualTo("IN_APPROVAL");
		assertThat((String) submitted.json("$.userStatus")).isEqualTo("SUBMITTED");
		assertThat((String) submitted.json("$.number")).matches("DEMO-TN-PLT-PR-\\d{4}-\\d{6}");
		assertThat((String) submitted.json("$.submittedAt")).isNotBlank();
		assertThat((String) submitted.json("$.stage")).isEqualTo("APPROVAL");
		assertThat((List<String>) submitted.json("$.lines[*].status")).containsOnly("CREATED");
		assertThat((List<String>) submitted.json("$.transitions[*].toStatus")).containsExactly("DRAFT", "IN_APPROVAL");
		assertThat((List<String>) submitted.json("$.actions")).isEmpty();

		// No second submission, no edit, no discard after submission.
		String versionBody = "{\"version\":" + submitted.version() + "}";
		assertThat(requester.post("/api/v1/requests/" + id + "/submit", versionBody).expectStatus(409).code())
			.isEqualTo("invalid-state");
		requester.put("/api/v1/requests/" + id, json(withVersion(body, submitted.version()))).expectStatus(409);
		requester.delete("/api/v1/requests/" + id + "?version=" + submitted.version()).expectStatus(409);

		// Audit trail: created and submitted events in the company chain, chain intact.
		long tn = companyId("DEMO-TN-PLT");
		assertThat(jdbc.queryForList("SELECT action FROM audit_event WHERE entity_type = 'PURCHASE_REQUEST' "
				+ "AND entity_id = ? ORDER BY chain_seq", String.class, Long.toString(id)))
			.containsExactly("DRAFT_CREATED", "SUBMITTED");
		assertThat(auditLog.verifyChain("company:" + tn).valid()).isTrue();

		// Visibility after submission (sprint-1 visibility table).
		ApiClient manager = signedIn("manager.tn@demo.local");
		assertThat((List<Integer>) manager.get("/api/v1/requests?scope=visible").json("$.items[*].id")).contains((int) id);
		assertThat((List<String>) manager.get("/api/v1/requests/" + id).expectStatus(200).json("$.actions")).isEmpty();
		signedIn("ceo@demo.local").get("/api/v1/requests/" + id).expectStatus(200);
		signedIn("buyer@demo.local").get("/api/v1/requests/" + id).expectStatus(404);
		signedIn("requester.fr@demo.local").get("/api/v1/requests/" + id).expectStatus(404);
	}

	@Test
	void draftsAreVisibleOnlyToTheirAuthors() {
		ApiClient requester = signedIn("requester.tn@demo.local");
		long id = requester.post("/api/v1/requests", json(completeTnRequest("Private draft"))).expectStatus(201).id();

		for (String other : List.of("manager.tn@demo.local", "ceo@demo.local", "assistant.tn@demo.local",
				"requester.fr@demo.local", "admin@demo.local")) {
			ApiClient client = signedIn(other);
			client.get("/api/v1/requests/" + id).expectStatus(404);
			assertThat((List<Integer>) client.get("/api/v1/requests?scope=visible&size=100").json("$.items[*].id"))
				.doesNotContain((int) id);
		}
	}

	@Test
	void anotherCompanysRequestIsIndistinguishableFromANonExistentOne() {
		ApiClient owner = signedIn("requester.tn@demo.local");
		ApiClient.Response created = owner.post("/api/v1/requests", json(completeTnRequest("Isolation"))).expectStatus(201);
		long id = created.id();
		long missing = 999_999_999L;
		ApiClient other = signedIn("requester.fr@demo.local");
		String update = json(withVersion(completeTnRequest("Hijack"), created.version()));
		String version = "{\"version\":" + created.version() + "}";

		assertSameNotFound(other.get("/api/v1/requests/" + id), other.get("/api/v1/requests/" + missing));
		assertSameNotFound(other.put("/api/v1/requests/" + id, update), other.put("/api/v1/requests/" + missing, update));
		assertSameNotFound(other.post("/api/v1/requests/" + id + "/submit", version),
				other.post("/api/v1/requests/" + missing + "/submit", version));
		assertSameNotFound(other.delete("/api/v1/requests/" + id + "?version=0"),
				other.delete("/api/v1/requests/" + missing + "?version=0"));
		assertThat((List<Integer>) other.get("/api/v1/requests?scope=visible&size=100").json("$.items[*].id"))
			.doesNotContain((int) id);

		// Nothing changed.
		ApiClient.Response unchanged = owner.get("/api/v1/requests/" + id).expectStatus(200);
		assertThat((String) unchanged.json("$.title")).isEqualTo("Isolation");
		assertThat((String) unchanged.json("$.status")).isEqualTo("DRAFT");
	}

	@Test
	void identifiersOutsideTheRulesAreRejected() {
		ApiClient requester = signedIn("requester.tn@demo.local");
		long fr = companyId("DEMO-FR-PLT");
		long frDepartment = jdbc.queryForObject("SELECT id FROM department WHERE company_id = ? AND code = 'DEMO-MAINT'",
				Long.class, fr);
		long frSite = jdbc.queryForObject("SELECT id FROM site WHERE company_id = ?", Long.class, fr);
		long frCostCenter = jdbc.queryForObject("SELECT id FROM cost_center WHERE company_id = ?", Long.class, fr);

		assertFieldError(requester, Map.of("departmentId", frDepartment), "departmentId", "not-in-company");
		assertFieldError(requester, Map.of("siteId", frSite), "siteId", "not-in-company");
		assertFieldError(requester, Map.of("costCenterId", frCostCenter), "costCenterId", "not-in-company");
		assertFieldError(requester, Map.of("beneficiarySiteId", frSite), "beneficiarySiteId", "not-in-company");
		assertFieldError(requester, Map.of("requesterId", userId("requester.fr@demo.local")), "requesterId",
				"requester-not-allowed");
		// Same company, but requester.tn has no ENTER_ON_BEHALF permission.
		assertFieldError(requester, Map.of("requesterId", userId("assistant.tn@demo.local")), "requesterId",
				"requester-not-allowed");
		assertFieldError(requester, Map.of("requestingCompanyId", fr), "requestingCompanyId", "company-not-allowed");
		for (String serverField : List.of("chargedCompanyId", "buyingCompanyId", "number", "status", "enteredById",
				"totalAmount", "origin")) {
			assertFieldError(requester, Map.of(serverField, serverField.endsWith("Id") ? fr : "X"), serverField,
					"not-editable");
		}
	}

	@Test
	void onlyRequestersCanCreate() {
		for (String user : List.of("buyer@demo.local", "ceo@demo.local", "admin@demo.local", "manager.tn@demo.local")) {
			ApiClient client = signedIn(user);
			assertThat(client.post("/api/v1/requests", json(completeTnRequest("Not allowed"))).expectStatus(403).code())
				.isEqualTo("forbidden");
			assertThat((List<Object>) client.get("/api/v1/requests/form-options").expectStatus(200).json("$.companies"))
				.isEmpty();
		}
	}

	@Test
	void lineValuesAreValidated() {
		ApiClient requester = signedIn("requester.tn@demo.local");

		assertLineError(requester, Map.of("quantity", 0), "lines[0].quantity", "quantity-positive");
		assertLineError(requester, Map.of("quantity", -1), "lines[0].quantity", "quantity-positive");
		assertLineError(requester, Map.of("quantity", 1.23456), "lines[0].quantity", "max-decimals");
		assertLineError(requester, Map.of("estimatedUnitPrice", -0.5), "lines[0].estimatedUnitPrice",
				"price-not-negative");
		assertLineError(requester, Map.of("estimatedUnitPrice", 1.00001), "lines[0].estimatedUnitPrice", "max-decimals");
		assertLineError(requester, Map.of("unit", "XX"), "lines[0].unit", "invalid-value");

		Map<String, Object> hundred = completeTnRequest("100 lines");
		hundred.put("lines", copies(line("Part", 1, "PCE", 1), 100));
		requester.post("/api/v1/requests", json(hundred)).expectStatus(201);
		Map<String, Object> tooMany = completeTnRequest("101 lines");
		tooMany.put("lines", copies(line("Part", 1, "PCE", 1), 101));
		ApiClient.Response refused = requester.post("/api/v1/requests", json(tooMany)).expectStatus(422);
		assertThat((String) refused.json("$.errors[0].code")).isEqualTo("too-many-lines");
		assertThat((Integer) refused.json("$.errors[0].params.max")).isEqualTo(100);
	}

	@Test
	void submissionRequiresCompleteDataAndFutureDates() {
		ApiClient requester = signedIn("requester.tn@demo.local");
		Map<String, Object> minimal = new LinkedHashMap<>();
		minimal.put("title", "Incomplete");
		ApiClient.Response draft = requester.post("/api/v1/requests", json(minimal)).expectStatus(201);

		ApiClient.Response refused = requester
			.post("/api/v1/requests/" + draft.id() + "/submit", "{\"version\":" + draft.version() + "}")
			.expectStatus(422);
		assertThat((List<String>) refused.json("$.errors[*].field")).contains("categoryId", "priority", "purchaseType",
				"departmentId", "requiredDate", "lines");

		Map<String, Object> urgent = completeTnRequest("Urgent without reason");
		urgent.put("priority", "URGENT");
		urgent.put("requiredDate", LocalDate.now(TUNIS).minusDays(1).toString());
		ApiClient.Response urgentDraft = requester.post("/api/v1/requests", json(urgent)).expectStatus(201);
		ApiClient.Response urgentRefused = requester
			.post("/api/v1/requests/" + urgentDraft.id() + "/submit", "{\"version\":" + urgentDraft.version() + "}")
			.expectStatus(422);
		assertThat((List<String>) urgentRefused.json("$.errors[*].code")).contains("required", "required-date-past");
		assertThat((List<String>) urgentRefused.json("$.errors[*].field")).contains("urgencyJustification",
				"requiredDate");
		assertThat((String) requester.get("/api/v1/requests/" + urgentDraft.id()).json("$.status")).isEqualTo("DRAFT");
	}

	@Test
	void totalsAreRoundedToTheCurrencyMinorUnits() {
		ApiClient tn = signedIn("requester.tn@demo.local");
		Map<String, Object> body = completeTnRequest("Rounding TND");
		body.put("lines", new ArrayList<>(List.of(line("A", 1.5, "PCE", 1.2345), line("B", 3, "PCE", 0.3333))));
		ApiClient.Response tnd = tn.post("/api/v1/requests", json(body)).expectStatus(201);
		assertThat((String) tnd.json("$.currency")).isEqualTo("TND");
		assertThat((Double) tnd.json("$.lines[0].lineTotal")).isEqualTo(1.852);
		assertThat(((Number) tnd.json("$.lines[1].lineTotal")).doubleValue()).isEqualTo(1.0);
		assertThat((Double) tnd.json("$.totalAmount")).isEqualTo(2.852);

		body.put("currency", "EUR");
		ApiClient.Response eur = tn.post("/api/v1/requests", json(body)).expectStatus(201);
		assertThat((Double) eur.json("$.lines[0].lineTotal")).isEqualTo(1.85);
		assertThat((Double) eur.json("$.totalAmount")).isEqualTo(2.85);

		body.put("currency", "JPY");
		body.put("lines", new ArrayList<>(List.of(line("A", 2, "PCE", 10.5))));
		assertThat(((Number) tn.post("/api/v1/requests", json(body)).expectStatus(201).json("$.totalAmount")).intValue())
			.isEqualTo(21);
	}

	@Test
	void centralisedSubsidiariesProposeThePurchasingCentreAsBuyer() {
		ApiClient fr = signedIn("requester.fr@demo.local");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", "Composite resin");
		ApiClient.Response created = fr.post("/api/v1/requests", json(body)).expectStatus(201);

		assertThat((String) created.json("$.currency")).isEqualTo("EUR");
		assertThat((String) created.json("$.chargedCompany.code")).isEqualTo("DEMO-FR-PLT");
		assertThat((String) created.json("$.buyingCompany.code")).isEqualTo("DEMO-CPA");
		assertThat((Boolean) created.json("$.buyingCompanyProposed")).isTrue();
	}

	@Test
	void draftsCanBeDiscardedByTheirAuthor() {
		ApiClient requester = signedIn("requester.tn@demo.local");
		ApiClient.Response draft = requester.post("/api/v1/requests", json(completeTnRequest("To discard")))
			.expectStatus(201);

		requester.delete("/api/v1/requests/" + draft.id() + "?version=" + (draft.version() + 1)).expectStatus(409);
		requester.delete("/api/v1/requests/" + draft.id() + "?version=" + draft.version()).expectStatus(204);

		requester.get("/api/v1/requests/" + draft.id()).expectStatus(404);
		assertThat(jdbc.queryForList("SELECT action FROM audit_event WHERE entity_type = 'PURCHASE_REQUEST' "
				+ "AND entity_id = ?", String.class, Long.toString(draft.id())))
			.contains("DRAFT_DISCARDED");
	}

	@Test
	void listParametersAreValidatedAndApplied() {
		ApiClient requester = signedIn("requester.tn@demo.local");
		requester.post("/api/v1/requests", json(completeTnRequest("Findable pump seal"))).expectStatus(201);

		ApiClient.Response found = requester.get("/api/v1/requests?q=pump%20seal&sort=title,asc&size=5")
			.expectStatus(200);
		assertThat((List<String>) found.json("$.items[*].title")).contains("Findable pump seal");
		assertThat((Integer) found.json("$.size")).isEqualTo(5);
		assertThat(((Number) found.json("$.totalItems")).intValue()).isPositive();
		assertThat((List<String>) requester.get("/api/v1/requests?status=DRAFT").json("$.items[*].status"))
			.containsOnly("DRAFT");

		for (String invalid : List.of("sort=password", "sort=title,sideways", "size=101", "size=0", "status=NOPE",
				"scope=everyone", "page=-1")) {
			assertThat(requester.get("/api/v1/requests?" + invalid).expectStatus(400).code())
				.isEqualTo("invalid-parameter");
		}
	}

	/**
	 * QA1-D-02: users who can see a request but are not its editor get 403, whatever its status.
	 */
	@Test
	void visibleButNotEditableRequestsAnswerForbidden() {
		ApiClient requester = signedIn("requester.tn@demo.local");
		ApiClient.Response draft = requester.post("/api/v1/requests", json(completeTnRequest("Forbidden actions")))
			.expectStatus(201);
		ApiClient.Response submitted = requester
			.post("/api/v1/requests/" + draft.id() + "/submit", "{\"version\":" + draft.version() + "}")
			.expectStatus(200);
		String update = json(withVersion(completeTnRequest("Forbidden"), submitted.version()));
		String version = "{\"version\":" + submitted.version() + "}";

		for (String viewer : List.of("manager.tn@demo.local", "ceo@demo.local")) {
			ApiClient client = signedIn(viewer);
			client.get("/api/v1/requests/" + draft.id()).expectStatus(200);
			assertThat(client.put("/api/v1/requests/" + draft.id(), update).expectStatus(403).code()).isEqualTo("forbidden");
			assertThat(client.post("/api/v1/requests/" + draft.id() + "/submit", version).expectStatus(403).code())
				.isEqualTo("forbidden");
			assertThat(client.delete("/api/v1/requests/" + draft.id() + "?version=" + submitted.version())
				.expectStatus(403)
				.code()).isEqualTo("forbidden");
		}
		// The editor still gets the state conflict.
		assertThat(requester.put("/api/v1/requests/" + draft.id(), update).expectStatus(409).code())
			.isEqualTo("invalid-state");
	}

	/**
	 * S1CR-04: on update, an omitted requester means unchanged.
	 */
	@Test
	void omittedRequesterOnUpdateKeepsTheRequester() {
		ApiClient assistant = signedIn("assistant.tn@demo.local");
		long requesterId = userId("requester.tn@demo.local");
		Map<String, Object> body = completeTnRequest("On behalf, edited");
		body.put("requesterId", requesterId);
		ApiClient.Response created = assistant.post("/api/v1/requests", json(body)).expectStatus(201);

		body.remove("requesterId");
		ApiClient.Response updated = assistant
			.put("/api/v1/requests/" + created.id(), json(withVersion(body, created.version())))
			.expectStatus(200);

		assertThat(((Number) updated.json("$.requester.id")).longValue()).isEqualTo(requesterId);
		assertThat(((Number) updated.json("$.currentOwner.id")).longValue()).isEqualTo(requesterId);
	}

	/**
	 * S1CR-07: lines are matched by id; new lines have no id; lines not sent are deleted; numbers are stable.
	 */
	@Test
	void linesAreMatchedById() {
		ApiClient requester = signedIn("requester.tn@demo.local");
		Map<String, Object> body = completeTnRequest("Line identity");
		body.put("lines", new ArrayList<>(List.of(line("First", 1, "PCE", 1), line("Second", 2, "PCE", 1),
				line("Third", 3, "PCE", 1))));
		ApiClient.Response created = requester.post("/api/v1/requests", json(body)).expectStatus(201);
		List<Integer> ids = created.json("$.lines[*].id");

		Map<String, Object> first = line("First", 1, "PCE", 1);
		first.put("id", ids.get(0));
		Map<String, Object> third = line("Third, changed", 30, "PCE", 1);
		third.put("id", ids.get(2));
		body.put("lines", new ArrayList<>(List.of(first, third, line("Fourth", 4, "PCE", 1))));
		ApiClient.Response updated = requester
			.put("/api/v1/requests/" + created.id(), json(withVersion(body, created.version())))
			.expectStatus(200);

		assertThat((List<Integer>) updated.json("$.lines[*].id")).hasSize(3).startsWith(ids.get(0), ids.get(2));
		assertThat((List<Integer>) updated.json("$.lines[*].lineNo")).containsExactly(1, 3, 4);
		assertThat((List<String>) updated.json("$.lines[*].description")).containsExactly("First", "Third, changed",
				"Fourth");

		// A line id of another request is refused.
		ApiClient.Response other = requester.post("/api/v1/requests", json(completeTnRequest("Other"))).expectStatus(201);
		Map<String, Object> foreign = line("Foreign", 1, "PCE", 1);
		foreign.put("id", ((List<Integer>) other.json("$.lines[*].id")).get(0));
		body.put("lines", new ArrayList<>(List.of(foreign)));
		ApiClient.Response refused = requester
			.put("/api/v1/requests/" + created.id(), json(withVersion(body, updated.version())))
			.expectStatus(422);
		assertThat((String) refused.json("$.errors[0].field")).isEqualTo("lines[0].id");
		assertThat((String) refused.json("$.errors[0].code")).isEqualTo("invalid-value");
	}

	@Test
	void statusEnumerationIsPublished() {
		ApiClient.Response statuses = signedIn("requester.tn@demo.local").get("/api/v1/requests/statuses")
			.expectStatus(200);

		assertThat((List<String>) statuses.json("$[*].code")).hasSize(RequestStatus.values().length)
			.startsWith("DRAFT", "CREATED", "IN_APPROVAL")
			.doesNotContain("IN_TRANSIT");
		assertThat((Boolean) statuses.json("$[1].lineStatus")).isTrue();
		assertThat((Boolean) statuses.json("$[2].lineStatus")).isFalse();
	}

	private void assertSameNotFound(ApiClient.Response actual, ApiClient.Response nonExistent) {
		actual.expectStatus(404);
		nonExistent.expectStatus(404);
		// Identical apart from "instance", which only echoes the caller's own request path.
		assertThat(withoutInstance(actual.body())).isEqualTo(withoutInstance(nonExistent.body()));
	}

	private void assertFieldError(ApiClient client, Map<String, Object> override, String field, String code) {
		Map<String, Object> body = completeTnRequest("Manipulation " + field);
		body.putAll(override);
		ApiClient.Response response = client.post("/api/v1/requests", json(body)).expectStatus(422);
		assertThat((List<String>) response.json("$.errors[?(@.field == '" + field + "')].code")).as(field)
			.containsExactly(code);
	}

	private void assertLineError(ApiClient client, Map<String, Object> override, String field, String code) {
		Map<String, Object> body = completeTnRequest("Line " + field);
		Map<String, Object> line = line("Part", 1, "PCE", 1);
		line.putAll(override);
		body.put("lines", new ArrayList<>(List.of(line)));
		ApiClient.Response response = client.post("/api/v1/requests", json(body)).expectStatus(422);
		assertThat((List<String>) response.json("$.errors[?(@.field == '" + field + "')].code")).as(field)
			.contains(code);
	}

	/**
	 * A complete, valid request for requester.tn (company DEMO-TN-PLT, maintenance department).
	 */
	private Map<String, Object> completeTnRequest(String title) {
		long tn = companyId("DEMO-TN-PLT");
		long group = jdbc.queryForObject("SELECT group_id FROM company WHERE id = ?", Long.class, tn);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("categoryId", jdbc.queryForObject(
				"SELECT id FROM request_category WHERE group_id = ? AND code = 'DEMO-SPARE'", Long.class, group));
		body.put("priority", "NORMAL");
		body.put("purchaseType", "LOCAL");
		body.put("departmentId", jdbc.queryForObject(
				"SELECT id FROM department WHERE company_id = ? AND code = 'DEMO-MAINT'", Long.class, tn));
		body.put("siteId", jdbc.queryForObject("SELECT id FROM site WHERE company_id = ? AND code = 'DEMO-SFAX'",
				Long.class, tn));
		body.put("costCenterId", jdbc.queryForObject("SELECT id FROM cost_center WHERE company_id = ?", Long.class, tn));
		body.put("beneficiary", "Production line 2");
		body.put("justification", "Preventive maintenance");
		body.put("requiredDate", LocalDate.now(TUNIS).plusDays(30).toString());
		body.put("deliveryLocation", "Sfax plant store");
		body.put("lines", new ArrayList<>(List.of(line("Bearing SKF 6205", 10, "PCE", 12.5),
				line("Grease cartridge", 4, "PCE", 8.75))));
		return body;
	}

	private static Map<String, Object> line(String description, Number quantity, String unit, Number price) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("description", description);
		line.put("quantity", quantity);
		line.put("unit", unit);
		line.put("estimatedUnitPrice", price);
		return line;
	}

	private static List<Map<String, Object>> copies(Map<String, Object> line, int count) {
		List<Map<String, Object>> lines = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			lines.add(new LinkedHashMap<>(line));
		}
		return lines;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> lines(Map<String, Object> body) {
		return (List<Map<String, Object>>) body.get("lines");
	}

	private static Map<String, Object> withVersion(Map<String, Object> body, int version) {
		Map<String, Object> copy = new LinkedHashMap<>(body);
		copy.put("version", version);
		return copy;
	}

	private static String withoutInstance(String body) {
		return body.replaceAll("\"instance\":\"[^\"]*\",?", "");
	}

	private static String json(Object value) {
		return JSON.writeValueAsString(value);
	}

}
