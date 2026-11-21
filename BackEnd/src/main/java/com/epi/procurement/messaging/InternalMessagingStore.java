package com.epi.procurement.messaging;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.epi.procurement.platform.persistence.UtcTimes;

/**
 * SQL for internal messaging (V22). It holds no authorisation rule of its own except one: conversation and message
 * reads take the reading user and return only conversations in which that user is a current participant. Request
 * access is checked by {@link InternalMessagingService} on top of that.
 */
@Repository
class InternalMessagingStore {

	private final JdbcTemplate jdbc;

	private final NamedParameterJdbcTemplate named;

	InternalMessagingStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.named = new NamedParameterJdbcTemplate(jdbc);
	}

	// ---- People ---------------------------------------------------------------------------------------------------

	/** An employee as shown in the directory and on conversations. {@code roles} are the codes held in the company. */
	record Person(long id, String displayName, long companyId, Long departmentId, String departmentName, boolean active,
			boolean supplier, List<String> roles) {
	}

	/**
	 * Active employees of one company, never suppliers. Filters are optional; {@code text} matches name or e-mail,
	 * {@code department} the department name or code, {@code role} a role code held in that company.
	 */
	List<Person> searchEmployees(long companyId, long excludeUserId, String text, String department, String role,
			int limit) {
		StringBuilder sql = new StringBuilder("""
				SELECT TOP (:limit) u.id
				FROM user_account u
				LEFT JOIN department d ON d.id = u.department_id
				WHERE u.company_id = :company AND u.is_active = 1 AND u.id <> :self
				  AND NOT EXISTS (SELECT 1 FROM user_role s WHERE s.user_id = u.id AND s.role = 'SUPPLIER_CONTACT')
				""");
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit)
			.addValue("company", companyId)
			.addValue("self", excludeUserId);
		if (text != null) {
			sql.append(" AND (u.display_name LIKE :text ESCAPE '\\' OR u.email LIKE :text ESCAPE '\\')");
			params.addValue("text", "%" + like(text) + "%");
		}
		if (department != null) {
			sql.append(" AND (d.name LIKE :dept ESCAPE '\\' OR d.code LIKE :dept ESCAPE '\\')");
			params.addValue("dept", "%" + like(department) + "%");
		}
		if (role != null) {
			sql.append(" AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = :role "
					+ "AND (r.company_id = :company OR r.company_id IS NULL))");
			params.addValue("role", role);
		}
		sql.append(" ORDER BY u.display_name, u.id");
		List<Long> ids = named.queryForList(sql.toString(), params, Long.class);
		Map<Long, Person> people = people(ids);
		return ids.stream().map(people::get).filter(p -> p != null).toList();
	}

	Optional<Person> person(long userId) {
		return Optional.ofNullable(people(List.of(userId)).get(userId));
	}

	/** People by id, with their department and the role codes they hold in their own company or group-wide. */
	Map<Long, Person> people(Collection<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		MapSqlParameterSource params = new MapSqlParameterSource("ids", userIds);
		Map<Long, List<String>> roles = new java.util.HashMap<>();
		named.query("""
				SELECT r.user_id, r.role FROM user_role r JOIN user_account u ON u.id = r.user_id
				WHERE r.user_id IN (:ids) AND (r.company_id = u.company_id OR r.company_id IS NULL)
				ORDER BY r.role""", params, rs -> {
			roles.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
		});
		Map<Long, Person> result = new java.util.LinkedHashMap<>();
		named.query("""
				SELECT u.id, u.display_name, u.company_id, u.department_id, d.name AS department_name, u.is_active
				FROM user_account u LEFT JOIN department d ON d.id = u.department_id
				WHERE u.id IN (:ids)""", params, rs -> {
			long id = rs.getLong("id");
			Long dept = rs.getLong("department_id");
			if (rs.wasNull()) {
				dept = null;
			}
			List<String> held = roles.getOrDefault(id, List.of());
			result.put(id, new Person(id, rs.getString("display_name"), rs.getLong("company_id"), dept,
					rs.getString("department_name"), rs.getBoolean("is_active"), held.contains("SUPPLIER_CONTACT"),
					held.stream().distinct().toList()));
		});
		return result;
	}

	// ---- Current approvers ----------------------------------------------------------------------------------------

	/** A pending approval task's assignee; {@code onBehalfOfUserId} is set when the task was delegated to them. */
	record CurrentApprover(long userId, Long onBehalfOfUserId, int stageNo, String stageName) {
	}

	/**
	 * Who has to decide on the request now: the assignee of every pending task of the active round, the same rule as
	 * the record's "next actors" (approval module, {@code ApprovalQueries}). Delegation is already applied there: an
	 * absence or a delegation puts the delegate in {@code assignee_user_id} and keeps the original approver in
	 * {@code original_user_id}. Read here rather than through the approval module, which depends on notification,
	 * which depends on messaging (a Java dependency would be a module cycle).
	 */
	List<CurrentApprover> currentApprovers(long requestId) {
		return jdbc.query("""
				SELECT t.assignee_user_id, t.original_user_id, t.stage_no, t.stage_name
				FROM approval_task t
				JOIN approval_round r ON r.id = t.round_id
				WHERE t.request_id = ? AND r.status = 'ACTIVE' AND t.status = 'PENDING'
				ORDER BY t.stage_no, t.id""", (rs, i) -> {
			long assignee = rs.getLong("assignee_user_id");
			long original = rs.getLong("original_user_id");
			Long onBehalfOf = rs.wasNull() || original == assignee ? null : original;
			return new CurrentApprover(assignee, onBehalfOf, rs.getInt("stage_no"), rs.getString("stage_name"));
		}, requestId);
	}

	// ---- Conversations --------------------------------------------------------------------------------------------

	record Conversation(long id, long companyId, String kind, Long requestId, Long userLowId, Long userHighId,
			long createdByUserId, Instant createdAt, Long lastMessageId, Instant lastMessageAt) {

		boolean isDirect() {
			return "DIRECT".equals(kind);
		}

	}

	private static final String CONVERSATION_COLUMNS = """
			c.id, c.company_id, c.kind, c.request_id, c.user_low_id, c.user_high_id, c.created_by_user_id, c.created_at,
			c.last_message_id, c.last_message_at""" + " ";

	Optional<Conversation> findConversation(long id) {
		return jdbc.query("SELECT " + CONVERSATION_COLUMNS + " FROM internal_conversation c WHERE c.id = ?",
				this::conversation, id).stream().findFirst();
	}

	/**
	 * Locks the conversation row until the transaction ends. Every transaction that writes messages or participants
	 * takes it first, so the order is always conversation, then message, then audit chain head. Without it, two sends
	 * with the same client id deadlock: the second insert holds a shared lock on this row (foreign key check) while it
	 * waits for the first one's unique key, and the first one needs this row to record its last message.
	 */
	void lockConversation(long id) {
		jdbc.queryForList("SELECT id FROM internal_conversation WITH (UPDLOCK, ROWLOCK) WHERE id = ?", Long.class, id);
	}

	Optional<Conversation> findDirect(long companyId, long low, long high) {
		return jdbc.query("SELECT " + CONVERSATION_COLUMNS + """
				 FROM internal_conversation c
				WHERE c.kind = 'DIRECT' AND c.company_id = ? AND c.user_low_id = ? AND c.user_high_id = ?""",
				this::conversation, companyId, low, high).stream().findFirst();
	}

	Optional<Conversation> findRequestDiscussion(long requestId) {
		return jdbc.query("SELECT " + CONVERSATION_COLUMNS
				+ " FROM internal_conversation c WHERE c.kind = 'REQUEST' AND c.request_id = ?", this::conversation,
				requestId).stream().findFirst();
	}

	/** Inserts a conversation; a concurrent insert of the same pair or request raises DuplicateKeyException. */
	long insertConversation(long companyId, String kind, Long requestId, Long low, Long high, long createdBy,
			Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_internal_conversation", Long.class);
		jdbc.update("""
				INSERT INTO internal_conversation (id, company_id, kind, request_id, user_low_id, user_high_id,
				    created_by_user_id, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", id, companyId, kind, requestId, low, high, createdBy,
				UtcTimes.db(now));
		return id;
	}

	/** One row per conversation the user currently takes part in, newest activity first. */
	record ConversationListRow(Conversation conversation, Long lastReadMessageId, int unread) {
	}

	List<ConversationListRow> listForUser(long userId, int limit) {
		return jdbc.query("SELECT TOP (?) " + CONVERSATION_COLUMNS + """
				, p.last_read_message_id,
				  (SELECT COUNT(*) FROM internal_message m
				   WHERE m.conversation_id = c.id AND m.sender_user_id <> p.user_id
				     AND (p.last_read_message_id IS NULL OR m.id > p.last_read_message_id)) AS unread
				FROM internal_participant p
				JOIN internal_conversation c ON c.id = p.conversation_id
				WHERE p.user_id = ? AND p.removed_at IS NULL
				ORDER BY COALESCE(c.last_message_at, c.created_at) DESC, c.id DESC""", (rs, i) -> {
			Long lastRead = rs.getLong("last_read_message_id");
			if (rs.wasNull()) {
				lastRead = null;
			}
			return new ConversationListRow(conversation(rs, i), lastRead, rs.getInt("unread"));
		}, limit, userId);
	}

	// ---- Participants ---------------------------------------------------------------------------------------------

	record Participant(long userId, Long addedByUserId, Instant joinedAt, Long lastReadMessageId) {
	}

	List<Participant> participants(long conversationId) {
		return jdbc.query("""
				SELECT user_id, added_by_user_id, joined_at, last_read_message_id FROM internal_participant
				WHERE conversation_id = ? AND removed_at IS NULL ORDER BY joined_at, id""", (rs, i) -> {
			Long addedBy = rs.getLong("added_by_user_id");
			if (rs.wasNull()) {
				addedBy = null;
			}
			Long lastRead = rs.getLong("last_read_message_id");
			if (rs.wasNull()) {
				lastRead = null;
			}
			return new Participant(rs.getLong("user_id"), addedBy, UtcTimes.instant(rs, "joined_at"), lastRead);
		}, conversationId);
	}

	boolean isParticipant(long conversationId, long userId) {
		Integer n = jdbc.queryForObject("""
				SELECT COUNT(*) FROM internal_participant
				WHERE conversation_id = ? AND user_id = ? AND removed_at IS NULL""", Integer.class, conversationId,
				userId);
		return n != null && n > 0;
	}

	/**
	 * Adds the user; returns false when they already are a current participant. A participant removed earlier
	 * ({@code removed_at} set) keeps their row, so adding them again restores that row rather than inserting.
	 */
	boolean addParticipant(long conversationId, long userId, Long addedBy, Instant now) {
		if (isParticipant(conversationId, userId)) {
			return false;
		}
		int restored = jdbc.update("""
				UPDATE internal_participant SET removed_at = NULL, added_by_user_id = ?, joined_at = ?
				WHERE conversation_id = ? AND user_id = ? AND removed_at IS NOT NULL""", addedBy, UtcTimes.db(now),
				conversationId, userId);
		if (restored == 1) {
			return true;
		}
		try {
			jdbc.update("""
					INSERT INTO internal_participant (conversation_id, user_id, added_by_user_id, joined_at)
					VALUES (?, ?, ?, ?)""", conversationId, userId, addedBy, UtcTimes.db(now));
			return true;
		}
		catch (org.springframework.dao.DuplicateKeyException ex) {
			// Added by someone else at the same moment.
			return false;
		}
	}

	/**
	 * Moves the read marker forward only: a late or concurrent request with a smaller id never moves it back. The id
	 * must belong to the conversation, so a marker can never point past messages the user has not been sent.
	 */
	void markRead(long conversationId, long userId, long messageId) {
		jdbc.update("""
				UPDATE p SET last_read_message_id = ?
				FROM internal_participant p
				WHERE p.conversation_id = ? AND p.user_id = ? AND p.removed_at IS NULL
				  AND (p.last_read_message_id IS NULL OR p.last_read_message_id < ?)
				  AND EXISTS (SELECT 1 FROM internal_message m WHERE m.id = ? AND m.conversation_id = p.conversation_id)""",
				messageId, conversationId, userId, messageId, messageId);
	}

	/** Unread messages across every conversation of the user, per conversation (for the total and the list). */
	Map<Long, Integer> unreadByConversation(long userId) {
		return jdbc.query("""
				SELECT p.conversation_id, COUNT(m.id) AS unread
				FROM internal_participant p
				JOIN internal_message m ON m.conversation_id = p.conversation_id
				WHERE p.user_id = ? AND p.removed_at IS NULL AND m.sender_user_id <> p.user_id
				  AND (p.last_read_message_id IS NULL OR m.id > p.last_read_message_id)
				GROUP BY p.conversation_id""", rs -> {
			Map<Long, Integer> out = new java.util.HashMap<>();
			while (rs.next()) {
				out.put(rs.getLong(1), rs.getInt(2));
			}
			return out;
		}, userId);
	}

	// ---- Messages -------------------------------------------------------------------------------------------------

	record Message(long id, long conversationId, String kind, long senderUserId, String bodyText, Long linkedRequestId,
			String clientMessageId, Instant createdAt) {
	}

	private static final String MESSAGE_COLUMNS = """
			id, conversation_id, kind, sender_user_id, body_text, linked_request_id, client_message_id, created_at""" + " ";

	/** Inserts a message and moves the conversation's "last message"; a repeated client id raises DuplicateKey. */
	Message insertMessage(long conversationId, String kind, long senderUserId, String bodyText, Long linkedRequestId,
			String clientMessageId, Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_internal_message", Long.class);
		jdbc.update("""
				INSERT INTO internal_message (id, conversation_id, kind, sender_user_id, body_text, linked_request_id,
				    client_message_id, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", id, conversationId, kind, senderUserId, bodyText, linkedRequestId,
				clientMessageId, UtcTimes.db(now));
		jdbc.update("""
				UPDATE internal_conversation SET last_message_id = ?, last_message_at = ?
				WHERE id = ? AND (last_message_id IS NULL OR last_message_id < ?)""", id, UtcTimes.db(now),
				conversationId, id);
		return new Message(id, conversationId, kind, senderUserId, bodyText, linkedRequestId, clientMessageId, now);
	}

	Optional<Message> findByClientId(long conversationId, long senderUserId, String clientMessageId) {
		return jdbc.query("SELECT " + MESSAGE_COLUMNS + """
				 FROM internal_message WHERE conversation_id = ? AND sender_user_id = ? AND client_message_id = ?""",
				this::message, conversationId, senderUserId, clientMessageId).stream().findFirst();
	}

	Optional<Message> findMessage(long id) {
		return jdbc.query("SELECT " + MESSAGE_COLUMNS + " FROM internal_message WHERE id = ?", this::message, id)
			.stream()
			.findFirst();
	}

	/**
	 * A page of a conversation in ascending id order. {@code beforeId}: older history (the newest {@code size} below
	 * it). {@code afterId}: everything newer, for resynchronising after a reconnect. Neither: the newest page.
	 */
	List<Message> page(long conversationId, Long beforeId, Long afterId, int size) {
		if (afterId != null) {
			return jdbc.query("SELECT TOP (?) " + MESSAGE_COLUMNS
					+ " FROM internal_message WHERE conversation_id = ? AND id > ? ORDER BY id ASC", this::message,
					size, conversationId, afterId);
		}
		List<Message> newestFirst = beforeId == null
				? jdbc.query("SELECT TOP (?) " + MESSAGE_COLUMNS
						+ " FROM internal_message WHERE conversation_id = ? ORDER BY id DESC", this::message, size,
						conversationId)
				: jdbc.query("SELECT TOP (?) " + MESSAGE_COLUMNS
						+ " FROM internal_message WHERE conversation_id = ? AND id < ? ORDER BY id DESC",
						this::message, size, conversationId, beforeId);
		List<Message> ascending = new ArrayList<>(newestFirst);
		java.util.Collections.reverse(ascending);
		return ascending;
	}

	boolean hasOlder(long conversationId, long id) {
		Integer n = jdbc.queryForObject(
				"SELECT CASE WHEN EXISTS (SELECT 1 FROM internal_message WHERE conversation_id = ? AND id < ?) THEN 1 ELSE 0 END",
				Integer.class, conversationId, id);
		return n != null && n == 1;
	}

	Map<Long, Message> lastMessages(Collection<Long> messageIds) {
		if (messageIds.isEmpty()) {
			return Map.of();
		}
		return named
			.query("SELECT " + MESSAGE_COLUMNS + " FROM internal_message WHERE id IN (:ids)",
					new MapSqlParameterSource("ids", messageIds), this::message)
			.stream()
			.collect(Collectors.toMap(Message::id, m -> m));
	}

	// ---- Mapping --------------------------------------------------------------------------------------------------

	private Conversation conversation(ResultSet rs, int row) throws SQLException {
		return new Conversation(rs.getLong("id"), rs.getLong("company_id"), rs.getString("kind"),
				nullableLong(rs, "request_id"), nullableLong(rs, "user_low_id"), nullableLong(rs, "user_high_id"),
				rs.getLong("created_by_user_id"), UtcTimes.instant(rs, "created_at"),
				nullableLong(rs, "last_message_id"), rs.getTimestamp("last_message_at") == null ? null
						: UtcTimes.instant(rs, "last_message_at"));
	}

	private Message message(ResultSet rs, int row) throws SQLException {
		return new Message(rs.getLong("id"), rs.getLong("conversation_id"), rs.getString("kind"),
				rs.getLong("sender_user_id"), rs.getString("body_text"), nullableLong(rs, "linked_request_id"),
				rs.getString("client_message_id"), UtcTimes.instant(rs, "created_at"));
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static String like(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
	}

}
