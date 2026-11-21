package com.epi.procurement.identity;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.platform.persistence.SqlServerErrors;

/**
 * Decides whether a sign-in attempt succeeds (S1-AC-01, S1DR-03/04/05).
 * <p>
 * Every failure - unknown e-mail, wrong password, inactive account, demo account outside the demo profile,
 * throttled caller - looks the same to the caller, and a password hash comparison always runs so timing does not
 * reveal whether the account exists. Failures are logged with a hashed e-mail only; the audit chain receives
 * successful sign-ins and throttle activations for existing accounts.
 */
class SignInService {

	private static final Logger log = LoggerFactory.getLogger(SignInService.class);

	/** Attempts of the sign-in audit append when SQL Server chooses it as a deadlock victim (CI, 2026-11-18). */
	static final int AUDIT_ATTEMPTS = 3;

	private final CredentialStore credentials;

	private final PasswordEncoder passwordEncoder;

	private final LoginThrottle throttle;

	private final AuditLog auditLog;

	private final boolean demoActive;

	private final String dummyHash;

	SignInService(CredentialStore credentials, PasswordEncoder passwordEncoder, LoginThrottle throttle,
			AuditLog auditLog, boolean demoActive) {
		this.credentials = credentials;
		this.passwordEncoder = passwordEncoder;
		this.throttle = throttle;
		this.auditLog = auditLog;
		this.demoActive = demoActive;
		this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	/**
	 * Appends the {@code SIGNED_IN} event. The append is its own transaction (no transaction surrounds sign-in), so
	 * when SQL Server chooses it as a deadlock victim (error 1205) the whole append was rolled back and is run again
	 * in a new transaction: no event is lost or written twice, and the chain stays intact. Only the append is retried,
	 * never the credential check; any other failure, or a deadlock on the last attempt, propagates and the sign-in
	 * fails. Inside an outer transaction nothing is retried, because a deadlock has already rolled that back.
	 */
	private void recordSignIn(AuditRecord record) {
		boolean retryable = !TransactionSynchronizationManager.isActualTransactionActive();
		for (int attempt = 1;; attempt++) {
			try {
				auditLog.record(record);
				return;
			}
			catch (DataAccessException ex) {
				if (!retryable || !SqlServerErrors.isDeadlockVictim(ex) || attempt >= AUDIT_ATTEMPTS) {
					throw ex;
				}
				log.warn("Sign-in audit retried after a deadlock (attempt {} of {})", attempt, AUDIT_ATTEMPTS);
			}
		}
	}

	/**
	 * @return the signed-in principal, or empty for any kind of failure
	 */
	Optional<AuthenticatedUser> signIn(String rawEmail, String password, String address) {
		String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
		String secret = password == null ? "" : password;
		Optional<CredentialStore.StoredCredential> credential = email.isEmpty() ? Optional.empty()
				: credentials.findByEmail(email);
		boolean passwordMatches = passwordEncoder.matches(secret,
				credential.map(CredentialStore.StoredCredential::passwordHash).orElse(dummyHash));
		boolean blocked = throttle.isBlocked(email, address);

		if (credential.isPresent() && passwordMatches && !blocked && credential.get().active()
				&& (!credential.get().demo() || demoActive)) {
			throttle.recordSuccess(email, address);
			CredentialStore.StoredCredential user = credential.get();
			recordSignIn(new AuditRecord(user.userId(), user.email(), null, "USER_SESSION",
					Long.toString(user.userId()), "SIGNED_IN", null, null, null, null));
			return Optional.of(new AuthenticatedUser(user.userId(), user.email()));
		}

		String reason = blocked ? "throttled" : credential.isEmpty() ? "unknown-or-no-credential"
				: !passwordMatches ? "bad-password" : !credential.get().active() ? "inactive" : "demo-disabled";
		log.info("Sign-in refused [account={}, address={}, reason={}]", LoginThrottle.hashEmail(email), address, reason);
		if (!blocked && throttle.recordFailure(email, address) && credential.isPresent()) {
			CredentialStore.StoredCredential user = credential.get();
			log.warn("Sign-in throttled [account={}, address={}]", LoginThrottle.hashEmail(email), address);
			auditLog.record(new AuditRecord(null, "system", null, "USER_SESSION", Long.toString(user.userId()),
					"SIGN_IN_THROTTLED", null, null, null, null));
		}
		return Optional.empty();
	}

}
