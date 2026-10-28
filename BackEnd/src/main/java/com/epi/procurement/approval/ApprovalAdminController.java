package com.epi.procurement.approval;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.identity.UserDirectory;

/**
 * Administration (ADMIN of the caller's group): approval rules, exchange rates, absences of other users, and
 * Sprint 1 requests awaiting a circuit. Users' own absences are under {@code /api/v1/me/absences}.
 */
@RestController
@RequestMapping("/api/v1")
class ApprovalAdminController {

	private final ApprovalRulesAdmin rules;

	private final Absences absences;

	private final ApprovalQueries queries;

	private final CurrentUserProvider currentUser;

	private final com.epi.procurement.organization.OrganizationDirectory organization;

	private final UserDirectory users;

	ApprovalAdminController(ApprovalRulesAdmin rules, Absences absences, ApprovalQueries queries,
			CurrentUserProvider currentUser, com.epi.procurement.organization.OrganizationDirectory organization,
			UserDirectory users) {
		this.organization = organization;
		this.users = users;
		this.rules = rules;
		this.absences = absences;
		this.queries = queries;
		this.currentUser = currentUser;
	}

	/**
	 * Companies of the administrator's group (S2CR-02).
	 */
	@GetMapping("/admin/companies")
	List<CompanyItem> companies() {
		CurrentUser admin = currentUser.require();
		Absences.requireAdmin(admin);
		return organization.companiesOfGroup(admin.groupId())
			.stream()
			.map(c -> new CompanyItem(c.id(), c.code(), c.name(), c.currencyCode()))
			.toList();
	}

	/**
	 * Active users of the administrator's group (S2CR-02), e.g. to pick a named approver or an absence delegate.
	 */
	@GetMapping("/admin/users")
	List<UserDirectory.UserListing> users(@RequestParam(required = false) Long companyId,
			@RequestParam(required = false) String role, @RequestParam(required = false) String q) {
		CurrentUser admin = currentUser.require();
		Absences.requireAdmin(admin);
		if (role != null && com.epi.procurement.identity.Role.parse(role).isEmpty()) {
			throw com.epi.procurement.platform.web.ApiProblemException.invalidParameter("role");
		}
		if (q != null && q.length() > 100) {
			throw com.epi.procurement.platform.web.ApiProblemException.invalidParameter("q");
		}
		return users.searchUsers(admin.groupId(), companyId, role, q);
	}

	record CompanyItem(long id, String code, String name, String currency) {
	}

	@GetMapping("/admin/approval-rules")
	List<ApprovalViews.Rule> rules(@RequestParam(required = false) Long companyId) {
		return rules.list(currentUser.require(), companyId);
	}

	@GetMapping("/admin/approval-rules/{id}")
	ApprovalViews.Rule rule(@PathVariable long id) {
		return rules.get(currentUser.require(), id);
	}

	@GetMapping("/admin/approval-rules/{id}/versions")
	List<ApprovalViews.Rule> versions(@PathVariable long id) {
		return rules.versions(currentUser.require(), id);
	}

	@PostMapping("/admin/approval-rules")
	ResponseEntity<ApprovalViews.Rule> createRule(@RequestBody ApprovalRulesAdmin.RuleInput input) {
		ApprovalViews.Rule created = rules.create(currentUser.require(), input);
		return ResponseEntity.created(URI.create("/api/v1/admin/approval-rules/" + created.id())).body(created);
	}

	/**
	 * Edits a never-used draft in place; otherwise creates a new draft version (the response has the new id).
	 */
	@PutMapping("/admin/approval-rules/{id}")
	ApprovalViews.Rule updateRule(@PathVariable long id, @RequestBody ApprovalRulesAdmin.RuleInput input) {
		return rules.update(currentUser.require(), id, input);
	}

	@PostMapping("/admin/approval-rules/{id}/activate")
	ApprovalViews.Rule activate(@PathVariable long id) {
		return rules.activate(currentUser.require(), id);
	}

	@PostMapping("/admin/approval-rules/{id}/retire")
	ApprovalViews.Rule retire(@PathVariable long id) {
		return rules.retire(currentUser.require(), id);
	}

	@GetMapping("/admin/exchange-rates")
	List<ApprovalViews.Rate> rates() {
		return rules.rates(currentUser.require());
	}

	@PostMapping("/admin/exchange-rates")
	ResponseEntity<ApprovalViews.Rate> addRate(@RequestBody ApprovalRulesAdmin.RateInput input) {
		return ResponseEntity.status(201).body(rules.addRate(currentUser.require(), input));
	}

	@GetMapping("/admin/approvals/awaiting-circuit")
	List<ApprovalViews.AwaitingCircuit> awaitingCircuit() {
		return queries.awaitingCircuit(currentUser.require());
	}

	@GetMapping("/admin/absences")
	List<ApprovalViews.Absence> adminAbsences(@RequestParam(required = false) Long userId) {
		return absences.listForGroup(currentUser.require(), userId);
	}

	@PostMapping("/admin/absences")
	ResponseEntity<ApprovalViews.Absence> adminCreateAbsence(@RequestBody AdminAbsenceBody body) {
		CurrentUser admin = currentUser.require();
		Absences.requireAdmin(admin);
		if (body.userId() == null) {
			throw new com.epi.procurement.platform.web.ValidationFailedException(
					List.of(com.epi.procurement.platform.web.FieldViolation.of("userId", "required")));
		}
		return ResponseEntity.status(201)
			.body(absences.create(admin, body.userId(), new Absences.AbsenceInput(body.delegateUserId(), body.startsAt(),
					body.endsAt(), body.reason(), Boolean.TRUE.equals(body.adminOverride())), true));
	}

	@DeleteMapping("/admin/absences/{id}")
	ResponseEntity<Void> adminCancelAbsence(@PathVariable long id) {
		absences.cancel(currentUser.require(), id, true);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me/absences")
	List<ApprovalViews.Absence> myAbsences() {
		return absences.list(currentUser.require().id());
	}

	@PostMapping("/me/absences")
	ResponseEntity<ApprovalViews.Absence> createAbsence(@RequestBody Absences.AbsenceInput body) {
		CurrentUser user = currentUser.require();
		return ResponseEntity.status(201)
			.body(absences.create(user, user.id(), new Absences.AbsenceInput(body.delegateUserId(), body.startsAt(),
					body.endsAt(), body.reason(), Boolean.FALSE), false));
	}

	@DeleteMapping("/me/absences/{id}")
	ResponseEntity<Void> cancelAbsence(@PathVariable long id) {
		absences.cancel(currentUser.require(), id, false);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Delegation candidates for an absence: users of the caller's companies holding an approver role.
	 */
	@GetMapping("/me/absences/delegates")
	List<ApprovalViews.Delegate> absenceDelegates() {
		return absences.candidates(currentUser.require());
	}

	record AdminAbsenceBody(Long userId, Long delegateUserId, java.time.Instant startsAt, java.time.Instant endsAt,
			String reason, Boolean adminOverride) {
	}

}
