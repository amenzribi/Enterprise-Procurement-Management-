package com.epi.procurement.organization;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * Read-only organisation lookups for other modules (companies, departments, sites, cost centres). Only active
 * records are returned.
 */
@Service
public class OrganizationDirectory {

	private static final String COMPANY_COLUMNS = """
			id, group_id, code, legal_name, company_type, currency_code, time_zone, purchasing_mode""";

	private static final RowMapper<CompanyRef> COMPANY = (rs, i) -> new CompanyRef(rs.getLong("id"),
			rs.getLong("group_id"), rs.getString("code"), rs.getString("legal_name"),
			CompanyType.valueOf(rs.getString("company_type")), rs.getString("currency_code"), rs.getString("time_zone"),
			PurchasingMode.valueOf(rs.getString("purchasing_mode")));

	private final JdbcTemplate jdbc;

	OrganizationDirectory(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	public Optional<CompanyRef> findCompany(long companyId) {
		return jdbc.query("SELECT " + COMPANY_COLUMNS + " FROM company WHERE id = ? AND is_active = 1", COMPANY,
				companyId).stream().findFirst();
	}

	public List<CompanyRef> companiesOfGroup(long groupId) {
		return jdbc.query("SELECT " + COMPANY_COLUMNS + " FROM company WHERE group_id = ? AND is_active = 1 ORDER BY code",
				COMPANY, groupId);
	}

	/**
	 * The group's central purchasing company, when the group has one (the first by code if several exist).
	 */
	public Optional<CompanyRef> purchasingCenterOf(long groupId) {
		return jdbc.query("SELECT TOP (1) " + COMPANY_COLUMNS + " FROM company WHERE group_id = ? AND is_active = 1 "
				+ "AND company_type = 'PURCHASING_CENTER' ORDER BY code", COMPANY, groupId).stream().findFirst();
	}

	public List<UnitRef> departments(long companyId) {
		return jdbc.query("SELECT id, company_id, code, name, site_id FROM department WHERE company_id = ? AND is_active = 1 "
				+ "ORDER BY name", (rs, i) -> new UnitRef(rs.getLong("id"), rs.getLong("company_id"), rs.getString("code"),
						rs.getString("name"), rs.getObject("site_id", Long.class)), companyId);
	}

	public List<UnitRef> sites(long companyId) {
		return jdbc.query("SELECT id, company_id, code, name FROM site WHERE company_id = ? AND is_active = 1 ORDER BY name",
				(rs, i) -> new UnitRef(rs.getLong("id"), rs.getLong("company_id"), rs.getString("code"),
						rs.getString("name"), null), companyId);
	}

	public List<UnitRef> costCenters(long companyId) {
		return jdbc.query("SELECT id, company_id, code, name FROM cost_center WHERE company_id = ? AND is_active = 1 "
				+ "ORDER BY code", (rs, i) -> new UnitRef(rs.getLong("id"), rs.getLong("company_id"), rs.getString("code"),
						rs.getString("name"), null), companyId);
	}

	public Optional<UnitRef> findDepartment(long departmentId) {
		return jdbc.query("SELECT id, company_id, code, name, site_id FROM department WHERE id = ? AND is_active = 1",
				(rs, i) -> new UnitRef(rs.getLong("id"), rs.getLong("company_id"), rs.getString("code"),
						rs.getString("name"), rs.getObject("site_id", Long.class)), departmentId).stream().findFirst();
	}

	public Optional<UnitRef> findSite(long siteId) {
		return jdbc.query("SELECT id, company_id, code, name FROM site WHERE id = ? AND is_active = 1", (rs, i) -> new UnitRef(
				rs.getLong("id"), rs.getLong("company_id"), rs.getString("code"), rs.getString("name"), null), siteId)
			.stream().findFirst();
	}

	public Optional<UnitRef> findCostCenter(long costCenterId) {
		return jdbc.query("SELECT id, company_id, code, name FROM cost_center WHERE id = ? AND is_active = 1", (rs, i) -> new UnitRef(
				rs.getLong("id"), rs.getLong("company_id"), rs.getString("code"), rs.getString("name"), null),
				costCenterId).stream().findFirst();
	}

	/**
	 * A company of the group as seen by other modules.
	 */
	public record CompanyRef(long id, long groupId, String code, String name, CompanyType type, String currencyCode,
			String timeZone, PurchasingMode purchasingMode) {
	}

	/**
	 * A department, site or cost centre. {@code siteId} is only set for departments attached to a site.
	 */
	public record UnitRef(long id, long companyId, String code, String name, Long siteId) {
	}

}
