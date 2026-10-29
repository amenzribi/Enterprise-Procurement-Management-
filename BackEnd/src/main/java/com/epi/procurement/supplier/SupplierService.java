package com.epi.procurement.supplier;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.Role;
import com.epi.procurement.platform.persistence.UtcTimes;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;

@Service
public class SupplierService {

	private final JdbcTemplate jdbc;

	private final AuditLog auditLog;

	private final Clock clock;

	SupplierService(DataSource dataSource, AuditLog auditLog, ObjectProvider<Clock> clock) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.auditLog = auditLog;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@Transactional(readOnly = true)
	public Page list(CurrentUser user, String q, String visibility, Long companyId, int page, int size) {
		requireRead(user);
		int pageSize = size < 1 || size > 100 ? 20 : size;
		int pageNo = Math.max(0, page);
		StringBuilder where = new StringBuilder(" WHERE s.group_id = ? AND (s.visibility = 'SHARED' "
				+ "OR s.owning_company_id = ? OR EXISTS (SELECT 1 FROM supplier_company sc WHERE sc.supplier_id = s.id "
				+ "AND sc.company_id = ?))");
		java.util.ArrayList<Object> args = new java.util.ArrayList<>();
		args.add(user.groupId());
		args.add(user.companyId());
		args.add(user.companyId());
		if (StringUtils.hasText(q)) {
			where.append(" AND (LOWER(s.code) LIKE ? OR LOWER(s.legal_name) LIKE ?)");
			String like = "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
			args.add(like);
			args.add(like);
		}
		if (StringUtils.hasText(visibility)) {
			where.append(" AND s.visibility = ?");
			args.add(visibility);
		}
		if (companyId != null) {
			where.append(" AND (s.owning_company_id = ? OR EXISTS (SELECT 1 FROM supplier_company sc "
					+ "WHERE sc.supplier_id = s.id AND sc.company_id = ?))");
			args.add(companyId);
			args.add(companyId);
		}
		Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM supplier s" + where, Integer.class, args.toArray());
		args.add(pageNo * pageSize);
		args.add(pageSize);
		List<Item> items = jdbc.query("SELECT s.id, s.code, s.legal_name, s.country_code, s.visibility, "
				+ "s.owning_company_id, s.tax_id, s.erp_code, s.email, s.city, s.is_active, s.version FROM supplier s"
				+ where + " ORDER BY s.legal_name OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", ITEM, args.toArray());
		int count = total == null ? 0 : total;
		return new Page(items, pageNo, pageSize, count, (count + pageSize - 1) / pageSize);
	}

	@Transactional(readOnly = true)
	public Item get(CurrentUser user, long id) {
		requireRead(user);
		return visible(user, id);
	}

	@Transactional
	public Item create(CurrentUser user, Body body) {
		requireManage(user);
		validate(body);
		if ("COMPANY".equals(body.visibility()) && body.owningCompanyId() == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("owningCompanyId", "required")));
		}
		if (codeTaken(user.groupId(), body.code(), null) || taxTaken(user.groupId(), body.taxId(), null)) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "duplicate-supplier",
					"A supplier with this code or tax id already exists in the group.");
		}
		Instant now = clock.instant();
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_supplier", Long.class);
		jdbc.update("""
				INSERT INTO supplier (id, group_id, owning_company_id, code, legal_name, country_code, visibility,
				                      tax_id, erp_code, email, city, is_active, created_at, updated_at, version)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, 0)""", id, user.groupId(),
				"SHARED".equals(body.visibility()) ? null : body.owningCompanyId(), body.code().strip(),
				body.legalName().strip(), body.countryCode().toUpperCase(Locale.ROOT), body.visibility(),
				blankToNull(body.taxId()), blankToNull(body.erpCode()), blankToNull(body.email()),
				blankToNull(body.city()), UtcTimes.db(now), UtcTimes.db(now));
		auditLog.record(new AuditRecord(user.id(), user.displayName(), user.companyId(), "Supplier", Long.toString(id),
				"SUPPLIER_CREATED", null, null, null, "{\"code\":\"" + body.code().strip() + "\"}"));
		return get(user, id);
	}

	@Transactional
	public Item update(CurrentUser user, long id, Body body) {
		requireManage(user);
		Item existing = visible(user, id);
		validate(body);
		if (body.version() == null || body.version() != existing.version()) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "stale-version",
					"The supplier was changed by someone else. Reload it and try again.");
		}
		if (codeTaken(user.groupId(), body.code(), id) || taxTaken(user.groupId(), body.taxId(), id)) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "duplicate-supplier",
					"A supplier with this code or tax id already exists in the group.");
		}
		jdbc.update("""
				UPDATE supplier SET code = ?, legal_name = ?, country_code = ?, tax_id = ?, erp_code = ?, email = ?,
				                    city = ?, updated_at = ?, version = version + 1
				WHERE id = ? AND version = ?""", body.code().strip(), body.legalName().strip(),
				body.countryCode().toUpperCase(Locale.ROOT), blankToNull(body.taxId()), blankToNull(body.erpCode()),
				blankToNull(body.email()), blankToNull(body.city()), UtcTimes.db(clock.instant()), id, body.version());
		auditLog.record(new AuditRecord(user.id(), user.displayName(), user.companyId(), "Supplier", Long.toString(id),
				"SUPPLIER_UPDATED", null, null, null, null));
		return get(user, id);
	}

	@Transactional
	public void deactivate(CurrentUser user, long id) {
		requireManage(user);
		visible(user, id);
		Integer refs = jdbc.queryForObject("SELECT COUNT(*) FROM request_supplier_ref WHERE supplier_id = ?",
				Integer.class, id);
		jdbc.update("UPDATE supplier SET is_active = 0, updated_at = ?, version = version + 1 WHERE id = ?",
				UtcTimes.db(clock.instant()), id);
		auditLog.record(new AuditRecord(user.id(), user.displayName(), user.companyId(), "Supplier", Long.toString(id),
				"SUPPLIER_DEACTIVATED", null, null, refs != null && refs > 0 ? "referenced" : null, null));
	}

	/**
	 * Used by the import wizard in the same transaction.
	 */
	public long insertImported(long groupId, Long owningCompanyId, String visibility, String code, String legalName,
			String country, String taxId, String erpCode, Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_supplier", Long.class);
		jdbc.update("""
				INSERT INTO supplier (id, group_id, owning_company_id, code, legal_name, country_code, visibility,
				                      tax_id, erp_code, is_active, created_at, updated_at, version)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, 0)""", id, groupId, owningCompanyId, code, legalName,
				country, visibility, taxId, erpCode, UtcTimes.db(now), UtcTimes.db(now));
		return id;
	}

	public boolean codeTaken(long groupId, String code, Long exceptId) {
		Integer n = exceptId == null
				? jdbc.queryForObject("SELECT COUNT(*) FROM supplier WHERE group_id = ? AND LOWER(code) = LOWER(?)",
						Integer.class, groupId, code)
				: jdbc.queryForObject("SELECT COUNT(*) FROM supplier WHERE group_id = ? AND LOWER(code) = LOWER(?) AND id <> ?",
						Integer.class, groupId, code, exceptId);
		return n != null && n > 0;
	}

	public boolean taxTaken(long groupId, String taxId, Long exceptId) {
		if (!StringUtils.hasText(taxId)) {
			return false;
		}
		Integer n = exceptId == null
				? jdbc.queryForObject("SELECT COUNT(*) FROM supplier WHERE group_id = ? AND tax_id = ?", Integer.class,
						groupId, taxId.strip())
				: jdbc.queryForObject("SELECT COUNT(*) FROM supplier WHERE group_id = ? AND tax_id = ? AND id <> ?",
						Integer.class, groupId, taxId.strip(), exceptId);
		return n != null && n > 0;
	}

	private Item visible(CurrentUser user, long id) {
		return jdbc.query("""
				SELECT s.id, s.code, s.legal_name, s.country_code, s.visibility, s.owning_company_id, s.tax_id,
				       s.erp_code, s.email, s.city, s.is_active, s.version
				FROM supplier s WHERE s.id = ? AND s.group_id = ? AND (s.visibility = 'SHARED'
				    OR s.owning_company_id = ? OR EXISTS (SELECT 1 FROM supplier_company sc
				        WHERE sc.supplier_id = s.id AND sc.company_id = ?))""", ITEM, id, user.groupId(),
				user.companyId(), user.companyId()).stream().findFirst().orElseThrow(ApiProblemException::notFound);
	}

	private static void requireRead(CurrentUser user) {
		if (!user.hasRole(Role.BUYER) && !user.hasRole(Role.PURCHASING_MANAGER) && !user.hasRole(Role.ADMIN)
				&& !user.hasRole(Role.GROUP_MANAGEMENT)) {
			throw ApiProblemException.forbidden();
		}
	}

	private static void requireManage(CurrentUser user) {
		if (!user.hasRole(Role.PURCHASING_MANAGER) && !user.hasRole(Role.ADMIN)) {
			throw ApiProblemException.forbidden();
		}
	}

	private static void validate(Body body) {
		List<FieldViolation> errors = new java.util.ArrayList<>();
		if (!StringUtils.hasText(body.code())) {
			errors.add(FieldViolation.of("code", "required"));
		}
		if (!StringUtils.hasText(body.legalName())) {
			errors.add(FieldViolation.of("legalName", "required"));
		}
		if (!StringUtils.hasText(body.countryCode()) || body.countryCode().length() != 2) {
			errors.add(FieldViolation.of("countryCode", "required"));
		}
		if (!"SHARED".equals(body.visibility()) && !"COMPANY".equals(body.visibility())) {
			errors.add(FieldViolation.of("visibility", "required"));
		}
		if (!errors.isEmpty()) {
			throw new ValidationFailedException(errors);
		}
	}

	private static String blankToNull(String value) {
		return StringUtils.hasText(value) ? value.strip() : null;
	}

	public record Item(long id, String code, String legalName, String countryCode, String visibility,
			Long owningCompanyId, String taxId, String erpCode, String email, String city, boolean active, int version) {
	}

	public record Body(String code, String legalName, String countryCode, String visibility, Long owningCompanyId,
			String taxId, String erpCode, String email, String city, Integer version) {
	}

	public record Page(List<Item> items, int page, int size, long totalItems, int totalPages) {
	}

	private static final RowMapper<Item> ITEM = (rs, i) -> new Item(rs.getLong("id"), rs.getString("code"),
			rs.getString("legal_name"), rs.getString("country_code"), rs.getString("visibility"),
			rs.getObject("owning_company_id", Long.class), rs.getString("tax_id"), rs.getString("erp_code"),
			rs.getString("email"), rs.getString("city"), rs.getBoolean("is_active"), rs.getInt("version"));
}
