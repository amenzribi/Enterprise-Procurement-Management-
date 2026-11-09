package com.epi.procurement.approval;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.organization.ExchangeRates;
import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.request.RequestSnapshot;
import com.epi.procurement.request.RequestWorkflow;

/**
 * One-tap e-mail approval (S2-AC-07, S2DR-07). A token is valid only while: it exists, is unused and not expired,
 * its task is still pending and assigned to the token's user, that user is active and still eligible, and no other
 * signed-in user presents it. Every invalid case returns the same neutral 404 {@code link-invalid}. Only Approve is
 * possible through a link; nothing happens on a GET.
 */
@Service
class ApprovalLinks {

	private static final Logger log = LoggerFactory.getLogger(ApprovalLinks.class);

	private final ApprovalStore store;

	private final ApprovalEngine engine;

	private final ApproverResolver resolver;

	private final RequestWorkflow requests;

	private final UserDirectory users;

	private final OrganizationDirectory organization;

	private final ExchangeRates rates;

	private final TransactionTemplate transaction;

	private final CurrentUserProvider actingUsers;

	private final Clock clock;

	ApprovalLinks(ApprovalStore store, ApprovalEngine engine, ApproverResolver resolver, RequestWorkflow requests,
			UserDirectory users, OrganizationDirectory organization, ExchangeRates rates,
			PlatformTransactionManager transactionManager, CurrentUserProvider actingUsers, ObjectProvider<Clock> clock) {
		this.store = store;
		this.engine = engine;
		this.resolver = resolver;
		this.requests = requests;
		this.users = users;
		this.organization = organization;
		this.rates = rates;
		this.transaction = new TransactionTemplate(transactionManager);
		this.actingUsers = actingUsers;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	ApprovalViews.LinkSummary summary(String token, Long sessionUserId) {
		Valid valid = validate(token, sessionUserId);
		RequestSnapshot request = valid.request();
		String requester = users.findContact(request.requesterUserId()).map(UserDirectory.UserContact::displayName).orElse("");
		ApprovalViews.Ref company = organization.findCompany(request.requestingCompanyId())
			.map(c -> new ApprovalViews.Ref(c.id(), c.code(), c.name()))
			.orElse(new ApprovalViews.Ref(request.requestingCompanyId(), "", ""));
		return new ApprovalViews.LinkSummary(request.number(), request.title(),
				new ApprovalViews.UserRef(request.requesterUserId(), requester), company,
				request.totalAmount() == null ? null
						: request.totalAmount().setScale(rates.minorUnits(request.currency()), RoundingMode.HALF_UP),
				request.currency(), rates.minorUnits(request.currency()), request.requiredDate(), valid.task().stageName(),
				request.lines()
					.stream()
					.limit(5)
					.map(l -> new ApprovalViews.LinkLine(l.description(),
							l.quantity() == null ? null : l.quantity().stripTrailingZeros(), l.unit(), l.lineKind()))
					.toList(),
				request.lines().size(), request.serviceLineCount(), valid.token().expiresAt());
	}

	/**
	 * Approves as the token's user, then marks the token used, in one transaction: if the decision fails (already
	 * decided, reassigned) nothing changes and the link answers as invalid.
	 */
	ApprovalViews.LinkResult approve(String token, Long sessionUserId) {
		try {
			return transaction.execute(status -> {
				Valid valid = validate(token, sessionUserId);
				engine.decide(valid.approver(), valid.task().id(), valid.task().version(), ApprovalEngine.Action.APPROVE,
						null, null, "EMAIL");
				if (!store.markTokenUsed(valid.token().id(), clock.instant())) {
					throw invalid();
				}
				return new ApprovalViews.LinkResult("APPROVED", valid.request().number());
			});
		}
		catch (ApiProblemException ex) {
			if ("link-invalid".equals(ex.getCode()) || "busy".equals(ex.getCode())) {
				throw ex;
			}
			// Decided meanwhile, stale version, no next approver...: the link is no longer usable.
			throw invalid();
		}
	}

	private Valid validate(String token, Long sessionUserId) {
		if (token == null || token.length() < 20 || token.length() > 100) {
			throw invalid();
		}
		Instant now = clock.instant();
		ApprovalStore.Token stored = store.tokenByHash(LinkTokens.hash(token)).orElseThrow(ApprovalLinks::invalid);
		if (stored.usedAt() != null || !stored.expiresAt().isAfter(now)) {
			throw invalid();
		}
		if (sessionUserId != null && sessionUserId != stored.userId()) {
			// QA2-61: never act for another user; security log only (the token itself is never logged).
			log.warn("Approval link {} presented by another signed-in user {}", stored.id(), sessionUserId);
			throw invalid();
		}
		ApprovalStore.Task task = store.task(stored.taskId()).orElseThrow(ApprovalLinks::invalid);
		if (!task.isPending() || task.assigneeUserId() != stored.userId()) {
			throw invalid();
		}
		CurrentUser approver = actingUsers.loadActingUser(stored.userId()).orElseThrow(ApprovalLinks::invalid);
		RequestSnapshot request = requests.find(task.requestId()).orElseThrow(ApprovalLinks::invalid);
		// Still entitled (S2DR-07): delegated tasks were checked when delegated; others need the stage role, or the
		// escalation role when the stage fell back to it.
		boolean eligible = !request.isAuthor(approver.id()) && (task.originalUserId() != null
				|| resolver.isEligible(approver.id(), task.approverType(), store.namedUserOfTask(task.id()), request)
				|| (task.escalationRole() != null
						&& resolver.holdsStageRole(approver.id(), task.escalationRole(), request.requestingCompanyId())));
		if (!eligible) {
			throw invalid();
		}
		return new Valid(stored, task, approver, request);
	}

	static ApiProblemException invalid() {
		return new ApiProblemException(HttpStatus.NOT_FOUND, "link-invalid", "This link is no longer valid.");
	}

	private record Valid(ApprovalStore.Token token, ApprovalStore.Task task, CurrentUser approver,
			RequestSnapshot request) {
	}

	/**
	 * Optional session user id (the page also works without being signed in).
	 */
	static Long sessionUser(Optional<CurrentUser> user) {
		return user.map(CurrentUser::id).orElse(null);
	}

}
