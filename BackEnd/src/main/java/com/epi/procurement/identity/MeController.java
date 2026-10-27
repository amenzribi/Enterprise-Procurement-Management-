package com.epi.procurement.identity;

import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/me")
class MeController {

	private static final Set<String> LANGUAGES = Set.of("en", "fr");

	private final CurrentUserProvider currentUser;

	private final MeAssembler me;

	private final UserPreferences preferences;

	MeController(CurrentUserProvider currentUser, MeAssembler me, UserPreferences preferences) {
		this.currentUser = currentUser;
		this.me = me;
		this.preferences = preferences;
	}

	@GetMapping
	MeResponse me(HttpServletRequest request) {
		return me.assemble(currentUser.require(), request.getSession(false));
	}

	/**
	 * Saves the interface language on the account, so it follows the user to any browser (S1-AC-12).
	 */
	@PutMapping("/preferences")
	ResponseEntity<Void> updatePreferences(@RequestBody PreferencesRequest body) {
		CurrentUser user = currentUser.require();
		if (body.preferredLanguage() == null || !LANGUAGES.contains(body.preferredLanguage())) {
			throw new ValidationFailedException(List.of(FieldViolation.of("preferredLanguage", "invalid-value")));
		}
		preferences.updateLanguage(user.id(), body.preferredLanguage());
		return ResponseEntity.noContent().build();
	}

	record PreferencesRequest(String preferredLanguage) {
	}

}
