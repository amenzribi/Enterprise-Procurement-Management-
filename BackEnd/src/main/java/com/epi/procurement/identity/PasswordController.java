package com.epi.procurement.identity;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/v1/auth")
class PasswordController {

	private final PasswordService passwords;

	private final CurrentUserProvider currentUser;

	PasswordController(PasswordService passwords, CurrentUserProvider currentUser) {
		this.passwords = passwords;
		this.currentUser = currentUser;
	}

	@PostMapping({ "/password", "/password/change" })
	ResponseEntity<Void> changeOwn(@RequestBody ChangePasswordRequest body, HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		passwords.changeOwnPassword(currentUser.require(), body.currentPassword(), body.newPassword(), session);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/password-reset/request")
	ResponseEntity<Void> requestReset(@RequestBody ForgotRequest body, HttpServletRequest request) {
		passwords.requestForgotPassword(body.email(), request);
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/password-reset/redeem")
	ResponseEntity<Void> redeem(@RequestBody RedeemRequest body, HttpServletRequest request) {
		passwords.redeemReset(body.token(), body.newPassword(), request);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	record ForgotRequest(String email) {
		@Override
		public String toString() {
			return "ForgotRequest[email=***]";
		}
	}

	record ChangePasswordRequest(String currentPassword, String newPassword) {
		@Override
		public String toString() {
			return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
		}
	}

	record RedeemRequest(String token, String newPassword) {
		@Override
		public String toString() {
			return "RedeemRequest[token=***, newPassword=***]";
		}
	}

}
