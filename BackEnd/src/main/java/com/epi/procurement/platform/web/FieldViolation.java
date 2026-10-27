package com.epi.procurement.platform.web;

import java.util.Map;

/**
 * One invalid field. {@code field} is a property path such as {@code title} or {@code lines[2].quantity};
 * {@code code} is a stable key the user interface translates, with optional {@code params} (e.g. {@code max}).
 */
public record FieldViolation(String field, String code, Map<String, Object> params) {

	public FieldViolation {
		params = params == null ? Map.of() : Map.copyOf(params);
	}

	public static FieldViolation of(String field, String code) {
		return new FieldViolation(field, code, Map.of());
	}

	public static FieldViolation of(String field, String code, String param, Object value) {
		return new FieldViolation(field, code, Map.of(param, value));
	}

}
