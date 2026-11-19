package com.epi.procurement.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.ClientAddressResolver;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.RequestRateLimiter;
import com.epi.procurement.platform.web.ValidationFailedException;

import jakarta.servlet.http.HttpServletRequest;

import tools.jackson.databind.json.JsonMapper;

/**
 * Correction of an account's e-mail address by a global administrator (Sprint 2, ADR-016 step 5). Rules:
 * <ul>
 * <li>Global administrators only, within their group, never on their own account, never on a supplier contact;
 * company scope, peer and four-eyes rules of user administration apply ({@code UserAdminService.assertCanManage}).</li>
 * <li>An account holding a sensitive or administrator role needs a second global administrator, who is neither the
 * requester, nor the account, nor an account the requester created.</li>
 * <li>Nothing changes until the owner of the NEW mailbox opens the single-use, expiring link sent there. Then the
 * address changes, outstanding set-up and reset links expire, sessions end, and the OLD address is told.</li>
 * <li>While a change is open, the account is neither activated nor sent a set-up or reset link (they would go to the
 * address being replaced).</li>
 * <li>Every step and every refused attempt is recorded in the audit trail (refusals in their own transaction).</li>
 * </ul>
 */
@Service
public class EmailChangeService {

	private static final Logger log = LoggerFactory.getLogger(EmailChangeService.class);

	private static final String ENTITY = "USER_ACCOUNT";

	public record View(long id, String status, String oldEmail, String newEmail, String reason, long requestedBy,
			Instant requestedAt, Long approvedBy, Instant tokenExpiresAt) {
	}

	private final UserAdminStore accounts;

	private final UserAdminService admin;

	private final EmailChangeStore changes;

	private final UserDirectory directory;

	private final PasswordResetStore resetTokens;

	private final AuditLog auditLog;

	private final ApplicationEventPublisher events;

	private final RequestRateLimiter rateLimiter;

	private final ClientAddressResolver addresses;

	private final JsonMapper json;

	private final TransactionTemplate independent;

	private final Clock clock;

	private final int confirmLimitPerMinute;

	EmailChangeService(UserAdminStore accounts, UserAdminService admin, EmailChangeStore changes, UserDirectory directory,
			PasswordResetStore resetTokens, AuditLog auditLog, ApplicationEventPublisher events,
			RequestRateLimiter rateLimiter, ClientAddressResolver addresses, JsonMapper json,
			PlatformTransactionManager transactions, ObjectProvider<Clock> clock,
			@Value("${app.security.password-reset-rate-limit-per-minute:20}") int confirmLimitPerMinute) {
		this.accounts = accounts;
		this.admin = admin;
		this.changes = changes;
		this.directory = directory;
		this.resetTokens = resetTokens;
		this.auditLog = auditLog;
		this.events = events;
		this.rateLimiter = rateLimiter;
		this.addresses = addresses;
		this.json = json;
		this.independent = new TransactionTemplate(transactions);
		this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock.getIfAvailable(Clock::systemUTC);
		this.confirmLimitPerMinute = confirmLimitPerMinute;
	}

	@Transactional(readOnly = true)
	public View current(CurrentUser actor, long userId) {
		UserAdminStore.AccountRow account = manageable(actor, userId, "EMAIL_CHANGE_VIEW");
		return changes.open(account.id()).map(EmailChangeService::view).orElse(null);
	}

	@Transactional
	public View request(CurrentUser actor, long userId, String newEmail, String reason) {
		UserAdminStore.AccountRow account = manageable(actor, userId, "EMAIL_CHANGE_REQUEST");
		if (actor.id() == userId) {
			throw refuse(actor, userId, "EMAIL_CHANGE_REQUEST", HttpStatus.FORBIDDEN, "self-email-change",
					"You cannot change your own address here. Ask another global administrator.");
		}
		if (directory.isSupplierContact(userId)) {
			throw refuse(actor, userId, "EMAIL_CHANGE_REQUEST", HttpStatus.FORBIDDEN, "supplier-contact-account",
					"A supplier contact's address is managed by inviting the contact again.");
		}
		String address = UserAdminService.normalizeEmail(newEmail);
		String why = reason == null ? "" : reason.strip();
		if (why.length() < 5 || why.length() > 500) {
			throw new ValidationFailedException(List.of(FieldViolation.of("reason", "required")));
		}
		if (address.equals(account.email())) {
			throw new ValidationFailedException(List.of(FieldViolation.of("newEmail", "same-email")));
		}
		if (changes.openForUpdate(userId).isPresent()) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "email-change-pending",
					"A change of address is already waiting for this account. Cancel it first.");
		}
		if (changes.addressTaken(address, userId)) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "email-in-use", "This address is already used.");
		}
		boolean sensitive = sensitive(userId);
		Instant now = clock.instant();
		long id = changes.insert(userId, account.email(), address, why,
				sensitive ? EmailChangeStore.PENDING_APPROVAL : EmailChangeStore.PENDING_VERIFICATION, actor.id(), now);
		audit(actor.id(), actor.email(), account, "EMAIL_CHANGE_REQUESTED",
				Map.of("changeId", id, "newEmail", address, "secondApprovalRequired", sensitive), why);
		if (!sensitive) {
			events.publishEvent(new EmailChangeVerificationRequested(id, userId, address, account.displayName()));
		}
		return view(changes.find(id).orElseThrow());
	}

	@Transactional
	public View approve(CurrentUser actor, long userId) {
		UserAdminStore.AccountRow account = manageable(actor, userId, "EMAIL_CHANGE_APPROVE");
		EmailChangeStore.Change change = changes.openForUpdate(userId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.CONFLICT, "no-email-change",
					"No change of address is waiting for this account."));
		if (!EmailChangeStore.PENDING_APPROVAL.equals(change.status())) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "email-change-not-awaiting-approval",
					"This change of address does not wait for an approval.");
		}
		// Independent authorisation: not the requester, not the account itself, not an identity the requester created.
		if (actor.id() == change.requestedBy() || actor.id() == userId || directory.createdBy(actor.id(), change.requestedBy())) {
			throw refuse(actor, userId, "EMAIL_CHANGE_APPROVE", HttpStatus.FORBIDDEN, "email-change-four-eyes",
					"Another global administrator, independent of the request, must approve it.");
		}
		Instant now = clock.instant();
		changes.approve(change.id(), actor.id(), now);
		audit(actor.id(), actor.email(), account, "EMAIL_CHANGE_APPROVED", Map.of("changeId", change.id()), null);
		events.publishEvent(new EmailChangeVerificationRequested(change.id(), userId, change.newEmail(),
				account.displayName()));
		return view(changes.find(change.id()).orElseThrow());
	}

	@Transactional
	public void cancel(CurrentUser actor, long userId) {
		UserAdminStore.AccountRow account = manageable(actor, userId, "EMAIL_CHANGE_CANCEL");
		EmailChangeStore.Change change = changes.openForUpdate(userId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.CONFLICT, "no-email-change",
					"No change of address is waiting for this account."));
		changes.close(change.id(), "CANCELLED", actor.id(), clock.instant());
		audit(actor.id(), actor.email(), account, "EMAIL_CHANGE_CANCELLED", Map.of("changeId", change.id()), null);
	}

	/**
	 * Proof of control: the owner of the new mailbox opened the link. Every invalid link gets the same answer (404).
	 * One transaction decides and commits either way: the change takes effect, or the link is closed and the refused
	 * attempt recorded; only then is the neutral answer given (so the record survives it).
	 */
	public void confirm(String rawToken, HttpServletRequest request) {
		rateLimiter.check("email-change:" + addresses.resolve(request), confirmLimitPerMinute);
		Boolean confirmed = independent.execute(status -> confirmInTransaction(rawToken));
		if (!Boolean.TRUE.equals(confirmed)) {
			throw neutralInvalid();
		}
	}

	private boolean confirmInTransaction(String rawToken) {
		Instant now = clock.instant();
		EmailChangeStore.Change change = changes.byTokenForUpdate(PasswordResetTokens.hash(rawToken == null ? "" : rawToken))
			.orElse(null);
		if (change == null) {
			return false;
		}
		UserAdminStore.AccountRow account = accounts.findAccount(change.userId()).orElse(null);
		if (account == null) {
			return false;
		}
		String problem = change.tokenExpiresAt() == null || !change.tokenExpiresAt().isAfter(now) ? "expired"
				: !account.email().equals(change.oldEmail()) ? "address-changed-meanwhile"
						: changes.addressTaken(change.newEmail(), change.userId()) ? "address-taken" : null;
		if (problem != null) {
			changes.close(change.id(), "expired".equals(problem) ? "EXPIRED" : "CANCELLED", null, now);
			audit(null, "email-change-link", account, "EMAIL_CHANGE_CONFIRM_REFUSED",
					Map.of("changeId", change.id(), "reason", problem), null);
			return false;
		}
		changes.changeAccountEmail(change.userId(), change.newEmail(), now);
		changes.close(change.id(), "CONFIRMED", null, now);
		// Links sent to the old address stop working; sessions end (credentials version).
		resetTokens.expireActive(change.userId(), now);
		int version = accounts.bumpCredentialsVersion(change.userId());
		audit(change.userId(), change.newEmail(), account, "EMAIL_CHANGED", Map.of("changeId", change.id(), "oldEmail",
				change.oldEmail(), "newEmail", change.newEmail(), "credentialsVersion", version), null);
		events.publishEvent(new EmailAddressChanged(change.userId(), change.oldEmail(), change.newEmail(),
				account.displayName()));
		return true;
	}

	/** True while a change is open: activation and set-up or reset links wait for it. */
	boolean changeOpen(long userId) {
		return changes.open(userId).isPresent();
	}

	private UserAdminStore.AccountRow manageable(CurrentUser actor, long userId, String step) {
		if (!actor.isGlobalAdmin()) {
			throw refuse(actor, userId, step, HttpStatus.FORBIDDEN, "forbidden",
					"Only a global administrator can correct an account's e-mail address.");
		}
		UserAdminStore.AccountRow account = accounts.findAccount(userId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "User not found."));
		admin.assertCanManage(actor, account);
		return account;
	}

	private boolean sensitive(long userId) {
		return accounts.isGlobalAdminTarget(userId) || accounts.listGrants(userId)
			.stream()
			.map(grant -> Role.parse(grant.role()).orElse(null))
			.anyMatch(role -> role != null && role.requiresFourEyes());
	}

	/** Records the refused attempt in its own transaction (it survives the rollback), then returns the answer. */
	private ApiProblemException refuse(CurrentUser actor, long userId, String step, HttpStatus status, String code,
			String message) {
		try {
			independent.executeWithoutResult(tx -> auditLog.record(new AuditRecord(actor.id(), actor.email(), null, ENTITY,
					Long.toString(userId), "EMAIL_CHANGE_REFUSED", null, null, null,
					json.writeValueAsString(Map.of("step", step, "code", code)))));
		}
		catch (RuntimeException ex) {
			log.warn("Refused e-mail change attempt could not be recorded: {}", ex.getClass().getSimpleName());
		}
		return new ApiProblemException(status, code, message);
	}

	private void audit(Long actorId, String actorLabel, UserAdminStore.AccountRow account, String action,
			Map<String, Object> details, String comment) {
		auditLog.record(new AuditRecord(actorId, actorLabel, account.companyId(), ENTITY, Long.toString(account.id()),
				action, null, null, comment, json.writeValueAsString(details)));
	}

	private static View view(EmailChangeStore.Change change) {
		return new View(change.id(), change.status(), change.oldEmail(), change.newEmail(), change.reason(),
				change.requestedBy(), change.requestedAt(), change.approvedBy(), change.tokenExpiresAt());
	}

	private static ApiProblemException neutralInvalid() {
		return new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "This link is not valid.");
	}

}
