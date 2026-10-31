package com.epi.procurement.assistant;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.epi.procurement.platform.persistence.UtcTimes;

@Repository
class AssistantStore {

	private final JdbcTemplate jdbc;

	AssistantStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	long createThread(long userId, long companyId, String title, Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_assistant_thread", Long.class);
		jdbc.update("""
				INSERT INTO assistant_thread (id, user_id, company_id, title, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?)""", id, userId, companyId, title, UtcTimes.db(now), UtcTimes.db(now));
		return id;
	}

	Optional<Long> findThreadOwner(long threadId) {
		List<Long> ids = jdbc.queryForList("SELECT user_id FROM assistant_thread WHERE id = ?", Long.class, threadId);
		return ids.stream().findFirst();
	}

	void touchThread(long threadId, Instant now) {
		jdbc.update("UPDATE assistant_thread SET updated_at = ? WHERE id = ?", UtcTimes.db(now), threadId);
	}

	long insertTurn(long threadId, String role, String body, String mode, String sourcesJson, String draftJson,
			Integer durationMs, Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_assistant_turn", Long.class);
		jdbc.update("""
				INSERT INTO assistant_turn (id, thread_id, role, body_text, mode, sources_json, draft_json, duration_ms,
				                            created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, threadId, role, body, mode, sourcesJson, draftJson,
				durationMs, UtcTimes.db(now));
		return id;
	}

	List<AssistantViews.ThreadSummary> listThreads(long userId) {
		return jdbc.query("""
				SELECT TOP 20 id, title, updated_at FROM assistant_thread
				WHERE user_id = ? ORDER BY updated_at DESC""", (rs, i) -> {
			Timestamp ts = rs.getTimestamp("updated_at");
			return new AssistantViews.ThreadSummary(rs.getLong("id"), rs.getString("title"),
					ts == null ? null : ts.toInstant());
		}, userId);
	}

}
