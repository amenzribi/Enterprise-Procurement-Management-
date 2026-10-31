package com.epi.procurement.identity;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.epi.procurement.platform.web.ApiProblemException;

/**
 * Password strength rules for Sprint 10. There was no prior enforced policy; this is the installation baseline
 * (minimum length plus letter and digit).
 */
@Component
class PasswordPolicy {

	static final int MIN_LENGTH = 12;

	void validate(String password) {
		if (password == null || password.length() < MIN_LENGTH) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "password-too-weak",
					"Password must be at least " + MIN_LENGTH + " characters.");
		}
		boolean letter = false;
		boolean digit = false;
		for (int i = 0; i < password.length(); i++) {
			char c = password.charAt(i);
			if (Character.isLetter(c)) {
				letter = true;
			}
			if (Character.isDigit(c)) {
				digit = true;
			}
		}
		if (!letter || !digit) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "password-too-weak",
					"Password must include at least one letter and one digit.");
		}
	}

}
