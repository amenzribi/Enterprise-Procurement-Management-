package com.epi.procurement.audit;

import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends events to the hash-chained audit trail and verifies its integrity. There is one chain per company
 * plus a global chain for events without a company (DR-03).
 * <p>
 * Database protection (V1, V3): events cannot be updated, deleted or truncated, and a chain head cannot be deleted,
 * re-keyed or rewound. Known limit, accepted under DR-04: a principal allowed to alter the schema (db_owner) can
 * drop those triggers and constraints. Sprint 1 separates the runtime and migration principals; Sprint 9 anchors
 * chain heads outside the database.
 */
@Service
public class AuditLog {

	private final JdbcTemplate jdbc;

	private final Clock clock;

	AuditLog(DataSource dataSource, ObjectProvider<Clock> clock) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	/**
	 * Appends an event inside the caller's transaction, so the audit row commits or rolls back with the
	 * business change it describes. The chain head row is locked (UPDLOCK, HOLDLOCK) until commit, so
	 * concurrent appends to the same chain are serialised and the chain cannot fork.
	 * @return the id of the new audit event
	 */
	@Transactional
	public long record(AuditRecord record) {
		String chainKey = AuditHasher.chainKey(record.companyId());
		// UPDLOCK + HOLDLOCK takes an update (range) lock even when the head row does not exist yet, so a
		// concurrent first append for the same chain waits here instead of inserting a duplicate head.
		ChainHead head = jdbc.query(
				"SELECT last_seq, last_hash FROM audit_chain_head WITH (UPDLOCK, HOLDLOCK) WHERE chain_key = ?",
				rs -> rs.next() ? new ChainHead(rs.getLong("last_seq"), rs.getBytes("last_hash")) : null, chainKey);
		if (head == null) {
			jdbc.update("INSERT INTO audit_chain_head (chain_key, last_seq, last_hash) VALUES (?, 0, NULL)", chainKey);
			head = new ChainHead(0, null);
		}

		long chainSeq = head.lastSeq() + 1;
		Instant occurredAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
		byte[] hash = AuditHasher.hash(chainKey, chainSeq, head.lastHash(), occurredAt, record);

		Long id = jdbc.queryForObject("""
				INSERT INTO audit_event (chain_key, chain_seq, occurred_at, actor_user_id, actor_label, company_id,
				                         entity_type, entity_id, action, from_status, to_status, comment,
				                         payload_json, previous_hash, hash)
				OUTPUT INSERTED.id
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", Long.class, chainKey, chainSeq,
				LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC), typed(Types.BIGINT, record.actorUserId()),
				record.actorLabel(), typed(Types.BIGINT, record.companyId()), record.entityType(), record.entityId(),
				record.action(), typed(Types.VARCHAR, record.fromStatus()), typed(Types.VARCHAR, record.toStatus()),
				typed(Types.NVARCHAR, record.comment()), typed(Types.NVARCHAR, record.payloadJson()),
				typed(Types.BINARY, head.lastHash()), typed(Types.BINARY, hash));
		jdbc.update("UPDATE audit_chain_head SET last_seq = ?, last_hash = ?, last_event_id = ?, "
				+ "updated_at = SYSUTCDATETIME() WHERE chain_key = ?", chainSeq, typed(Types.BINARY, hash), id, chainKey);
		return id;
	}

	/**
	 * Verifies every chain, including any chain whose events exist without a head.
	 */
	@Transactional(readOnly = true)
	public AuditChainVerification verifyAll() {
		List<String> chainKeys = jdbc.queryForList(
				"SELECT chain_key FROM audit_chain_head UNION SELECT chain_key FROM audit_event ORDER BY chain_key",
				String.class);
		long checked = 0;
		for (String chainKey : chainKeys) {
			AuditChainVerification result = verifyChain(chainKey);
			checked += result.eventsChecked();
			if (!result.valid()) {
				return new AuditChainVerification(false, checked, result.firstInvalidEventId());
			}
		}
		return new AuditChainVerification(true, checked, null);
	}

	/**
	 * Recomputes every hash of one chain in sequence order and reports the first event whose content, position
	 * or link is invalid. The head is read first and only events up to it are checked, so appends committed during
	 * verification cannot cause a false alarm (CR-04). A missing head, or a head that does not point at the last
	 * verified event, makes the chain invalid.
	 */
	@Transactional(readOnly = true)
	public AuditChainVerification verifyChain(String chainKey) {
		ChainHead head = jdbc.query("SELECT last_seq, last_hash FROM audit_chain_head WHERE chain_key = ?",
				rs -> rs.next() ? new ChainHead(rs.getLong(1), rs.getBytes(2)) : null, chainKey);
		if (head == null) {
			return new AuditChainVerification(false, 0, null);
		}
		ChainState state = new ChainState();
		jdbc.query("""
				SELECT id, chain_seq, occurred_at, actor_user_id, actor_label, company_id, entity_type, entity_id,
				       action, from_status, to_status, comment, payload_json, previous_hash, hash
				FROM audit_event WHERE chain_key = ? AND chain_seq <= ? ORDER BY chain_seq""", rs -> {
			if (state.firstInvalid != null) {
				return;
			}
			state.checked++;
			long chainSeq = rs.getLong("chain_seq");
			Instant occurredAt = rs.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC);
			AuditRecord record = new AuditRecord(rs.getObject("actor_user_id", Long.class), rs.getString("actor_label"),
					rs.getObject("company_id", Long.class), rs.getString("entity_type"), rs.getString("entity_id"),
					rs.getString("action"), rs.getString("from_status"), rs.getString("to_status"),
					rs.getString("comment"), rs.getString("payload_json"));
			byte[] previousHash = rs.getBytes("previous_hash");
			byte[] hash = rs.getBytes("hash");
			boolean inSequence = chainSeq == state.checked;
			boolean linked = Arrays.equals(previousHash, state.lastHash);
			boolean intact = Arrays.equals(hash,
					AuditHasher.hash(chainKey, chainSeq, previousHash, occurredAt, record));
			if (!inSequence || !linked || !intact) {
				state.firstInvalid = rs.getLong("id");
			}
			state.lastHash = hash;
		}, chainKey, head.lastSeq());
		if (state.firstInvalid == null) {
			if (head.lastSeq() != state.checked || !Arrays.equals(head.lastHash(), state.lastHash)) {
				// Events were removed from the end of the chain (or the head was altered).
				return new AuditChainVerification(false, state.checked, null);
			}
		}
		return new AuditChainVerification(state.firstInvalid == null, state.checked, state.firstInvalid);
	}

	/**
	 * Untyped nulls are sent as NVARCHAR by the driver, which SQL Server cannot convert to BINARY or BIGINT.
	 */
	private static SqlParameterValue typed(int sqlType, Object value) {
		return new SqlParameterValue(sqlType, value);
	}

	private record ChainHead(long lastSeq, byte[] lastHash) {
	}

	private static final class ChainState {

		private long checked;

		private Long firstInvalid;

		private byte[] lastHash;

	}

}
