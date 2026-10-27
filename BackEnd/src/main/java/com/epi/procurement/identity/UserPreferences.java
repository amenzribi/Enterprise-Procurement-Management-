package com.epi.procurement.identity;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserPreferences {

	private final JdbcTemplate jdbc;

	UserPreferences(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	@Transactional
	void updateLanguage(long userId, String language) {
		jdbc.update("UPDATE user_account SET preferred_language = ?, updated_at = SYSUTCDATETIME(), version = version + 1 "
				+ "WHERE id = ?", language, userId);
	}

}
