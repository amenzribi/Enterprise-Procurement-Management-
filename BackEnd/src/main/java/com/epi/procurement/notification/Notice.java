package com.epi.procurement.notification;

import java.util.List;
import java.util.Map;

/**
 * A notification to one user, rendered in the recipient's language when enqueued.
 *
 * @param kind template key, e.g. {@code TASK_ASSIGNED} (see notification/messages*.properties)
 * @param params values for the template placeholders ({@code {number}}, {@code {title}}, {@code {actor}}, ...)
 * @param lines summary lines of the request shown in the e-mail (at most 5 are rendered)
 * @param actions e-mail buttons; the first is the primary action
 * @param inAppLink application path opened from the in-app notification
 */
public record Notice(String kind, long recipientUserId, Long requestId, Map<String, String> params,
		List<String> lines, List<Action> actions, String inAppLink) {

	public Notice {
		params = Map.copyOf(params);
		lines = List.copyOf(lines);
		actions = List.copyOf(actions);
	}

	/**
	 * An e-mail button. {@code url} may be absolute or an application path (prefixed with the public base URL).
	 */
	public record Action(String labelKey, String url) {
	}

}
