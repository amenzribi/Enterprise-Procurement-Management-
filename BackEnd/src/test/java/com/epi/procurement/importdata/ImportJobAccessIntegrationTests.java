package com.epi.procurement.importdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

/**
 * Sprint 2 security regression: import jobs are visible within their scope only (ADR-007). A company-scoped job
 * (organisation import): global administrators and administrators of that company. A group-level job (supplier
 * import): global administrators and its creator. Everyone else in the group gets the same 404 as for an id that does
 * not exist, with no status or counts. The jobs and the single-company administrators are created here, so nothing
 * depends on what other tests left in the database or on the order they ran in.
 */
class ImportJobAccessIntegrationTests extends SqlServerTestSupport {

	private static final String IMPORTS = "/api/v1/imports";

	/** Roles granted for this test only (removed after each test). */
	private final List<Long> grants = new ArrayList<>();

	/**
	 * Sites created by the organisation imports, removed after each test: the shared database keeps its reference
	 * data (RequestApiIntegrationTests expects the French plant to have one site).
	 */
	private final List<String> siteCodes = new ArrayList<>();

	private long tn;

	private long fr;

	private long cpa;

	@BeforeEach
	void singleCompanyRoles() {
		tn = companyId("DEMO-TN-PLT");
		fr = companyId("DEMO-FR-PLT");
		cpa = companyId("DEMO-CPA");
		grant("manager.tn@demo.local", "ADMIN", tn); // creator of company A's import
		grant("requester.tn@demo.local", "ADMIN", tn); // another administrator of company A
		grant("manager.fr@demo.local", "ADMIN", fr); // administrator of company B
		grant("buyer@demo.local", "PURCHASING_MANAGER", cpa); // a second purchasing manager of the group
	}

	@AfterEach
	void removeRoles() {
		for (long grant : grants) {
			jdbc.update("DELETE FROM user_role WHERE id = ?", grant);
		}
		grants.clear();
		for (String code : siteCodes) {
			jdbc.update("DELETE FROM site WHERE code = ?", code);
		}
		siteCodes.clear();
	}

	@Test
	void aCompanyScopedJobIsReadableByThatCompanysAdministratorsOnly() {
		ApiClient tnCreator = signedIn("manager.tn@demo.local");
		ApiClient tnAdmin = signedIn("requester.tn@demo.local");
		ApiClient frAdmin = signedIn("manager.fr@demo.local");
		ApiClient purchasingManager = signedIn("purchasing.manager@demo.local");
		ApiClient otherPurchasingManager = signedIn("buyer@demo.local");
		ApiClient global = signedIn("global.admin@demo.local");

		long jobA = organisationImport(tnCreator, tn, siteCsv()).expectStatus(200).id();
		long jobB = organisationImport(frAdmin, fr, siteCsv()).expectStatus(200).id();

		for (ApiClient allowed : List.of(tnCreator, tnAdmin, global)) {
			ApiClient.Response job = allowed.get(IMPORTS + "/" + jobA).expectStatus(200);
			assertThat((String) job.json("$.status")).isEqualTo("COMMITTED");
			assertThat(((Number) job.json("$.accepted")).intValue()).isEqualTo(1);
		}
		for (ApiClient refused : List.of(frAdmin, purchasingManager, otherPurchasingManager)) {
			assertConcealed(refused, jobA);
		}
		frAdmin.get(IMPORTS + "/" + jobB).expectStatus(200);
		assertConcealed(tnCreator, jobB);
		assertConcealed(tnAdmin, jobB);
		global.get(IMPORTS + "/" + jobB).expectStatus(200);
	}

	@Test
	void theCreatorOfACompanyScopedJobLosesItWithTheCompany() {
		ApiClient creator = signedIn("manager.tn@demo.local");
		long job = organisationImport(creator, tn, siteCsv()).expectStatus(200).id();
		creator.get(IMPORTS + "/" + job).expectStatus(200);

		// Now an administrator of company B only: still allowed to import, no longer entitled to company A's job.
		jdbc.update("DELETE FROM user_role WHERE id = ?", grants.remove(0));
		grant("manager.tn@demo.local", "ADMIN", fr);
		assertConcealed(signedIn("manager.tn@demo.local"), job);
	}

	@Test
	void aGroupLevelSupplierJobIsReadableByItsCreatorAndGlobalAdministratorsOnly() {
		ApiClient creator = signedIn("purchasing.manager@demo.local");
		long job = supplierImport(creator, supplierCsv()).expectStatus(200).id();

		creator.get(IMPORTS + "/" + job).expectStatus(200);
		signedIn("global.admin@demo.local").get(IMPORTS + "/" + job).expectStatus(200);
		for (String refused : List.of("buyer@demo.local", "manager.tn@demo.local", "requester.tn@demo.local",
				"manager.fr@demo.local")) {
			assertConcealed(signedIn(refused), job);
		}
	}

	@Test
	void theSameFileNeverReturnsAJobOfAnotherScopeOrAnotherCreator() {
		ApiClient tnCreator = signedIn("manager.tn@demo.local");
		ApiClient frAdmin = signedIn("manager.fr@demo.local");
		String sites = siteCsv();
		String code = sites.lines().skip(1).findFirst().orElseThrow().split(",")[0];
		long jobA = organisationImport(tnCreator, tn, sites).expectStatus(200).id();

		// The same file for company B: a job of its own, and B's site is created (before: A's job came back).
		ApiClient.Response forB = organisationImport(frAdmin, fr, sites).expectStatus(200);
		assertThat(forB.id()).isNotEqualTo(jobA);
		assertThat(forB.body()).doesNotContain("\"id\":" + jobA);
		assertThat(count("SELECT COUNT(*) FROM site WHERE company_id = ? AND code = ?", fr, code)).isEqualTo(1);
		// Within the same scope, by an administrator entitled to it, the earlier result is still returned.
		assertThat(organisationImport(signedIn("requester.tn@demo.local"), tn, sites).expectStatus(200).id())
			.isEqualTo(jobA);

		// A group-level supplier file imported by one purchasing manager: another one does not get that job back.
		String suppliers = supplierCsv();
		long supplierJob = supplierImport(signedIn("purchasing.manager@demo.local"), suppliers).expectStatus(200).id();
		ApiClient.Response again = supplierImport(signedIn("buyer@demo.local"), suppliers).expectStatus(422);
		assertThat(again.code()).isEqualTo("import-rejected");
		assertThat(((Number) again.json("$.jobId")).longValue()).isNotEqualTo(supplierJob);
		assertThat(jdbc.queryForObject("SELECT status FROM import_job WHERE id = ?", String.class, supplierJob))
			.isEqualTo("COMMITTED");
	}

	@Test
	void aSupplierImportTakesNoCompanyFromTheRequest() {
		long before = count("SELECT COUNT(*) FROM import_job");
		signedIn("purchasing.manager@demo.local")
			.upload(IMPORTS + "?kind=SUPPLIER&companyId=" + tn, "suppliers.csv", "text/csv",
					supplierCsv().getBytes(StandardCharsets.UTF_8), null)
			.expectStatus(400);
		assertThat(count("SELECT COUNT(*) FROM import_job")).isEqualTo(before);
	}

	/** Refused exactly like an unknown id: 404, the same body apart from the path, no job details. */
	private void assertConcealed(ApiClient client, long job) {
		ApiClient.Response refused = client.get(IMPORTS + "/" + job).expectStatus(404);
		String unknown = client.get(IMPORTS + "/999999001").expectStatus(404).body();
		assertThat(withoutInstance(refused.body())).isEqualTo(withoutInstance(unknown));
		assertThat(refused.body()).doesNotContain("COMMITTED").doesNotContain("accepted").doesNotContain("rejected");
	}

	private static String withoutInstance(String problem) {
		return problem.replaceAll("\"instance\":\"[^\"]*\",?", "");
	}

	private ApiClient.Response organisationImport(ApiClient client, long companyId, String csv) {
		return client.upload(IMPORTS + "?kind=ORGANISATION&companyId=" + companyId, "sites.csv", "text/csv",
				csv.getBytes(StandardCharsets.UTF_8), null);
	}

	private static ApiClient.Response supplierImport(ApiClient client, String csv) {
		return client.upload(IMPORTS + "?kind=SUPPLIER", "suppliers.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8),
				null);
	}

	private String siteCsv() {
		String code = "S2-IMP-" + UUID.randomUUID().toString().substring(0, 8);
		siteCodes.add(code);
		return "code,name,type\n" + code + ",Import site (Demo),BRANCH\n";
	}

	private static String supplierCsv() {
		return "code,legalName,country,visibility\nS2-IMP-" + UUID.randomUUID().toString().substring(0, 8)
				+ ",Imported supplier (Demo),TN,SHARED\n";
	}

	private void grant(String email, String role, long companyId) {
		long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_user_role", Long.class);
		jdbc.update("INSERT INTO user_role (id, user_id, role, company_id) VALUES (?, ?, ?, ?)", id, userId(email), role,
				companyId);
		grants.add(id);
	}

	private long count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

}
