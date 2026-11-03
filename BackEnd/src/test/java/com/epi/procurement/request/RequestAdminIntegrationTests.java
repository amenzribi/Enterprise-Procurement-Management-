package com.epi.procurement.request;

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

/**
 * GLOBAL_ADMIN request administration against SQL Server: permissions, matrix actions, cleanup preview/execute,
 * archive filters and protected UAT reference.
 */
class RequestAdminIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	@Test
	void onlyGlobalAdminHoldsRequestAdminCapability() {
		ApiClient global = signedIn("global.admin@demo.local");
		assertThat((List<String>) global.get("/api/v1/me").expectStatus(200).json("$.permissions"))
			.contains("REQUEST_ADMIN")
			.doesNotContain("PURCHASING_ACT", "APPROVAL_ACT");

		assertThat((List<String>) signedIn("admin@demo.local").get("/api/v1/me").json("$.permissions"))
			.doesNotContain("REQUEST_ADMIN");
		assertThat((List<String>) signedIn("requester.tn@demo.local").get("/api/v1/me").json("$.permissions"))
			.doesNotContain("REQUEST_ADMIN");
		assertThat((List<String>) signedIn("buyer@demo.local").get("/api/v1/me").json("$.permissions"))
			.doesNotContain("REQUEST_ADMIN");

		signedIn("admin@demo.local").get("/api/v1/admin/requests").expectStatus(403);
		signedIn("requester.tn@demo.local").get("/api/v1/admin/requests").expectStatus(403);
		global.get("/api/v1/admin/requests?size=5").expectStatus(200);
	}

	@Test
	void groupBoundaryHidesForeignGroupRequests() {
		ApiClient global = signedIn("global.admin@demo.local");
		long foreignGroup = ensureForeignGroup();
		long foreignCompany = jdbc.queryForObject("SELECT TOP 1 id FROM company WHERE group_id = ?", Long.class,
				foreignGroup);
		long requester = userId("requester.tn@demo.local");
		long id = jdbc.queryForObject("""
				INSERT INTO purchase_request (group_id, requesting_company_id, charged_company_id, buying_company_id,
				  currency_code, requester_user_id, entered_by_user_id, updated_by_user_id, status, stage, title,
				  total_amount, current_owner_user_id, owner_since, created_at, updated_at, version, origin, round_no)
				OUTPUT INSERTED.id
				VALUES (?, ?, ?, ?, 'TND', ?, ?, ?, 'DRAFT', 'REQUEST', N'Foreign group draft (test)', 1,
				  ?, SYSUTCDATETIME(), SYSUTCDATETIME(), SYSUTCDATETIME(), 0, 'INTERNAL', 0)""", Long.class, foreignGroup,
				foreignCompany, foreignCompany, foreignCompany, requester, requester, requester, requester);
		try {
			global.get("/api/v1/admin/requests/" + id).expectStatus(404);
			assertThat((List<Integer>) global.get("/api/v1/admin/requests?q=Foreign%20group%20draft").expectStatus(200)
				.json("$.items[*].id")).doesNotContain((int) id);
		}
		finally {
			jdbc.update("DELETE FROM purchase_request WHERE id = ?", id);
		}
	}

	@Test
	void draftCorrectDeleteCancelReturnArchiveCleanupAndCommitmentBlock() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		ApiClient global = signedIn("global.admin@demo.local");

		// Isolated draft for correction + delete.
		ApiClient.Response draft = sami.post("/api/v1/requests", json(tnDraft("Admin-IT draft cleanup")))
			.expectStatus(201);
		long draftId = draft.id();
		ApiClient.Response corrected = global
			.post("/api/v1/admin/requests/" + draftId + "/correct-draft",
					"{\"version\":" + draft.version()
							+ ",\"reason\":\"Spelling fix for IT\",\"title\":\"Admin-IT draft corrected\"}")
			.expectStatus(200);
		assertThat((String) corrected.json("$.detail.title")).isEqualTo("Admin-IT draft corrected");
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM audit_event WHERE entity_id = ? AND action = 'REQUEST_ADMIN_DRAFT_CORRECTED'",
				Integer.class, Long.toString(draftId))).isEqualTo(1);

		global.delete("/api/v1/admin/requests/" + draftId + "?version=" + corrected.json("$.detail.version")
				+ "&reason=Isolated%20IT%20draft").expectStatus(204);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_request WHERE id = ?", Integer.class, draftId))
			.isZero();
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM audit_event WHERE entity_id = ? AND action = 'REQUEST_ADMIN_DRAFT_DELETED'",
				Integer.class, Long.toString(draftId))).isEqualTo(1);

		// Protected UAT reference never hard-deleted.
		Long protectedId = jdbc.query(
				"SELECT id FROM purchase_request WHERE number = ?",
				rs -> rs.next() ? rs.getLong(1) : null, RequestAdminActions.PROTECTED_NUMBER);
		if (protectedId != null) {
			// Present on procurement_dev UAT data; may be absent on a clean procurement_test DB.
			ApiClient.Response protectedDetail = global.get("/api/v1/admin/requests/" + protectedId).expectStatus(200);
			assertThat((List<Boolean>) protectedDetail.json("$.actions[?(@.action=='DELETE_DRAFT')].allowed"))
				.containsExactly(false);
			assertThat(global.delete("/api/v1/admin/requests/" + protectedId + "?reason=should-fail").expectStatus(409)
				.code()).isEqualTo("protected-record");
		}

		// Return + cancel of in-approval request (isolated).
		ApiClient.Response created = sami.post("/api/v1/requests", json(tnDraft("Admin-IT return cancel")))
			.expectStatus(201);
		long approvalId = sami
			.post("/api/v1/requests/" + created.id() + "/submit", "{\"version\":" + created.version() + "}")
			.expectStatus(200)
			.id();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_task WHERE request_id = ? AND status = 'PENDING'",
				Integer.class, approvalId)).isPositive();
		int pendingBefore = jdbc.queryForObject(
				"SELECT COUNT(*) FROM approval_link_token k JOIN approval_task t ON t.id = k.task_id "
						+ "WHERE t.request_id = ? AND k.used_at IS NULL",
				Integer.class, approvalId);

		ApiClient.Response detail = global.get("/api/v1/admin/requests/" + approvalId).expectStatus(200);
		int version = ((Number) detail.json("$.version")).intValue();
		global.post("/api/v1/admin/requests/" + approvalId + "/return-for-revision",
				"{\"version\":" + version + ",\"reason\":\"Admin return for IT\"}").expectStatus(200);
		assertThat((String) sami.get("/api/v1/requests/" + approvalId).json("$.status")).isEqualTo("REVISION_REQUESTED");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_task WHERE request_id = ? AND status = 'PENDING'",
				Integer.class, approvalId)).isZero();

		// Resubmit then cancel.
		ApiClient.Response editable = sami.get("/api/v1/requests/" + approvalId).expectStatus(200);
		Map<String, Object> body = tnDraft("Admin-IT return cancel");
		body.put("version", editable.version());
		ApiClient.Response saved = sami.put("/api/v1/requests/" + approvalId, json(body)).expectStatus(200);
		sami.post("/api/v1/requests/" + approvalId + "/submit", "{\"version\":" + saved.version() + "}")
			.expectStatus(200);
		detail = global.get("/api/v1/admin/requests/" + approvalId).expectStatus(200);
		global.post("/api/v1/admin/requests/" + approvalId + "/cancel",
				"{\"version\":" + detail.json("$.version") + ",\"reason\":\"Admin cancel IT\"}").expectStatus(200);
		assertThat((String) global.get("/api/v1/admin/requests/" + approvalId).json("$.status")).isEqualTo("CANCELLED");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_task WHERE request_id = ? AND status = 'PENDING'",
				Integer.class, approvalId)).isZero();
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM approval_link_token k JOIN approval_task t ON t.id = k.task_id "
						+ "WHERE t.request_id = ? AND k.used_at IS NULL",
				Integer.class, approvalId)).isZero();
		if (pendingBefore > 0) {
			assertThat(jdbc.queryForObject(
					"SELECT COUNT(*) FROM approval_link_token k JOIN approval_task t ON t.id = k.task_id "
							+ "WHERE t.request_id = ? AND k.used_at IS NOT NULL",
					Integer.class, approvalId)).isPositive();
		}

		// Commitment block.
		ApiClient.Response orderedDraft = sami.post("/api/v1/requests", json(tnDraft("Admin-IT ordered block")))
			.expectStatus(201);
		long orderedId = sami
			.post("/api/v1/requests/" + orderedDraft.id() + "/submit", "{\"version\":" + orderedDraft.version() + "}")
			.expectStatus(200)
			.id();
		jdbc.update("UPDATE purchase_request SET status = 'ORDERED', stage = 'COMMITMENT' WHERE id = ?", orderedId);
		assertThat(global.post("/api/v1/admin/requests/" + orderedId + "/cancel",
				"{\"reason\":\"Should fail on commitment\"}").expectStatus(409).code()).isEqualTo("commitment-exists");
		assertThat(global.delete("/api/v1/admin/requests/" + orderedId + "?reason=not-draft-check").expectStatus(409).code())
			.isEqualTo("not-draft");

		// Archive / restore terminal cancelled request; business status unchanged; everyday list excludes archived.
		detail = global.get("/api/v1/admin/requests/" + approvalId).expectStatus(200);
		global.post("/api/v1/admin/requests/" + approvalId + "/archive",
				"{\"version\":" + detail.json("$.version") + ",\"reason\":\"Archive IT cancel\"}").expectStatus(200);
		assertThat((Boolean) global.get("/api/v1/admin/requests/" + approvalId).json("$.archived")).isTrue();
		assertThat((String) global.get("/api/v1/admin/requests/" + approvalId).json("$.status")).isEqualTo("CANCELLED");
		assertThat((List<Integer>) sami.get("/api/v1/requests?scope=mine&size=100").expectStatus(200).json("$.items[*].id"))
			.doesNotContain((int) approvalId);
		assertThat((List<Integer>) global.get("/api/v1/admin/requests?archived=true&q=Admin-IT%20return")
			.expectStatus(200)
			.json("$.items[*].id")).contains((int) approvalId);

		detail = global.get("/api/v1/admin/requests/" + approvalId).expectStatus(200);
		global.post("/api/v1/admin/requests/" + approvalId + "/restore",
				"{\"version\":" + detail.json("$.version") + ",\"reason\":\"Restore IT\"}").expectStatus(200);
		assertThat((Boolean) global.get("/api/v1/admin/requests/" + approvalId).json("$.archived")).isFalse();
		assertThat((String) global.get("/api/v1/admin/requests/" + approvalId).json("$.status")).isEqualTo("CANCELLED");

		// Cleanup preview + one-shot execute.
		ApiClient.Response cleanupDraft = sami.post("/api/v1/requests", json(tnDraft("Admin-IT cleanup candidate")))
			.expectStatus(201);
		long cleanupId = cleanupDraft.id();
		ApiClient.Response preview = global
			.post("/api/v1/admin/requests/cleanup/preview",
					"{\"ids\":[" + cleanupId + "],\"draftsOnly\":true}")
			.expectStatus(200);
		String token = (String) preview.json("$.previewToken");
		assertThat(((Number) preview.json("$.deletableCount")).intValue()).isGreaterThanOrEqualTo(1);
		ApiClient.Response executed = global.post("/api/v1/admin/requests/cleanup/execute",
				"{\"previewToken\":\"" + token + "\",\"ids\":[" + cleanupId
						+ "],\"confirmation\":\"APPLY\",\"reason\":\"IT cleanup batch\"}")
			.expectStatus(200);
		assertThat(((Number) executed.json("$.succeeded")).intValue()).isGreaterThanOrEqualTo(1);
		assertThat(global.post("/api/v1/admin/requests/cleanup/execute",
				"{\"previewToken\":\"" + token + "\",\"ids\":[" + cleanupId
						+ "],\"confirmation\":\"APPLY\",\"reason\":\"retry\"}")
			.expectStatus(422)
			.code()).isEqualTo("preview-expired");

		// Stale version conflict.
		ApiClient.Response staleDraft = sami.post("/api/v1/requests", json(tnDraft("Admin-IT stale version")))
			.expectStatus(201);
		assertThat(global
			.post("/api/v1/admin/requests/" + staleDraft.id() + "/correct-draft",
					"{\"version\":" + (staleDraft.version() - 1)
							+ ",\"reason\":\"stale\",\"title\":\"Should conflict\"}")
			.expectStatus(409)
			.code()).isEqualTo("version-conflict");
		global.delete("/api/v1/admin/requests/" + staleDraft.id() + "?version=" + staleDraft.version()
				+ "&reason=cleanup%20stale%20fixture").expectStatus(204);
	}

	private long ensureForeignGroup() {
		Long existing = jdbc.query("SELECT TOP 1 id FROM organization_group WHERE code = 'IT-OTHER-GRP'",
				rs -> rs.next() ? rs.getLong(1) : null);
		if (existing != null) {
			return existing;
		}
		long groupId = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_organization_group", Long.class);
		jdbc.update("""
				INSERT INTO organization_group (id, code, name, consolidation_currency)
				VALUES (?, 'IT-OTHER-GRP', N'IT other group', 'TND')""", groupId);
		long companyId = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_company", Long.class);
		jdbc.update("""
				INSERT INTO company (id, group_id, code, legal_name, company_type, country_code, currency_code,
				  default_language, time_zone, purchasing_mode)
				VALUES (?, ?, 'IT-OTHER-CO', N'IT Other Co', 'SUBSIDIARY', 'TN', 'TND', 'en', 'Africa/Tunis',
				  'CENTRALIZED')""", companyId, groupId);
		return groupId;
	}

	private Map<String, Object> tnDraft(String title) {
		long tn = companyId("DEMO-TN-PLT");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("categoryId", jdbc.queryForObject(
				"SELECT c.id FROM request_category c JOIN organization_group g ON g.id = c.group_id "
						+ "WHERE g.code = 'DEMO-GRP' AND c.code = 'DEMO-SPARE'",
				Long.class));
		body.put("priority", "NORMAL");
		body.put("purchaseType", "LOCAL");
		body.put("currency", "TND");
		body.put("departmentId", jdbc.queryForObject(
				"SELECT id FROM department WHERE company_id = ? AND code = 'DEMO-MAINT'", Long.class, tn));
		body.put("requiredDate", LocalDate.now(TUNIS).plusDays(20).toString());
		body.put("lines", new ArrayList<>(List.of(Map.of("description", title + " line", "quantity", 1, "unit", "PCE",
				"estimatedUnitPrice", 25))));
		return body;
	}

	private static String json(Object value) {
		return JSON.writeValueAsString(value);
	}

}
