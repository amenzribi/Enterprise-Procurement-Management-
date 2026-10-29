package com.epi.procurement.importdata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.Role;
import com.epi.procurement.organization.OrganizationAdmin;
import com.epi.procurement.platform.persistence.UtcTimes;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.supplier.SupplierService;

@Service
class ImportService {

	private final JdbcTemplate jdbc;

	private final SupplierService suppliers;

	private final OrganizationAdmin organization;

	private final AuditLog auditLog;

	private final Clock clock;

	ImportService(DataSource dataSource, SupplierService suppliers, OrganizationAdmin organization, AuditLog auditLog,
			ObjectProvider<Clock> clock) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.suppliers = suppliers;
		this.organization = organization;
		this.auditLog = auditLog;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@Transactional
	JobResult importCsv(CurrentUser user, String kind, Long companyId, MultipartFile file) {
		requireImport(user);
		if (file == null || file.isEmpty()) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "validation-failed", "A CSV file is required.");
		}
		if (!"SUPPLIER".equals(kind) && !"ORGANISATION".equals(kind)) {
			throw ApiProblemException.invalidParameter("kind");
		}
		if ("ORGANISATION".equals(kind) && (companyId == null || !user.hasRole(Role.ADMIN))) {
			throw ApiProblemException.forbidden();
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		}
		catch (java.io.IOException ex) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "validation-failed", "The file could not be read.");
		}
		String hash = sha256(bytes);
		List<JobResult> already = jdbc.query("""
				SELECT TOP 1 id, status, accepted_rows, rejected_rows FROM import_job
				WHERE group_id = ? AND kind = ? AND file_hash = ? AND status = 'COMMITTED' ORDER BY id DESC""",
				(rs, i) -> new JobResult(rs.getLong("id"), rs.getString("status"), rs.getInt("accepted_rows"),
						rs.getInt("rejected_rows"), List.of()),
				user.groupId(), kind, hash);
		if (!already.isEmpty()) {
			return already.getFirst();
		}
		String csv = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
		String[] lines = csv.split("\\r?\\n");
		if (lines.length < 2) {
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "import-empty",
					"The file has no data rows.");
		}
		Instant now = clock.instant();
		Long jobId = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_import_job", Long.class);
		jdbc.update("""
				INSERT INTO import_job (id, group_id, company_id, kind, file_name, file_hash, status, created_by,
				                        created_at)
				VALUES (?, ?, ?, ?, ?, ?, 'VALIDATING', ?, ?)""", jobId, user.groupId(), companyId, kind,
				file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename(), hash, user.id(),
				UtcTimes.db(now));

		List<RowResult> rows = new ArrayList<>();
		int accepted = 0;
		int rejected = 0;
		for (int i = 1; i < lines.length; i++) {
			if (!StringUtils.hasText(lines[i])) {
				continue;
			}
			String[] cols = splitCsv(lines[i]);
			RowResult result = "SUPPLIER".equals(kind) ? supplierRow(user, cols, now) : orgRow(user, companyId, cols);
			rows.add(result);
			if ("ACCEPTED".equals(result.status())) {
				accepted++;
			}
			else {
				rejected++;
			}
			Long rowId = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_import_row", Long.class);
			jdbc.update("INSERT INTO import_row (id, job_id, row_no, status, entity_id, payload, errors) VALUES (?,?,?,?,?,?,?)",
					rowId, jobId, i + 1, result.status(), result.entityId(), lines[i], result.errors());
		}
		if (rejected > 0) {
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "import-rejected",
					"The file was not imported because some rows failed.",
					java.util.Map.of("jobId", jobId, "accepted", 0, "rejected", rejected, "rows",
							rows.stream().filter(r -> "REJECTED".equals(r.status())).toList()));
		}
		jdbc.update("UPDATE import_job SET status = 'COMMITTED', completed_at = ?, accepted_rows = ?, rejected_rows = 0 WHERE id = ?",
				UtcTimes.db(now), accepted, jobId);
		auditLog.record(new AuditRecord(user.id(), user.displayName(), user.companyId(), "ImportJob",
				Long.toString(jobId), "IMPORT_COMMITTED", null, null, kind,
				"{\"accepted\":" + accepted + ",\"hash\":\"" + hash + "\"}"));
		return new JobResult(jobId, "COMMITTED", accepted, 0, rows);
	}

	@Transactional(readOnly = true)
	JobResult get(CurrentUser user, long id) {
		requireImport(user);
		return jdbc.query("""
				SELECT id, status, accepted_rows, rejected_rows FROM import_job WHERE id = ? AND group_id = ?""",
				(rs, i) -> new JobResult(rs.getLong("id"), rs.getString("status"), rs.getInt("accepted_rows"),
						rs.getInt("rejected_rows"), List.of()),
				id, user.groupId()).stream().findFirst().orElseThrow(ApiProblemException::notFound);
	}

	private RowResult supplierRow(CurrentUser user, String[] cols, Instant now) {
		if (cols.length < 4) {
			return new RowResult("REJECTED", null, "code, legalName, country, visibility are required");
		}
		String code = cols[0].strip();
		String name = cols[1].strip();
		String country = cols[2].strip().toUpperCase(Locale.ROOT);
		String visibility = cols[3].strip().toUpperCase(Locale.ROOT);
		String tax = cols.length > 4 ? blank(cols[4]) : null;
		if (code.isEmpty() || name.isEmpty() || country.length() != 2
				|| (!"SHARED".equals(visibility) && !"COMPANY".equals(visibility))) {
			return new RowResult("REJECTED", null, "invalid columns");
		}
		if (suppliers.codeTaken(user.groupId(), code, null) || suppliers.taxTaken(user.groupId(), tax, null)) {
			return new RowResult("REJECTED", null, "duplicate");
		}
		Long owner = "COMPANY".equals(visibility) ? user.companyId() : null;
		long id = suppliers.insertImported(user.groupId(), owner, visibility, code, name, country, tax, null, now);
		return new RowResult("ACCEPTED", id, null);
	}

	private RowResult orgRow(CurrentUser user, Long companyId, String[] cols) {
		if (cols.length < 3) {
			return new RowResult("REJECTED", null, "code, name, type are required");
		}
		String code = cols[0].strip();
		String name = cols[1].strip();
		String type = cols[2].strip().toUpperCase(Locale.ROOT);
		if (organization.siteCodeTaken(companyId, code)) {
			return new RowResult("REJECTED", null, "duplicate");
		}
		long id = organization.insertImportedSite(companyId, code, name,
				StringUtils.hasText(type) ? type : "BRANCH", "TN");
		return new RowResult("ACCEPTED", id, null);
	}

	private static void requireImport(CurrentUser user) {
		if (!user.hasRole(Role.ADMIN) && !user.hasRole(Role.PURCHASING_MANAGER)) {
			throw ApiProblemException.forbidden();
		}
	}

	private static String[] splitCsv(String line) {
		return line.split(",", -1);
	}

	private static String blank(String value) {
		return StringUtils.hasText(value) ? value.strip() : null;
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	record JobResult(long id, String status, int accepted, int rejected, List<RowResult> rows) {
	}

	record RowResult(String status, Long entityId, String errors) {
	}
}
