package com.epi.procurement.messaging;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.Idempotency;

@RestController
@RequestMapping("/api/v1/messaging")
class MessagingController {

	private final MessagingService messaging;

	private final CurrentUserProvider currentUser;

	private final Idempotency idempotency;

	MessagingController(MessagingService messaging, CurrentUserProvider currentUser, Idempotency idempotency) {
		this.messaging = messaging;
		this.currentUser = currentUser;
		this.idempotency = idempotency;
	}

	@GetMapping("/conversations")
	List<MessagingViews.ConversationSummary> list(@RequestParam(required = false) Long supplierId) {
		return messaging.listBuyer(currentUser.require(), supplierId);
	}

	@PostMapping("/conversations")
	ResponseEntity<?> open(@RequestBody MessagingViews.OpenRequest body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		var user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key,
				"msg-open|" + body.contextType() + "|" + body.contextId() + "|" + body.supplierId(),
				() -> ResponseEntity.ok(messaging.open(user, body)));
	}

	@GetMapping("/conversations/{id}")
	MessagingViews.ConversationDetail get(@PathVariable long id) {
		return messaging.getBuyer(currentUser.require(), id);
	}

	@GetMapping("/conversations/{id}/messages")
	MessagingViews.MessagePage messages(@PathVariable long id, @RequestParam(required = false) Long beforeId) {
		return messaging.messagesBuyer(currentUser.require(), id, beforeId);
	}

	@PostMapping("/conversations/{id}/messages")
	ResponseEntity<?> send(@PathVariable long id, @RequestBody MessagingViews.SendRequest body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		var user = currentUser.require();
		String clientKey = body.clientMessageId() != null ? body.clientMessageId() : key;
		return idempotency.execute("user:" + user.id(), clientKey,
				"msg-send|" + id + "|" + body.audience() + "|" + body.bodyText(),
				() -> ResponseEntity.status(HttpStatus.CREATED).body(messaging.sendBuyer(user, id,
						new MessagingViews.SendRequest(body.audience(), body.bodyText(), clientKey))));
	}

	@PostMapping("/conversations/{id}/read")
	ResponseEntity<Void> read(@PathVariable long id, @RequestBody ReadRequest body) {
		messaging.markReadBuyer(currentUser.require(), id, body.messageId());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/suppliers/{supplierId}/contacts")
	List<MessagingViews.ContactView> contacts(@PathVariable long supplierId) {
		return messaging.listContacts(currentUser.require(), supplierId);
	}

	@PostMapping("/suppliers/{supplierId}/contacts")
	ResponseEntity<?> invite(@PathVariable long supplierId, @RequestBody MessagingViews.InviteContactRequest body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		var user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "msg-invite|" + supplierId + "|" + body.email(),
				() -> ResponseEntity.status(HttpStatus.CREATED).body(messaging.invite(user, supplierId, body)));
	}

	@PostMapping("/contacts/{contactId}/revoke")
	ResponseEntity<?> revoke(@PathVariable long contactId,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		var user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "msg-revoke|" + contactId, () -> {
			messaging.revoke(user, contactId);
			return ResponseEntity.noContent().build();
		});
	}

	record ReadRequest(long messageId) {
	}

}
