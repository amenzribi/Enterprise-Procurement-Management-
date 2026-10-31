package com.epi.procurement.notification;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.epi.procurement.identity.PasswordResetTokens;

/**
 * Creates the password setup/reset token when the e-mail is sent (same deferred-secret pattern as approval links).
 */
@Component
class PasswordResetLinkResolver implements NoticeLinkResolver {

	static final String KIND = "password-reset";

	private final PasswordResetTokens tokens;

	PasswordResetLinkResolver(PasswordResetTokens tokens) {
		this.tokens = tokens;
	}

	@Override
	public Optional<String> resolve(String kind, List<String> args) {
		if (!KIND.equals(kind) || args.size() != 3) {
			return Optional.empty();
		}
		long userId = Long.parseLong(args.get(0));
		String purpose = args.get(1);
		Long createdBy = "0".equals(args.get(2)) ? null : Long.parseLong(args.get(2));
		return Optional.of(tokens.createLinkPath(userId, purpose, createdBy));
	}

}
