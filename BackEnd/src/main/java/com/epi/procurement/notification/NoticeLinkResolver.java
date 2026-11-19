package com.epi.procurement.notification;

import java.util.List;
import java.util.Optional;

/**
 * Resolves a deferred link placeholder ({@code {{link:kind:arg:...}}}) when the e-mail is sent, so secrets such as
 * one-tap approval tokens never sit in the outbox (S2CR-01). Implemented by the modules that own the link.
 */
public interface NoticeLinkResolver {

	/** What a link kind may look like (lower-case words joined by hyphens); the sender matches exactly this. */
	String KIND = "[a-z]+(?:-[a-z]+)*";

	/** What an argument may look like (ids, or a server-side code such as {@code SETUP}); never user input. */
	String ARG = "[A-Za-z0-9]+";

	/**
	 * @return an application path (starting with {@code /}) for this kind of link, or empty when not handled
	 */
	Optional<String> resolve(String kind, List<String> args);

	/**
	 * The placeholder to put in a {@link Notice.Action} URL.
	 */
	static String placeholder(String kind, Object... args) {
		// A placeholder the sender cannot match is mailed as a dead link (found in Sprint 2): refuse it here instead.
		if (kind == null || !kind.matches(KIND)) {
			throw new IllegalArgumentException("Invalid link kind: " + kind);
		}
		for (Object arg : args) {
			if (arg == null || !String.valueOf(arg).matches(ARG)) {
				throw new IllegalArgumentException("Invalid link argument");
			}
		}
		StringBuilder text = new StringBuilder("{{link:").append(kind);
		for (Object arg : args) {
			text.append(':').append(arg);
		}
		return text.append("}}").toString();
	}

}
