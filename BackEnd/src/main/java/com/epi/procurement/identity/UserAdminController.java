package com.epi.procurement.identity;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * User administration API (Sprint 10). Server enforces GLOBAL_ADMIN / ADMIN matrix.
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
class UserAdminController {

	private final UserAdminService service;

	private final CurrentUserProvider currentUser;

	UserAdminController(UserAdminService service, CurrentUserProvider currentUser) {
		this.service = service;
		this.currentUser = currentUser;
	}

	@GetMapping
	List<UserAdminViews.ListItem> list(@RequestParam(required = false) String q,
			@RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(currentUser.require(), q, active, page, size);
	}

	@GetMapping("/{id}")
	UserAdminViews.Detail get(@PathVariable long id) {
		return service.get(currentUser.require(), id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	UserAdminViews.Detail create(@RequestBody UserAdminViews.CreateRequest body) {
		return service.create(currentUser.require(), body);
	}

	@PutMapping("/{id}")
	UserAdminViews.Detail update(@PathVariable long id, @RequestBody UserAdminViews.UpdateRequest body) {
		return service.update(currentUser.require(), id, body);
	}

	@PostMapping("/{id}/active")
	UserAdminViews.StatusResult setActive(@PathVariable long id, @RequestBody UserAdminViews.ActiveRequest body) {
		return service.setActive(currentUser.require(), id, body.active());
	}

	@PostMapping("/{id}/roles")
	UserAdminViews.Detail addRole(@PathVariable long id, @RequestBody UserAdminViews.GrantRequest body) {
		return service.addGrant(currentUser.require(), id, body);
	}

	@PostMapping("/{id}/roles/{grantId}/remove")
	UserAdminViews.Detail removeRole(@PathVariable long id, @PathVariable long grantId) {
		return service.removeGrant(currentUser.require(), id, grantId);
	}

	@PostMapping("/{id}/password-reset")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void passwordReset(@PathVariable long id) {
		service.initiatePasswordReset(currentUser.require(), id);
	}


	@PostMapping("/{id}/revoke-sessions")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revokeSessions(@PathVariable long id) {
		service.revokeSessions(currentUser.require(), id);
	}

}
