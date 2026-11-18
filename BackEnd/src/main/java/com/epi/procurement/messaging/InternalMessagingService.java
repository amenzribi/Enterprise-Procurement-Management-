package com.epi.procurement.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.Role;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.RequestRateLimiter;
import com.epi.procurement.platform.web.ValidationFailedException;
import com.epi.procurement.request.RequestMessagingQueries;
import com.epi.procurement.request.RequestMessagingQueries.RequestCard;

/**
 * Internal employee messaging (docs/architecture/internal-messaging.md).
 *
 * <p><b>Access, checked on every call from the signed-in user the server knows, never from the request body:</b>
 * <ul>
 * <li>Supplier contacts are refused outright (403); they keep the supplier portal only.</li>
 * <li>A conversation is visible only to its current participants. A conversation the caller is not part of is "not
 * found" (404), whoever the caller is: company ADMIN and GLOBAL_ADMIN get no reading right.</li>
 * <li>DIRECT: both people are active employees of one company, and the reader must still belong to that company.</li>
 * <li>REQUEST: the reader must also see the request now, under the request screens' rule. Losing request access
 * hides the discussion, its history and its live events.</li>
 * <li>A request card grants nothing: it is resolved for each viewer, each time, and shows nothing when the viewer may
 * not see the request.</li>
 * </ul>
 */
@Service
class InternalMessagingService {

	static final int MAX_BODY = 4000;

	static final int PAGE_SIZE = 40;

	private static final int MAX_CONVERSATIONS = 200;

	private static final int SENDS_PER_MINUTE = 30;

	private static final Pattern CLIENT_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

	private static final Set<String> SEARCHABLE_ROLES = Set.of("REQUESTER", "ENTER_ON_BEHALF", "DEPARTMENT_MANAGER",
			"BUDGET_APPROVER", "MANAGEMENT_APPROVER", "BUYER", "PURCHASING_MANAGER", "RECEIVER", "GROUP_MANAGEMENT",
			"ADMIN");

	private final InternalMessagingStore store;

	private final RequestMessagingQueries requests;

	private final UserDirectory users;

	private final AuditLog auditLog;

	private final ApplicationEventPublisher events;

	private final RequestRateLimiter rateLimiter;

	private final Clock clock;

	InternalMessagingService(InternalMessagingStore store, RequestMessagingQueries requests,
			UserDirectory users, AuditLog auditLog,
			ApplicationEventPublisher events, RequestRateLimiter rateLimiter, ObjectProvider<Clock> clock) {
		this.store = store;
		this.requests = requests;
		this.users = users;
		this.auditLog = auditLog;
		this.events = events;
		this.rateLimiter = rateLimiter;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	// ---- Directory ------------------------------------------------------------------------------------------------

	List<InternalMessagingViews.Person> directory(CurrentUser actor, String text, String department, String role) {
		requireEmployee(actor);
		String roleCode = blankToNull(role) == null ? null : role.trim().toUpperCase(Locale.ROOT);
		if (roleCode != null && !SEARCHABLE_ROLES.contains(roleCode)) {
			throw ApiProblemException.invalidParameter("role");
		}
		return store.searchEmployees(actor.companyId(), actor.id(), limit(text, 100), limit(department, 100), roleCode, 30)
			.stream()
			.map(InternalMessagingService::person)
			.toList();
	}

	// ---- Conversations --------------------------------------------------------------------------------------------

	List<InternalMessagingViews.ConversationSummary> conversations(CurrentUser actor, String query) {
		requireEmployee(actor);
		List<InternalMessagingStore.ConversationListRow> rows = store.listForUser(actor.id(), MAX_CONVERSATIONS);
		Map<Long, Optional<RequestCard>> cards = new HashMap<>();
		List<InternalMessagingStore.ConversationListRow> visible = rows.stream()
			.filter(row -> readable(actor, row.conversation(), cards))
			.toList();
		Set<Long> personIds = new HashSet<>();
		Map<Long, InternalMessagingStore.Message> last = store.lastMessages(visible.stream()
			.map(r -> r.conversation().lastMessageId())
			.filter(id -> id != null)
			.toList());
		Map<Long, List<InternalMessagingStore.Participant>> participants = new HashMap<>();
		for (var row : visible) {
			var list = store.participants(row.conversation().id());
			participants.put(row.conversation().id(), list);
			list.forEach(p -> personIds.add(p.userId()));
		}
		last.values().forEach(m -> personIds.add(m.senderUserId()));
		Map<Long, InternalMessagingStore.Person> people = store.people(personIds);
		String needle = blankToNull(query) == null ? null : query.trim().toLowerCase(Locale.ROOT);
		List<InternalMessagingViews.ConversationSummary> out = new ArrayList<>();
		for (var row : visible) {
			var c = row.conversation();
			InternalMessagingViews.Person counterpart = c.isDirect()
					? person(people.get(c.userLowId() == actor.id() ? c.userHighId() : c.userLowId()))
					: null;
			InternalMessagingViews.RequestCard card = c.isDirect() ? null : card(cards.get(c.requestId()));
			var message = c.lastMessageId() == null ? null : last.get(c.lastMessageId());
			if (needle != null && !matches(needle, counterpart, card)) {
				continue;
			}
			out.add(new InternalMessagingViews.ConversationSummary(c.id(), c.kind(), counterpart, card,
					participants.get(c.id()).size(), message == null ? null : message.kind(),
					message == null ? null : preview(message), message == null ? null : person(people.get(message.senderUserId())),
					c.lastMessageAt(), row.unread()));
		}
		return out;
	}

	/** Opens (or returns) the one direct conversation between the caller and another employee of their company. */
	@Transactional
	InternalMessagingViews.ConversationDetail openDirect(CurrentUser actor, Long userId) {
		requireEmployee(actor);
		long conversationId = directConversation(actor, requireId(userId, "userId"));
		return detail(actor, load(actor, conversationId));
	}

	InternalMessagingViews.ConversationDetail conversation(CurrentUser actor, long id) {
		return detail(actor, load(actor, id));
	}

	InternalMessagingViews.MessagePage messages(CurrentUser actor, long id, Long beforeId, Long afterId) {
		Access access = load(actor, id);
		if (beforeId != null && afterId != null) {
			throw ApiProblemException.invalidParameter("afterId");
		}
		List<InternalMessagingStore.Message> page = store.page(id, beforeId, afterId, PAGE_SIZE);
		boolean hasOlder = afterId == null && !page.isEmpty() && store.hasOlder(id, page.get(0).id());
		return new InternalMessagingViews.MessagePage(views(actor, page), hasOlder);
	}

	@Transactional
	InternalMessagingViews.SendResult send(CurrentUser actor, long id, InternalMessagingViews.Send body) {
		Access access = load(actor, id);
		return post(actor, access, body.bodyText(), body.clientMessageId(), body.requestId());
	}

	@Transactional
	void markRead(CurrentUser actor, long id, Long messageId) {
		load(actor, id);
		store.markRead(id, actor.id(), requireId(messageId, "messageId"));
		events.publishEvent(new InternalReadMarked(actor.id(), id));
	}

	InternalMessagingViews.Unread unread(CurrentUser actor) {
		requireEmployee(actor);
		Map<Long, Integer> byConversation = store.unreadByConversation(actor.id());
		Map<Long, Optional<RequestCard>> cards = new HashMap<>();
		int total = 0;
		for (var entry : byConversation.entrySet()) {
			var conversation = store.findConversation(entry.getKey());
			if (conversation.isPresent() && readable(actor, conversation.get(), cards)) {
				total += entry.getValue();
			}
		}
		return new InternalMessagingViews.Unread(total);
	}

	// ---- Request discussions --------------------------------------------------------------------------------------

	InternalMessagingViews.Discussion discussion(CurrentUser actor, long requestId) {
		requireEmployee(actor);
		requests.visibleTo(actor, requestId).orElseThrow(ApiProblemException::notFound);
		var existing = store.findRequestDiscussion(requestId);
		if (existing.isEmpty()) {
			return new InternalMessagingViews.Discussion(null, false, true);
		}
		if (!store.isParticipant(existing.get().id(), actor.id())) {
			return new InternalMessagingViews.Discussion(null, true, false);
		}
		return new InternalMessagingViews.Discussion(detail(actor, load(actor, existing.get().id())), true, false);
	}

	/**
	 * Starts the request's discussion with the caller and every current approver who can see the request. A second
	 * start, or one that loses a race, is refused (409): participants are added by name, never by joining oneself.
	 */
	@Transactional
	InternalMessagingViews.ConversationDetail startDiscussion(CurrentUser actor, long requestId) {
		requireEmployee(actor);
		RequestCard request = requests.visibleTo(actor, requestId).orElseThrow(ApiProblemException::notFound);
		if (store.findRequestDiscussion(requestId).isPresent()) {
			throw ApiProblemException.conflict("discussion-exists", "This request already has a discussion.");
		}
		Instant now = clock.instant();
		long id;
		try {
			id = store.insertConversation(request.requestingCompanyId(), "REQUEST", requestId, null, null, actor.id(), now);
		}
		catch (DuplicateKeyException ex) {
			throw ApiProblemException.conflict("discussion-exists", "This request already has a discussion.");
		}
		store.addParticipant(id, actor.id(), actor.id(), now);
		List<Long> added = new ArrayList<>();
		for (long approver : distinctApproverIds(requestId)) {
			if (approver != actor.id() && canSeeRequest(approver, requestId) && store.addParticipant(id, approver, actor.id(), now)) {
				added.add(approver);
			}
		}
		auditLog.record(new AuditRecord(actor.id(), actor.email(), request.requestingCompanyId(), "INTERNAL_CONVERSATION",
				Long.toString(id), "DISCUSSION_STARTED", null, null, null,
				"{\"requestId\":" + requestId + ",\"participants\":" + idsJson(added, actor.id()) + "}"));
		var system = store.insertMessage(id, "SYSTEM", actor.id(), "DISCUSSION_STARTED", null, "system-start-" + id, now);
		publish(id, system.id(), actor.id());
		return detail(actor, load(actor, id));
	}

	/** People the caller may add to a request discussion: those who can already see the request. */
	List<InternalMessagingViews.Person> candidates(CurrentUser actor, long conversationId, String text) {
		Access access = load(actor, conversationId);
		if (access.conversation().isDirect()) {
			throw ApiProblemException.conflict("not-a-discussion", "Participants can only be added to a request discussion.");
		}
		Set<Long> current = new HashSet<>();
		store.participants(conversationId).forEach(p -> current.add(p.userId()));
		Set<Long> companies = new LinkedHashSet<>();
		companies.add(access.request().requestingCompanyId());
		if (access.request().buyingCompanyId() != null) {
			companies.add(access.request().buyingCompanyId());
		}
		List<InternalMessagingViews.Person> out = new ArrayList<>();
		for (long company : companies) {
			for (var person : store.searchEmployees(company, actor.id(), limit(text, 100), null, null, 25)) {
				if (out.size() >= 15) {
					break;
				}
				if (!current.contains(person.id()) && canSeeRequest(person.id(), access.request().id())) {
					out.add(person(person));
					current.add(person.id());
				}
			}
		}
		return out;
	}

	@Transactional
	InternalMessagingViews.ConversationDetail addParticipant(CurrentUser actor, long conversationId, Long userId) {
		Access access = load(actor, conversationId);
		if (access.conversation().isDirect()) {
			throw ApiProblemException.conflict("not-a-discussion", "Participants can only be added to a request discussion.");
		}
		long candidateId = requireId(userId, "userId");
		var candidate = store.person(candidateId).orElse(null);
		RequestCard request = access.request();
		boolean companyOk = candidate != null && (candidate.companyId() == request.requestingCompanyId()
				|| (request.buyingCompanyId() != null && candidate.companyId() == request.buyingCompanyId()));
		if (candidate == null || !candidate.active() || candidate.supplier() || !companyOk) {
			throw new ValidationFailedException(List.of(FieldViolation.of("userId", "not-eligible")));
		}
		if (!canSeeRequest(candidateId, request.id())) {
			throw new ValidationFailedException(List.of(FieldViolation.of("userId", "no-request-access")));
		}
		Instant now = clock.instant();
		store.lockConversation(conversationId);
		if (store.addParticipant(conversationId, candidateId, actor.id(), now)) {
			auditLog.record(new AuditRecord(actor.id(), actor.email(), access.conversation().companyId(),
					"INTERNAL_CONVERSATION", Long.toString(conversationId), "PARTICIPANT_ADDED", null, null, null,
					"{\"userId\":" + candidateId + ",\"requestId\":" + request.id() + "}"));
			var system = store.insertMessage(conversationId, "SYSTEM", actor.id(), "PARTICIPANT_ADDED:" + candidateId,
					// Unique per addition: a participant removed and added again gets a second line.
					null, "system-add-" + conversationId + "-" + candidateId + "-" + now.toEpochMilli(), now);
			publish(conversationId, system.id(), actor.id());
		}
		return detail(actor, load(actor, conversationId));
	}

	// ---- Message approver -----------------------------------------------------------------------------------------

	InternalMessagingViews.Approvers approvers(CurrentUser actor, long requestId) {
		requireEmployee(actor);
		RequestCard request = requests.visibleTo(actor, requestId).orElseThrow(ApiProblemException::notFound);
		if (!request.awaitingApproval()) {
			return new InternalMessagingViews.Approvers("NOT_IN_APPROVAL", List.of());
		}
		var current = store.currentApprovers(requestId);
		if (current.isEmpty()) {
			return new InternalMessagingViews.Approvers("NO_APPROVER", List.of());
		}
		Set<Long> ids = new HashSet<>();
		current.forEach(a -> {
			ids.add(a.userId());
			if (a.onBehalfOfUserId() != null) {
				ids.add(a.onBehalfOfUserId());
			}
		});
		Map<Long, InternalMessagingStore.Person> people = store.people(ids);
		Map<Long, InternalMessagingViews.Approver> byUser = new LinkedHashMap<>();
		for (var a : current) {
			var p = people.get(a.userId());
			if (a.userId() == actor.id() || p == null || !p.active() || p.supplier()) {
				continue;
			}
			byUser.putIfAbsent(a.userId(), new InternalMessagingViews.Approver(person(p), a.stageName(),
					a.onBehalfOfUserId() == null ? null : person(people.get(a.onBehalfOfUserId()))));
		}
		if (byUser.isEmpty()) {
			boolean self = current.stream().anyMatch(a -> a.userId() == actor.id());
			return new InternalMessagingViews.Approvers(self ? "SELF_ONLY" : "NO_APPROVER", List.of());
		}
		return new InternalMessagingViews.Approvers("AWAITING", List.copyOf(byUser.values()));
	}

	/**
	 * Sends the caller's message to a current approver with the request attached. The recipient must still be a
	 * current approver when Send is pressed; otherwise 409 {@code approver-changed} and nothing is sent. Same company:
	 * the direct conversation. Another company (a group-level approver): the request discussion, with both added.
	 */
	@Transactional
	InternalMessagingViews.ApproverMessageResult messageApprover(CurrentUser actor, long requestId,
			InternalMessagingViews.MessageApprover body) {
		requireEmployee(actor);
		RequestCard request = requests.visibleTo(actor, requestId).orElseThrow(ApiProblemException::notFound);
		long recipientId = requireId(body.recipientUserId(), "recipientUserId");
		InternalMessagingViews.Approvers current = approvers(actor, requestId);
		boolean stillApprover = current.approvers().stream().anyMatch(a -> a.person().id() == recipientId);
		if (!stillApprover) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "approver-changed",
					"The approver of this request has changed. Choose the current approver.",
					Map.of("state", current.state()));
		}
		var recipient = store.person(recipientId).orElseThrow(ApiProblemException::notFound);
		long conversationId;
		if (recipient.companyId() == actor.companyId()) {
			conversationId = directConversation(actor, recipientId);
		}
		else {
			Instant now = clock.instant();
			var existing = store.findRequestDiscussion(requestId);
			// Participation stays explicit: an existing discussion is never joined this way by someone not in it.
			if (existing.isPresent() && !store.isParticipant(existing.get().id(), actor.id())) {
				throw ApiProblemException.conflict("discussion-not-participant",
						"This request has a discussion you are not part of. Ask a participant to add you.");
			}
			conversationId = existing.map(InternalMessagingStore.Conversation::id)
				.orElseGet(() -> startDiscussion(actor, requestId).id());
			store.lockConversation(conversationId);
			for (long userId : List.of(actor.id(), recipientId)) {
				if (store.addParticipant(conversationId, userId, actor.id(), now)) {
					auditLog.record(new AuditRecord(actor.id(), actor.email(), request.requestingCompanyId(),
							"INTERNAL_CONVERSATION", Long.toString(conversationId), "PARTICIPANT_ADDED", null, null,
							"message-approver", "{\"userId\":" + userId + ",\"requestId\":" + requestId + "}"));
				}
			}
		}
		Access access = load(actor, conversationId);
		InternalMessagingViews.SendResult sent = post(actor, access, body.bodyText(), body.clientMessageId(), requestId);
		return new InternalMessagingViews.ApproverMessageResult(conversationId,
				access.conversation().kind(), sent.message(), sent.duplicate());
	}

	// ---- Live delivery --------------------------------------------------------------------------------------------

	/** Whether an event about this conversation may be sent to this user now (account, participation, access). */
	boolean deliverable(long userId, long conversationId) {
		Optional<CurrentUser> user = users.loadCurrentUser(userId);
		if (user.isEmpty() || user.get().hasRole(Role.SUPPLIER_CONTACT)) {
			return false;
		}
		var conversation = store.findConversation(conversationId);
		return conversation.isPresent() && store.isParticipant(conversationId, userId)
				&& readable(user.get(), conversation.get(), new HashMap<>());
	}

	// ---- Internals ------------------------------------------------------------------------------------------------

	private record Access(InternalMessagingStore.Conversation conversation, RequestCard request) {
	}

	/** The single gate for every conversation id received from a client. Anything not readable is "not found". */
	private Access load(CurrentUser actor, long id) {
		requireEmployee(actor);
		var conversation = store.findConversation(id).orElseThrow(ApiProblemException::notFound);
		if (!store.isParticipant(id, actor.id())) {
			throw ApiProblemException.notFound();
		}
		if (conversation.isDirect()) {
			if (conversation.companyId() != actor.companyId()) {
				throw ApiProblemException.notFound();
			}
			return new Access(conversation, null);
		}
		RequestCard request = requests.visibleTo(actor, conversation.requestId())
			.orElseThrow(ApiProblemException::notFound);
		return new Access(conversation, request);
	}

	private boolean readable(CurrentUser actor, InternalMessagingStore.Conversation conversation,
			Map<Long, Optional<RequestCard>> cards) {
		if (conversation.isDirect()) {
			return conversation.companyId() == actor.companyId();
		}
		return cards.computeIfAbsent(conversation.requestId(), id -> requests.visibleTo(actor, id)).isPresent();
	}

	private long directConversation(CurrentUser actor, long otherId) {
		if (otherId == actor.id()) {
			throw new ValidationFailedException(List.of(FieldViolation.of("userId", "self")));
		}
		var other = store.person(otherId).orElse(null);
		// A colleague of another company, an inactive account or a supplier contact are all "not found" here.
		if (other == null || !other.active() || other.supplier() || other.companyId() != actor.companyId()) {
			throw ApiProblemException.notFound();
		}
		long low = Math.min(actor.id(), otherId);
		long high = Math.max(actor.id(), otherId);
		var existing = store.findDirect(actor.companyId(), low, high);
		if (existing.isPresent()) {
			return existing.get().id();
		}
		Instant now = clock.instant();
		long id;
		try {
			id = store.insertConversation(actor.companyId(), "DIRECT", null, low, high, actor.id(), now);
		}
		catch (DuplicateKeyException ex) {
			// Both people opened it at the same moment: the first insert wins and both get its id.
			return store.findDirect(actor.companyId(), low, high).orElseThrow(() -> ex).id();
		}
		store.addParticipant(id, low, actor.id(), now);
		store.addParticipant(id, high, actor.id(), now);
		auditLog.record(new AuditRecord(actor.id(), actor.email(), actor.companyId(), "INTERNAL_CONVERSATION",
				Long.toString(id), "DIRECT_OPENED", null, null, null, "{\"participants\":[" + low + "," + high + "]}"));
		return id;
	}

	/**
	 * Stores one message. Retries are safe for as long as the message exists: the same client id from the same sender
	 * in the same conversation returns the stored message ({@code duplicate}); a different text with that id is 409.
	 *
	 * <p>A request card is attached only if the sender can see the request, and, for every other participant, only if
	 * they can see it too. Otherwise the text is sent without the card and {@code cardWithheld} is true, so the sender
	 * can be told. Nothing about the request is stored but its id.
	 */
	private InternalMessagingViews.SendResult post(CurrentUser actor, Access access, String rawBody, String clientId,
			Long requestId) {
		String body = normalizeBody(rawBody);
		String key = requireClientId(clientId);
		long conversationId = access.conversation().id();
		store.lockConversation(conversationId);
		var previous = store.findByClientId(conversationId, actor.id(), key);
		if (previous.isPresent()) {
			return replay(actor, previous.get(), body);
		}
		rateLimiter.check("internal-messaging:user:" + actor.id(), SENDS_PER_MINUTE);
		Long linked = null;
		boolean withheld = false;
		if (requestId != null) {
			requests.visibleTo(actor, requestId)
				.orElseThrow(() -> new ValidationFailedException(List.of(FieldViolation.of("requestId", "not-found"))));
			boolean everyoneSees = store.participants(conversationId)
				.stream()
				.filter(p -> p.userId() != actor.id())
				.allMatch(p -> canSeeRequest(p.userId(), requestId));
			if (everyoneSees) {
				linked = requestId;
			}
			else {
				withheld = true;
			}
		}
		Instant now = clock.instant();
		InternalMessagingStore.Message stored;
		try {
			stored = store.insertMessage(conversationId, "TEXT", actor.id(), body, linked, key, now);
		}
		catch (DuplicateKeyException ex) {
			// A concurrent retry with the same client id stored it first.
			return replay(actor, store.findByClientId(conversationId, actor.id(), key).orElseThrow(() -> ex), body);
		}
		auditLog.record(new AuditRecord(actor.id(), actor.email(), access.conversation().companyId(),
				"INTERNAL_MESSAGE", Long.toString(stored.id()), "POSTED", null, null, null,
				"{\"conversationId\":" + conversationId + ",\"linkedRequestId\":" + linked + ",\"cardWithheld\":"
						+ withheld + "}"));
		publish(conversationId, stored.id(), actor.id());
		return new InternalMessagingViews.SendResult(views(actor, List.of(stored)).get(0), false, withheld);
	}

	private InternalMessagingViews.SendResult replay(CurrentUser actor, InternalMessagingStore.Message previous,
			String body) {
		if (!previous.bodyText().equals(body)) {
			throw ApiProblemException.conflict("client-message-id-reused",
					"This message id was already used for a different text.");
		}
		return new InternalMessagingViews.SendResult(views(actor, List.of(previous)).get(0), true, false);
	}

	private void publish(long conversationId, long messageId, long senderId) {
		Set<Long> recipients = new LinkedHashSet<>();
		store.participants(conversationId).forEach(p -> {
			if (p.userId() != senderId) {
				recipients.add(p.userId());
			}
		});
		events.publishEvent(new InternalMessagePosted(conversationId, messageId, senderId, Set.copyOf(recipients)));
	}

	private InternalMessagingViews.ConversationDetail detail(CurrentUser actor, Access access) {
		var c = access.conversation();
		var participants = store.participants(c.id());
		Set<Long> ids = new HashSet<>();
		participants.forEach(p -> ids.add(p.userId()));
		Map<Long, InternalMessagingStore.Person> people = store.people(ids);
		InternalMessagingViews.Person counterpart = c.isDirect()
				? person(people.get(c.userLowId() == actor.id() ? c.userHighId() : c.userLowId()))
				: null;
		// findFirst() would throw on a null element, and a never-read conversation has a null marker.
		Long lastRead = participants.stream()
			.filter(p -> p.userId() == actor.id())
			.findFirst()
			.map(InternalMessagingStore.Participant::lastReadMessageId)
			.orElse(null);
		return new InternalMessagingViews.ConversationDetail(c.id(), c.kind(), counterpart,
				c.isDirect() ? null : card(Optional.of(access.request())),
				participants.stream().map(p -> person(people.get(p.userId()))).toList(), !c.isDirect(), lastRead,
				c.createdAt());
	}

	private List<InternalMessagingViews.Message> views(CurrentUser actor, List<InternalMessagingStore.Message> messages) {
		Set<Long> ids = new HashSet<>();
		for (var m : messages) {
			ids.add(m.senderUserId());
			subjectId(m).ifPresent(ids::add);
		}
		Map<Long, InternalMessagingStore.Person> people = store.people(ids);
		Map<Long, Optional<RequestCard>> cards = new HashMap<>();
		List<InternalMessagingViews.Message> out = new ArrayList<>();
		for (var m : messages) {
			InternalMessagingViews.RequestCard card = m.linkedRequestId() == null ? null
					: card(cards.computeIfAbsent(m.linkedRequestId(), id -> requests.visibleTo(actor, id)));
			out.add(new InternalMessagingViews.Message(m.id(), m.conversationId(), m.kind(),
					person(people.get(m.senderUserId())), subjectId(m).map(id -> person(people.get(id))).orElse(null),
					m.senderUserId() == actor.id(), "SYSTEM".equals(m.kind()) ? systemCode(m) : m.bodyText(), card,
					m.clientMessageId(), m.createdAt()));
		}
		return out;
	}

	private static Optional<Long> subjectId(InternalMessagingStore.Message m) {
		if ("SYSTEM".equals(m.kind()) && m.bodyText().startsWith("PARTICIPANT_ADDED:")) {
			return Optional.of(Long.parseLong(m.bodyText().substring("PARTICIPANT_ADDED:".length())));
		}
		return Optional.empty();
	}

	private static String systemCode(InternalMessagingStore.Message m) {
		int colon = m.bodyText().indexOf(':');
		return colon < 0 ? m.bodyText() : m.bodyText().substring(0, colon);
	}

	private static String preview(InternalMessagingStore.Message m) {
		if ("SYSTEM".equals(m.kind())) {
			return systemCode(m);
		}
		String text = m.bodyText().replaceAll("\\s+", " ").trim();
		return text.length() <= 140 ? text : text.substring(0, 139) + "…";
	}

	private boolean canSeeRequest(long userId, long requestId) {
		return users.loadCurrentUser(userId)
			.filter(u -> !u.hasRole(Role.SUPPLIER_CONTACT))
			.flatMap(u -> requests.visibleTo(u, requestId))
			.isPresent();
	}

	private List<Long> distinctApproverIds(long requestId) {
		return store.currentApprovers(requestId)
			.stream()
			.map(InternalMessagingStore.CurrentApprover::userId)
			.distinct()
			.toList();
	}

	private static boolean matches(String needle, InternalMessagingViews.Person counterpart,
			InternalMessagingViews.RequestCard card) {
		if (counterpart != null && counterpart.displayName().toLowerCase(Locale.ROOT).contains(needle)) {
			return true;
		}
		return card != null && card.available() && ((card.number() != null
				&& card.number().toLowerCase(Locale.ROOT).contains(needle))
				|| (card.title() != null && card.title().toLowerCase(Locale.ROOT).contains(needle)));
	}

	private static InternalMessagingViews.RequestCard card(Optional<RequestCard> request) {
		return request == null || request.isEmpty() ? InternalMessagingViews.RequestCard.unavailable()
				: new InternalMessagingViews.RequestCard(true, request.get().id(), request.get().number(),
						request.get().title(), request.get().status(), request.get().userStatus());
	}

	private static InternalMessagingViews.Person person(InternalMessagingStore.Person p) {
		if (p == null) {
			return null;
		}
		return new InternalMessagingViews.Person(p.id(), p.displayName(), p.departmentName(),
				p.roles().stream().filter(r -> !"SUPPLIER_CONTACT".equals(r)).toList());
	}

	private static void requireEmployee(CurrentUser actor) {
		if (actor.hasRole(Role.SUPPLIER_CONTACT)) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
					"Internal messaging is for employees. Use the supplier portal.");
		}
	}

	private static String normalizeBody(String raw) {
		String body = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n');
		if (body.isBlank()) {
			throw new ValidationFailedException(List.of(FieldViolation.of("bodyText", "required")));
		}
		if (body.length() > MAX_BODY) {
			throw new ValidationFailedException(List.of(FieldViolation.of("bodyText", "too-long")));
		}
		return body.strip();
	}

	private static String requireClientId(String clientId) {
		if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) {
			throw new ValidationFailedException(List.of(FieldViolation.of("clientMessageId", "invalid-value")));
		}
		return clientId;
	}

	private static long requireId(Long id, String field) {
		if (id == null || id <= 0) {
			throw new ValidationFailedException(List.of(FieldViolation.of(field, "required")));
		}
		return id;
	}

	private static String idsJson(List<Long> added, long actorId) {
		List<Long> all = new ArrayList<>();
		all.add(actorId);
		all.addAll(added);
		return all.toString().replace(" ", "");
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String limit(String value, int max) {
		String v = blankToNull(value);
		if (v == null) {
			return null;
		}
		v = v.trim();
		return v.length() > max ? v.substring(0, max) : v;
	}

}
