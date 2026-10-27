package com.epi.procurement.platform.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * 422 {@code validation-failed} with the list of invalid fields in the {@code errors} property.
 */
public class ValidationFailedException extends ApiProblemException {

	private final transient List<FieldViolation> errors;

	public ValidationFailedException(List<FieldViolation> errors) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, "validation-failed", "One or more fields are invalid.",
				Map.of("errors", List.copyOf(errors)));
		this.errors = List.copyOf(errors);
	}

	public List<FieldViolation> getErrors() {
		return errors;
	}

}
