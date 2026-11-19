package com.epi.procurement.auditcenter;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.epi.procurement.audit.AuditSearch;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;

/**
 * Audit Center API, for global administrators only (the URL rule and the service both check it). Read-only: there is
 * no endpoint that changes an audit event.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
class AuditCenterController {

	/** Action and entity type codes are upper-case words (SHIPMENT_MILESTONE_UPDATED, PURCHASE_REQUEST). */
	private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,59}");

	private final AuditCenter center;

	private final CurrentUserProvider currentUser;

	AuditCenterController(AuditCenter center, CurrentUserProvider currentUser) {
		this.center = center;
		this.currentUser = currentUser;
	}

	record EventPage(List<AuditSearch.Entry> items, String nextCursor) {
	}

	@GetMapping("/events")
	EventPage events(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(required = false) Long companyId, @RequestParam(required = false) Long actorUserId,
			@RequestParam(required = false) String action, @RequestParam(required = false) String entityType,
			@RequestParam(required = false) String entityId, @RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "50") int size) {
		CurrentUser user = currentUser.require();
		AuditSearch.Filter filter = filter(from, to, companyId, actorUserId, action, entityType, entityId);
		AuditSearch.Page page = center.search(user, filter, decode(cursor), size);
		return new EventPage(page.items(), encode(page.next()));
	}

	@GetMapping("/events/{id}")
	AuditSearch.Entry event(@PathVariable long id) {
		return center.event(currentUser.require(), id);
	}

	@GetMapping("/events/export")
	ResponseEntity<StreamingResponseBody> export(@RequestParam(required = false) String from,
			@RequestParam(required = false) String to, @RequestParam(required = false) Long companyId,
			@RequestParam(required = false) Long actorUserId, @RequestParam(required = false) String action,
			@RequestParam(required = false) String entityType, @RequestParam(required = false) String entityId) {
		CurrentUser user = currentUser.require();
		AuditSearch.Filter filter = filter(from, to, companyId, actorUserId, action, entityType, entityId);
		// Refuse before any byte is written: a refusal after streaming started could not be a proper problem answer.
		center.requireAccess(user);
		StreamingResponseBody body = out -> {
			Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
			center.export(user, filter, writer);
		};
		return ResponseEntity.ok()
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-trail.csv\"")
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.body(body);
	}

	@PostMapping("/verifications")
	ResponseEntity<Verification> verify() {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(center.startVerification(currentUser.require()));
	}

	@GetMapping("/verifications/current")
	Verification verification() {
		return center.verification(currentUser.require());
	}

	private static AuditSearch.Filter filter(String from, String to, Long companyId, Long actorUserId, String action,
			String entityType, String entityId) {
		List<FieldViolation> violations = new ArrayList<>();
		Instant start = instant("from", from, violations);
		Instant end = instant("to", to, violations);
		if (start != null && end != null && !start.isBefore(end)) {
			violations.add(FieldViolation.of("to", "before-from"));
		}
		String actionCode = code("action", action, violations);
		String typeCode = code("entityType", entityType, violations);
		String entity = blankToNull(entityId);
		if (entity != null && entity.length() > 64) {
			violations.add(FieldViolation.of("entityId", "too-long"));
		}
		if (!violations.isEmpty()) {
			throw new ValidationFailedException(violations);
		}
		return new AuditSearch.Filter(start, end, companyId, actorUserId, actionCode, typeCode, entity);
	}

	private static Instant instant(String field, String value, List<FieldViolation> violations) {
		String text = blankToNull(value);
		if (text == null) {
			return null;
		}
		try {
			return Instant.parse(text);
		}
		catch (DateTimeParseException ex) {
			violations.add(FieldViolation.of(field, "invalid-instant"));
			return null;
		}
	}

	private static String code(String field, String value, List<FieldViolation> violations) {
		String text = blankToNull(value);
		if (text != null && !CODE.matcher(text).matches()) {
			violations.add(FieldViolation.of(field, "invalid-code"));
			return null;
		}
		return text;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	/** The cursor is opaque to the client: "&lt;epoch millis&gt;.&lt;id&gt;". */
	static String encode(AuditSearch.Cursor cursor) {
		return cursor == null ? null : cursor.occurredAt().toEpochMilli() + "." + cursor.id();
	}

	static AuditSearch.Cursor decode(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		String[] parts = cursor.split("\\.");
		try {
			if (parts.length != 2) {
				throw new NumberFormatException();
			}
			return new AuditSearch.Cursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
		}
		catch (NumberFormatException ex) {
			throw new ValidationFailedException(List.of(FieldViolation.of("cursor", "invalid-cursor")));
		}
	}

}
