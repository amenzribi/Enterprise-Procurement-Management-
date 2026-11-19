package com.epi.procurement.notification;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.epi.procurement.identity.EmailChangeTokens;

/** Creates the e-mail change proof-of-control token when the e-mail is sent (deferred secret, never in the outbox). */
@Component
class EmailChangeLinkResolver implements NoticeLinkResolver {

	static final String KIND = "email-change";

	private final EmailChangeTokens tokens;

	EmailChangeLinkResolver(EmailChangeTokens tokens) {
		this.tokens = tokens;
	}

	@Override
	public Optional<String> resolve(String kind, List<String> args) {
		if (!KIND.equals(kind) || args.size() != 1) {
			return Optional.empty();
		}
		return Optional.of(tokens.createLinkPath(Long.parseLong(args.get(0))));
	}

}
