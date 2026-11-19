package com.epi.procurement.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only search over the whole audit trail, for the global administrators' Audit Center (Sprint 2, P1.3). Filters
 * are exact matches on indexed columns; results are newest first, paged by a keyset cursor (time, then id), so a
 * page costs the same however deep it is and new events never shift a page already read.
 */
@Service
public class AuditSearch {

	/** Largest page a caller may ask for. */
	public static final int MAX_PAGE = 200;

	private final JdbcTemplate jdbc;

	AuditSearch(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	/**
	 * @param from inclusive, or null
	 * @param to exclusive, or null
	 * @param companyId a company's events, or null for every company and the group-wide chain
	 */
	public record Filter(Instant from, Instant to, Long companyId, Long actorUserId, String action, String entityType,
			String entityId) {
	}

	/** Position after the last row of a page: the next page starts strictly before it. */
	public record Cursor(Instant occurredAt, long id) {
	}

	public record Entry(long id, String chainKey, long chainSeq, Instant occurredAt, Long actorUserId, String actorLabel,
			Long companyId, String companyName, String entityType, String entityId, String action, String fromStatus,
			String toStatus, String comment, String payloadJson) {
	}

	public record Page(List<Entry> items, Cursor next) {
	}

	public Page search(Filter filter, Cursor before, int size) {
		int limit = Math.max(1, Math.min(size, MAX_PAGE));
		StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
		List<Object> args = new ArrayList<>();
		if (filter.from() != null) {
			sql.append(" AND e.occurred_at >= ?");
			args.add(timestamp(filter.from()));
		}
		if (filter.to() != null) {
			sql.append(" AND e.occurred_at < ?");
			args.add(timestamp(filter.to()));
		}
		if (filter.companyId() != null) {
			sql.append(" AND e.company_id = ?");
			args.add(filter.companyId());
		}
		if (filter.actorUserId() != null) {
			sql.append(" AND e.actor_user_id = ?");
			args.add(filter.actorUserId());
		}
		if (filter.action() != null) {
			sql.append(" AND e.action = ?");
			args.add(filter.action());
		}
		if (filter.entityType() != null) {
			sql.append(" AND e.entity_type = ?");
			args.add(filter.entityType());
		}
		if (filter.entityId() != null) {
			sql.append(" AND e.entity_id = ?");
			args.add(filter.entityId());
		}
		if (before != null) {
			sql.append(" AND (e.occurred_at < ? OR (e.occurred_at = ? AND e.id < ?))");
			Timestamp at = timestamp(before.occurredAt());
			args.add(at);
			args.add(at);
			args.add(before.id());
		}
		// One row more than the page tells whether another page exists.
		sql.append(" ORDER BY e.occurred_at DESC, e.id DESC OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY");
		args.add(limit + 1);
		List<Entry> rows = jdbc.query(sql.toString(), (rs, i) -> entry(rs), args.toArray());
		if (rows.size() <= limit) {
			return new Page(rows, null);
		}
		List<Entry> page = rows.subList(0, limit);
		Entry last = page.get(page.size() - 1);
		return new Page(List.copyOf(page), new Cursor(last.occurredAt(), last.id()));
	}

	public Optional<Entry> find(long id) {
		return jdbc.query(SELECT + " WHERE e.id = ?", (rs, i) -> entry(rs), id).stream().findFirst();
	}

	private static final String SELECT = """
			SELECT e.id, e.chain_key, e.chain_seq, e.occurred_at, e.actor_user_id, e.actor_label, e.company_id,
			       c.legal_name AS company_name, e.entity_type, e.entity_id, e.action, e.from_status, e.to_status, e.comment,
			       e.payload_json
			FROM audit_event e LEFT JOIN company c ON c.id = e.company_id""";

	private static Timestamp timestamp(Instant instant) {
		return Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
	}

	private static Entry entry(ResultSet rs) throws SQLException {
		return new Entry(rs.getLong("id"), rs.getString("chain_key"), rs.getLong("chain_seq"),
				rs.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
				rs.getObject("actor_user_id", Long.class), rs.getString("actor_label"), rs.getObject("company_id", Long.class),
				rs.getString("company_name"), rs.getString("entity_type"), rs.getString("entity_id"), rs.getString("action"),
				rs.getString("from_status"), rs.getString("to_status"), rs.getString("comment"), rs.getString("payload_json"));
	}

}
