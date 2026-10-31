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
@RequestMapping("/api/v1/supplier-portal")
class SupplierPortalController {

	private final MessagingService messaging;

	private final CurrentUserProvider currentUser;

	private final Idempotency idempotency;

	SupplierPortalController(MessagingService messaging, CurrentUserProvider currentUser, Idempotency idempotency) {
		this.messaging = messaging;
		this.currentUser = currentUser;
		this.idempotency = idempotency;
	}

	@GetMapping("/conversations")
	List<MessagingViews.ConversationSummary> list() {
		return messaging.listSupplier(currentUser.require());
	}

	@GetMapping("/conversations/{id}")
	MessagingViews.ConversationDetail get(@PathVariable long id) {
		return messaging.getSupplier(currentUser.require(), id);
	}

	@GetMapping("/conversations/{id}/messages")
	MessagingViews.MessagePage messages(@PathVariable long id, @RequestParam(required = false) Long beforeId) {
		return messaging.messagesSupplier(currentUser.require(), id, beforeId);
	}

	@PostMapping("/conversations/{id}/messages")
	ResponseEntity<?> send(@PathVariable long id, @RequestBody MessagingViews.SendRequest body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		var user = currentUser.require();
		String clientKey = body.clientMessageId() != null ? body.clientMessageId() : key;
		return idempotency.execute("user:" + user.id(), clientKey, "portal-send|" + id + "|" + body.bodyText(),
				() -> ResponseEntity.status(HttpStatus.CREATED)
					.body(messaging.sendSupplier(user, id,
							new MessagingViews.SendRequest("EXTERNAL", body.bodyText(), clientKey))));
	}

	@PostMapping("/conversations/{id}/read")
	ResponseEntity<Void> read(@PathVariable long id, @RequestBody MessagingController.ReadRequest body) {
		messaging.markReadSupplier(currentUser.require(), id, body.messageId());
		return ResponseEntity.noContent().build();
	}

}
