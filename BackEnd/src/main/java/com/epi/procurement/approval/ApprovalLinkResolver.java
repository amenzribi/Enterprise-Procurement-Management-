package com.epi.procurement.approval;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.epi.procurement.notification.NoticeLinkResolver;

/**
 * Creates the one-tap token when the e-mail is sent (S2CR-01): the outbox only holds
 * {@code {{link:approval:taskId:userId}}}. A task no longer pending for that user gets the request page instead.
 */
@Component
class ApprovalLinkResolver implements NoticeLinkResolver {

	static final String KIND = "approval";

	private final ApprovalStore store;

	private final LinkTokens tokens;

	private final Clock clock;

	ApprovalLinkResolver(ApprovalStore store, LinkTokens tokens, ObjectProvider<Clock> clock) {
		this.store = store;
		this.tokens = tokens;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@Override
	public Optional<String> resolve(String kind, List<String> args) {
		if (!KIND.equals(kind) || args.size() != 2) {
			return Optional.empty();
		}
		long taskId = Long.parseLong(args.get(0));
		long userId = Long.parseLong(args.get(1));
		return store.task(taskId).map(task -> task.isPending() && task.assigneeUserId() == userId
				? tokens.create(taskId, userId, clock.instant())
				: "/requests/" + task.requestId());
	}

}
