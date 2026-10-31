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

	private static final Set<Role> COMPANY_ADMIN_FORBIDDEN = Set.of(Role.GLOBAL_ADMIN, Role.ADMIN, Role.GROUP_MANAGEMENT,
			Role.SUPPLIER_CONTACT);

	private final UserAdminStore store;

	private final CredentialStore credentials;

	private final PasswordEncoder encoder;

	private final AuditLog auditLog;

	private final ApplicationEventPublisher events;

	UserAdminService(UserAdminStore store, CredentialStore credentials, PasswordEncoder encoder, AuditLog auditLog,
			ApplicationEventPublisher events) {
		this.store = store;
		this.credentials = credentials;
		this.encoder = encoder;
		this.auditLog = auditLog;
		this.events = events;
	}

	public List<UserAdminViews.ListItem> list(CurrentUser actor, String text, Boolean active, int page, int size) {
		requireUserAdmin(actor);
		return store.search(actor.groupId(), text, active, page, size)
			.stream()
			.map(r -> new UserAdminViews.ListItem(r.id(), r.email(), r.displayName(), r.companyId(), r.companyCode(),
					r.active()))
			.toList();
	}

	public UserAdminViews.Detail get(CurrentUser actor, long userId) {
		requireUserAdmin(actor);
		UserAdminStore.AccountRow account = store.findAccount(userId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "User not found."));
		assertCanManage(actor, account);
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
		boolean active = body.active() == null || body.active();
		long id = store.insertAccount(email, requireName(body.displayName()), body.companyId(), body.siteId(),
				body.departmentId(), language(body.preferredLanguage()), active, false);
		// Unusable random hash until setup link is redeemed (or bootstrap sets one).
		credentials.savePasswordHash(id, encoder.encode(java.util.UUID.randomUUID().toString()), true);
		if (body.roles() != null) {
			for (UserAdminViews.GrantRequest grant : body.roles()) {
				addGrantInternal(actor, id, grant, false);
			}
		}
		auditLog.record(new AuditRecord(actor.id(), actor.email(), body.companyId(), "USER_ACCOUNT", Long.toString(id),
				"USER_CREATED", null, active ? "ACTIVE" : "INACTIVE", null, "{\"email\":\"" + email + "\"}"));
		publishPasswordSetup(id, "SETUP", actor, requireName(body.displayName()), body.companyId());
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
		if (actor.id() == userId && !active) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "cannot-deactivate-self",
					"You cannot deactivate your own account.");
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
		assertCanManage(actor, account);
		UserAdminStore.RoleGrantRow grant = store.findGrant(grantId)
			.filter(g -> g.userId() == userId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "Role grant not found."));
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
		publishPasswordSetup(userId, "RESET", actor, account.displayName(), account.companyId());
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
		assertCanManage(actor, account);
		Role role = Role.parse(grant.role() == null ? "" : grant.role().trim().toUpperCase(Locale.ROOT))
			.orElseThrow(() -> new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-role", "Unsupported role."));
		if (role == Role.SUPPLIER_CONTACT) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-role",
					"Supplier contacts are invited from messaging, not user administration.");
		}
		if (!actor.isGlobalAdmin()) {
			if (COMPANY_ADMIN_FORBIDDEN.contains(role) || role == Role.GLOBAL_ADMIN) {
				throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
						"Company administrators cannot grant this role.");
			}
			if (grant.companyId() == null || !store.companyInGroup(grant.companyId(), actor.groupId())) {
				throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-company",
						"Role grants must target a company in your organisation.");
			}
		}
		Long companyId = role.isGroupWide() ? null : grant.companyId();
		if (!role.isGroupWide()) {
			if (companyId == null || !store.companyInGroup(companyId, account.groupId())) {
				throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-company",
						"Company-scoped roles require a valid company.");
			}
		}
		else if (!actor.isGlobalAdmin() && role == Role.ADMIN) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
					"Company administrators cannot grant ADMIN.");
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

	private void assertCanManage(CurrentUser actor, UserAdminStore.AccountRow account) {
		if (!store.companyInGroup(account.companyId(), actor.groupId())) {
			throw new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "User not found.");
		}
		if (!actor.isGlobalAdmin() && store.isGlobalAdminTarget(account.id())) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
					"Company administrators cannot manage global administrators.");
		}
	}

	private void validateHome(CurrentUser actor, long companyId, Long siteId, Long departmentId) {
		if (!store.companyInGroup(companyId, actor.groupId())) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-company", "Company is not in your group.");
		}
		if (siteId != null && !store.siteInCompany(siteId, companyId)) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-site", "Site does not belong to the company.");
		}
		if (departmentId != null && !store.departmentInCompany(departmentId, companyId)) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-department",
					"Department does not belong to the company.");
		}
	}

	private static String normalizeEmail(String email) {
		if (!StringUtils.hasText(email) || !email.contains("@")) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-email", "A valid e-mail is required.");
		}
		return email.trim().toLowerCase(Locale.ROOT);
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
