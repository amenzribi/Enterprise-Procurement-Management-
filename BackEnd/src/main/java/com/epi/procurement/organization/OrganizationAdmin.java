package com.epi.procurement.organization;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.platform.persistence.UtcTimes;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;

@Service
public class OrganizationAdmin {

	private final JdbcTemplate jdbc;

	private final AuditLog auditLog;

	private final Clock clock;

	OrganizationAdmin(DataSource dataSource, AuditLog auditLog, ObjectProvider<Clock> clock) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.auditLog = auditLog;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@Transactional(readOnly = true)
	public List<CompanyRow> companies(Actor admin) {
		requireAdmin(admin);
		return jdbc.query("""
				SELECT id, code, legal_name, company_type, country_code, currency_code, default_language, time_zone,
				       purchasing_mode, parent_company_id, erp_company_code, is_active, version
				FROM company WHERE group_id = ? ORDER BY code""", (rs, i) -> new CompanyRow(rs.getLong("id"),
				rs.getString("code"), rs.getString("legal_name"), rs.getString("company_type"),
				rs.getString("country_code"), rs.getString("currency_code"), rs.getString("default_language"),
				rs.getString("time_zone"), rs.getString("purchasing_mode"), rs.getObject("parent_company_id", Long.class),
				rs.getString("erp_company_code"), rs.getBoolean("is_active"), rs.getInt("version")), admin.groupId());
	}

	@Transactional
	public CompanyRow createCompany(Actor admin, CompanyBody body) {
		requireAdmin(admin);
		require(body.code(), "code");
		require(body.legalName(), "legalName");
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_company", Long.class);
		try {
			jdbc.update("""
					INSERT INTO company (id, group_id, parent_company_id, code, legal_name, company_type, country_code,
					                     currency_code, default_language, time_zone, erp_company_code, purchasing_mode)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, admin.groupId(), body.parentCompanyId(),
					body.code().strip(), body.legalName().strip(), body.companyType(),
					body.countryCode().toUpperCase(Locale.ROOT), body.currencyCode().toUpperCase(Locale.ROOT),
					body.defaultLanguage(), body.timeZone(), blank(body.erpCompanyCode()), body.purchasingMode());
		}
		catch (org.springframework.dao.DuplicateKeyException ex) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "duplicate-code", "That code is already used.");
		}
		audit(admin, "Company", id, "COMPANY_CREATED");
		return companies(admin).stream().filter(c -> c.id() == id).findFirst().orElseThrow();
	}

	@Transactional
	public CompanyRow updateCompany(Actor admin, long id, CompanyBody body) {
		requireAdmin(admin);
		CompanyRow existing = companies(admin).stream().filter(c -> c.id() == id).findFirst()
			.orElseThrow(ApiProblemException::notFound);
		if (body.version() == null || body.version() != existing.version()) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "stale-version",
					"The company was changed by someone else. Reload it and try again.");
		}
		jdbc.update("""
				UPDATE company SET legal_name = ?, time_zone = ?, purchasing_mode = ?, erp_company_code = ?,
				                   is_active = ?, default_language = ?, version = version + 1, updated_at = ?
				WHERE id = ? AND group_id = ? AND version = ?""", body.legalName().strip(), body.timeZone(),
				body.purchasingMode(), blank(body.erpCompanyCode()), body.active() == null || body.active(),
				body.defaultLanguage(), UtcTimes.db(clock.instant()), id, admin.groupId(), body.version());
		audit(admin, "Company", id, "COMPANY_UPDATED");
		return companies(admin).stream().filter(c -> c.id() == id).findFirst().orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<UnitRow> sites(Actor admin, long companyId) {
		requireAdmin(admin);
		requireCompany(admin, companyId);
		return jdbc.query("SELECT id, company_id, code, name, is_active FROM site WHERE company_id = ? ORDER BY code",
				UNIT, companyId);
	}

	@Transactional
	public UnitRow createSite(Actor admin, long companyId, UnitBody body) {
		requireAdmin(admin);
		requireCompany(admin, companyId);
		require(body.code(), "code");
		require(body.name(), "name");
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_site", Long.class);
		jdbc.update("""
				INSERT INTO site (id, company_id, code, name, site_type, country_code)
				VALUES (?, ?, ?, ?, ?, ?)""", id, companyId, body.code().strip(), body.name().strip(),
				StringUtils.hasText(body.type()) ? body.type() : "BRANCH",
				StringUtils.hasText(body.countryCode()) ? body.countryCode().toUpperCase(Locale.ROOT) : "TN");
		audit(admin, "Site", id, "SITE_CREATED");
		return sites(admin, companyId).stream().filter(s -> s.id() == id).findFirst().orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<UnitRow> departments(Actor admin, long companyId) {
		requireAdmin(admin);
		requireCompany(admin, companyId);
		return jdbc.query("SELECT id, company_id, code, name, is_active FROM department WHERE company_id = ? ORDER BY code",
				UNIT, companyId);
	}

	@Transactional
	public UnitRow createDepartment(Actor admin, long companyId, UnitBody body) {
		requireAdmin(admin);
		requireCompany(admin, companyId);
		require(body.code(), "code");
		require(body.name(), "name");
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_department", Long.class);
		jdbc.update("INSERT INTO department (id, company_id, site_id, code, name) VALUES (?, ?, ?, ?, ?)", id,
				companyId, body.siteId(), body.code().strip(), body.name().strip());
		audit(admin, "Department", id, "DEPARTMENT_CREATED");
		return departments(admin, companyId).stream().filter(s -> s.id() == id).findFirst().orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<UnitRow> costCenters(Actor admin, long companyId) {
		requireAdmin(admin);
		requireCompany(admin, companyId);
		return jdbc.query("SELECT id, company_id, code, name, is_active FROM cost_center WHERE company_id = ? ORDER BY code",
				UNIT, companyId);
	}

	@Transactional
	public UnitRow createCostCenter(Actor admin, long companyId, UnitBody body) {
		requireAdmin(admin);
		requireCompany(admin, companyId);
		require(body.code(), "code");
		require(body.name(), "name");
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_cost_center", Long.class);
		jdbc.update("INSERT INTO cost_center (id, company_id, code, name, erp_code) VALUES (?, ?, ?, ?, ?)", id,
				companyId, body.code().strip(), body.name().strip(), blank(body.erpCode()));
		audit(admin, "CostCenter", id, "COST_CENTER_CREATED");
		return costCenters(admin, companyId).stream().filter(s -> s.id() == id).findFirst().orElseThrow();
	}

	public long insertImportedSite(long companyId, String code, String name, String type, String country) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_site", Long.class);
		jdbc.update("INSERT INTO site (id, company_id, code, name, site_type, country_code) VALUES (?, ?, ?, ?, ?, ?)",
				id, companyId, code, name, type, country);
		return id;
	}

	public boolean siteCodeTaken(long companyId, String code) {
		Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM site WHERE company_id = ? AND LOWER(code) = LOWER(?)",
				Integer.class, companyId, code);
		return n != null && n > 0;
	}

	private void requireCompany(Actor admin, long companyId) {
		Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM company WHERE id = ? AND group_id = ?", Integer.class,
				companyId, admin.groupId());
		if (n == null || n == 0) {
			throw ApiProblemException.notFound();
		}
	}

	private static void requireAdmin(Actor user) {
		if (!user.admin()) {
			throw ApiProblemException.forbidden();
		}
	}

	private static void require(String value, String field) {
		if (!StringUtils.hasText(value)) {
			throw new ValidationFailedException(List.of(FieldViolation.of(field, "required")));
		}
	}

	private void audit(Actor admin, String entity, long id, String action) {
		auditLog.record(new AuditRecord(admin.userId(), admin.displayName(), admin.companyId(), entity, Long.toString(id),
				action, null, null, null, null));
	}

	public record Actor(long userId, String displayName, long companyId, long groupId, boolean admin) {
	}

	private static String blank(String value) {
		return StringUtils.hasText(value) ? value.strip() : null;
	}

	public record CompanyRow(long id, String code, String legalName, String companyType, String countryCode,
			String currencyCode, String defaultLanguage, String timeZone, String purchasingMode, Long parentCompanyId,
			String erpCompanyCode, boolean active, int version) {
	}

	public record CompanyBody(String code, String legalName, String companyType, String countryCode,
			String currencyCode, String defaultLanguage, String timeZone, String purchasingMode, Long parentCompanyId,
			String erpCompanyCode, Boolean active, Integer version) {
	}

	public record UnitRow(long id, long companyId, String code, String name, boolean active) {
	}

	public record UnitBody(String code, String name, String type, String countryCode, Long siteId, String erpCode) {
	}

	private static final org.springframework.jdbc.core.RowMapper<UnitRow> UNIT = (rs, i) -> new UnitRow(rs.getLong("id"),
			rs.getLong("company_id"), rs.getString("code"), rs.getString("name"), rs.getBoolean("is_active"));
}
