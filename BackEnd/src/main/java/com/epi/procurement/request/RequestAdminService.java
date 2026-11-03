package com.epi.procurement.request;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.request.RequestAdminActions.Action;
import com.epi.procurement.request.RequestAdminActions.LinkCounts;
import com.epi.procurement.request.RequestAdminActions.Matrix;

import jakarta.persistence.criteria.Predicate;

/**
 * GLOBAL_ADMIN request administration: group-scoped search and state-safe mutations. Does not grant buyer, approver,
 * messaging or assistant powers. Boundary: {@code CurrentUser.groupId()} (installation/group).
 */
@Service
class RequestAdminService {

	private static final String ENTITY = "PURCHASE_REQUEST";

	private static final String CONFIRM_DELETE = "DELETE";

	private final ScopedRequests requests;

	private final RequestTransitionRepository transitions;

	private final RequestAdminLinks links;

	private final OrganizationDirectory organization;

	private final UserDirectory users;

	private final AuditLog auditLog;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	/** Short-lived cleanup previews (token → snapshot). In-memory is enough for a single-node admin tool. */
	private final ConcurrentHashMap<String, PreviewSession> previews = new ConcurrentHashMap<>();

	RequestAdminService(ScopedRequests requests, RequestTransitionRepository transitions, RequestAdminLinks links,
			OrganizationDirectory organization, UserDirectory users, AuditLog auditLog,
			ApplicationEventPublisher events, ObjectProvider<Clock> clock) {
		this.requests = requests;
		this.transitions = transitions;
		this.links = links;
		this.organization = organization;
		this.users = users;
		this.auditLog = auditLog;
		this.events = events;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	static void requireRequestAdmin(CurrentUser user) {
		if (!user.isGlobalAdmin()) {
			throw ApiProblemException.forbidden();
		}
	}

	@Transactional(readOnly = true)
	RequestAdminViews.Page search(CurrentUser user, String q, Long companyId, String status, Boolean archived,
			int page, int size) {
		requireRequestAdmin(user);
		if (page < 0) {
			throw ApiProblemException.invalidParameter("page");
		}
		if (size < 1 || size > 100) {
			throw ApiProblemException.invalidParameter("size");
		}
		var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by("id").descending()));
		var result = requests.searchInGroup(user.groupId(), filter(q, companyId, status, archived), pageable);
		List<RequestAdminViews.Summary> items = result.getContent().stream().map(this::summary).toList();
		return new RequestAdminViews.Page(items, page, size, result.getTotalElements(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	RequestAdminViews.Detail get(CurrentUser user, long id) {
		requireRequestAdmin(user);
		return detail(requests.getInGroup(user.groupId(), id));
	}

	@Transactional
	RequestAdminViews.MutationResult archive(CurrentUser user, long id, RequestAdminViews.ReasonPayload body) {
		requireRequestAdmin(user);
		String reason = requireReason(body == null ? null : body.reason());
		PurchaseRequest request = requests.getInGroupForUpdate(user.groupId(), id);
		requireVersion(body == null ? null : body.version(), request);
		Matrix matrix = RequestAdminActions.evaluate(request, links.counts(request.getId()));
		requireAllowed(matrix, Action.ARCHIVE);
		Instant now = clock.instant();
		String from = request.getStatus().name();
		request.archive(user.id(), reason, now);
		requests.saveAndFlush(request);
		audit(user, request, "REQUEST_ARCHIVED", from, from, reason,
				Map.of("archived", true, "title", nullToEmpty(request.getTitle())));
		return new RequestAdminViews.MutationResult(detail(request),
				"Request archived. It leaves everyday lists; history and the business number are preserved.");
	}

	@Transactional
	RequestAdminViews.MutationResult restore(CurrentUser user, long id, RequestAdminViews.ReasonPayload body) {
		requireRequestAdmin(user);
		PurchaseRequest request = requests.getInGroupForUpdate(user.groupId(), id);
		requireVersion(body == null ? null : body.version(), request);
		Matrix matrix = RequestAdminActions.evaluate(request, links.counts(request.getId()));
		requireAllowed(matrix, Action.RESTORE);
		Instant now = clock.instant();
		String from = request.getStatus().name();
		String previousReason = request.getArchiveReason();
		request.restoreFromArchive(user.id(), now);
		requests.saveAndFlush(request);
		audit(user, request, "REQUEST_RESTORED", from, from, body == null ? null : body.reason(),
				Map.of("previousArchiveReason", nullToEmpty(previousReason)));
		return new RequestAdminViews.MutationResult(detail(request),
				"Request restored to normal lists. Business status was not changed.");
	}

	@Transactional
	RequestAdminViews.MutationResult cancel(CurrentUser user, long id, RequestAdminViews.ReasonPayload body) {
		requireRequestAdmin(user);
		String reason = requireReason(body == null ? null : body.reason());
		PurchaseRequest request = requests.getInGroupForUpdate(user.groupId(), id);
		requireVersion(body == null ? null : body.version(), request);
		LinkCounts counts = links.counts(request.getId());
		Matrix matrix = RequestAdminActions.evaluate(request, counts);
		requireAllowed(matrix, Action.CANCEL);
		Instant now = clock.instant();
		RequestStatus from = request.getStatus();
		request.transitionTo(RequestStatus.CANCELLED, user.id(), now);
		request.touch(user.id(), now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(request.getId(), from, RequestStatus.CANCELLED,
				request.getCurrentOwnerUserId(), user.id(), now, reason));
		events.publishEvent(new RequestAdministrativelyCancelledEvent(request.getId(), user.id(), now, "CANCELLED"));
		audit(user, request, "REQUEST_ADMIN_CANCELLED", from.name(), RequestStatus.CANCELLED.name(), reason,
				Map.of("number", nullToEmpty(request.getNumber())));
		return new RequestAdminViews.MutationResult(detail(request),
				"Request cancelled. Pending approvals and usable approval links are invalidated. Completed decisions remain.");
	}

	@Transactional
	RequestAdminViews.MutationResult returnForRevision(CurrentUser user, long id,
			RequestAdminViews.ReasonPayload body) {
		requireRequestAdmin(user);
		String reason = requireReason(body == null ? null : body.reason());
		PurchaseRequest request = requests.getInGroupForUpdate(user.groupId(), id);
		requireVersion(body == null ? null : body.version(), request);
		Matrix matrix = RequestAdminActions.evaluate(request, links.counts(request.getId()));
		requireAllowed(matrix, Action.RETURN_FOR_REVISION);
		Instant now = clock.instant();
		RequestStatus from = request.getStatus();
		request.transitionTo(RequestStatus.REVISION_REQUESTED, request.getRequesterUserId(), now);
		request.touch(user.id(), now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(request.getId(), from, RequestStatus.REVISION_REQUESTED,
				request.getCurrentOwnerUserId(), user.id(), now, reason));
		events.publishEvent(
				new RequestAdministrativelyCancelledEvent(request.getId(), user.id(), now, "REVISION_REQUESTED"));
		audit(user, request, "REQUEST_ADMIN_RETURNED", from.name(), RequestStatus.REVISION_REQUESTED.name(), reason,
				Map.of());
		return new RequestAdminViews.MutationResult(detail(request),
				"Request returned for revision. Authors edit and resubmit through the normal circuit; self-approval rules still apply.");
	}

	@Transactional
	RequestAdminViews.MutationResult correctDraft(CurrentUser user, long id,
			RequestAdminViews.DraftCorrectionPayload body) {
		requireRequestAdmin(user);
		if (body == null) {
			throw ApiProblemException.invalidParameter("body");
		}
		String reason = requireReason(body.reason());
		PurchaseRequest request = requests.getInGroupForUpdate(user.groupId(), id);
		requireVersion(body.version(), request);
		Matrix matrix = RequestAdminActions.evaluate(request, links.counts(request.getId()));
		requireAllowed(matrix, Action.CORRECT_DRAFT);
		Map<String, Object> before = Map.of("title", nullToEmpty(request.getTitle()), "justification",
				nullToEmpty(request.getJustification()));
		if (StringUtils.hasText(body.title())) {
			String title = body.title().strip();
			if (title.length() > 200) {
				throw ApiProblemException.invalidParameter("title");
			}
			request.setTitle(title);
		}
		if (body.justification() != null) {
			String justification = body.justification().strip();
			if (justification.length() > 4000) {
				throw ApiProblemException.invalidParameter("justification");
			}
			request.setJustification(justification.isEmpty() ? null : justification);
		}
		Instant now = clock.instant();
		request.touch(user.id(), now);
		requests.saveAndFlush(request);
		audit(user, request, "REQUEST_ADMIN_DRAFT_CORRECTED", RequestStatus.DRAFT.name(), RequestStatus.DRAFT.name(),
				reason, Map.of("before", before, "after",
						Map.of("title", nullToEmpty(request.getTitle()), "justification",
								nullToEmpty(request.getJustification()))));
		return new RequestAdminViews.MutationResult(detail(request), "Draft corrected. Changes are audited.");
	}

	@Transactional
	void deleteDraft(CurrentUser user, long id, Integer version, String reason) {
		requireRequestAdmin(user);
		String cleanReason = requireReason(reason);
		PurchaseRequest request = requests.getInGroupForUpdate(user.groupId(), id);
		requireVersion(version, request);
		Matrix matrix = RequestAdminActions.evaluate(request, links.counts(request.getId()));
		requireAllowed(matrix, Action.DELETE_DRAFT);
		String entityId = request.getId().toString();
		Long companyId = request.getRequestingCompanyId();
		String title = request.getTitle();
		auditLog.record(new AuditRecord(user.id(), user.displayName(), companyId, ENTITY, entityId,
				"REQUEST_ADMIN_DRAFT_DELETED", RequestStatus.DRAFT.name(), null, cleanReason,
				json(Map.of("title", nullToEmpty(title), "numberPreserved", false))));
		requests.delete(request);
	}

	@Transactional(readOnly = true)
	RequestAdminViews.CleanupPreview previewCleanup(CurrentUser user, RequestAdminViews.CleanupFilter filter) {
		requireRequestAdmin(user);
		Specification<PurchaseRequest> spec = cleanupFilter(filter);
		var page = requests.searchInGroup(user.groupId(), spec,
				PageRequest.of(0, RequestAdminActions.MAX_CLEANUP_BATCH,
						Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by("id").descending())));
		List<RequestAdminViews.CleanupCandidate> candidates = new ArrayList<>();
		int deletable = 0;
		int cancellable = 0;
		int archivable = 0;
		int blocked = 0;
		for (PurchaseRequest request : page.getContent()) {
			LinkCounts counts = links.counts(request.getId());
			Matrix matrix = RequestAdminActions.evaluate(request, counts);
			String recommended = null;
			String block = null;
			if (matrix.allows(Action.DELETE_DRAFT)) {
				recommended = Action.DELETE_DRAFT.name();
				deletable++;
			}
			else if (matrix.allows(Action.CANCEL)) {
				recommended = Action.CANCEL.name();
				cancellable++;
			}
			else if (matrix.allows(Action.ARCHIVE)) {
				recommended = Action.ARCHIVE.name();
				archivable++;
			}
			else {
				blocked++;
				block = matrix.of(Action.DELETE_DRAFT).explanation();
			}
			candidates.add(new RequestAdminViews.CleanupCandidate(request.getId(), request.getNumber(),
					request.getTitle(), request.getStatus().name(), request.isArchived(), companyRef(request),
					toViewCounts(counts), toViewActions(matrix), recommended, block));
		}
		purgeExpiredPreviews();
		String token = UUID.randomUUID().toString();
		Instant expires = clock.instant().plusSeconds(15 * 60);
		Map<Long, String> recommendedById = new LinkedHashMap<>();
		for (RequestAdminViews.CleanupCandidate c : candidates) {
			if (c.recommendedAction() != null) {
				recommendedById.put(c.id(), c.recommendedAction());
			}
		}
		previews.put(token, new PreviewSession(user.id(), user.groupId(), expires, recommendedById));
		String impact = "Preview only — nothing was deleted, cancelled or archived. Select records and confirm explicitly. "
				+ "Protected reference " + RequestAdminActions.PROTECTED_NUMBER + " is never hard-deleted. "
				+ "Batch capped at " + RequestAdminActions.MAX_CLEANUP_BATCH + " candidates.";
		return new RequestAdminViews.CleanupPreview(token, expires, candidates.size(), deletable, cancellable,
				archivable, blocked, candidates, impact);
	}

	@Transactional
	RequestAdminViews.CleanupExecuteResult executeCleanup(CurrentUser user,
			RequestAdminViews.CleanupExecutePayload body) {
		requireRequestAdmin(user);
		if (body == null || !StringUtils.hasText(body.previewToken())) {
			throw ApiProblemException.invalidParameter("previewToken");
		}
		if (!CONFIRM_DELETE.equals(body.confirmation()) && !"APPLY".equals(body.confirmation())) {
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "confirmation-required",
					"Set confirmation to APPLY (or DELETE for draft-only) after reviewing the preview.");
		}
		String reason = requireReason(body.reason());
		PreviewSession session = previews.get(body.previewToken());
		if (session == null || session.expiresAt().isBefore(clock.instant())) {
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "preview-expired",
					"Cleanup preview expired or is unknown. Run preview again.");
		}
		if (session.userId() != user.id() || session.groupId() != user.groupId()) {
			throw ApiProblemException.forbidden();
		}
		List<Long> ids = body.ids() == null ? List.copyOf(session.recommendedById().keySet()) : body.ids();
		if (ids.size() > RequestAdminActions.MAX_CLEANUP_BATCH) {
			throw ApiProblemException.invalidParameter("ids");
		}
		List<RequestAdminViews.CleanupItemResult> results = new ArrayList<>();
		int succeeded = 0;
		int skipped = 0;
		for (Long id : ids) {
			String recommended = session.recommendedById().get(id);
			if (recommended == null) {
				results.add(new RequestAdminViews.CleanupItemResult(id, null, "SKIPPED",
						"Not part of this preview or had no recommended action."));
				skipped++;
				continue;
			}
			try {
				RequestAdminViews.ReasonPayload payload = new RequestAdminViews.ReasonPayload(null, reason);
				switch (Action.valueOf(recommended)) {
					case DELETE_DRAFT -> {
						if (!CONFIRM_DELETE.equals(body.confirmation()) && !"APPLY".equals(body.confirmation())) {
							throw ApiProblemException.conflict("confirmation-required", "DELETE confirmation required.");
						}
						PurchaseRequest before = requests.getInGroup(user.groupId(), id);
						String number = before.getNumber();
						deleteDraft(user, id, before.getVersion(), reason);
						results.add(new RequestAdminViews.CleanupItemResult(id, number, "DELETED", "Draft removed."));
					}
					case CANCEL -> {
						RequestAdminViews.MutationResult r = cancel(user, id, payload);
						results.add(new RequestAdminViews.CleanupItemResult(id, r.detail().number(), "CANCELLED",
								r.message()));
					}
					case ARCHIVE -> {
						RequestAdminViews.MutationResult r = archive(user, id, payload);
						results.add(new RequestAdminViews.CleanupItemResult(id, r.detail().number(), "ARCHIVED",
								r.message()));
					}
					default -> {
						results.add(new RequestAdminViews.CleanupItemResult(id, null, "SKIPPED",
								"Recommended action is not executable in cleanup."));
						skipped++;
						continue;
					}
				}
				succeeded++;
			}
			catch (ApiProblemException ex) {
				results.add(new RequestAdminViews.CleanupItemResult(id, null, "BLOCKED", ex.getMessage()));
				skipped++;
			}
		}
		// One-shot token: retries must re-preview (avoids double processing).
		previews.remove(body.previewToken());
		return new RequestAdminViews.CleanupExecuteResult(ids.size(), succeeded, skipped, results);
	}

	private RequestAdminViews.Summary summary(PurchaseRequest request) {
		LinkCounts counts = links.counts(request.getId());
		Matrix matrix = RequestAdminActions.evaluate(request, counts);
		return new RequestAdminViews.Summary(request.getId(), request.getNumber(), request.getTitle(),
				request.getStatus().name(), request.getStatus().userStatus().name(), request.getStage().name(),
				request.isArchived(), request.getTotalAmount(), request.getCurrencyCode(), request.getUpdatedAt(),
				request.getSubmittedAt(), request.getArchivedAt(), companyRef(request), userRef(request.getRequesterUserId()),
				matrix.allowedCodes(), toViewActions(matrix), toViewCounts(counts));
	}

	private RequestAdminViews.Detail detail(PurchaseRequest request) {
		LinkCounts counts = links.counts(request.getId());
		Matrix matrix = RequestAdminActions.evaluate(request, counts);
		String next = nextStep(request, matrix);
		String note = request.isArchived()
				? "Archived: hidden from everyday lists and dashboards that exclude archived records. Outstanding tasks should already be terminal or cancelled."
				: "Active administrative view within your installation group only.";
		return new RequestAdminViews.Detail(request.getId(), request.getNumber(), request.getTitle(),
				request.getStatus().name(), request.getStatus().userStatus().name(), request.getStage().name(),
				request.getVersion(), request.isArchived(), request.getArchivedAt(), request.getArchivedByUserId(),
				request.getArchiveReason(), request.getTotalAmount(), request.getCurrencyCode(), request.getJustification(),
				request.getCreatedAt(), request.getUpdatedAt(), request.getSubmittedAt(), companyRef(request),
				userRef(request.getRequesterUserId()), userRef(request.getEnteredByUserId()),
				request.getCurrentOwnerUserId() == null ? null : userRef(request.getCurrentOwnerUserId()),
				toViewActions(matrix), toViewCounts(counts), links.items(request.getId()), next, note);
	}

	private static String nextStep(PurchaseRequest request, Matrix matrix) {
		if (request.isArchived()) {
			return "No active work. Restore only if the record must reappear in normal lists.";
		}
		return switch (request.getStatus()) {
			case DRAFT -> matrix.allows(Action.DELETE_DRAFT)
					? "Draft: author continues in Requests, or admin may correct/delete when eligible."
					: "Draft: correct limited fields or leave for the author.";
			case CREATED, IN_APPROVAL -> "Approvers act in Approvals / My work. Admin may return or cancel with a reason.";
			case REVISION_REQUESTED, PURCHASING_RETURNED -> "Author revises and resubmits from the request form.";
			case REJECTED, CANCELLED, CLOSED -> "Terminal. Archive to clear everyday lists without destroying history.";
			case ORDERED, ORDER_CONFIRMED, SHIPPED, DELIVERED, ACCEPTED, INVOICED, PAID ->
				"Commitment or later — administrative hard-delete/cancel is blocked. Use operational modules.";
			default -> "Follow role-appropriate inbox (Purchasing, Logistics, Receipts) for the current stage.";
		};
	}

	private Specification<PurchaseRequest> filter(String q, Long companyId, String status, Boolean archived) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (companyId != null) {
				predicates.add(cb.equal(root.get("requestingCompanyId"), companyId));
			}
			if (StringUtils.hasText(status)) {
				RequestStatus parsed = RequestStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
				predicates.add(cb.equal(root.get("status"), parsed));
			}
			// null = all (admin needs both); false = active everyday list; true = archive only.
			if (Boolean.FALSE.equals(archived)) {
				predicates.add(cb.isNull(root.get("archivedAt")));
			}
			else if (Boolean.TRUE.equals(archived)) {
				predicates.add(cb.isNotNull(root.get("archivedAt")));
			}
			if (StringUtils.hasText(q)) {
				String pattern = "%" + escapeLike(q.strip().toLowerCase(Locale.ROOT)) + "%";
				predicates.add(cb.or(cb.like(cb.lower(root.get("number")), pattern, '\\'),
						cb.like(cb.lower(root.get("title")), pattern, '\\'),
						cb.like(cb.lower(root.get("beneficiary")), pattern, '\\')));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private Specification<PurchaseRequest> cleanupFilter(RequestAdminViews.CleanupFilter filter) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (filter != null && filter.ids() != null && !filter.ids().isEmpty()) {
				predicates.add(root.get("id").in(filter.ids()));
			}
			if (filter != null && filter.companyId() != null) {
				predicates.add(cb.equal(root.get("requestingCompanyId"), filter.companyId()));
			}
			if (filter != null && Boolean.TRUE.equals(filter.draftsOnly())) {
				predicates.add(cb.equal(root.get("status"), RequestStatus.DRAFT));
			}
			if (filter != null && StringUtils.hasText(filter.status())) {
				predicates.add(cb.equal(root.get("status"),
						RequestStatus.valueOf(filter.status().trim().toUpperCase(Locale.ROOT))));
			}
			if (filter != null && filter.archived() != null) {
				predicates.add(Boolean.TRUE.equals(filter.archived()) ? cb.isNotNull(root.get("archivedAt"))
						: cb.isNull(root.get("archivedAt")));
			}
			if (filter != null && StringUtils.hasText(filter.q())) {
				String pattern = "%" + escapeLike(filter.q().strip().toLowerCase(Locale.ROOT)) + "%";
				predicates.add(cb.or(cb.like(cb.lower(root.get("number")), pattern, '\\'),
						cb.like(cb.lower(root.get("title")), pattern, '\\')));
			}
			// Never include the protected UAT number in destructive recommendations path as selected-all default.
			predicates.add(cb.or(cb.isNull(root.get("number")),
					cb.notEqual(root.get("number"), RequestAdminActions.PROTECTED_NUMBER)));
			if (predicates.isEmpty()) {
				return cb.conjunction();
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private RequestAdminViews.Ref companyRef(PurchaseRequest request) {
		return organization.findCompany(request.getRequestingCompanyId())
			.map(c -> new RequestAdminViews.Ref(c.id(), c.code(), c.name()))
			.orElse(new RequestAdminViews.Ref(request.getRequestingCompanyId(), "", ""));
	}

	private RequestAdminViews.UserRef userRef(long userId) {
		return users.findContact(userId)
			.map(c -> new RequestAdminViews.UserRef(c.id(), c.displayName()))
			.orElse(new RequestAdminViews.UserRef(userId, "#" + userId));
	}

	private static List<RequestAdminViews.ActionAvailability> toViewActions(Matrix matrix) {
		return matrix.decisions()
			.stream()
			.map(d -> new RequestAdminViews.ActionAvailability(d.action().name(), d.allowed(), d.reasonCode(),
					d.explanation()))
			.toList();
	}

	private static RequestAdminViews.LinkedCounts toViewCounts(LinkCounts counts) {
		return new RequestAdminViews.LinkedCounts(counts.approvals(), counts.rfqs(), counts.quotations(),
				counts.awards(), counts.purchaseOrders(), counts.shipments(), counts.receipts(), counts.invoices());
	}

	private void requireAllowed(Matrix matrix, Action action) {
		var decision = matrix.of(action);
		if (!decision.allowed()) {
			throw new ApiProblemException(HttpStatus.CONFLICT, decision.reasonCode(), decision.explanation());
		}
	}

	private static void requireVersion(Integer version, PurchaseRequest request) {
		if (version != null && version != request.getVersion()) {
			throw ApiProblemException.conflict("version-conflict",
					"The request changed since you loaded it. Refresh and try again.");
		}
	}

	private static String requireReason(String reason) {
		if (!StringUtils.hasText(reason) || reason.strip().length() < 3) {
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "reason-required",
					"Provide a clear administrative reason (at least 3 characters).");
		}
		String clean = reason.strip();
		if (clean.length() > 1000) {
			throw ApiProblemException.invalidParameter("reason");
		}
		return clean;
	}

	private void audit(CurrentUser user, PurchaseRequest request, String action, String from, String to, String comment,
			Map<String, ?> payload) {
		auditLog.record(new AuditRecord(user.id(), user.displayName(), request.getRequestingCompanyId(), ENTITY,
				request.getId().toString(), action, from, to, comment, json(payload)));
	}

	private void purgeExpiredPreviews() {
		Instant now = clock.instant();
		previews.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
	}

	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static String json(Map<String, ?> payload) {
		if (payload == null || payload.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<String, ?> e : payload.entrySet()) {
			if (!first) {
				sb.append(',');
			}
			first = false;
			sb.append('"').append(e.getKey()).append("\":");
			Object v = e.getValue();
			if (v == null) {
				sb.append("null");
			}
			else if (v instanceof Map<?, ?> nested) {
				@SuppressWarnings("unchecked")
				Map<String, ?> cast = (Map<String, ?>) nested;
				sb.append(json(cast));
			}
			else if (v instanceof Boolean || v instanceof Number) {
				sb.append(v);
			}
			else {
				sb.append('"').append(String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
			}
		}
		return sb.append('}').toString();
	}

	private record PreviewSession(long userId, long groupId, Instant expiresAt, Map<Long, String> recommendedById) {
	}

}
