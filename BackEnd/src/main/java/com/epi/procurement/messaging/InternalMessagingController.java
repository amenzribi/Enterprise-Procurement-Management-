package com.epi.procurement.messaging;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.identity.Role;
import com.epi.procurement.platform.web.ApiProblemException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Internal employee messaging API (docs/architecture/internal-messaging.md). Every endpoint takes the user from the
 * session; ids in paths and bodies are checked by {@link InternalMessagingService}.
 */
@RestController
@RequestMapping("/api/v1/internal-messaging")
class InternalMessagingController {

	private final InternalMessagingService messaging;

	private final InternalMessageStream stream;

	private final CurrentUserProvider currentUser;

	InternalMessagingController(InternalMessagingService messaging, InternalMessageStream stream,
			CurrentUserProvider currentUser) {
		this.messaging = messaging;
		this.stream = stream;
		this.currentUser = currentUser;
	}

	@GetMapping("/directory")
	List<InternalMessagingViews.Person> directory(@RequestParam(required = false) String q,
			@RequestParam(required = false) String department, @RequestParam(required = false) String role) {
		return messaging.directory(currentUser.require(), q, department, role);
	}

	@GetMapping("/conversations")
	List<InternalMessagingViews.ConversationSummary> conversations(@RequestParam(required = false) String q) {
		return messaging.conversations(currentUser.require(), q);
	}

	@PostMapping("/conversations/direct")
	InternalMessagingViews.ConversationDetail openDirect(@RequestBody InternalMessagingViews.OpenDirect body) {
		return messaging.openDirect(currentUser.require(), body.userId());
	}

	@GetMapping("/conversations/{id}")
	InternalMessagingViews.ConversationDetail conversation(@PathVariable long id) {
		return messaging.conversation(currentUser.require(), id);
	}

	@GetMapping("/conversations/{id}/messages")
	InternalMessagingViews.MessagePage messages(@PathVariable long id, @RequestParam(required = false) Long beforeId,
			@RequestParam(required = false) Long afterId) {
		return messaging.messages(currentUser.require(), id, beforeId, afterId);
	}

	/** 201 for a new message, 200 when the same client id was already stored (a retry). */
	@PostMapping("/conversations/{id}/messages")
	ResponseEntity<InternalMessagingViews.SendResult> send(@PathVariable long id,
			@RequestBody InternalMessagingViews.Send body) {
		var result = messaging.send(currentUser.require(), id, body);
		return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
	}

	@PostMapping("/conversations/{id}/read")
	ResponseEntity<Void> read(@PathVariable long id, @RequestBody InternalMessagingViews.MarkRead body) {
		messaging.markRead(currentUser.require(), id, body.messageId());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/conversations/{id}/candidates")
	List<InternalMessagingViews.Person> candidates(@PathVariable long id, @RequestParam(required = false) String q) {
		return messaging.candidates(currentUser.require(), id, q);
	}

	@PostMapping("/conversations/{id}/participants")
	InternalMessagingViews.ConversationDetail addParticipant(@PathVariable long id,
			@RequestBody InternalMessagingViews.AddParticipant body) {
		return messaging.addParticipant(currentUser.require(), id, body.userId());
	}

	@GetMapping("/unread")
	InternalMessagingViews.Unread unread() {
		return messaging.unread(currentUser.require());
	}

	@GetMapping("/requests/{requestId}/discussion")
	InternalMessagingViews.Discussion discussion(@PathVariable long requestId) {
		return messaging.discussion(currentUser.require(), requestId);
	}

	@PostMapping("/requests/{requestId}/discussion")
	ResponseEntity<InternalMessagingViews.ConversationDetail> startDiscussion(@PathVariable long requestId) {
		return ResponseEntity.status(HttpStatus.CREATED).body(messaging.startDiscussion(currentUser.require(), requestId));
	}

	@GetMapping("/requests/{requestId}/approvers")
	InternalMessagingViews.Approvers approvers(@PathVariable long requestId) {
		return messaging.approvers(currentUser.require(), requestId);
	}

	@PostMapping("/requests/{requestId}/message-approver")
	ResponseEntity<InternalMessagingViews.ApproverMessageResult> messageApprover(@PathVariable long requestId,
			@RequestBody InternalMessagingViews.MessageApprover body) {
		var result = messaging.messageApprover(currentUser.require(), requestId, body);
		return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
	}

	/** The live event stream. Authenticated by the session cookie; no credential ever travels in the URL. */
	@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter stream(HttpServletRequest request) {
		var user = currentUser.require();
		if (user.hasRole(Role.SUPPLIER_CONTACT)) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden", "Internal messaging is for employees.");
		}
		return stream.open(user, request.getSession(false));
	}

}
