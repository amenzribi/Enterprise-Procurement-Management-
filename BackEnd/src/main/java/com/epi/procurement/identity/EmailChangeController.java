package com.epi.procurement.identity;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * E-mail correction (Sprint 2, ADR-016 step 5): requested, approved and cancelled by global administrators; made
 * effective only by the owner of the new mailbox through the link sent there.
 */
@RestController
class EmailChangeController {

	private final EmailChangeService changes;

	private final CurrentUserProvider currentUser;

	EmailChangeController(EmailChangeService changes, CurrentUserProvider currentUser) {
		this.changes = changes;
		this.currentUser = currentUser;
	}

	record ChangeRequest(String newEmail, String reason) {
	}

	record ConfirmRequest(String token) {
		@Override
		public String toString() {
			return "ConfirmRequest[token=***]";
		}
	}

	/** The open change of the account; 204 when there is none. */
	@GetMapping("/api/v1/admin/accounts/{id}/email-change")
	ResponseEntity<EmailChangeService.View> current(@PathVariable long id) {
		EmailChangeService.View view = changes.current(currentUser.require(), id);
		return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
	}

	@PostMapping("/api/v1/admin/accounts/{id}/email-change")
	ResponseEntity<EmailChangeService.View> request(@PathVariable long id, @RequestBody(required = false) ChangeRequest body) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(changes.request(currentUser.require(), id, body == null ? null : body.newEmail(),
					body == null ? null : body.reason()));
	}

	@PostMapping("/api/v1/admin/accounts/{id}/email-change/approve")
	EmailChangeService.View approve(@PathVariable long id) {
		return changes.approve(currentUser.require(), id);
	}

	@PostMapping("/api/v1/admin/accounts/{id}/email-change/cancel")
	ResponseEntity<Void> cancel(@PathVariable long id) {
		changes.cancel(currentUser.require(), id);
		return ResponseEntity.noContent().build();
	}

	/** Public: the token in the body proves control of the new mailbox. Every invalid link answers the same 404. */
	@PostMapping("/api/v1/auth/email-change/confirm")
	ResponseEntity<Void> confirm(@RequestBody(required = false) ConfirmRequest body, HttpServletRequest request) {
		changes.confirm(body == null ? null : body.token(), request);
		return ResponseEntity.noContent().build();
	}

}
