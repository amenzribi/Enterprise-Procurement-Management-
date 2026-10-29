package com.epi.procurement.purchasing;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.epi.procurement.platform.persistence.UtcTimes;

@Repository
class BuyerAssignmentStore {

	private final JdbcTemplate jdbc;

	BuyerAssignmentStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	long insert(long requestId, long buyingCompanyId, long buyerUserId, long assignedBy, Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_buyer_assignment", Long.class);
		jdbc.update("""
				INSERT INTO buyer_assignment (id, request_id, buying_company_id, buyer_user_id, assigned_by_user_id,
				                              status, assigned_at)
				VALUES (?, ?, ?, ?, ?, 'ASSIGNED', ?)""", id, requestId, buyingCompanyId, buyerUserId, assignedBy,
				UtcTimes.db(now));
		return id;
	}

	void supersedeOpen(long requestId, Instant now) {
		jdbc.update("""
				UPDATE buyer_assignment SET status = 'SUPERSEDED', decided_at = ?
				WHERE request_id = ? AND status IN ('ASSIGNED', 'ACCEPTED')""", UtcTimes.db(now), requestId);
	}

	void decide(long id, String status, String reason, Instant now) {
		jdbc.update("UPDATE buyer_assignment SET status = ?, reason = ?, decided_at = ? WHERE id = ?", status, reason,
				UtcTimes.db(now), id);
	}

	Optional<Row> current(long requestId) {
		return jdbc.query("""
				SELECT TOP (1) id, request_id, buying_company_id, buyer_user_id, assigned_by_user_id, status, reason,
				               assigned_at, decided_at
				FROM buyer_assignment WHERE request_id = ? AND status IN ('ASSIGNED', 'ACCEPTED')
				ORDER BY id DESC""", ROW, requestId).stream().findFirst();
	}

	List<Row> history(long requestId) {
		return jdbc.query("""
				SELECT id, request_id, buying_company_id, buyer_user_id, assigned_by_user_id, status, reason,
				       assigned_at, decided_at
				FROM buyer_assignment WHERE request_id = ? ORDER BY id DESC""", ROW, requestId);
	}

	Map<Long, Integer> openWorkload(List<Long> buyerIds) {
		if (buyerIds.isEmpty()) {
			return Map.of();
		}
		StringBuilder in = new StringBuilder();
		for (int i = 0; i < buyerIds.size(); i++) {
			if (i > 0) {
				in.append(',');
			}
			in.append(buyerIds.get(i));
		}
		Map<Long, Integer> counts = new java.util.HashMap<>();
		buyerIds.forEach(id -> counts.put(id, 0));
		jdbc.query("SELECT buyer_user_id, COUNT(*) AS n FROM buyer_assignment WHERE status IN ('ASSIGNED', 'ACCEPTED') "
				+ "AND buyer_user_id IN (" + in + ") GROUP BY buyer_user_id", rs -> {
					counts.put(rs.getLong("buyer_user_id"), rs.getInt("n"));
				});
		return counts;
	}

	record Row(long id, long requestId, long buyingCompanyId, long buyerUserId, long assignedByUserId, String status,
			String reason, Instant assignedAt, Instant decidedAt) {
	}

	private static final RowMapper<Row> ROW = (rs, i) -> new Row(rs.getLong("id"), rs.getLong("request_id"),
			rs.getLong("buying_company_id"), rs.getLong("buyer_user_id"), rs.getLong("assigned_by_user_id"),
			rs.getString("status"), rs.getString("reason"), 			UtcTimes.instant(rs, "assigned_at"), UtcTimes.instant(rs, "decided_at"));
}
