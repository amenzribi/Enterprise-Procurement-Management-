package com.epi.procurement.platform.web;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Renders every API error as an RFC 9457 problem detail with a stable {@code code} property the frontend can
 * translate. Unexpected failures are logged with an error reference that is returned instead of internal details.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		String reference = UUID.randomUUID().toString();
		log.error("Unhandled exception [errorReference={}]", reference, ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"An unexpected error occurred.");
		problem.setTitle("Internal Server Error");
		problem.setProperty("code", "internal-error");
		problem.setProperty("errorReference", reference);
		return problem;
	}

	/**
	 * Unknown paths get a generic message instead of the framework's "No static resource ..." wording (QA-D-12).
	 */
	@Override
	protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
				"The requested resource does not exist.");
		problem.setTitle("Not Found");
		problem.setProperty("code", "not-found");
		return handleExceptionInternal(ex, problem, headers, HttpStatus.NOT_FOUND, request);
	}

}
