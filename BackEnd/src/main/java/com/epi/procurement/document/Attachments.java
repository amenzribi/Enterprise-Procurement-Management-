package com.epi.procurement.document;

import static com.epi.procurement.platform.persistence.UtcTimes.db;
import static com.epi.procurement.platform.persistence.UtcTimes.instant;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;
import com.epi.procurement.request.RequestSnapshot;
import com.epi.procurement.request.RequestWorkflow;

/**
 * Attachments of a request or of one of its lines (S2-AC-12, S2DR-11).
 * <ul>
 * <li>Upload and delete while the request is editable (draft, returned for revision), by its authors.</li>
 * <li>Content type from the bytes, max 20 MB per file, 30 files and 100 MB per request.</li>
 * <li>A file with the same name on the same request/line becomes a new version; earlier versions are kept.</li>
 * <li>Download only for users who can see the request, only after a clean scan, always as a download.</li>
 * <li>Upload and delete are audited.</li>
 * </ul>
 */
@Service
class Attachments {

	static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

	private static final int MAX_FILES = 30;

	private static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;

	private static final String COLUMNS = """
			id, request_id, line_id, company_id, file_name, content_type, size_bytes, sha256, storage_key, version_no,
			scan_status, uploaded_by, uploaded_at""";

	private final JdbcTemplate jdbc;

	private final RequestWorkflow requests;

	private final FileStorage storage;

	private final AttachmentScanner scanner;

	private final UserDirectory users;

	private final AuditLog auditLog;

	private final Clock clock;

	private final long maxFileBytes;

	private final com.epi.procurement.platform.persistence.TransactionLocks locks;

	Attachments(DataSource dataSource, RequestWorkflow requests, FileStorage storage, AttachmentScanner scanner,
			UserDirectory users, AuditLog auditLog, ObjectProvider<Clock> clock,
			com.epi.procurement.platform.persistence.TransactionLocks locks,
			@Value("${app.attachments.max-file-bytes:20971520}") long maxFileBytes) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.requests = requests;
		this.storage = storage;
		this.scanner = scanner;
		this.users = users;
		this.auditLog = auditLog;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
		this.maxFileBytes = maxFileBytes;
		this.locks = locks;
	}

	@Transactional(readOnly = true)
	List<AttachmentView> list(CurrentUser user, long requestId) {
		requests.requireVisible(user, requestId);
		List<Row> rows = jdbc.query("SELECT " + COLUMNS + " FROM attachment WHERE request_id = ? AND deleted_at IS NULL "
				+ "ORDER BY COALESCE(line_id, 0), file_name, version_no DESC", this::row, requestId);
		Map<Long, String> names = users.findUsers(rows.stream().map(Row::uploadedBy).collect(Collectors.toSet()))
			.values()
			.stream()
			.collect(Collectors.toMap(com.epi.procurement.identity.UserSummary::id,
					com.epi.procurement.identity.UserSummary::displayName));
		boolean editable = requests.canEditContent(user, requestId);
		return rows.stream().map(r -> view(r, names, editable)).toList();
	}

	@Transactional
	AttachmentView upload(CurrentUser user, long requestId, Long lineId, String originalName, byte[] content) {
		RequestSnapshot request = requireEditable(user, requestId);
		if (lineId != null && request.lines().stream().noneMatch(l -> l.id() == lineId)) {
			throw new ValidationFailedException(List.of(FieldViolation.of("lineId", "invalid-value")));
		}
		if (content.length == 0) {
			throw new ValidationFailedException(List.of(FieldViolation.of("file", "required")));
		}
		if (content.length > maxFileBytes) {
			throw new ApiProblemException(HttpStatus.CONTENT_TOO_LARGE, "file-too-large", "The file is too large.",
					Map.of("maxBytes", maxFileBytes));
		}
		String fileName = sanitize(originalName);
		String contentType = ContentSniffer.detect(fileName, content)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "file-type-not-allowed",
					"This type of file is not allowed."));
		// One upload per request at a time, so concurrent uploads cannot both pass the limits (S2CR-12).
		locks.lockExclusively("attachments:" + requestId);
		Long[] usage = jdbc.queryForObject(
				"SELECT COUNT(*), COALESCE(SUM(size_bytes), 0) FROM attachment WHERE request_id = ? AND deleted_at IS NULL",
				(rs, i) -> new Long[] { rs.getLong(1), rs.getLong(2) }, requestId);
		if (usage[0] >= MAX_FILES || usage[1] + content.length > MAX_TOTAL_BYTES) {
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "attachment-limit-reached",
					"This request already has the maximum number or size of attachments.",
					Map.of("maxFiles", MAX_FILES, "maxTotalBytes", MAX_TOTAL_BYTES));
		}
		if (scanner.scan(fileName, content) == AttachmentScanner.ScanResult.INFECTED) {
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "file-infected",
					"The file was rejected by the antivirus.");
		}
		String key;
		try {
			key = storage.store(content);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		Integer previous = jdbc.queryForObject("""
				SELECT MAX(version_no) FROM attachment WHERE request_id = ? AND COALESCE(line_id, 0) = ? AND file_name = ?""",
				Integer.class, requestId, lineId == null ? 0L : lineId, fileName);
		int version = previous == null ? 1 : previous + 1;
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_attachment", Long.class);
		Instant now = clock.instant();
		jdbc.update("""
				INSERT INTO attachment (id, request_id, line_id, company_id, file_name, content_type, size_bytes, sha256,
				                        storage_key, version_no, scan_status, uploaded_by, uploaded_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, requestId, lineId, request.requestingCompanyId(),
				fileName, contentType, content.length, sha256(content), key, version,
				"CLEAN", user.id(), db(now));
		auditLog.record(new AuditRecord(user.id(), user.displayName(), request.requestingCompanyId(), "ATTACHMENT",
				id.toString(), "ATTACHMENT_UPLOADED", null, null, null,
				"{\"requestId\":" + requestId + ",\"version\":" + version + ",\"sizeBytes\":" + content.length + "}"));
		Row row = jdbc.queryForObject("SELECT " + COLUMNS + " FROM attachment WHERE id = ?", this::row, id);
		return view(row, Map.of(user.id(), user.displayName()), true);
	}

	@Transactional(readOnly = true)
	Download download(CurrentUser user, long attachmentId) {
		Row row = find(attachmentId);
		requests.requireVisible(user, row.requestId());
		if (!"CLEAN".equals(row.scanStatus())) {
			throw ApiProblemException.notFound();
		}
		try {
			return new Download(row.fileName(), row.contentType(), storage.read(row.storageKey()));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Transactional
	void delete(CurrentUser user, long attachmentId) {
		Row row = find(attachmentId);
		RequestSnapshot request = requireEditable(user, row.requestId());
		jdbc.update("UPDATE attachment SET deleted_at = ?, deleted_by = ? WHERE id = ? AND deleted_at IS NULL",
				db(clock.instant()), user.id(), attachmentId);
		auditLog.record(new AuditRecord(user.id(), user.displayName(), request.requestingCompanyId(), "ATTACHMENT",
				Long.toString(attachmentId), "ATTACHMENT_DELETED", null, null, null,
				"{\"requestId\":" + row.requestId() + "}"));
	}

	/**
	 * 404 when the request is not visible; 403 for a visible request the user does not author; 409 when the request
	 * is no longer editable.
	 */
	private RequestSnapshot requireEditable(CurrentUser user, long requestId) {
		RequestSnapshot request = requests.requireVisible(user, requestId);
		if (requests.canEditContent(user, requestId)) {
			return request;
		}
		if (request.isAuthor(user.id())) {
			throw ApiProblemException.conflict("invalid-state",
					"Attachments can only change while the request is a draft or returned for revision.");
		}
		throw ApiProblemException.forbidden();
	}

	private Row find(long attachmentId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM attachment WHERE id = ? AND deleted_at IS NULL", this::row,
				attachmentId).stream().findFirst().orElseThrow(ApiProblemException::notFound);
	}

	/**
	 * Keeps the last path segment, removes control and bidirectional-override characters (S2DR-11), keeps Arabic and
	 * accented letters, limits the length.
	 */
	static String sanitize(String name) {
		String value = name == null ? "" : name;
		value = value.replace('\\', '/');
		value = value.substring(value.lastIndexOf('/') + 1);
		StringBuilder clean = new StringBuilder();
		value.codePoints().forEach(cp -> {
			boolean bidi = (cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069) || cp == 0x200E
					|| cp == 0x200F || cp == 0x061C;
			if (!Character.isISOControl(cp) && !bidi && cp != '"' && cp != '<' && cp != '>' && cp != ':' && cp != '*'
					&& cp != '?' && cp != '|') {
				clean.appendCodePoint(cp);
			}
		});
		String result = clean.toString().strip();
		while (result.startsWith(".")) {
			result = result.substring(1);
		}
		if (result.isEmpty()) {
			result = "file";
		}
		if (result.length() > 200) {
			String extension = ContentSniffer.extension(result);
			result = result.substring(0, 200 - extension.length() - 1) + "." + extension;
		}
		return result;
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private AttachmentView view(Row r, Map<Long, String> names, boolean editable) {
		return new AttachmentView(r.id(), r.requestId(), r.lineId(), r.fileName(), r.contentType(), r.sizeBytes(),
				r.versionNo(), r.scanStatus(), new AttachmentView.UserRef(r.uploadedBy(), names.getOrDefault(r.uploadedBy(), "")),
				r.uploadedAt(), "/api/v1/attachments/" + r.id() + "/content", editable);
	}

	private Row row(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
		return new Row(rs.getLong("id"), rs.getLong("request_id"), rs.getObject("line_id", Long.class),
				rs.getString("file_name"), rs.getString("content_type"), rs.getLong("size_bytes"),
				rs.getString("storage_key"), rs.getInt("version_no"), rs.getString("scan_status"), rs.getLong("uploaded_by"),
				instant(rs, "uploaded_at"));
	}

	private record Row(long id, long requestId, Long lineId, String fileName, String contentType, long sizeBytes,
			String storageKey, int versionNo, String scanStatus, long uploadedBy, Instant uploadedAt) {
	}

	record Download(String fileName, String contentType, byte[] content) {
	}

	/**
	 * @param canDelete whether the caller may delete it now (editable request, author)
	 */
	record AttachmentView(long id, long requestId, Long lineId, String fileName, String contentType, long sizeBytes,
			int version, String scanStatus, UserRef uploadedBy, Instant uploadedAt, String downloadUrl,
			boolean canDelete) {

		record UserRef(long id, String displayName) {
		}

	}

}
