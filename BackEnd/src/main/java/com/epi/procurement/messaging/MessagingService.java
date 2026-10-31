package com.epi.procurement.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.identity.CredentialStore;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.PasswordSetupRequested;
import com.epi.procurement.identity.Role;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;

@Service
class MessagingService {

	private static final int MAX_BODY = 4000;

	private static final int PAGE_SIZE = 50;

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private final MessagingStore store;

	private final AuditLog auditLog;

	private final ApplicationEventPublisher events;

	private final CredentialStore credentials;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	MessagingService(MessagingStore store, AuditLog auditLog, ApplicationEventPublisher events,
			CredentialStore credentials, PasswordEncoder passwordEncoder, ObjectProvider<Clock> clock) {
		this.store = store;
		this.auditLog = auditLog;
		this.events = events;
		this.credentials = credentials;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@Transactional
	MessagingViews.ConversationDetail open(CurrentUser actor, MessagingViews.OpenRequest body) {
		requireBuyer(actor);
		ContextResolved ctx = resolveContext(body.contextType(), body.contextId(), body.supplierId());
		assertBuyerAccess(actor, ctx.row());
		boolean closed = isBusinessClosed(ctx.row().businessStatus());
		Instant now = clock.instant();
		var existing = store.findByContext(ctx.row().contextType(), ctx.row().contextId(), ctx.row().supplierId());
		long id = existing.map(MessagingStore.ConversationRow::id).orElseGet(() -> {
			long created = store.insertConversation(ctx.row(), actor.id(), now, closed);
			store.ensureParticipant(created, actor.id(), "INTERNAL");
			auditLog.record(new AuditRecord(actor.id(), actor.email(), ctx.row().buyingCompanyId(), "CONVERSATION",
					Long.toString(created), "OPENED", null, closed ? "CLOSED" : "OPEN", null,
					"{\"contextType\":\"" + ctx.row().contextType() + "\",\"contextId\":" + ctx.row().contextId()
							+ ",\"supplierId\":" + ctx.row().supplierId() + "}"));
			return created;
		});
		if (existing.isPresent() && existing.get().status().equals("OPEN") && closed) {
			// soft: leave status; writable computed from business status
		}
		store.ensureParticipant(id, actor.id(), "INTERNAL");
		return detail(actor, id, false);
	}

	List<MessagingViews.ConversationSummary> listBuyer(CurrentUser actor, Long supplierId) {
		requireBuyer(actor);
		java.util.LinkedHashMap<Long, MessagingViews.ConversationSummary> byId = new java.util.LinkedHashMap<>();
		for (var companyGrant : actor.grants().stream()
			.filter(g -> g.role() == Role.BUYER || g.role() == Role.PURCHASING_MANAGER)
			.toList()) {
			Long companyId = companyGrant.companyId();
			if (companyId == null) {
				continue;
			}
			for (var row : store.listForBuyer(companyId, supplierId)) {
				if (!canAccessBuyerConversation(actor, row.buyingCompanyId(), row.requestId())) {
					continue;
				}
				int unread = store.countUnread(row.id(), actor.id(), false);
				byId.putIfAbsent(row.id(),
						new MessagingViews.ConversationSummary(row.id(), row.contextType(), row.contextId(),
								row.supplierId(), row.supplierCode(), row.supplierName(), row.status(), row.requestId(),
								row.buyingCompanyId(), unread, row.createdAt(), row.lastMessageAt()));
			}
		}
		return List.copyOf(byId.values());
	}

	List<MessagingViews.ConversationSummary> listSupplier(CurrentUser actor) {
		requireSupplier(actor);
		List<MessagingViews.ConversationSummary> rows = new ArrayList<>();
		for (var row : store.listForSupplierContact(actor.id())) {
			int unread = store.countUnread(row.id(), actor.id(), true);
			rows.add(new MessagingViews.ConversationSummary(row.id(), row.contextType(), row.contextId(),
					row.supplierId(), row.supplierCode(), row.supplierName(), row.status(), row.requestId(),
					row.buyingCompanyId(), unread, row.createdAt(), row.lastMessageAt()));
		}
		return rows;
	}

	MessagingViews.ConversationDetail getBuyer(CurrentUser actor, long id) {
		requireBuyer(actor);
		return detail(actor, id, false);
	}

	MessagingViews.ConversationDetail getSupplier(CurrentUser actor, long id) {
		requireSupplier(actor);
		return detail(actor, id, true);
	}

	MessagingViews.MessagePage messagesBuyer(CurrentUser actor, long id, Long beforeId) {
		requireBuyer(actor);
		ConversationAccess access = loadBuyerAccess(actor, id);
		return page(access.conversation().id(), beforeId, false);
	}

	MessagingViews.MessagePage messagesSupplier(CurrentUser actor, long id, Long beforeId) {
		requireSupplier(actor);
		ConversationAccess access = loadSupplierAccess(actor, id);
		return page(access.conversation().id(), beforeId, true);
	}

	@Transactional
	MessagingViews.MessageView sendBuyer(CurrentUser actor, long id, MessagingViews.SendRequest body) {
		requireBuyer(actor);
		ConversationAccess access = loadBuyerAccess(actor, id);
		assertWritable(access);
		String audience = normalizeAudience(body.audience());
		return send(access.conversation(), actor, audience, body.bodyText(), body.clientMessageId(), false);
	}

	@Transactional
	MessagingViews.MessageView sendSupplier(CurrentUser actor, long id, MessagingViews.SendRequest body) {
		requireSupplier(actor);
		ConversationAccess access = loadSupplierAccess(actor, id);
		assertWritable(access);
		return send(access.conversation(), actor, "EXTERNAL", body.bodyText(), body.clientMessageId(), true);
	}

	@Transactional
	void markReadBuyer(CurrentUser actor, long id, long messageId) {
		requireBuyer(actor);
		loadBuyerAccess(actor, id);
		store.ensureParticipant(id, actor.id(), "INTERNAL");
		store.markRead(id, actor.id(), messageId);
	}

	@Transactional
	void markReadSupplier(CurrentUser actor, long id, long messageId) {
		requireSupplier(actor);
		loadSupplierAccess(actor, id);
		store.ensureParticipant(id, actor.id(), "SUPPLIER");
		store.markRead(id, actor.id(), messageId);
	}

	List<MessagingViews.ContactView> listContacts(CurrentUser actor, long supplierId) {
		requireBuyer(actor);
		assertBuyerCanManageSupplier(actor, supplierId);
		return store.listContacts(supplierId)
			.stream()
			.map(c -> new MessagingViews.ContactView(c.id(), c.userId(), c.email(), c.displayName(), c.active(),
					c.invitedAt(), c.revokedAt()))
			.toList();
	}

	@Transactional
	MessagingViews.ContactView invite(CurrentUser actor, long supplierId, MessagingViews.InviteContactRequest body) {
		requireBuyer(actor);
		assertBuyerCanManageSupplier(actor, supplierId);
		String email = body.email() == null ? "" : body.email().trim().toLowerCase(Locale.ROOT);
		String displayName = body.displayName() == null ? "" : body.displayName().trim();
		List<FieldViolation> violations = new ArrayList<>();
		if (!EMAIL.matcher(email).matches()) {
			violations.add(FieldViolation.of("email", "invalid-value"));
		}
		if (displayName.isBlank() || displayName.length() > 200) {
			violations.add(FieldViolation.of("displayName", "invalid-value"));
		}
		if (!violations.isEmpty()) {
			throw new ValidationFailedException(violations);
		}
		Instant now = clock.instant();
		Long existingUser = store.findUserIdByEmail(email).orElse(null);
		long userId;
		if (existingUser != null) {
			userId = existingUser;
			var existingContact = store.findActiveContact(supplierId, userId);
			if (existingContact.isPresent()) {
				throw new ApiProblemException(HttpStatus.CONFLICT, "contact-exists",
						"This contact is already authorised for the supplier.");
			}
			store.grantSupplierContactRole(userId);
		}
		else {
			var home = store.companyHome(actor.companyId())
				.orElseThrow(() -> new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-company",
						"Cannot place the contact account."));
			userId = store.insertSupplierContactUser(email, displayName, home.companyId(), home.siteId(),
					home.departmentId(), actor.preferredLanguage() == null ? "en" : actor.preferredLanguage());
			credentials.savePasswordHash(userId, passwordEncoder.encode(randomUnusableSecret()), true);
			store.grantSupplierContactRole(userId);
			events.publishEvent(new PasswordSetupRequested(userId, "SETUP", actor.id(), actor.email(),
					actor.companyId(), displayName));
		}
		long contactId = store.insertContact(supplierId, userId, displayName, actor.id(), now);
		auditLog.record(new AuditRecord(actor.id(), actor.email(), actor.companyId(), "SUPPLIER_CONTACT",
				Long.toString(contactId), "INVITED", null, null, null,
				"{\"supplierId\":" + supplierId + ",\"userId\":" + userId + "}"));
		return store.findContact(contactId)
			.map(c -> new MessagingViews.ContactView(c.id(), c.userId(), c.email(), c.displayName(), c.active(),
					c.invitedAt(), c.revokedAt()))
			.orElseThrow();
	}

	@Transactional
	void revoke(CurrentUser actor, long contactId) {
		requireBuyer(actor);
		MessagingStore.ContactRow contact = store.findContact(contactId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "Contact not found."));
		assertBuyerCanManageSupplier(actor, contact.supplierId());
		store.revokeContact(contactId, actor.id(), clock.instant());
		store.bumpCredentialsVersion(contact.userId());
		auditLog.record(new AuditRecord(actor.id(), actor.email(), actor.companyId(), "SUPPLIER_CONTACT",
				Long.toString(contactId), "REVOKED", null, null, null,
				"{\"supplierId\":" + contact.supplierId() + ",\"userId\":" + contact.userId() + "}"));
	}

	private MessagingViews.MessageView send(MessagingStore.ConversationRow conversation, CurrentUser actor,
			String audience, String bodyText, String clientMessageId, boolean supplierSender) {
		String body = bodyText == null ? "" : bodyText.trim();
		if (body.isEmpty() || body.length() > MAX_BODY) {
			throw new ValidationFailedException(List.of(FieldViolation.of("bodyText", "invalid-value")));
		}
		if (clientMessageId != null && clientMessageId.length() > 100) {
			throw new ValidationFailedException(List.of(FieldViolation.of("clientMessageId", "invalid-value")));
		}
		var replay = store.findMessageByClientId(conversation.id(), clientMessageId);
		if (replay.isPresent()) {
			return replay.get();
		}
		Instant now = clock.instant();
		store.ensureParticipant(conversation.id(), actor.id(), supplierSender ? "SUPPLIER" : "INTERNAL");
		long messageId = store.insertMessage(conversation.id(), audience, body, actor.id(), clientMessageId, now);
		store.markRead(conversation.id(), actor.id(), messageId);
		auditLog.record(new AuditRecord(actor.id(), actor.email(), conversation.buyingCompanyId(),
				"CONVERSATION_MESSAGE", Long.toString(messageId), "POSTED", null, audience, null,
				"{\"conversationId\":" + conversation.id() + ",\"audience\":\"" + audience + "\"}"));
		events.publishEvent(new MessagePosted(conversation.id(), messageId, audience, actor.id(), actor.displayName(),
				conversation.requestId(), conversation.buyingCompanyId(), conversation.supplierId(), supplierSender));
		return store.findMessageByClientId(conversation.id(), clientMessageId)
			.orElseGet(() -> new MessagingViews.MessageView(messageId, audience, body, actor.id(), actor.displayName(),
					now));
	}

	private MessagingViews.MessagePage page(long conversationId, Long beforeId, boolean externalOnly) {
		List<MessagingViews.MessageView> fetched = store.listMessages(conversationId, beforeId, PAGE_SIZE, externalOnly);
		Long next = null;
		if (fetched.size() > PAGE_SIZE) {
			fetched = new ArrayList<>(fetched.subList(0, PAGE_SIZE));
			next = fetched.get(fetched.size() - 1).id();
		}
		List<MessagingViews.MessageView> chronological = new ArrayList<>(fetched);
		java.util.Collections.reverse(chronological);
		return new MessagingViews.MessagePage(chronological, next);
	}

	private MessagingViews.ConversationDetail detail(CurrentUser actor, long id, boolean supplierView) {
		ConversationAccess access = supplierView ? loadSupplierAccess(actor, id) : loadBuyerAccess(actor, id);
		var c = access.conversation();
		var supplier = store.findSupplier(c.supplierId()).orElseThrow();
		boolean writable = "OPEN".equals(c.status()) && !isBusinessClosed(access.businessStatus());
		int unread = store.countUnread(c.id(), actor.id(), supplierView);
		return new MessagingViews.ConversationDetail(c.id(), c.contextType(), c.contextId(), c.supplierId(),
				supplier.code(), supplier.name(), c.status(), writable, c.requestId(), c.buyingCompanyId(), unread,
				c.createdAt());
	}

	private ConversationAccess loadBuyerAccess(CurrentUser actor, long id) {
		var conversation = store.findConversation(id)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "Conversation not found."));
		if (!canAccessBuyerConversation(actor, conversation.buyingCompanyId(), conversation.requestId())) {
			throw new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "Conversation not found.");
		}
		String business = businessStatus(conversation);
		return new ConversationAccess(conversation, business);
	}

	private ConversationAccess loadSupplierAccess(CurrentUser actor, long id) {
		var conversation = store.findConversation(id)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "Conversation not found."));
		if (store.findActiveContact(conversation.supplierId(), actor.id()).isEmpty()) {
			throw new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "Conversation not found.");
		}
		return new ConversationAccess(conversation, businessStatus(conversation));
	}

	private String businessStatus(MessagingStore.ConversationRow conversation) {
		return resolveContext(conversation.contextType(), conversation.contextId(), conversation.supplierId()).row()
			.businessStatus();
	}

	private ContextResolved resolveContext(String contextType, long contextId, long supplierId) {
		String type = contextType == null ? "" : contextType.trim().toUpperCase(Locale.ROOT);
		java.util.Optional<MessagingStore.ContextRow> row = switch (type) {
			case "RFQ" -> store.findRfqContext(contextId, supplierId);
			case "PO" -> store.findPoContext(contextId, supplierId);
			default -> java.util.Optional.empty();
		};
		return new ContextResolved(row.orElseThrow(
				() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "Context or supplier not found.")));
	}

	private void assertBuyerAccess(CurrentUser actor, MessagingStore.ContextRow ctx) {
		if (!canAccessBuyerConversation(actor, ctx.buyingCompanyId(), ctx.requestId())) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
					"You are not authorised for this conversation context.");
		}
	}

	private void assertBuyerCanManageSupplier(CurrentUser actor, long supplierId) {
		if (!(actor.hasRole(Role.BUYER) || actor.hasRole(Role.PURCHASING_MANAGER))) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden", "Not authorised.");
		}
		store.findSupplier(supplierId)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "Supplier not found."));
	}

	private boolean canAccessBuyerConversation(CurrentUser actor, long buyingCompanyId, long requestId) {
		if (actor.hasRole(Role.SUPPLIER_CONTACT)) {
			return false;
		}
		if (actor.hasRoleIn(Role.PURCHASING_MANAGER, buyingCompanyId)) {
			return true;
		}
		if (actor.hasRoleIn(Role.BUYER, buyingCompanyId) && store.isAssignedBuyer(requestId, actor.id())) {
			return true;
		}
		return false;
	}

	private void assertWritable(ConversationAccess access) {
		if (!"OPEN".equals(access.conversation().status()) || isBusinessClosed(access.businessStatus())) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "conversation-closed",
					"This conversation is read-only.");
		}
	}

	private static boolean isBusinessClosed(String status) {
		return "CLOSED".equals(status) || "CANCELLED".equals(status);
	}

	private static String normalizeAudience(String audience) {
		String value = audience == null ? "" : audience.trim().toUpperCase(Locale.ROOT);
		if (!value.equals("INTERNAL") && !value.equals("EXTERNAL")) {
			throw new ValidationFailedException(List.of(FieldViolation.of("audience", "invalid-value")));
		}
		return value;
	}

	private static void requireBuyer(CurrentUser actor) {
		if (actor.hasRole(Role.SUPPLIER_CONTACT) || !(actor.hasRole(Role.BUYER) || actor.hasRole(Role.PURCHASING_MANAGER))) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden", "Buyer messaging access required.");
		}
	}

	private static void requireSupplier(CurrentUser actor) {
		if (!actor.hasRole(Role.SUPPLIER_CONTACT)) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden", "Supplier portal access required.");
		}
	}

	private static String randomUnusableSecret() {
		return "PendingSetup-" + java.util.UUID.randomUUID();
	}

	private record ContextResolved(MessagingStore.ContextRow row) {
	}

	private record ConversationAccess(MessagingStore.ConversationRow conversation, String businessStatus) {
	}

}
