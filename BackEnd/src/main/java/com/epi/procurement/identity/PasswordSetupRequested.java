package com.epi.procurement.identity;

/**
 * Published when an administrator issues a password setup or reset e-mail (handled by the notification module).
 */
public record PasswordSetupRequested(long userId, String purpose, Long actorUserId, String actorLabel, long companyId,
		String displayName) {
}
