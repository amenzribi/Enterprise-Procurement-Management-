package com.epi.procurement.notification;

import java.util.List;
import java.util.Optional;

/**
 * Resolves a deferred link placeholder ({@code {{link:kind:arg:...}}}) when the e-mail is sent, so secrets such as
 * one-tap approval tokens never sit in the outbox (S2CR-01). Implemented by the modules that own the link.
 */
public interface NoticeLinkResolver {

	/**
	 * @return an application path (starting with {@code /}) for this kind of link, or empty when not handled
	 */
	Optional<String> resolve(String kind, List<String> args);

	/**
	 * The placeholder to put in a {@link Notice.Action} URL.
	 */
	static String placeholder(String kind, Object... args) {
		StringBuilder text = new StringBuilder("{{link:").append(kind);
		for (Object arg : args) {
			text.append(':').append(arg);
		}
		return text.append("}}").toString();
	}

}
