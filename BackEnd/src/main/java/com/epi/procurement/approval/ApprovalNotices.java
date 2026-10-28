package com.epi.procurement.approval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.notification.Notice;
import com.epi.procurement.notification.Notifications;
import com.epi.procurement.organization.ExchangeRates;
import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.request.RequestSnapshot;

/**
 * Builds and enqueues the approval notifications (S2-AC-10) with the request summary of S2-AC-07.
 */
@Component
class ApprovalNotices {

	private final Notifications notifications;

	private final UserDirectory users;

	private final OrganizationDirectory organization;

	private final ExchangeRates rates;

	ApprovalNotices(Notifications notifications, UserDirectory users, OrganizationDirectory organization,
			ExchangeRates rates) {
		this.notifications = notifications;
		this.users = users;
		this.organization = organization;
		this.rates = rates;
	}

	/**
	 * A new (or delegated, or reminded) task: summary plus Approve (one-tap link) and Review buttons.
	 */
	void task(String kind, String stageName, long taskId, long assignee, RequestSnapshot request, Long actorId,
			Instant now) {
		Map<String, String> params = params(request, stageName);
		if (actorId != null) {
			params.put("actor", name(actorId));
		}
		// The token is created when the e-mail is sent, never stored in the outbox (S2CR-01).
		String link = com.epi.procurement.notification.NoticeLinkResolver.placeholder(ApprovalLinkResolver.KIND, taskId,
				assignee);
		notifications.enqueue(new Notice(kind, assignee, request.id(), params, lines(request),
				List.of(new Notice.Action("mail.action.approve", link),
						new Notice.Action("mail.action.review", "/requests/" + request.id())),
				"/requests/" + request.id()));
	}

	/**
	 * A notice to the request's authors (requester and entered-by user).
	 */
	void authors(String kind, RequestSnapshot request, long actorId, String stageName, String text) {
		Map<String, String> params = params(request, stageName);
		params.put("actor", name(actorId));
		if (text != null) {
			params.put("text", text);
		}
		for (long author : request.requesterUserId() == request.enteredByUserId() ? List.of(request.requesterUserId())
				: List.of(request.requesterUserId(), request.enteredByUserId())) {
			notifications.enqueue(new Notice(kind, author, request.id(), params, List.of(),
					List.of(new Notice.Action("mail.action.open", "/requests/" + request.id())),
					"/requests/" + request.id()));
		}
	}

	void user(String kind, long recipient, RequestSnapshot request, Long actorId, String stageName, String text,
			Long onBehalfOf) {
		Map<String, String> params = params(request, stageName);
		if (actorId != null) {
			params.put("actor", name(actorId));
		}
		if (onBehalfOf != null) {
			params.put("onBehalfOf", name(onBehalfOf));
		}
		if (text != null) {
			params.put("text", text);
		}
		notifications.enqueue(new Notice(kind, recipient, request.id(), params, List.of(),
				List.of(new Notice.Action("mail.action.open", "/requests/" + request.id())),
				"/requests/" + request.id()));
	}

	/**
	 * Tells the group's administrators that a submission failed for lack of an eligible approver; committed even
	 * though the submission rolls back.
	 */
	void noEligibleApprover(RequestSnapshot request, String stageName) {
		Map<String, String> params = new HashMap<>();
		params.put("company", companyName(request.requestingCompanyId()));
		params.put("stage", stageName);
		for (long admin : users.administratorsOfGroup(request.groupId())) {
			notifications.enqueueIndependently(new Notice("NO_ELIGIBLE_APPROVER", admin, null, params, List.of(),
					List.of(new Notice.Action("mail.action.open", "/admin/approval-rules")), "/admin/approval-rules"));
		}
	}

	String name(long userId) {
		return users.findContact(userId).map(UserDirectory.UserContact::displayName).orElse("");
	}

	private Map<String, String> params(RequestSnapshot request, String stageName) {
		Map<String, String> params = new HashMap<>();
		params.put("number", request.number() == null ? "" : request.number());
		params.put("title", request.title() == null ? "" : request.title());
		params.put("requester", name(request.requesterUserId()));
		params.put("company", companyName(request.requestingCompanyId()));
		params.put("amount", money(request.totalAmount(), request.currency()));
		params.put("requiredDate", request.requiredDate() == null ? "" : request.requiredDate().toString());
		if (stageName != null) {
			params.put("stage", stageName);
		}
		return params;
	}

	private String money(BigDecimal amount, String currency) {
		if (amount == null) {
			return "";
		}
		return amount.setScale(rates.minorUnits(currency), RoundingMode.HALF_UP).toPlainString() + " " + currency;
	}

	private String companyName(long companyId) {
		return organization.findCompany(companyId).map(OrganizationDirectory.CompanyRef::name).orElse("");
	}

	private static List<String> lines(RequestSnapshot request) {
		return request.lines()
			.stream()
			.limit(5)
			.map(l -> (l.quantity() == null ? "" : l.quantity().stripTrailingZeros().toPlainString() + " "
					+ (l.unit() == null ? "" : l.unit()) + " - ") + (l.description() == null ? "" : l.description()))
			.toList();
	}

}
