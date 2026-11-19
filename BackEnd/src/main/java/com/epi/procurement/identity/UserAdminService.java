package com.epi.procurement.identity;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.platform.web.ApiProblemException;

/**
 * Installation and company-scoped user administration (Sprint 10).
 */
@Service
public class UserAdminService {

	private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern
		.compile("[a-z0-9._%+'-]+@[a-z0-9-]+(\\.[a-z0-9-]+)+");

	private static final Set<Role> COMPANY_ADMIN_FORBIDDEN = Set.of(Role.GLOBAL_ADMIN, Role.ADMIN, Role.GROUP_MANAGEMENT,
			Role.SUPPLIER_CONTACT);

	private final UserAdminStore store;

	private final CredentialStore credentials;

	private final PasswordEncoder encoder;

	private final AuditLog auditLog;

	private final ApplicationEventPublisher events;

	private final PasswordPolicy passwordPolicy;

	private final UserDirectory directory;

	private final EmailChangeStore emailChanges;

	UserAdminService(UserAdminStore store, CredentialStore credentials, PasswordEncoder encoder, AuditLog auditLog,
			ApplicationEventPublisher events, PasswordPolicy passwordPolicy, UserDirectory directory,
			EmailChangeStore emailChanges) {
		this.emailChanges = emailChanges;
		this.passwordPolicy = passwordPolicy;
		this.directory = directory;
		this.store = store;
		this.credentials = credentials;
		this.encoder = encoder;
		this.auditLog = auditLog;
		this.events = events;
	}

	public List<UserAdminViews.ListItem> list(CurrentUser actor, String text, Boolean active, int page, int size) {
		requireUserAdmin(actor);
		// Scope belongs in the query: filtering after OFFSET/FETCH returned short or empty pages.
		return store.search(actor.groupId(), actor.isGlobalAdmin() ? null : actor.adminCompanyIds(), text, active, page, size)
			.stream()
			.map(r -> new UserAdminViews.ListItem(r.id(), r.email(), r.displayName(), r.companyId(), r.companyCode(),
					r.active()))
			.toList();
	}

	public UserAdminViews.Detail get(CurrentUser actor, long userId) {
		requireUserAdmin(actor);
		UserAdminStore.AccountRow account = store.findAccount(userId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "User not found."));
		assertVisible(actor, account);
		if (!actor.isGlobalAdmin() && store.isGlobalAdminTarget(account.id())) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
					"Company administrators cannot manage global administrators.");
		}
		return toDetail(account);
	}

	@Transactional
	public UserAdminViews.Detail create(CurrentUser actor, UserAdminViews.CreateRequest body) {
		requireUserAdmin(actor);
		String email = normalizeEmail(body.email());
		if (store.emailTaken(email, null)) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "email-taken", "An account with this e-mail already exists.");
		}
		validateHome(actor, body.companyId(), body.siteId(), body.departmentId());
		// Four eyes on identities (F-1): a new account stays inactive until another administrator activates it, so
		// nobody can create an account with a mailbox they control and use it alone. `active` in the body is ignored.
		boolean active = false;
		long id = store.insertAccount(email, requireName(body.displayName()), body.companyId(), body.siteId(),
				body.departmentId(), language(body.preferredLanguage()), active, false, actor.id());
		// Unusable random hash until setup link is redeemed (or bootstrap sets one).
		credentials.savePasswordHash(id, encoder.encode(java.util.UUID.randomUUID().toString()), true);
		if (body.roles() != null) {
			for (UserAdminViews.GrantRequest grant : body.roles()) {
				// Audited like any later grant (review finding 6, ADR-006).
				addGrantInternal(actor, id, grant, true);
			}
		}
		auditLog.record(new AuditRecord(actor.id(), actor.email(), body.companyId(), "USER_ACCOUNT", Long.toString(id),
				"USER_CREATED", null, active ? "ACTIVE" : "INACTIVE", null, "{\"email\":" + AuditRecord.jsonString(email) + "}"));
		// The set-up link is sent when another administrator activates the account (setActive).
		return get(actor, id);
	}

	@Transactional
	public UserAdminViews.Detail update(CurrentUser actor, long userId, UserAdminViews.UpdateRequest body) {
		requireUserAdmin(actor);
		UserAdminStore.AccountRow account = store.findAccount(userId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "User not found."));
		assertCanManage(actor, account);
		validateHome(actor, body.companyId(), body.siteId(), body.departmentId());
		store.updateAccount(userId, requireName(body.displayName()), body.companyId(), body.siteId(),
				body.departmentId(), language(body.preferredLanguage()), body.version());
		auditLog.record(new AuditRecord(actor.id(), actor.email(), body.companyId(), "USER_ACCOUNT",
				Long.toString(userId), "USER_UPDATED", null, null, null, null));
		return get(actor, userId);
	}

	@Transactional
	public UserAdminViews.StatusResult setActive(CurrentUser actor, long userId, boolean active) {
		requireUserAdmin(actor);
		UserAdminStore.AccountRow account = store.findAccount(userId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "User not found."));
		assertCanManage(actor, account);
		// A set-up link sent at activation would go to the address being replaced (ADR-016 step 5).
		if (active && !account.active() && emailChanges.open(userId).isPresent()) {
			throw emailChangePending();
		}
		if (actor.id() == userId && !active) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "cannot-deactivate-self",
					"You cannot deactivate your own account.");
		}
		// Four eyes on identities (F-1, F-8): nobody in the account's creation chain activates it, and neither does an
		// administrator whose own account was created by its creator or someone in that chain (indirect self-approval).
		if (active && !account.active()) {
			java.util.Optional<Long> creator = directory.creatorOf(userId);
			if (creator.isPresent() && (directory.createdBy(userId, actor.id()) || creator.get() == actor.id()
					|| directory.createdBy(actor.id(), creator.get()))) {
				throw new ApiProblemException(HttpStatus.FORBIDDEN, "four-eyes",
						"You created this account, or its creator created yours. Another administrator must activate it.");
			}
		}
		if (!active && store.isGlobalAdminTarget(userId)) {
			int remaining = store.countActiveGlobalAdminsForUpdate();
			if (remaining <= 1) {
				throw new ApiProblemException(HttpStatus.CONFLICT, "last-global-admin",
						"Cannot deactivate the last active global administrator.");
			}
		}
		UserAdminStore.OpenAssignments open = store.openAssignments(userId);
		store.setActive(userId, active);
		store.bumpCredentialsVersion(userId);
		auditLog.record(new AuditRecord(actor.id(), actor.email(), account.companyId(), "USER_ACCOUNT",
				Long.toString(userId), active ? "USER_ACTIVATED" : "USER_DEACTIVATED",
				account.active() ? "ACTIVE" : "INACTIVE", active ? "ACTIVE" : "INACTIVE", null,
				"{\"pendingApprovals\":" + open.pendingApprovals() + ",\"assignedBuying\":" + open.assignedBuying()
						+ "}"));
		// First activation of an account created through administration: the owner receives the set-up link now.
		if (active && !account.active() && credentials.flags(userId).map(CredentialStore.CredentialFlags::mustChangePassword)
			.orElse(false)) {
			publishPasswordSetup(userId, "SETUP", actor, account.displayName(), account.companyId());
		}
		return new UserAdminViews.StatusResult(userId, active, open.pendingApprovals(), open.assignedBuying());
	}

	@Transactional
	public UserAdminViews.Detail addGrant(CurrentUser actor, long userId, UserAdminViews.GrantRequest grant) {
		requireUserAdmin(actor);
		addGrantInternal(actor, userId, grant, true);
		return get(actor, userId);
	}

	@Transactional
	public UserAdminViews.Detail removeGrant(CurrentUser actor, long userId, long grantId) {
		requireUserAdmin(actor);
		UserAdminStore.AccountRow account = store.findAccount(userId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "User not found."));
		// An account of another company does not exist for this administrator (F-7, ADR-007).
		assertVisible(actor, account);
		if (!actor.isGlobalAdmin() && store.isGlobalAdminTarget(account.id())) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
					"Company administrators cannot manage global administrators.");
		}
		assertNotPeerAdministrator(actor, account);
		UserAdminStore.RoleGrantRow grant = store.findGrant(grantId)
			.filter(g -> g.userId() == userId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "Role grant not found."));
		// A supplier contact is an external person: removing the role would turn the account into an employee
		// account without a recorded creator, outside the four-eyes rule (F-4). Revoke the contact from messaging.
		if (Role.SUPPLIER_CONTACT.name().equals(grant.role())) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "supplier-contact-role",
					"A supplier contact cannot become an employee account. Revoke the contact from messaging instead.");
		}
		if (Role.GLOBAL_ADMIN.name().equals(grant.role())) {
			if (actor.id() == userId) {
				throw new ApiProblemException(HttpStatus.CONFLICT, "cannot-remove-own-global-admin",
						"You cannot remove your own global administrator role.");
			}
			int remaining = store.countActiveGlobalAdminsForUpdate();
			if (remaining <= 1 && account.active()) {
				throw new ApiProblemException(HttpStatus.CONFLICT, "last-global-admin",
						"Cannot remove the last active global administrator.");
			}
		}
		if (!actor.isGlobalAdmin() && COMPANY_ADMIN_FORBIDDEN.contains(Role.parse(grant.role()).orElse(Role.REQUESTER))) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden", "You cannot manage this role.");
		}
		if (!actor.isGlobalAdmin() && (grant.companyId() == null || !actor.canAdminCompany(grant.companyId()))) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
					"You cannot modify role grants outside your authorised companies.");
		}
		store.deleteGrant(grantId, userId);
		store.bumpCredentialsVersion(userId);
		auditLog.record(new AuditRecord(actor.id(), actor.email(), account.companyId(), "USER_ROLE",
				Long.toString(grantId), "ROLE_REMOVED", grant.role(), null, null,
				"{\"userId\":" + userId + ",\"role\":\"" + grant.role() + "\"}"));
		return get(actor, userId);
	}

	@Transactional
	public void initiatePasswordReset(CurrentUser actor, long userId) {
		requireUserAdmin(actor);
		UserAdminStore.AccountRow account = store.findAccount(userId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "User not found."));
		assertCanManage(actor, account);
		if (!actor.isGlobalAdmin() && store.isGlobalAdminTarget(userId)) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
					"Company administrators cannot reset a global administrator password.");
		}
		// The reset link would go to the address being replaced (ADR-016 step 5).
		if (emailChanges.open(userId).isPresent()) {
			throw emailChangePending();
		}
		publishPasswordSetup(userId, "RESET", actor, account.displayName(), account.companyId());
	}

	private static ApiProblemException emailChangePending() {
		return new ApiProblemException(HttpStatus.CONFLICT, "email-change-pending",
				"A change of address is waiting for proof of control of the new mailbox. Complete or cancel it first.");
	}

	private void publishPasswordSetup(long userId, String purpose, CurrentUser actor, String displayName,
			long companyId) {
		events.publishEvent(new PasswordSetupRequested(userId, purpose, actor.id(), actor.email(), companyId,
				displayName));
		auditLog.record(new AuditRecord(actor.id(), actor.email(), companyId, "USER_ACCOUNT", Long.toString(userId),
				"PASSWORD_" + purpose + "_ISSUED", null, null, null, "{\"purpose\":\"" + purpose + "\"}"));
	}

	@Transactional
	public void revokeSessions(CurrentUser actor, long userId) {
		requireUserAdmin(actor);
		UserAdminStore.AccountRow account = store.findAccount(userId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "User not found."));
		assertCanManage(actor, account);
		int version = store.bumpCredentialsVersion(userId);
		auditLog.record(new AuditRecord(actor.id(), actor.email(), account.companyId(), "USER_SESSION",
				Long.toString(userId), "SESSIONS_REVOKED", null, Integer.toString(version), null, null));
	}

	private void addGrantInternal(CurrentUser actor, long userId, UserAdminViews.GrantRequest grant, boolean audit) {
		UserAdminStore.AccountRow account = store.findAccount(userId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "User not found."));
		// Before anything else: an account of another company does not exist for this administrator. A grant
		// would otherwise sign that user out and return their details (review RR-7, ADR-007).
		assertVisible(actor, account);
		if (!actor.isGlobalAdmin() && store.isGlobalAdminTarget(account.id())) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
					"Company administrators cannot manage global administrators.");
		}
		assertNotPeerAdministrator(actor, account);
		Role role = Role.parse(grant.role() == null ? "" : grant.role().trim().toUpperCase(Locale.ROOT))
			.orElseThrow(() -> new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-role", "Unsupported role."));
		// Nobody decides their own rights. A company administrator changes none of their own roles (R-2); a global
		// administrator gives themselves no sensitive role: another global administrator must (RR-3).
		if (actor.id() == userId && (!actor.isGlobalAdmin() || role.requiresFourEyes())) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "self-grant",
					"You cannot change your own roles. Ask a global administrator.");
		}
		// Four eyes, permanent and for every administrator (RR-3): whoever created an account never gives it a
		// sensitive role (approval, buying, ordering, receiving). Another administrator, or a global administrator
		// for a company with a single administrator, makes that grant; the grant itself is the second decision.
		// F-2: the same for ADMIN and GLOBAL_ADMIN, so nobody creates a second identity and makes it an administrator.
		if (role.requiresFourEyes() && directory.createdBy(userId, actor.id())) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "four-eyes",
					"You created this account. Another administrator must give it this role.");
		}
		if (role == Role.SUPPLIER_CONTACT) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-role",
					"Supplier contacts are invited from messaging, not user administration.");
		}
		// An external contact's account never holds an internal role: with one it could approve through an
		// e-mail link although the portal filter keeps it out of the application (review RR-5).
		if (directory.isSupplierContact(userId)) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-role",
					"A supplier contact account cannot hold an internal role.");
		}
		if (!actor.isGlobalAdmin()) {
			if (COMPANY_ADMIN_FORBIDDEN.contains(role) || role == Role.GLOBAL_ADMIN || role == Role.ADMIN) {
				throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
						"Company administrators cannot grant this role.");
			}
			if (grant.companyId() == null || !actor.canAdminCompany(grant.companyId())) {
				throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
						"You can only grant roles in companies you administer.");
			}
		}
		Long companyId = role.isGroupWide() ? null : grant.companyId();
		if (!role.isGroupWide()) {
			if (companyId == null || !store.companyInGroup(companyId, account.groupId())) {
				throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-company",
						"Company-scoped roles require a valid company.");
			}
			if (role == Role.ADMIN && !actor.isGlobalAdmin()) {
				throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
						"Only a global administrator can grant ADMIN.");
			}
		}
		Long departmentId = grant.departmentId();
		if (departmentId != null) {
			if (companyId == null || !store.departmentInCompany(departmentId, companyId)) {
				throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-department",
						"Department does not belong to the grant company.");
			}
		}
		try {
			long grantId = store.insertGrant(userId, role, companyId, departmentId);
			store.bumpCredentialsVersion(userId);
			if (audit) {
				auditLog.record(new AuditRecord(actor.id(), actor.email(), account.companyId(), "USER_ROLE",
						Long.toString(grantId), "ROLE_GRANTED", null, role.name(), null,
						"{\"userId\":" + userId + ",\"role\":\"" + role.name() + "\"}"));
			}
		}
		catch (org.springframework.dao.DuplicateKeyException ex) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "role-exists", "This role grant already exists.");
		}
	}

	private UserAdminViews.Detail toDetail(UserAdminStore.AccountRow account) {
		List<UserAdminViews.GrantView> grants = store.listGrants(account.id())
			.stream()
			.map(g -> new UserAdminViews.GrantView(g.id(), g.role(), g.companyId(), g.departmentId()))
			.toList();
		UserAdminStore.OpenAssignments open = store.openAssignments(account.id());
		return new UserAdminViews.Detail(account.id(), account.email(), account.displayName(), account.companyId(),
				account.siteId(), account.departmentId(), account.preferredLanguage(), account.active(), account.demo(),
				account.version(), grants, open.pendingApprovals(), open.assignedBuying());
	}

	private void requireUserAdmin(CurrentUser actor) {
		if (!actor.isGlobalAdmin() && !actor.hasRole(Role.ADMIN)) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden", "User administration is not permitted.");
		}
	}

	/** Home company + security actions (activate, password, sessions, e-mail correction). */
	void assertCanManage(CurrentUser actor, UserAdminStore.AccountRow account) {
		assertSameGroup(actor, account);
		if (!actor.isGlobalAdmin() && store.isGlobalAdminTarget(account.id())) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
					"Company administrators cannot manage global administrators.");
		}
		if (!actor.isGlobalAdmin() && !actor.canAdminCompany(account.companyId())) {
			throw new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "User not found.");
		}
		assertNotPeerAdministrator(actor, account);
		if (!actor.isGlobalAdmin() && hasGrantsOutsideAdminScope(actor, account.id())) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "out-of-scope-grants",
					"This account has role grants outside your authorised companies. A global administrator must perform account-wide security changes.");
		}
	}

	/**
	 * A company administrator has no power over a peer: resetting, deactivating, signing out, or adding and removing
	 * the roles of another administrator would let one take over, weaken or lock out the other (audit S-4, review
	 * R-3). Every role change also ends the target's sessions. Administrators are managed by global administrators.
	 */
	private void assertNotPeerAdministrator(CurrentUser actor, UserAdminStore.AccountRow account) {
		if (!actor.isGlobalAdmin() && actor.id() != account.id()
				&& store.listGrants(account.id()).stream().anyMatch(g -> Role.ADMIN.name().equals(g.role()))) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "peer-administrator",
					"Company administrators cannot manage another administrator. Ask a global administrator.");
		}
	}

	/**
	 * The account is visible to the administrator: same group, and for a company administrator a home company or a
	 * role grant in one of their companies. Otherwise 404, as if the account did not exist (ADR-007).
	 */
	private void assertVisible(CurrentUser actor, UserAdminStore.AccountRow account) {
		assertSameGroup(actor, account);
		if (!actor.isGlobalAdmin() && !actor.canAdminCompany(account.companyId()) && !store.listGrants(account.id())
			.stream()
			.anyMatch(g -> g.companyId() != null && actor.canAdminCompany(g.companyId()))) {
			throw new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "User not found.");
		}
	}

	private void assertSameGroup(CurrentUser actor, UserAdminStore.AccountRow account) {
		if (!store.companyInGroup(account.companyId(), actor.groupId())) {
			throw new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "User not found.");
		}
	}

	private boolean hasGrantsOutsideAdminScope(CurrentUser actor, long userId) {
		Set<Long> allowed = actor.adminCompanyIds();
		for (UserAdminStore.RoleGrantRow grant : store.listGrants(userId)) {
			if (grant.companyId() == null) {
				return true;
			}
			if (!allowed.contains(grant.companyId())) {
				return true;
			}
		}
		return false;
	}

	private void validateHome(CurrentUser actor, long companyId, Long siteId, Long departmentId) {
		if (!store.companyInGroup(companyId, actor.groupId())) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-company", "Company is not in your group.");
		}
		if (!actor.isGlobalAdmin() && !actor.canAdminCompany(companyId)) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
					"You can only place users in companies you administer.");
		}
		if (siteId != null && !store.siteInCompany(siteId, companyId)) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-site", "Site does not belong to the company.");
		}
		if (departmentId != null && !store.departmentInCompany(departmentId, companyId)) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-department",
					"Department does not belong to the company.");
		}
	}

	static String normalizeEmail(String email) {
		String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
		// One address, no spaces, quotes, brackets or control characters; a dot in the domain (audit S-13).
		if (value.length() > 254 || !EMAIL.matcher(value).matches()) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-email", "A valid e-mail is required.");
		}
		return value;
	}

	private static String requireName(String name) {
		if (!StringUtils.hasText(name)) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-name", "Display name is required.");
		}
		return name.trim();
	}

	private static String language(String language) {
		if (!StringUtils.hasText(language)) {
			return "en";
		}
		String value = language.trim().toLowerCase(Locale.ROOT);
		if (!Set.of("en", "fr", "ar").contains(value)) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-language", "Language must be en, fr or ar.");
		}
		return value;
	}

}
