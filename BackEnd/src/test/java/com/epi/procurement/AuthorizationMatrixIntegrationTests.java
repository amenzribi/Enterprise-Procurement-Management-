package com.epi.procurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

import tools.jackson.databind.json.JsonMapper;

/**
 * Authorization matrix over every endpoint of the application (audit A-4, roadmap item 1.16).
 * <p>
 * The endpoints are read from the running application, not from a list kept by hand, and each one is called as
 * three callers who must be refused:
 * <ul>
 * <li><b>anonymous</b>: no session. Expected 401, except the public endpoints named in {@link #PUBLIC}.</li>
 * <li><b>supplier</b>: a supplier contact. Expected 403 outside the supplier portal.</li>
 * <li><b>outsider</b>: a requester of the French plant with no other role, calling with the identifiers of objects
 * that belong to the Tunisian plant (company isolation and object-level authorization), and calling the endpoints
 * reserved for other roles (role-based access, privilege escalation).</li>
 * </ul>
 * The expected answer of every endpoint for every caller is the reviewed file
 * {@code src/test/resources/authorization-matrix.txt}. The test fails when an answer differs from the file and when
 * an endpoint is not in the file, so a new endpoint cannot ship without a decision about who may call it. The
 * answers observed in the run are written to {@code target/authorization-matrix-actual.txt}.
 * <p>
 * Cells: {@code 401}, {@code 403}, {@code 404}; {@code DENY} = 403 or 404; {@code OK} = 2xx, allowed for this
 * caller; {@code INPUT} = 400, 409, 415 or 422, refused for its content before any object is reached (see the
 * comment of the line in the file); {@code SKIP} = not called for this caller.
 */
class AuthorizationMatrixIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** Endpoints that work without a session; each is protected by its own token, password or rate limit. */
	private static final Set<String> PUBLIC = Set.of("GET /api/v1/auth/csrf", "POST /api/v1/auth/login",
			"POST /api/v1/auth/logout", "POST /api/v1/auth/password-reset/request",
			"POST /api/v1/auth/password-reset/redeem", "POST /api/v1/approval-links/summary",
			"POST /api/v1/approval-links/approve", "POST /api/v1/quote-links/summary", "POST /api/v1/quote-links/submit",
			// Status only for anonymous callers, as /actuator/health (Sprint 2, P1.4).
			"GET /healthz");

	/**
	 * Query string and body for the endpoints that check their input before the caller: with valid input the answer
	 * shows the authorization decision instead of a validation error. {@code <tn>} is the other company's id.
	 */
	private static final Map<String, String[]> INPUTS = Map.of(
			"DELETE /api/v1/admin/requests/{id}", new String[] { "?reason=matrix", null },
			"GET /api/v1/purchasing/buyers", new String[] { "?companyId=<tn>", null },
			"POST /api/v1/admin/accounts", new String[] { "", "{\"email\":\"matrix.probe@demo.local\",\"displayName\":"
					+ "\"Matrix Probe\",\"companyId\":<tn>,\"preferredLanguage\":\"en\",\"active\":true,\"roles\":[]}" },
			"POST /api/v1/admin/accounts/{id}/active", new String[] { "", "{\"active\":true}" },
			"PUT /api/v1/admin/accounts/{id}", new String[] { "", "{\"displayName\":\"Matrix Probe\",\"companyId\":<tn>,"
					+ "\"preferredLanguage\":\"en\",\"version\":0}" },
			"POST /api/v1/messaging/conversations", new String[] { "", "{\"contextType\":\"PO\",\"contextId\":1,"
					+ "\"supplierId\":1}" },
			"POST /api/v1/messaging/conversations/{id}/read", new String[] { "", "{\"messageId\":1}" },
			"POST /api/v1/supplier-portal/conversations/{id}/read", new String[] { "", "{\"messageId\":1}" });

	private static final Pattern VARIABLE = Pattern.compile("\\{([^}/]+)}");

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping mappings;

	@Test
	void everyEndpointRefusesTheCallersWhoHaveNoRightToIt() throws IOException {
		Map<String, Endpoint> endpoints = endpoints();
		Map<String, Long> ids = foreignObjects();

		ApiClient anonymous = client();
		anonymous.csrf();
		ApiClient supplier = signedIn("contact.skf@demo.local");
		ApiClient outsider = signedIn("requester.fr@demo.local");

		Map<String, String[]> expected = expected();
		List<String> actualLines = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		for (Endpoint endpoint : endpoints.values()) {
			String[] cells = expected.get(endpoint.key());
			String[] input = INPUTS.getOrDefault(endpoint.key(), new String[] { "", "{}" });
			String tn = Long.toString(ids.get("companies"));
			String path = endpoint.path(ids) + input[0].replace("<tn>", tn);
			String body = endpoint.method().equals("GET") || endpoint.method().equals("DELETE") ? null
					: input[1].replace("<tn>", tn);
			int asAnonymous = anonymous.send(endpoint.method(), path, body).status();
			if (endpoint.key().equals("POST /api/v1/auth/logout")) {
				// Signing out replaces the CSRF token; without a new one the next refusals would be 403, not 401.
				anonymous.csrf();
			}
			// Signing out would end the session the following calls rely on.
			boolean endsSession = endpoint.key().equals("POST /api/v1/auth/logout");
			Integer asSupplier = endsSession ? null : supplier.send(endpoint.method(), path, body).status();
			boolean skipOutsider = endsSession || endpoint.streaming() || cells != null && cells[2].equals("SKIP");
			Integer asOutsider = skipOutsider ? null : outsider.send(endpoint.method(), path, body).status();
			actualLines.add(String.format("%-98s | %s | %s | %s", endpoint.key(), asAnonymous,
					asSupplier == null ? "SKIP" : asSupplier.toString(), asOutsider == null ? "SKIP" : asOutsider.toString()));
			if (cells == null) {
				problems.add(endpoint.key() + ": not in authorization-matrix.txt (observed " + asAnonymous + ", "
						+ asSupplier + ", " + asOutsider + "). Decide who may call it and add the line.");
				continue;
			}
			check(problems, endpoint, "anonymous", cells[0], asAnonymous);
			if (asSupplier != null) {
				check(problems, endpoint, "supplier", cells[1], asSupplier);
			}
			if (asOutsider != null) {
				check(problems, endpoint, "outsider", cells[2], asOutsider);
			}
			// Whatever the file says, these two rules hold for every endpoint.
			if (!PUBLIC.contains(endpoint.key()) && asAnonymous != 401) {
				problems.add(endpoint.key() + ": an anonymous caller got " + asAnonymous + ", not 401");
			}
			if (endpoint.hasVariables() && asOutsider != null && asOutsider >= 200 && asOutsider < 300
					&& !cells[2].equals("OK")) {
				problems.add(endpoint.key() + ": a user of another company got " + asOutsider + " on " + path);
			}
		}
		Files.createDirectories(Path.of("target"));
		Files.write(Path.of("target/authorization-matrix-actual.txt"), actualLines, StandardCharsets.UTF_8);

		for (String key : expected.keySet()) {
			if (!endpoints.containsKey(key)) {
				problems.add(key + ": in authorization-matrix.txt but no longer an endpoint; remove the line");
			}
		}
		// The outsider and the supplier must still be signed in: no call may have ended their session.
		outsider.get("/api/v1/me").expectStatus(200);
		supplier.get("/api/v1/me").expectStatus(200);
		assertThat(problems).as("authorization matrix (%d endpoints; observed answers in "
				+ "target/authorization-matrix-actual.txt)", endpoints.size()).isEmpty();
		assertThat(endpoints.size()).as("endpoints found").isGreaterThan(170);
	}

	/**
	 * Privilege escalation: an ordinary user tries to give themselves rights, and a company administrator tries to
	 * exceed theirs. The state is checked in the database, not only the status.
	 */
	@Test
	void nobodyCanRaiseTheirOwnPrivileges() {
		ApiClient claire = signedIn("requester.fr@demo.local");
		long claireId = userId("requester.fr@demo.local");
		int rolesBefore = roleCount(claireId);
		for (String role : new String[] { "ADMIN", "GLOBAL_ADMIN", "BUYER", "BUDGET_APPROVER" }) {
			claire.post("/api/v1/admin/accounts/" + claireId + "/roles",
					"{\"role\":\"" + role + "\",\"companyId\":" + companyId("DEMO-FR-PLT") + ",\"departmentId\":null}")
				.expectStatus(403);
		}
		claire.post("/api/v1/admin/accounts/" + claireId + "/active", "{\"active\":true}").expectStatus(403);
		claire.post("/api/v1/admin/accounts", "{\"email\":\"x@demo.local\",\"displayName\":\"X\",\"companyId\":"
				+ companyId("DEMO-FR-PLT") + ",\"roles\":[{\"role\":\"ADMIN\",\"companyId\":" + companyId("DEMO-FR-PLT")
				+ "}]}").expectStatus(403);
		// Fields a profile update must ignore.
		claire.send("PUT", "/api/v1/me/preferences",
				"{\"preferredLanguage\":\"fr\",\"roles\":[\"GLOBAL_ADMIN\"],\"companyId\":" + companyId("DEMO-TN-PLT")
						+ ",\"isActive\":true,\"groupId\":1}");
		assertThat(roleCount(claireId)).isEqualTo(rolesBefore);
		assertThat(jdbc.queryForObject("SELECT company_id FROM user_account WHERE id = ?", Long.class, claireId))
			.isEqualTo(companyId("DEMO-FR-PLT"));
		assertThat((List<String>) claire.get("/api/v1/me").expectStatus(200).json("$.roles")).containsExactly("REQUESTER");

		// A company administrator cannot create a peer or a superior, nor act outside their companies.
		ApiClient admin = signedIn("admin@demo.local");
		long adminId = userId("admin@demo.local");
		int adminRoles = roleCount(adminId);
		for (String role : new String[] { "GLOBAL_ADMIN", "ADMIN", "GROUP_MANAGEMENT" }) {
			admin.post("/api/v1/admin/accounts/" + adminId + "/roles",
					"{\"role\":\"" + role + "\",\"companyId\":" + companyId("DEMO-TN-PLT") + ",\"departmentId\":null}")
				.expectStatus(403);
			admin.post("/api/v1/admin/accounts/" + claireId + "/roles",
					"{\"role\":\"" + role + "\",\"companyId\":" + companyId("DEMO-FR-PLT") + ",\"departmentId\":null}")
				.expectStatus(403);
		}
		admin.post("/api/v1/admin/accounts/" + userId("global.admin@demo.local") + "/password-reset", null)
			.expectStatus(403);
		admin.post("/api/v1/admin/accounts/" + userId("global.admin@demo.local") + "/active", "{\"active\":false}")
			.expectStatus(403);
		assertThat(roleCount(adminId)).isEqualTo(adminRoles);
		assertThat(roleCount(claireId)).isEqualTo(rolesBefore);
	}

	/** The matrix sends JSON, which the upload endpoint refuses as a media type; this sends a real file. */
	@Test
	void aFileCannotBeAttachedToAnotherCompanysRequest() {
		long request = foreignObjects().get("requests");
		int before = jdbc.queryForObject("SELECT COUNT(*) FROM attachment WHERE request_id = ?", Integer.class, request);
		byte[] pdf = "%PDF-1.7 probe".getBytes(StandardCharsets.US_ASCII);
		signedIn("requester.fr@demo.local")
			.upload("/api/v1/requests/" + request + "/attachments", "probe.pdf", "application/pdf", pdf, null)
			.expectStatus(404);
		signedIn("contact.skf@demo.local")
			.upload("/api/v1/requests/" + request + "/attachments", "probe.pdf", "application/pdf", pdf, null)
			.expectStatus(403);
		ApiClient anonymous = client();
		anonymous.csrf();
		anonymous.upload("/api/v1/requests/" + request + "/attachments", "probe.pdf", "application/pdf", pdf, null)
			.expectStatus(401);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment WHERE request_id = ?", Integer.class, request))
			.isEqualTo(before);
	}

	/**
	 * Isolation between privileged users (review R-13). The outsider of the matrix is a plain requester, so on
	 * administration endpoints it is refused for its role before any object is looked at. Here the caller holds the
	 * roles: an administrator and department manager of the French plant only, calling every endpoint that takes an
	 * identifier with the identifiers of the Tunisian plant. Every answer must be a refusal.
	 */
	@Test
	void anAdministratorOfOneCompanyCannotReachAnotherCompanysObjects() throws IOException {
		Map<String, Long> ids = foreignObjects();
		ApiClient global = signedIn("global.admin@demo.local");
		long julien = userId("manager.fr@demo.local");
		long fr = companyId("DEMO-FR-PLT");
		String tn = Long.toString(ids.get("companies"));
		global.post("/api/v1/admin/accounts/" + julien + "/roles",
				"{\"role\":\"ADMIN\",\"companyId\":" + fr + ",\"departmentId\":null}").expectStatus(200);
		long grant = jdbc.queryForObject("SELECT id FROM user_role WHERE user_id = ? AND role = 'ADMIN' AND company_id = ?",
				Long.class, julien, fr);
		List<String> lines = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		int called = 0;
		try {
			ApiClient frAdmin = signedIn("manager.fr@demo.local");
			assertThat((List<String>) frAdmin.get("/api/v1/me").expectStatus(200).json("$.roles")).contains("ADMIN",
					"DEPARTMENT_MANAGER");
			for (Endpoint endpoint : endpoints().values()) {
				if (!endpoint.hasVariables() || endpoint.streaming()
						|| endpoint.pattern().startsWith("/api/v1/supplier-portal/")) {
					continue;
				}
				String[] input = PRIVILEGED_INPUTS.getOrDefault(endpoint.key(),
						INPUTS.getOrDefault(endpoint.key(), new String[] { "", "{}" }));
				String path = endpoint.path(ids) + input[0].replace("<tn>", tn);
				String body = endpoint.method().equals("GET") || endpoint.method().equals("DELETE") ? null
						: input[1].replace("<tn>", tn);
				int status = frAdmin.send(endpoint.method(), path, body).status();
				called++;
				lines.add(String.format("%-98s | %s", endpoint.key(), status));
				if (status != 403 && status != 404 && !MULTIPART_ONLY.contains(endpoint.key())) {
					problems.add(endpoint.key() + ": an administrator of another company got " + status + " on " + path);
				}
			}
			// The same person is still signed in: no call ended or damaged their own session.
			frAdmin.get("/api/v1/me").expectStatus(200);
		}
		finally {
			global.post("/api/v1/admin/accounts/" + julien + "/roles/" + grant + "/remove", null).expectStatus(200);
		}
		Files.createDirectories(Path.of("target"));
		Files.write(Path.of("target/authorization-privileged-actual.txt"), lines, StandardCharsets.UTF_8);
		assertThat(problems).as("privileged cross-company calls (%d endpoints; observed answers in "
				+ "target/authorization-privileged-actual.txt)", called).isEmpty();
		assertThat(called).isGreaterThan(100);
		// Nothing of the other company changed.
		assertThat(jdbc.queryForObject("SELECT is_active FROM user_account WHERE id = ?", Boolean.class,
				ids.get("accounts"))).isTrue();
	}

	/** Endpoints that accept only multipart bodies: JSON is refused as a media type before anything else. */
	private static final Set<String> MULTIPART_ONLY = Set.of("POST /api/v1/requests/{id}/attachments",
			"POST /api/v1/logistics/shipments/{shipmentId}/documents");

	/** Valid input for the administration endpoints, so that the answer is the authorization decision. */
	private static final Map<String, String[]> PRIVILEGED_INPUTS = Map.of(
			"POST /api/v1/admin/accounts/{id}/roles",
			new String[] { "", "{\"role\":\"REQUESTER\",\"companyId\":<tn>,\"departmentId\":null}" },
			"POST /api/v1/admin/requests/{id}/correct-draft",
			new String[] { "", "{\"reason\":\"Authorization matrix probe\",\"version\":0}" });

	// ---- endpoints

	private Map<String, Endpoint> endpoints() {
		Map<String, Endpoint> found = new TreeMap<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
			if (!entry.getValue().getBeanType().getPackageName().startsWith("com.epi.procurement")) {
				continue;
			}
			RequestMappingInfo info = entry.getKey();
			boolean streaming = info.getProducesCondition()
				.getProducibleMediaTypes()
				.contains(MediaType.TEXT_EVENT_STREAM);
			Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
			for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
				for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
					Endpoint endpoint = new Endpoint(method.name(), pattern, streaming);
					found.put(endpoint.key(), endpoint);
				}
			}
		}
		return found;
	}

	private record Endpoint(String method, String pattern, boolean streaming) {

		String key() {
			return method + " " + pattern;
		}

		boolean hasVariables() {
			return pattern.contains("{");
		}

		/** The pattern with each variable replaced by the id of an object the outsider must not reach. */
		String path(Map<String, Long> ids) {
			Matcher matcher = VARIABLE.matcher(pattern);
			StringBuilder path = new StringBuilder();
			while (matcher.find()) {
				String before = pattern.substring(0, matcher.start());
				String segment = before.substring(before.lastIndexOf('/', before.length() - 2) + 1, before.length() - 1);
				if (segment.equals("conversations") && pattern.startsWith("/api/v1/internal-messaging/")) {
					segment = "internal-conversations";
				}
				Long id = ids.get(segment);
				matcher.appendReplacement(path, Long.toString(id == null ? 987654321L : id));
			}
			matcher.appendTail(path);
			return path.toString();
		}

	}

	// ---- objects of another company

	/**
	 * Identifiers of objects that belong to the Tunisian plant or to other users, keyed by the path segment that
	 * precedes the variable ({@code /requests/{id}} gives {@code requests}). Created here, so they always exist: a
	 * request with an attachment and a pending approval task, and a second request with its RFQ, order, shipment and
	 * receipt. Supplier conversations, contacts, imports, absences and notifications are taken from what other test
	 * classes left in the shared database, and fall back to an identifier that does not exist.
	 */
	private Map<String, Long> foreignObjects() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long tn = companyId("DEMO-TN-PLT");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", "Authorization matrix");
		body.put("categoryId", jdbc.queryForObject("SELECT c.id FROM request_category c JOIN organization_group g "
				+ "ON g.id = c.group_id WHERE g.code = 'DEMO-GRP' AND c.code = 'DEMO-SPARE'", Long.class));
		body.put("priority", "NORMAL");
		body.put("purchaseType", "INTERNATIONAL");
		body.put("currency", "TND");
		body.put("departmentId", jdbc.queryForObject("SELECT id FROM department WHERE company_id = ? AND code = "
				+ "'DEMO-MAINT'", Long.class, tn));
		body.put("requiredDate", LocalDate.now().plusDays(30).toString());
		body.put("lines", List.of(Map.of("description", "Matrix item", "quantity", 1, "unit", "PCE",
				"estimatedUnitPrice", 10)));
		ApiClient.Response draft = sami.post("/api/v1/requests", JSON.writeValueAsString(body)).expectStatus(201);
		long request = draft.id();
		long attachment = sami.upload("/api/v1/requests/" + request + "/attachments", "matrix.pdf", "application/pdf",
				"%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), null).expectStatus(201).id();
		sami.post("/api/v1/requests/" + request + "/submit", "{\"version\":" + draft.version() + "}").expectStatus(200);

		long chain = receivedRequestOfTheOtherCompany(body);

		long outsider = userId("requester.fr@demo.local");
		Map<String, Long> ids = new LinkedHashMap<>();
		// Request paths use the request that has an order, a shipment and a receipt; task and attachment paths use
		// the one still waiting for approval.
		ids.put("requests", chain);
		ids.put("attachments", attachment);
		ids.put("tasks", lookup("SELECT TOP 1 id FROM approval_task WHERE request_id = ? AND status = 'PENDING'", request));
		ids.put("approval-rules", lookup("SELECT TOP 1 id FROM approval_rule WHERE company_id = ?", tn));
		ids.put("companies", tn);
		ids.put("accounts", userId("requester.tn@demo.local"));
		ids.put("absences", lookup("SELECT TOP 1 id FROM user_absence WHERE user_id <> ? ORDER BY id DESC", outsider));
		ids.put("notifications", lookup("SELECT TOP 1 id FROM in_app_notification WHERE user_id <> ? ORDER BY id DESC",
				outsider));
		ids.put("imports", lookup("SELECT TOP 1 id FROM import_job ORDER BY id DESC"));
		ids.put("purchase-orders", lookup("SELECT id FROM purchase_order WHERE request_id = ?", chain));
		ids.put("rfqs", lookup("SELECT id FROM rfq WHERE request_id = ?", chain));
		ids.put("shipments", lookup("SELECT id FROM shipment WHERE request_id = ?", chain));
		ids.put("milestones", lookup("SELECT TOP 1 m.id FROM shipment_milestone m JOIN shipment s ON s.id = m.shipment_id "
				+ "WHERE s.request_id = ?", chain));
		ids.put("goods-receipts", lookup("SELECT TOP 1 id FROM goods_receipt WHERE request_id = ?", chain));
		for (String real : new String[] { "requests", "attachments", "tasks", "approval-rules", "purchase-orders", "rfqs",
				"shipments", "milestones", "goods-receipts" }) {
			assertThat(ids.get(real)).as("a real object of the other company for /%s/{id}", real).isNotNull();
		}
		ids.put("conversations", lookup("SELECT TOP 1 id FROM conversation ORDER BY id DESC"));
		ids.put("internal-conversations",
				lookup("SELECT TOP 1 id FROM internal_conversation WHERE company_id = ? ORDER BY id DESC", tn));
		ids.put("contacts", lookup("SELECT TOP 1 id FROM supplier_contact ORDER BY id DESC"));
		// A supplier private to another company; shared suppliers are legitimately visible to everyone.
		ids.put("suppliers", lookup("SELECT TOP 1 id FROM supplier WHERE owning_company_id IS NOT NULL AND "
				+ "owning_company_id <> ? AND visibility <> 'SHARED' ORDER BY id DESC", companyId("DEMO-FR-PLT")));
		return ids;
	}

	/**
	 * A request of the Tunisian plant taken through its whole life: approved, assigned, sourced, awarded, ordered,
	 * shipped, delivered and received. It gives the matrix a real order, RFQ, shipment and receipt of another
	 * company, so a refusal on those endpoints is a refusal of an existing object.
	 */
	private long receivedRequestOfTheOtherCompany(Map<String, Object> body) {
		ApiClient sami = signedIn("requester.tn@demo.local");
		Map<String, Object> local = new LinkedHashMap<>(body);
		local.put("title", "Authorization matrix chain");
		local.put("purchaseType", "LOCAL");
		ApiClient.Response draft = sami.post("/api/v1/requests", JSON.writeValueAsString(local)).expectStatus(201);
		long id = sami.post("/api/v1/requests/" + draft.id() + "/submit", "{\"version\":" + draft.version() + "}")
			.expectStatus(200)
			.id();

		ApiClient karim = signedIn("manager.tn@demo.local");
		ApiClient.Response approval = karim.get("/api/v1/requests/" + id + "/approval").expectStatus(200);
		karim.post("/api/v1/approvals/tasks/" + approval.json("$.myTask.id") + "/approve",
				"{\"version\":" + approval.json("$.myTask.version") + "}").expectStatus(200);

		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		mehdi.post("/api/v1/purchasing/requests/" + id + "/assign",
				"{\"version\":" + version(mehdi, id) + ",\"buyerUserId\":" + userId("buyer@demo.local") + "}")
			.expectStatus(200);
		ApiClient ines = signedIn("buyer@demo.local");
		ines.post("/api/v1/purchasing/requests/" + id + "/accept", "{\"version\":" + version(ines, id) + "}")
			.expectStatus(200);

		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		ApiClient.Response rfq = ines.post("/api/v1/sourcing/requests/" + id + "/rfq",
				"{\"kind\":\"QUICK\",\"supplierIds\":[" + skf + "]}").expectStatus(200);
		ines.post("/api/v1/sourcing/rfqs/" + rfq.json("$.id") + "/quotes",
				JSON.writeValueAsString(Map.of("rfqSupplierId", rfq.json("$.suppliers[0].id"), "currency", "EUR",
						"freightAmount", 10, "dutyAmount", 0, "otherAmount", 0, "lines",
						List.of(Map.of("rfqLineId", rfq.json("$.lines[0].id"), "quantity", 1, "unitPrice", 25)))))
			.expectStatus(200);
		ines.post("/api/v1/sourcing/requests/" + id + "/validate-technical",
				"{\"requestVersion\":" + version(ines, id) + ",\"comment\":\"Fits\"}").expectStatus(200);
		ines.post("/api/v1/sourcing/requests/" + id + "/validate-commercial",
				"{\"requestVersion\":" + version(ines, id) + ",\"comment\":\"Price OK\"}").expectStatus(200);

		Object quote = ines.get("/api/v1/sourcing/requests/" + id + "/rfq").expectStatus(200).json("$.quotes[0].id");
		ines.post("/api/v1/ordering/requests/" + id + "/award",
				"{\"requestVersion\":" + version(ines, id) + ",\"quotationId\":" + quote + ",\"reason\":\"Matrix\"}")
			.expectStatus(200);
		ApiClient.Response po = ines.post("/api/v1/ordering/requests/" + id + "/purchase-order",
				"{\"requestVersion\":" + version(ines, id) + "}").expectStatus(200);
		ApiClient.Response issued = ines.post("/api/v1/ordering/purchase-orders/" + po.json("$.id") + "/issue",
				"{\"version\":" + po.json("$.version") + "}").expectStatus(200);
		ines.post("/api/v1/ordering/purchase-orders/" + po.json("$.id") + "/confirm",
				"{\"version\":" + issued.json("$.version") + ",\"confirmationNote\":\"OK\"}").expectStatus(200);

		LocalDate today = LocalDate.now(java.time.ZoneId.of("Africa/Tunis"));
		ApiClient.Response shipment = ines.post("/api/v1/logistics/requests/" + id + "/shipments",
				JSON.writeValueAsString(Map.of("requestVersion", version(ines, id), "kind", "LOCAL", "milestonePlans",
						List.of(Map.of("code", "DELIVERED", "plannedDate", today.minusDays(1).toString())))))
			.expectStatus(200);
		List<Map<String, Object>> milestones = shipment.json("$.milestones");
		Object delivered = milestones.stream()
			.filter(m -> "DELIVERED".equals(m.get("code")))
			.findFirst()
			.orElseThrow()
			.get("id");
		ines.put("/api/v1/logistics/shipments/" + shipment.json("$.id") + "/milestones/" + delivered,
				"{\"version\":" + shipment.json("$.version") + ",\"actualDate\":\"" + today + "\"}").expectStatus(200);

		ApiClient omar = signedIn("receiver.tn@demo.local");
		omar.post("/api/v1/receipts/requests/" + id + "/goods-receipts", "{\"requestVersion\":" + version(omar, id) + "}")
			.expectStatus(200);
		return id;
	}

	private static int version(ApiClient client, long requestId) {
		return ((Number) client.get("/api/v1/requests/" + requestId).expectStatus(200).json("$.version")).intValue();
	}

	private Long lookup(String sql, Object... args) {
		List<Long> found = jdbc.queryForList(sql, Long.class, args);
		return found.isEmpty() ? null : found.get(0);
	}

	private int roleCount(long user) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE user_id = ?", Integer.class, user);
	}

	// ---- expectations

	private static Map<String, String[]> expected() throws IOException {
		Map<String, String[]> expected = new LinkedHashMap<>();
		try (InputStream in = AuthorizationMatrixIntegrationTests.class.getResourceAsStream("/authorization-matrix.txt")) {
			if (in == null) {
				return expected;
			}
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String content = line.contains("#") ? line.substring(0, line.indexOf('#')) : line;
				if (content.isBlank()) {
					continue;
				}
				String[] parts = content.split("\\|");
				assertThat(parts).as(line).hasSize(4);
				expected.put(parts[0].trim().replaceAll("\\s+", " "),
						new String[] { parts[1].trim(), parts[2].trim(), parts[3].trim() });
			}
		}
		return expected;
	}

	private static void check(List<String> problems, Endpoint endpoint, String caller, String cell, int status) {
		boolean ok = switch (cell) {
			case "DENY" -> status == 403 || status == 404;
			case "OK" -> status >= 200 && status < 300;
			case "INPUT" -> status == 400 || status == 409 || status == 415 || status == 422;
			case "SKIP" -> true;
			default -> Integer.toString(status).equals(cell);
		};
		if (!ok) {
			problems.add(endpoint.key() + ": " + caller + " got " + status + ", expected " + cell);
		}
	}

}
