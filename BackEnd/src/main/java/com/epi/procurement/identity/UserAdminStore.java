package com.epi.procurement.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.epi.procurement.platform.web.ApiProblemException;

@Repository
class UserAdminStore {

	private final JdbcTemplate jdbc;

	private final NamedParameterJdbcTemplate named;

	UserAdminStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.named = new NamedParameterJdbcTemplate(jdbc);
	}

	Optional<AccountRow> findAccount(long userId) {
		return jdbc.query("""
				SELECT u.id, u.email, u.display_name, u.company_id, c.group_id, u.site_id, u.department_id,
				       u.preferred_language, u.is_active, u.is_demo, u.credentials_version, u.version
				FROM user_account u JOIN company c ON c.id = u.company_id WHERE u.id = ?""", ACCOUNT,
				userId).stream().findFirst();
	}

	Optional<AccountRow> findAccountByEmail(String email) {
		if (email == null || email.isBlank()) {
			return Optional.empty();
		}
		return jdbc.query("""
				SELECT u.id, u.email, u.display_name, u.company_id, c.group_id, u.site_id, u.department_id,
				       u.preferred_language, u.is_active, u.is_demo, u.credentials_version, u.version
				FROM user_account u JOIN company c ON c.id = u.company_id WHERE LOWER(u.email) = LOWER(?)""", ACCOUNT,
				email.strip()).stream().findFirst();
	}

	private static final org.springframework.jdbc.core.RowMapper<AccountRow> ACCOUNT = (rs, i) -> new AccountRow(
			rs.getLong("id"), rs.getString("email"), rs.getString("display_name"), rs.getLong("company_id"),
			rs.getLong("group_id"), rs.getObject("site_id", Long.class), rs.getObject("department_id", Long.class),
			rs.getString("preferred_language"), rs.getBoolean("is_active"), rs.getBoolean("is_demo"),
			rs.getInt("credentials_version"), rs.getInt("version"));

	AccountRow requireActiveUser(long userId) {
		return findAccount(userId).filter(AccountRow::active)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "not-found", "User not found."));
	}

	boolean emailTaken(String email, Long excludingUserId) {
		Integer count = excludingUserId == null
				? jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE LOWER(email) = LOWER(?)", Integer.class,
						email)
				: jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE LOWER(email) = LOWER(?) AND id <> ?",
						Integer.class, email, excludingUserId);
		return count != null && count > 0;
	}

	long insertAccount(String email, String displayName, long companyId, Long siteId, Long departmentId,
			String language, boolean active, boolean demo) {
		return jdbc.queryForObject("""
				INSERT INTO user_account (email, display_name, company_id, site_id, department_id, preferred_language,
				                          is_active, is_demo)
				OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", Long.class, email, displayName, companyId, siteId,
				departmentId, language, active ? 1 : 0, demo ? 1 : 0);
	}

	void updateAccount(long userId, String displayName, long companyId, Long siteId, Long departmentId,
			String language, int expectedVersion) {
		int updated = jdbc.update("""
				UPDATE user_account SET display_name = ?, company_id = ?, site_id = ?, department_id = ?,
				       preferred_language = ?, updated_at = SYSUTCDATETIME(), version = version + 1
				WHERE id = ? AND version = ?""", displayName, companyId, siteId, departmentId, language, userId,
				expectedVersion);
		if (updated == 0) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "version-conflict",
					"The user was modified by someone else. Reload and try again.");
		}
	}

	int bumpCredentialsVersion(long userId) {
		jdbc.update("""
				UPDATE user_account SET credentials_version = credentials_version + 1,
				       updated_at = SYSUTCDATETIME(), version = version + 1 WHERE id = ?""", userId);
		Integer version = jdbc.queryForObject("SELECT credentials_version FROM user_account WHERE id = ?", Integer.class,
				userId);
		return version == null ? 0 : version;
	}

	void setActive(long userId, boolean active) {
		jdbc.update("""
				UPDATE user_account SET is_active = ?, updated_at = SYSUTCDATETIME(), version = version + 1 WHERE id = ?""",
				active ? 1 : 0, userId);
	}

	List<RoleGrantRow> listGrants(long userId) {
		return jdbc.query("""
				SELECT id, role, company_id, department_id FROM user_role WHERE user_id = ? ORDER BY role, id""",
				(rs, i) -> new RoleGrantRow(rs.getLong("id"), rs.getString("role"), rs.getObject("company_id", Long.class),
						rs.getObject("department_id", Long.class)),
				userId);
	}

	long insertGrant(long userId, Role role, Long companyId, Long departmentId) {
		return jdbc.queryForObject("""
				INSERT INTO user_role (user_id, role, company_id, department_id) OUTPUT INSERTED.id
				VALUES (?, ?, ?, ?)""", Long.class, userId, role.name(), companyId, departmentId);
	}

	boolean deleteGrant(long grantId, long userId) {
		return jdbc.update("DELETE FROM user_role WHERE id = ? AND user_id = ?", grantId, userId) == 1;
	}

	Optional<RoleGrantRow> findGrant(long grantId) {
		return jdbc.query("SELECT id, user_id, role, company_id, department_id FROM user_role WHERE id = ?",
				(rs, i) -> new RoleGrantRow(rs.getLong("id"), rs.getString("role"),
						rs.getObject("company_id", Long.class), rs.getObject("department_id", Long.class),
						rs.getLong("user_id")),
				grantId).stream().findFirst();
	}

	int countActiveGlobalAdminsForUpdate() {
		// Serialize last-admin checks: lock active GLOBAL_ADMIN account rows.
		jdbc.queryForList("""
				SELECT u.id FROM user_account u WITH (UPDLOCK, HOLDLOCK)
				JOIN user_role r ON r.user_id = u.id AND r.role = 'GLOBAL_ADMIN'
				WHERE u.is_active = 1""", Long.class);
		Integer count = jdbc.queryForObject("""
				SELECT COUNT(DISTINCT u.id) FROM user_account u
				JOIN user_role r ON r.user_id = u.id AND r.role = 'GLOBAL_ADMIN'
				WHERE u.is_active = 1""", Integer.class);
		return count == null ? 0 : count;
	}

	boolean isGlobalAdminTarget(long userId) {
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM user_role WHERE user_id = ? AND role = 'GLOBAL_ADMIN'", Integer.class, userId);
		return count != null && count > 0;
	}

	boolean companyInGroup(long companyId, long groupId) {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM company WHERE id = ? AND group_id = ?", Integer.class,
				companyId, groupId);
		return count != null && count > 0;
	}

	boolean siteInCompany(long siteId, long companyId) {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM site WHERE id = ? AND company_id = ?", Integer.class,
				siteId, companyId);
		return count != null && count > 0;
	}

	boolean departmentInCompany(long departmentId, long companyId) {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM department WHERE id = ? AND company_id = ?",
				Integer.class, departmentId, companyId);
		return count != null && count > 0;
	}

	List<UserListRow> search(long groupId, String text, Boolean active, int page, int size) {
		int offset = Math.max(0, page) * Math.min(Math.max(size, 1), 100);
		int limit = Math.min(Math.max(size, 1), 100);
		StringBuilder sql = new StringBuilder("""
				SELECT u.id, u.email, u.display_name, u.company_id, c.code AS company_code, u.is_active
				FROM user_account u JOIN company c ON c.id = u.company_id
				WHERE c.group_id = :group""");
		Map<String, Object> params = new java.util.HashMap<>();
		params.put("group", groupId);
		if (active != null) {
			sql.append(" AND u.is_active = :active");
			params.put("active", active ? 1 : 0);
		}
		if (text != null && !text.isBlank()) {
			sql.append(
					" AND (LOWER(u.display_name) LIKE :text ESCAPE '\\' OR LOWER(u.email) LIKE :text ESCAPE '\\')");
			params.put("text", "%" + text.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
				.replace("_", "\\_").replace("[", "\\[") + "%");
		}
		sql.append(" ORDER BY u.display_name, u.id OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY");
		params.put("offset", offset);
		params.put("limit", limit);
		return named.query(sql.toString(), params,
				(rs, i) -> new UserListRow(rs.getLong("id"), rs.getString("email"), rs.getString("display_name"),
						rs.getLong("company_id"), rs.getString("company_code"), rs.getBoolean("is_active")));
	}

	/**
	 * Open approval tasks for the user. Buying-assignment counts are not queried here (request tables stay in the
	 * request module — ArchitectureTests / ADR-007); the UI still warns that open work is not auto-reassigned.
	 */
	OpenAssignments openAssignments(long userId) {
		Integer approvals = jdbc.queryForObject("""
				SELECT COUNT(*) FROM approval_task WHERE assignee_user_id = ? AND status = 'PENDING'""", Integer.class,
				userId);
		return new OpenAssignments(approvals == null ? 0 : approvals, 0);
	}

	record AccountRow(long id, String email, String displayName, long companyId, long groupId, Long siteId,
			Long departmentId, String preferredLanguage, boolean active, boolean demo, int credentialsVersion,
			int version) {
	}

	record RoleGrantRow(long id, String role, Long companyId, Long departmentId, long userId) {
		RoleGrantRow(long id, String role, Long companyId, Long departmentId) {
			this(id, role, companyId, departmentId, 0L);
		}
	}

	record UserListRow(long id, String email, String displayName, long companyId, String companyCode, boolean active) {
	}

	record OpenAssignments(int pendingApprovals, int assignedBuying) {
		boolean any() {
			return pendingApprovals > 0 || assignedBuying > 0;
		}
	}

}
