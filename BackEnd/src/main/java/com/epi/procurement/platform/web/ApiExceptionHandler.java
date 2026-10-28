package com.epi.procurement.platform.web;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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

	@ExceptionHandler(ApiProblemException.class)
	ResponseEntity<ProblemDetail> handleApiProblem(ApiProblemException ex) {
		ProblemDetail problem = problem(ex.getStatus(), ex.getCode(), ex.getMessage());
		ex.getProperties().forEach(problem::setProperty);
		return ResponseEntity.status(ex.getStatus()).body(problem);
	}

	/**
	 * Raised by method security or explicit checks; never reached for unauthenticated calls, which the security
	 * filter chain answers with 401 before any controller runs.
	 */
	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(problem(HttpStatus.FORBIDDEN, "forbidden", "You are not allowed to perform this action."));
	}

	/**
	 * A concurrent change detected by the JPA version check at flush time: same answer as an explicit stale version.
	 */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "stale-version",
					"The request was changed by someone else. Reload it and try again."));
	}

	/**
	 * Lock timeouts, and deadlocks still failing after the retries: retryable, answered like a busy application lock.
	 */
	@ExceptionHandler(PessimisticLockingFailureException.class)
	ResponseEntity<ProblemDetail> handleLockFailure(PessimisticLockingFailureException ex) {
		log.warn("Lock failure: {}", ex.getClass().getSimpleName());
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
			.body(problem(HttpStatus.SERVICE_UNAVAILABLE, "busy",
					"The system is busy with a concurrent operation. Please try again in a moment."));
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		String reference = UUID.randomUUID().toString();
		log.error("Unhandled exception [errorReference={}]", reference, ex);
		ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
				"An unexpected error occurred.");
		problem.setProperty("errorReference", reference);
		return problem;
	}

	/**
	 * Unknown paths get a generic message instead of the framework's "No static resource ..." wording (QA-D-12).
	 */
	@Override
	protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return handleExceptionInternal(ex,
				problem(HttpStatus.NOT_FOUND, "not-found", "The requested resource does not exist."), headers,
				HttpStatus.NOT_FOUND, request);
	}

	/**
	 * Malformed JSON or a value of the wrong type in the body. The parser message is not returned: it can echo
	 * internal class names.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return handleExceptionInternal(ex,
				problem(HttpStatus.BAD_REQUEST, "invalid-request", "The request body is malformed."), headers,
				HttpStatus.BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-parameter", "Invalid request parameter.");
		if (ex.getPropertyName() != null) {
			problem.setProperty("parameter", ex.getPropertyName());
		}
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-parameter", "Invalid request parameter.");
		problem.setProperty("parameter", ex.getParameterName());
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	/**
	 * An upload above the multipart limit (S2-AC-12): 413 with the translatable code.
	 */
	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
			org.springframework.web.multipart.MaxUploadSizeExceededException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return handleExceptionInternal(ex,
				problem(HttpStatus.CONTENT_TOO_LARGE, "file-too-large", "The file is too large."), headers,
				HttpStatus.CONTENT_TOO_LARGE, request);
	}

	private static ProblemDetail problem(HttpStatus status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(status.getReasonPhrase());
		problem.setProperty("code", code);
		return problem;
	}

}
