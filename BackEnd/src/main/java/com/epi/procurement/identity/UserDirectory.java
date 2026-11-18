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

	private final com.epi.procurement.audit.AuditLog auditLog;

	UserDirectory(DataSource dataSource, com.epi.procurement.audit.AuditLog auditLog) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.named = new NamedParameterJdbcTemplate(jdbc);
		this.auditLog = auditLog;
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
		return searchUsers(groupId, companyId, role, text, null);
	}

	/**
	 * @param scopeCompanyIds when not null, only users of these companies (home company or a role there) and holders
	 * of a group-wide role are returned: what a company administrator may see (ADR-007, audit S-7)
	 */
	public List<UserListing> searchUsers(long groupId, Long companyId, String role, String text,
			java.util.Collection<Long> scopeCompanyIds) {
		if (scopeCompanyIds != null && scopeCompanyIds.isEmpty()) {
			return List.of();
		}
		StringBuilder sql = new StringBuilder("""
				SELECT TOP (200) u.id, u.display_name, u.email, u.company_id FROM user_account u
				JOIN company c ON c.id = u.company_id WHERE c.group_id = :group AND u.is_active = 1""");
		Map<String, Object> params = new java.util.HashMap<>();
		params.put("group", groupId);
		if (companyId != null) {
			sql.append(" AND u.company_id = :company");
			params.put("company", companyId);
		}
		if (scopeCompanyIds != null) {
			sql.append(" AND (u.company_id IN (:scope) OR EXISTS (SELECT 1 FROM user_role sr WHERE sr.user_id = u.id "
					+ "AND ((sr.company_id IS NULL AND sr.role = 'GROUP_MANAGEMENT') OR sr.company_id IN (:scope))))");
			params.put("scope", scopeCompanyIds);
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
	/**
	 * Whether {@code administratorId} created the account {@code userId}. Basis of the four-eyes rule (review R-1,
	 * R-2, RR-3): the creator of an account never gives it a sensitive role, never names it approver or delegate.
	 * The creator is stored on the account since V24; for older accounts it is the actor of the account's
	 * {@code USER_CREATED} audit event. False when neither exists (demo seed, first administrator).
	 */
	public boolean createdBy(long userId, long administratorId) {
		return creationChain(userId).contains(administratorId);
	}

	/** Depth of the creation chain followed: deeper chains need that many deceived second administrators. */
	static final int CREATION_CHAIN_DEPTH = 5;

	/**
	 * The account's creator, that creator's creator, and so on (review of F-1/F-2, finding 3): whoever created B, who
	 * created C, created C too, so A cannot activate or empower C either. Bounded, and safe against cycles.
	 */
	public java.util.Set<Long> creationChain(long userId) {
		java.util.Set<Long> chain = new java.util.LinkedHashSet<>();
		long current = userId;
		for (int depth = 0; depth < CREATION_CHAIN_DEPTH; depth++) {
			Optional<Long> creator = creatorOf(current);
			if (creator.isEmpty() || creator.get() == userId || !chain.add(creator.get())) {
				break;
			}
			current = creator.get();
		}
		return chain;
	}

	/** Who created the account: the stored creator (V24), else the actor of its {@code USER_CREATED} event. */
	public Optional<Long> creatorOf(long userId) {
		// queryForList: the column is NULL for accounts created before V24, and Optional cannot hold null.
		List<Long> rows = jdbc.queryForList("SELECT created_by_user_id FROM user_account WHERE id = ?", Long.class,
				userId);
		Long stored = rows.isEmpty() ? null : rows.get(0);
		if (stored != null) {
			return Optional.of(stored);
		}
		return auditLog.firstActor("USER_ACCOUNT", Long.toString(userId), "USER_CREATED");
	}

	/**
	 * Whether the account is an external supplier contact. Such an account takes no part in internal decisions: it
	 * is never an approver, a delegate or the holder of an internal role (review R-1, RR-2, RR-5).
	 */
	public boolean isSupplierContact(long userId) {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE user_id = ? AND role = ?",
				Integer.class, userId, Role.SUPPLIER_CONTACT.name());
		return count != null && count > 0;
	}

	/** Users whose home company is one of the given companies. */
	public List<Long> userIdsOfCompanies(java.util.Collection<Long> companyIds) {
		if (companyIds.isEmpty()) {
			return List.of();
		}
		return named.queryForList("SELECT id FROM user_account WHERE company_id IN (:companies)",
				Map.of("companies", companyIds), Long.class);
	}

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
