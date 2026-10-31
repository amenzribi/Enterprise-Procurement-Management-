package com.epi.procurement.identity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read access to user accounts and their role grants.
 */
@Service
public class UserDirectory {

	private static final RowMapper<UserSummary> SUMMARY = (rs, i) -> new UserSummary(rs.getLong("id"),
			rs.getString("display_name"), rs.getString("email"), rs.getLong("company_id"),
			rs.getObject("department_id", Long.class), rs.getBoolean("is_active"));

	private final JdbcTemplate jdbc;

	private final NamedParameterJdbcTemplate named;

	UserDirectory(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.named = new NamedParameterJdbcTemplate(jdbc);
	}

	public Optional<UserSummary> findUser(long userId) {
		return jdbc.query("SELECT id, display_name, email, company_id, department_id, is_active FROM user_account "
				+ "WHERE id = ?", SUMMARY, userId).stream().findFirst();
	}

	public Map<Long, UserSummary> findUsers(java.util.Collection<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return named.query("SELECT id, display_name, email, company_id, department_id, is_active FROM user_account "
				+ "WHERE id IN (:ids)", Map.of("ids", userIds), SUMMARY)
			.stream()
			.collect(Collectors.toMap(UserSummary::id, Function.identity()));
	}

	/**
	 * Active users of a company, ordered by name (candidates for "entered on behalf of").
	 */
	public List<UserSummary> activeUsersOfCompany(long companyId) {
		return jdbc.query("SELECT id, display_name, email, company_id, department_id, is_active FROM user_account "
				+ "WHERE company_id = ? AND is_active = 1 ORDER BY display_name", SUMMARY, companyId);
	}

	/**
	 * E-mail and language of users, for notifications. Inactive users are included (callers decide).
	 */
	public Map<Long, UserContact> findContacts(java.util.Collection<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return named.query("""
				SELECT u.id, u.email, u.display_name, u.preferred_language, u.company_id, c.group_id, u.department_id,
				       u.is_active
				FROM user_account u JOIN company c ON c.id = u.company_id WHERE u.id IN (:ids)""", Map.of("ids", userIds),
				(rs, i) -> new UserContact(rs.getLong("id"), rs.getString("email"), rs.getString("display_name"),
						rs.getString("preferred_language"), rs.getLong("company_id"), rs.getLong("group_id"),
						rs.getObject("department_id", Long.class), rs.getBoolean("is_active")))
			.stream()
			.collect(Collectors.toMap(UserContact::id, Function.identity()));
	}

	public Optional<UserContact> findContact(long userId) {
		return Optional.ofNullable(findContacts(List.of(userId)).get(userId));
	}

	/**
	 * Active users holding {@code role} in {@code companyId} (company-scoped grants), with the department of the
	 * grant and of the account. Approver roles are always company-scoped (ck_user_role_group_wide).
	 */
	public List<RoleHolder> holdersOfRole(Role role, long companyId) {
		return jdbc.query("""
				SELECT u.id, u.display_name, r.department_id AS grant_department_id, u.department_id AS user_department_id
				FROM user_role r JOIN user_account u ON u.id = r.user_id
				WHERE r.role = ? AND r.company_id = ? AND u.is_active = 1
				ORDER BY u.display_name, u.id""", (rs, i) -> new RoleHolder(rs.getLong("id"), rs.getString("display_name"),
				rs.getObject("grant_department_id", Long.class), rs.getObject("user_department_id", Long.class)),
				role.name(), companyId);
	}

	/**
	 * Active users of a group for administration screens (S2CR-02), optionally filtered by company, role (held in
	 * any company) and text (name or e-mail), ordered by name, at most 200.
	 */
	public List<UserListing> searchUsers(long groupId, Long companyId, String role, String text) {
		StringBuilder sql = new StringBuilder("""
				SELECT TOP (200) u.id, u.display_name, u.email, u.company_id FROM user_account u
				JOIN company c ON c.id = u.company_id WHERE c.group_id = :group AND u.is_active = 1""");
		Map<String, Object> params = new java.util.HashMap<>();
		params.put("group", groupId);
		if (companyId != null) {
			sql.append(" AND u.company_id = :company");
			params.put("company", companyId);
		}
		if (role != null) {
			sql.append(" AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = :role)");
			params.put("role", role);
		}
		if (text != null && !text.isBlank()) {
			sql.append(" AND (LOWER(u.display_name) LIKE :text ESCAPE '\\' OR LOWER(u.email) LIKE :text ESCAPE '\\')");
			params.put("text", "%" + text.strip().toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\")
				.replace("%", "\\%").replace("_", "\\_").replace("[", "\\[") + "%");
		}
		sql.append(" ORDER BY u.display_name, u.id");
		List<UserListing> users = named.query(sql.toString(), params, (rs, i) -> new UserListing(rs.getLong("id"),
				rs.getString("display_name"), rs.getString("email"), rs.getLong("company_id"), new java.util.ArrayList<>()));
		if (!users.isEmpty()) {
			Map<Long, UserListing> byId = users.stream().collect(Collectors.toMap(UserListing::id, Function.identity()));
			named.query("SELECT DISTINCT user_id, role FROM user_role WHERE user_id IN (:ids) ORDER BY role",
					Map.of("ids", byId.keySet()), rs -> {
						byId.get(rs.getLong("user_id")).roles().add(rs.getString("role"));
					});
		}
		return users;
	}

	public record UserListing(long id, String displayName, String email, long companyId, List<String> roles) {
	}

	/**
	 * Ids of every user of a group (active or not).
	 */
	public List<Long> userIdsOfGroup(long groupId) {
		return jdbc.queryForList("SELECT u.id FROM user_account u JOIN company c ON c.id = u.company_id WHERE c.group_id = ?",
				Long.class, groupId);
	}

	/**
	 * Active administrators of a group (group-wide ADMIN grants of users of the group, or ADMIN grants on one of its
	 * companies).
	 */
	public List<Long> administratorsOfGroup(long groupId) {
		return jdbc.queryForList("""
				SELECT DISTINCT u.id FROM user_role r
				JOIN user_account u ON u.id = r.user_id JOIN company uc ON uc.id = u.company_id
				LEFT JOIN company gc ON gc.id = r.company_id
				WHERE r.role = 'ADMIN' AND u.is_active = 1
				  AND ((r.company_id IS NULL AND uc.group_id = ?) OR gc.group_id = ?)""", Long.class, groupId, groupId);
	}

	/**
	 * A user's contact details and organisation, for notifications.
	 */
	public record UserContact(long id, String email, String displayName, String preferredLanguage, long companyId,
			long groupId, Long departmentId, boolean active) {
	}

	/**
	 * A holder of a role in a company: {@code grantDepartmentId} restricts the grant to one department; when it is
	 * null a department-level role applies to the holder's own department.
	 */
	public record RoleHolder(long userId, String displayName, Long grantDepartmentId, Long userDepartmentId) {

		public Long effectiveDepartmentId() {
			return grantDepartmentId != null ? grantDepartmentId : userDepartmentId;
		}

	}

	/**
	 * Loads the full acting-user view, or empty when the account does not exist.
	 */
	public Optional<CurrentUser> loadCurrentUser(long userId) {
		List<RoleGrant> grants = jdbc.query("SELECT role, company_id, department_id FROM user_role WHERE user_id = ?",
				(rs, i) -> {
					Long company = rs.getObject("company_id", Long.class);
					Long department = rs.getObject("department_id", Long.class);
					// Unknown role codes (roles of later sprints) are ignored rather than failing the sign-in.
					return Role.parse(rs.getString("role")).map(role -> new RoleGrant(role, company, department)).orElse(null);
				}, userId)
			.stream()
			.filter(java.util.Objects::nonNull)
			.toList();
		return jdbc.query("""
				SELECT u.id, u.email, u.display_name, u.company_id, c.group_id, u.department_id, u.site_id,
				       u.preferred_language, u.is_demo, u.is_active, u.credentials_version,
				       ISNULL(c2.must_change_password, 0) AS must_change_password
				FROM user_account u
				JOIN company c ON c.id = u.company_id
				LEFT JOIN user_credential c2 ON c2.user_id = u.id
				WHERE u.id = ? AND u.is_active = 1""",
				(rs, i) -> new CurrentUser(rs.getLong("id"), rs.getString("email"), rs.getString("display_name"),
						rs.getLong("company_id"), rs.getLong("group_id"), rs.getObject("department_id", Long.class),
						rs.getObject("site_id", Long.class), rs.getString("preferred_language"),
						rs.getBoolean("is_demo"), rs.getInt("credentials_version"),
						rs.getBoolean("must_change_password"), grants),
				userId).stream().findFirst();
	}

	/** Active GLOBAL_ADMIN accounts (for last-admin protection). */
	public int countActiveGlobalAdmins() {
		Integer count = jdbc.queryForObject("""
				SELECT COUNT(DISTINCT u.id) FROM user_account u
				JOIN user_role r ON r.user_id = u.id AND r.role = 'GLOBAL_ADMIN'
				WHERE u.is_active = 1""", Integer.class);
		return count == null ? 0 : count;
	}

	public boolean isActiveGlobalAdmin(long userId) {
		Integer count = jdbc.queryForObject("""
				SELECT COUNT(*) FROM user_account u
				JOIN user_role r ON r.user_id = u.id AND r.role = 'GLOBAL_ADMIN'
				WHERE u.id = ? AND u.is_active = 1""", Integer.class, userId);
		return count != null && count > 0;
	}

}
