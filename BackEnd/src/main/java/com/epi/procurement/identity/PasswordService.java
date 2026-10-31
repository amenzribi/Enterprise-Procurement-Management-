package com.epi.procurement.identity;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.ClientAddressResolver;
import com.epi.procurement.platform.web.RequestRateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Own password change and public token redeem (S10-AC-04/05/09).
 */
@Service
public class PasswordService {

	private final CredentialStore credentials;

	private final PasswordResetStore tokens;

	private final PasswordEncoder encoder;

	private final PasswordPolicy policy;

	private final UserAdminStore users;

	private final AuditLog auditLog;

	private final RequestRateLimiter rateLimiter;

	private final ClientAddressResolver addresses;

	private final Clock clock;

	private final int redeemLimitPerMinute;

	PasswordService(CredentialStore credentials, PasswordResetStore tokens, PasswordEncoder encoder,
			PasswordPolicy policy, UserAdminStore users, AuditLog auditLog, RequestRateLimiter rateLimiter,
			ClientAddressResolver addresses, ObjectProvider<Clock> clock,
			@org.springframework.beans.factory.annotation.Value("${app.security.password-reset-rate-limit-per-minute:20}") int redeemLimitPerMinute) {
		this.credentials = credentials;
		this.tokens = tokens;
		this.encoder = encoder;
		this.policy = policy;
		this.users = users;
		this.auditLog = auditLog;
		this.rateLimiter = rateLimiter;
		this.addresses = addresses;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
		this.redeemLimitPerMinute = redeemLimitPerMinute;
	}

	@Transactional
	public void changeOwnPassword(CurrentUser actor, String currentPassword, String newPassword, HttpSession session) {
		policy.validate(newPassword);
		CredentialStore.StoredCredential stored = credentials.findByEmail(actor.email())
			.orElseThrow(() -> new ApiProblemException(HttpStatus.UNAUTHORIZED, "invalid-credentials",
					"The e-mail or password is incorrect."));
		if (!encoder.matches(currentPassword == null ? "" : currentPassword, stored.passwordHash())) {
			throw new ApiProblemException(HttpStatus.UNAUTHORIZED, "invalid-credentials",
					"The e-mail or password is incorrect.");
		}
		int version = applyNewPassword(actor.id(), actor.email(), newPassword, actor.id(), "PASSWORD_CHANGED");
		if (session != null) {
			SessionCredentials.bind(session, version);
		}
	}

	@Transactional
	public void redeemReset(String rawToken, String newPassword, HttpServletRequest request) {
		String address = addresses.resolve(request);
		rateLimiter.check("pwd-reset:" + address, redeemLimitPerMinute);
		policy.validate(newPassword);
		Instant now = clock.instant();
		String hash = PasswordResetTokens.hash(rawToken == null ? "" : rawToken);
		var row = tokens.findActiveByHash(hash, now).orElseThrow(PasswordService::neutralInvalid);
		if (!tokens.markUsed(row.id(), now)) {
			throw neutralInvalid();
		}
		UserAdminStore.AccountRow account = users.findAccount(row.userId()).orElseThrow(PasswordService::neutralInvalid);
		if (!account.active()) {
			throw neutralInvalid();
		}
		applyNewPassword(account.id(), account.email(), newPassword, null, "PASSWORD_RESET_REDEEMED");
	}

	private int applyNewPassword(long userId, String email, String newPassword, Long actorId, String action) {
		credentials.savePasswordHash(userId, encoder.encode(newPassword), false);
		int version = users.bumpCredentialsVersion(userId);
		tokens.expireActive(userId, clock.instant());
		auditLog.record(new AuditRecord(actorId != null ? actorId : userId, email, null, "USER_ACCOUNT",
				Long.toString(userId), action, null, Integer.toString(version), null, null));
		return version;
	}

	private static ApiProblemException neutralInvalid() {
		return new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "This link is not valid.");
	}

}
