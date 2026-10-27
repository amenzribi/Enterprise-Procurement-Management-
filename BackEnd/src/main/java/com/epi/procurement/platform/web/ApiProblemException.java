package com.epi.procurement.platform.web;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * A business error rendered as an RFC 9457 problem detail with a stable, translatable {@code code}. The detail text
 * is a neutral English explanation for logs and API clients; the user interface translates {@code code} instead.
 */
public class ApiProblemException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	private final transient Map<String, Object> properties;

	public ApiProblemException(HttpStatus status, String code, String detail) {
		this(status, code, detail, Map.of());
	}

	public ApiProblemException(HttpStatus status, String code, String detail, Map<String, Object> properties) {
		super(detail);
		this.status = status;
		this.code = code;
		this.properties = Map.copyOf(properties);
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public Map<String, Object> getProperties() {
		return properties;
	}

	/**
	 * 404 for a resource that does not exist or is outside the caller's visibility; both cases are indistinguishable
	 * on purpose (ADR-007).
	 */
	public static ApiProblemException notFound() {
		return new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "The requested resource does not exist.");
	}

	public static ApiProblemException forbidden() {
		return new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden", "You are not allowed to perform this action.");
	}

	public static ApiProblemException conflict(String code, String detail) {
		return new ApiProblemException(HttpStatus.CONFLICT, code, detail);
	}

	public static ApiProblemException invalidParameter(String parameter) {
		return new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter", "Invalid request parameter.",
				Map.of("parameter", parameter));
	}

}
