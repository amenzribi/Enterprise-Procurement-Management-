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
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.platform.persistence.TransactionLocks;

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

	private final TransactionLocks locks;

	AuditLog(DataSource dataSource, ObjectProvider<Clock> clock, TransactionLocks locks) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.clock = clock.getIfAvailable(Clock::systemUTC);
		this.locks = locks;
	}

	/**
	 * Appends an event inside the caller's transaction, so the audit row commits or rolls back with the
	 * business change it describes. The chain head row is locked (UPDLOCK) until commit, so
	 * concurrent appends to the same chain are serialised and the chain cannot fork.
	 * @return the id of the new audit event
	 */
	@Transactional
	public long record(AuditRecord record) {
		String chainKey = AuditHasher.chainKey(record.companyId());
		ChainHead head = lockHead(chainKey);
		// First event of this chain: created under an application lock (see TransactionLocks), then locked like any
		// existing row. lockHead waits for a first writer that has not committed yet; if that writer rolled back, the
		// head is gone and is created again (bounded).
		for (int attempt = 1; head == null; attempt++) {
			if (attempt > 3) {
				throw new IllegalStateException("Audit chain head could not be created: " + chainKey);
			}
			createHeadIfMissing(chainKey);
			head = lockHead(chainKey);
		}

		long chainSeq = head.lastSeq() + 1;
		Instant occurredAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
		byte[] hash = AuditHasher.hash(chainKey, chainSeq, head.lastHash(), occurredAt, record);

		// The id is drawn first and inserted explicitly: an OUTPUT clause without INTO is rejected by SQL Server as
		// soon as any trigger exists on the table (QA1-D-06).
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_audit_event", Long.class);
		jdbc.update("""
				INSERT INTO audit_event (id, chain_key, chain_seq, occurred_at, actor_user_id, actor_label, company_id,
				                         entity_type, entity_id, action, from_status, to_status, comment,
				                         payload_json, previous_hash, hash)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, chainKey, chainSeq,
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
	 * The user who performed the first {@code action} recorded for an entity, if any. Used to find who created an
	 * account before the creator was stored on the account itself (review RR-3).
	 */
	@Transactional(readOnly = true)
	public java.util.Optional<Long> firstActor(String entityType, String entityId, String action) {
		return jdbc.query("""
				SELECT TOP (1) actor_user_id FROM audit_event
				WHERE entity_type = ? AND entity_id = ? AND action = ? AND actor_user_id IS NOT NULL
				ORDER BY chain_seq""", (rs, i) -> rs.getLong(1), entityType, entityId, action).stream().findFirst();
	}

	/**
	 * Events for one business entity (newest last), for audit packs (Sprint 8).
	 */
	@Transactional(readOnly = true)
	public List<AuditEventView> listForEntity(String entityType, String entityId) {
		return jdbc.query("""
				SELECT id, occurred_at, actor_label, company_id, action, from_status, to_status, comment
				FROM audit_event WHERE entity_type = ? AND entity_id = ?
				ORDER BY occurred_at, id""", (rs, i) -> new AuditEventView(rs.getLong("id"),
				rs.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), rs.getString("actor_label"),
				rs.getObject("company_id", Long.class), rs.getString("action"), rs.getString("from_status"),
				rs.getString("to_status"), rs.getString("comment")), entityType, entityId);
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
		jdbc.query(VERIFY_ROWS + " WHERE chain_key = ? AND chain_seq <= ? ORDER BY chain_seq",
				(RowCallbackHandler) rs -> checkRow(rs, chainKey, state), chainKey, head.lastSeq());
		return finish(chainKey, head, state);
	}

	/**
	 * Same verification as {@link #verifyChain(String)}, read in batches of {@code batchSize} events, each its own
	 * short statement outside any transaction, so a long chain never holds a transaction open (the database limit is
	 * 30 s per transaction). Verifies up to the head read at the start; events appended meanwhile are left for the
	 * next run. {@code progress} receives the number of events checked after each batch.
	 */
	public AuditChainVerification verifyChainInBatches(String chainKey, int batchSize,
			java.util.function.LongConsumer progress) {
		ChainHead head = jdbc.query("SELECT last_seq, last_hash FROM audit_chain_head WHERE chain_key = ?",
				rs -> rs.next() ? new ChainHead(rs.getLong(1), rs.getBytes(2)) : null, chainKey);
		if (head == null) {
			return new AuditChainVerification(false, 0, null);
		}
		ChainState state = new ChainState();
		long after = 0;
		while (state.firstInvalid == null && after < head.lastSeq()) {
			long checkedBefore = state.checked;
			long[] lastSeq = { after };
			jdbc.query(VERIFY_ROWS + " WHERE chain_key = ? AND chain_seq > ? AND chain_seq <= ? ORDER BY chain_seq "
					+ "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY", (RowCallbackHandler) rs -> {
						checkRow(rs, chainKey, state);
						lastSeq[0] = rs.getLong("chain_seq");
					}, chainKey, after, head.lastSeq(), batchSize);
			if (state.checked == checkedBefore) {
				break;
			}
			after = lastSeq[0];
			progress.accept(state.checked);
		}
		return finish(chainKey, head, state);
	}

	/** Every chain known to the trail: those with a head, and any whose events exist without one. */
	public List<String> chainKeys() {
		return jdbc.queryForList(
				"SELECT chain_key FROM audit_chain_head UNION SELECT chain_key FROM audit_event ORDER BY chain_key",
				String.class);
	}

	private static final String VERIFY_ROWS = """
			SELECT id, chain_seq, occurred_at, actor_user_id, actor_label, company_id, entity_type, entity_id,
			       action, from_status, to_status, comment, payload_json, previous_hash, hash
			FROM audit_event""";

	private static void checkRow(java.sql.ResultSet rs, String chainKey, ChainState state) throws java.sql.SQLException {
		if (state.firstInvalid != null) {
			return;
		}
		state.checked++;
		long chainSeq = rs.getLong("chain_seq");
		Instant occurredAt = rs.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC);
		AuditRecord record = new AuditRecord(rs.getObject("actor_user_id", Long.class), rs.getString("actor_label"),
				rs.getObject("company_id", Long.class), rs.getString("entity_type"), rs.getString("entity_id"),
				rs.getString("action"), rs.getString("from_status"), rs.getString("to_status"), rs.getString("comment"),
				rs.getString("payload_json"));
		byte[] previousHash = rs.getBytes("previous_hash");
		byte[] hash = rs.getBytes("hash");
		boolean inSequence = chainSeq == state.checked;
		boolean linked = Arrays.equals(previousHash, state.lastHash);
		boolean intact = Arrays.equals(hash, AuditHasher.hash(chainKey, chainSeq, previousHash, occurredAt, record));
		if (!inSequence || !linked || !intact) {
			state.firstInvalid = rs.getLong("id");
		}
		state.lastHash = hash;
	}

	private AuditChainVerification finish(String chainKey, ChainHead head, ChainState state) {
		if (state.firstInvalid == null) {
			if (head.lastSeq() != state.checked || !Arrays.equals(head.lastHash(), state.lastHash)) {
				// Events were removed from the end of the chain (or the head was altered).
				return new AuditChainVerification(false, state.checked, null);
			}
			Long hidden = eventBeyondHead(chainKey, head.lastSeq());
			if (hidden != null) {
				return new AuditChainVerification(false, state.checked, hidden);
			}
		}
		return new AuditChainVerification(state.firstInvalid == null, state.checked, state.firstInvalid);
	}

	/**
	 * Events positioned after the chain head are hidden from verification, e.g. after the head was rewound by someone
	 * able to bypass the triggers (CR-25). Events appended concurrently are not a problem: an event commits together
	 * with its head update, so reading the events first and the head afterwards always shows such a head at or beyond
	 * them. Only events still beyond the re-read head are reported.
	 * @return the id of the first event beyond the head, or null
	 */
	private Long eventBeyondHead(String chainKey, long verifiedSeq) {
		List<long[]> beyond = jdbc.query(
				"SELECT id, chain_seq FROM audit_event WHERE chain_key = ? AND chain_seq > ? ORDER BY chain_seq",
				(rs, i) -> new long[] { rs.getLong(1), rs.getLong(2) }, chainKey, verifiedSeq);
		if (beyond.isEmpty()) {
			return null;
		}
		Long headNow = jdbc.query("SELECT last_seq FROM audit_chain_head WHERE chain_key = ?",
				rs -> rs.next() ? rs.getLong(1) : null, chainKey);
		long lastSeen = beyond.get(beyond.size() - 1)[1];
		if (headNow != null && headNow >= lastSeen) {
			return null;
		}
		return beyond.stream().filter(e -> headNow == null || e[1] > headNow).findFirst().map(e -> e[0]).orElse(null);
	}

	private ChainHead lockHead(String chainKey) {
		return jdbc.query("SELECT last_seq, last_hash FROM audit_chain_head WITH (UPDLOCK, ROWLOCK) WHERE chain_key = ?",
				rs -> rs.next() ? new ChainHead(rs.getLong("last_seq"), rs.getBytes("last_hash")) : null, chainKey);
	}

	private void createHeadIfMissing(String chainKey) {
		locks.lockExclusively("audit-chain-head:" + chainKey);
		// The existence check reads uncommitted rows (Sprint 2 stabilisation): a first writer's head, not yet committed,
		// is seen, so the next writer releases the lock at once instead of waiting for it while holding the lock (503
		// for the writers behind), and never inserts a second head under READ_COMMITTED_SNAPSHOT. The chain itself is
		// read with UPDLOCK in lockHead, which waits for committed data.
		jdbc.update("INSERT INTO audit_chain_head (chain_key, last_seq, last_hash) SELECT ?, 0, NULL "
				+ "WHERE NOT EXISTS (SELECT 1 FROM audit_chain_head WITH (READUNCOMMITTED) WHERE chain_key = ?)", chainKey,
				chainKey);
		locks.release("audit-chain-head:" + chainKey);
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
