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
				       u.preferred_language, u.is_demo, u.is_active
				FROM user_account u JOIN company c ON c.id = u.company_id
				WHERE u.id = ? AND u.is_active = 1""", (rs, i) -> new CurrentUser(rs.getLong("id"), rs.getString("email"),
				rs.getString("display_name"), rs.getLong("company_id"), rs.getLong("group_id"),
				rs.getObject("department_id", Long.class), rs.getObject("site_id", Long.class),
				rs.getString("preferred_language"), rs.getBoolean("is_demo"), grants), userId).stream().findFirst();
	}

}
