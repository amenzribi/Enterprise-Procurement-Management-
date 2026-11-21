package com.epi.procurement.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.epi.procurement.platform.persistence.UtcTimes;

@Repository
class MessagingStore {

	private final JdbcTemplate jdbc;

	MessagingStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	record ContextRow(long contextId, String contextType, long groupId, long buyingCompanyId, long supplierId,
			long requestId, String businessStatus) {
	}

	record ConversationRow(long id, long groupId, long buyingCompanyId, long supplierId, String contextType,
			long contextId, long requestId, String status, long createdBy, Instant createdAt, Instant closedAt,
			int version) {
	}

	record ContactRow(long id, long supplierId, long userId, String email, String displayName, boolean active,
			Instant invitedAt, Instant revokedAt) {
	}

	Optional<ContextRow> findRfqContext(long rfqId, long supplierId) {
		List<ContextRow> rows = jdbc.query("""
				SELECT r.id AS context_id, 'RFQ' AS context_type, r.group_id, r.buying_company_id, rs.supplier_id,
				       r.request_id, r.status AS business_status
				FROM rfq r
				JOIN rfq_supplier rs ON rs.rfq_id = r.id AND rs.supplier_id = ?
				WHERE r.id = ?""", CONTEXT, supplierId, rfqId);
		return rows.stream().findFirst();
	}

	Optional<ContextRow> findPoContext(long poId, long supplierId) {
		List<ContextRow> rows = jdbc.query("""
				SELECT p.id AS context_id, 'PO' AS context_type, p.group_id, p.buying_company_id, p.supplier_id,
				       p.request_id, p.status AS business_status
				FROM purchase_order p
				WHERE p.id = ? AND p.supplier_id = ?""", CONTEXT, poId, supplierId);
		return rows.stream().findFirst();
	}

	boolean isAssignedBuyer(long requestId, long userId) {
		Integer n = jdbc.queryForObject("""
				SELECT COUNT(*) FROM buyer_assignment
				WHERE request_id = ? AND buyer_user_id = ? AND status IN ('ASSIGNED', 'ACCEPTED')""", Integer.class,
				requestId, userId);
		return n != null && n > 0;
	}

	Optional<ConversationRow> findConversation(long id) {
		return jdbc.query("""
				SELECT id, group_id, buying_company_id, supplier_id, context_type, context_id, request_id, status,
				       created_by_user_id, created_at, closed_at, version
				FROM conversation WHERE id = ?""", CONVERSATION, id).stream().findFirst();
	}

	Optional<ConversationRow> findByContext(String contextType, long contextId, long supplierId) {
		return jdbc.query("""
				SELECT id, group_id, buying_company_id, supplier_id, context_type, context_id, request_id, status,
				       created_by_user_id, created_at, closed_at, version
				FROM conversation WHERE context_type = ? AND context_id = ? AND supplier_id = ?""", CONVERSATION,
				contextType, contextId, supplierId).stream().findFirst();
	}

	long insertConversation(ContextRow ctx, long actorId, Instant now, boolean closed) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_conversation", Long.class);
		jdbc.update("""
				INSERT INTO conversation (id, group_id, buying_company_id, supplier_id, context_type, context_id,
				                          request_id, status, created_by_user_id, created_at, closed_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, ctx.groupId(), ctx.buyingCompanyId(), ctx.supplierId(),
				ctx.contextType(), ctx.contextId(), ctx.requestId(), closed ? "CLOSED" : "OPEN", actorId,
				UtcTimes.db(now), closed ? UtcTimes.db(now) : null);
		return id;
	}

	void ensureParticipant(long conversationId, long userId, String kind) {
		Integer n = jdbc.queryForObject(
				"SELECT COUNT(*) FROM conversation_participant WHERE conversation_id = ? AND user_id = ?",
				Integer.class, conversationId, userId);
		if (n != null && n > 0) {
			return;
		}
		jdbc.update("""
				INSERT INTO conversation_participant (conversation_id, user_id, participant_kind)
				VALUES (?, ?, ?)""", conversationId, userId, kind);
	}

	List<MessagingViews.ConversationSummary> listForBuyer(long buyingCompanyId, Long supplierId) {
		String sql = """
				SELECT c.id, c.context_type, c.context_id, c.supplier_id, s.code, s.legal_name, c.status, c.request_id,
				       c.buying_company_id, c.created_at,
				       (SELECT MAX(m.created_at) FROM conversation_message m WHERE m.conversation_id = c.id) AS last_at
				FROM conversation c
				JOIN supplier s ON s.id = c.supplier_id
				WHERE c.buying_company_id = ?"""
				+ (supplierId == null ? "" : " AND c.supplier_id = ?")
				+ " ORDER BY COALESCE((SELECT MAX(m2.id) FROM conversation_message m2 WHERE m2.conversation_id = c.id), c.id) DESC";
		Object[] args = supplierId == null ? new Object[] { buyingCompanyId } : new Object[] { buyingCompanyId, supplierId };
		return jdbc.query(sql, (rs, i) -> new MessagingViews.ConversationSummary(rs.getLong("id"),
				rs.getString("context_type"), rs.getLong("context_id"), rs.getLong("supplier_id"), rs.getString("code"),
				rs.getString("legal_name"), rs.getString("status"), rs.getLong("request_id"),
				rs.getLong("buying_company_id"), 0, instant(rs.getTimestamp("created_at")),
				instant(rs.getTimestamp("last_at"))), args);
	}

	List<MessagingViews.ConversationSummary> listForSupplierContact(long userId) {
		return jdbc.query("""
				SELECT c.id, c.context_type, c.context_id, c.supplier_id, s.code, s.legal_name, c.status, c.request_id,
				       c.buying_company_id, c.created_at,
				       (SELECT MAX(m.created_at) FROM conversation_message m
				          WHERE m.conversation_id = c.id AND m.audience = 'EXTERNAL') AS last_at
				FROM conversation c
				JOIN supplier s ON s.id = c.supplier_id
				JOIN supplier_contact sc ON sc.supplier_id = c.supplier_id AND sc.user_id = ? AND sc.is_active = 1
				       AND sc.revoked_at IS NULL
				ORDER BY COALESCE((SELECT MAX(m2.id) FROM conversation_message m2
				          WHERE m2.conversation_id = c.id AND m2.audience = 'EXTERNAL'), c.id) DESC""",
				(rs, i) -> new MessagingViews.ConversationSummary(rs.getLong("id"), rs.getString("context_type"),
						rs.getLong("context_id"), rs.getLong("supplier_id"), rs.getString("code"),
						rs.getString("legal_name"), rs.getString("status"), rs.getLong("request_id"),
						rs.getLong("buying_company_id"), 0, instant(rs.getTimestamp("created_at")),
						instant(rs.getTimestamp("last_at"))),
				userId);
	}

	record SupplierRef(long id, String code, String name) {
	}

	Optional<SupplierRef> findSupplier(long supplierId) {
		return jdbc.query("SELECT id, code, legal_name FROM supplier WHERE id = ?",
				(rs, i) -> new SupplierRef(rs.getLong(1), rs.getString(2), rs.getString(3)), supplierId).stream()
			.findFirst();
	}

	/** Same visibility rules as the supplier register (group + SHARED / owning / linked company). */
	Optional<SupplierRef> findVisibleSupplier(long supplierId, long groupId, long companyId) {
		return jdbc.query("""
				SELECT s.id, s.code, s.legal_name FROM supplier s
				WHERE s.id = ? AND s.group_id = ?
				  AND (s.visibility = 'SHARED' OR s.owning_company_id = ?
				       OR EXISTS (SELECT 1 FROM supplier_company sc
				                  WHERE sc.supplier_id = s.id AND sc.company_id = ?))""",
				(rs, i) -> new SupplierRef(rs.getLong(1), rs.getString(2), rs.getString(3)), supplierId, groupId,
				companyId, companyId).stream().findFirst();
	}

	boolean userHasNonSupplierContactRoles(long userId) {
		Integer n = jdbc.queryForObject(
				"SELECT COUNT(*) FROM user_role WHERE user_id = ? AND role <> 'SUPPLIER_CONTACT'", Integer.class,
				userId);
		return n != null && n > 0;
	}

	int countUnread(long conversationId, long userId, boolean supplierView) {
		Long lastRead = jdbc.query("""
				SELECT last_read_message_id FROM conversation_participant
				WHERE conversation_id = ? AND user_id = ?""", rs -> rs.next() ? rs.getObject(1, Long.class) : null,
				conversationId, userId);
		long after = lastRead == null ? 0L : lastRead;
		String audienceClause = supplierView ? " AND audience = 'EXTERNAL'" : "";
		Integer n = jdbc.queryForObject(
				"SELECT COUNT(*) FROM conversation_message WHERE conversation_id = ? AND id > ?" + audienceClause,
				Integer.class, conversationId, after);
		return n == null ? 0 : n;
	}

	void markRead(long conversationId, long userId, long messageId) {
		jdbc.update("""
				UPDATE conversation_participant SET last_read_message_id =
				  CASE WHEN last_read_message_id IS NULL OR last_read_message_id < ? THEN ? ELSE last_read_message_id END
				WHERE conversation_id = ? AND user_id = ?""", messageId, messageId, conversationId, userId);
	}

	Optional<MessagingViews.MessageView> findMessageByClientId(long conversationId, String clientMessageId) {
		if (clientMessageId == null || clientMessageId.isBlank()) {
			return Optional.empty();
		}
		return jdbc.query("""
				SELECT m.id, m.audience, m.body_text, m.sender_user_id, u.display_name, m.created_at
				FROM conversation_message m
				JOIN user_account u ON u.id = m.sender_user_id
				WHERE m.conversation_id = ? AND m.client_message_id = ?""", MESSAGE, conversationId, clientMessageId)
			.stream()
			.findFirst();
	}

	long insertMessage(long conversationId, String audience, String body, long senderId, String clientMessageId,
			Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_conversation_message", Long.class);
		jdbc.update("""
				INSERT INTO conversation_message (id, conversation_id, audience, body_text, sender_user_id,
				                                  client_message_id, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)""", id, conversationId, audience, body, senderId,
				blankToNull(clientMessageId), UtcTimes.db(now));
		return id;
	}

	List<MessagingViews.MessageView> listMessages(long conversationId, Long beforeId, int limit, boolean externalOnly) {
		StringBuilder sql = new StringBuilder("""
				SELECT m.id, m.audience, m.body_text, m.sender_user_id, u.display_name, m.created_at
				FROM conversation_message m
				JOIN user_account u ON u.id = m.sender_user_id
				WHERE m.conversation_id = ?""");
		java.util.ArrayList<Object> args = new java.util.ArrayList<>();
		args.add(conversationId);
		if (beforeId != null) {
			sql.append(" AND m.id < ?");
			args.add(beforeId);
		}
		if (externalOnly) {
			sql.append(" AND m.audience = 'EXTERNAL'");
		}
		sql.append(" ORDER BY m.id DESC OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY");
		args.add(limit + 1);
		return jdbc.query(sql.toString(), MESSAGE, args.toArray());
	}

	Optional<ContactRow> findContact(long contactId) {
		return jdbc.query("""
				SELECT sc.id, sc.supplier_id, sc.user_id, u.email, sc.display_label, sc.is_active, sc.invited_at,
				       sc.revoked_at
				FROM supplier_contact sc
				JOIN user_account u ON u.id = sc.user_id
				WHERE sc.id = ?""", CONTACT, contactId).stream().findFirst();
	}

	Optional<ContactRow> findActiveContact(long supplierId, long userId) {
		return jdbc.query("""
				SELECT sc.id, sc.supplier_id, sc.user_id, u.email, sc.display_label, sc.is_active, sc.invited_at,
				       sc.revoked_at
				FROM supplier_contact sc
				JOIN user_account u ON u.id = sc.user_id
				WHERE sc.supplier_id = ? AND sc.user_id = ? AND sc.is_active = 1 AND sc.revoked_at IS NULL""", CONTACT,
				supplierId, userId).stream().findFirst();
	}

	List<ContactRow> listContacts(long supplierId) {
		return jdbc.query("""
				SELECT sc.id, sc.supplier_id, sc.user_id, u.email, sc.display_label, sc.is_active, sc.invited_at,
				       sc.revoked_at
				FROM supplier_contact sc
				JOIN user_account u ON u.id = sc.user_id
				WHERE sc.supplier_id = ?
				ORDER BY sc.id""", CONTACT, supplierId);
	}

	long insertContact(long supplierId, long userId, String label, Long invitedBy, Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_supplier_contact", Long.class);
		jdbc.update("""
				INSERT INTO supplier_contact (id, supplier_id, user_id, display_label, invited_by_user_id, invited_at,
				                              is_active)
				VALUES (?, ?, ?, ?, ?, ?, 1)""", id, supplierId, userId, label, invitedBy, UtcTimes.db(now));
		return id;
	}

	void revokeContact(long contactId, long actorId, Instant now) {
		jdbc.update("""
				UPDATE supplier_contact SET is_active = 0, revoked_at = ?, revoked_by_user_id = ?
				WHERE id = ?""", UtcTimes.db(now), actorId, contactId);
	}

	Optional<Long> findUserIdByEmail(String email) {
		List<Long> ids = jdbc.queryForList("SELECT id FROM user_account WHERE email = ?", Long.class, email);
		return ids.stream().findFirst();
	}

	/**
	 * @param createdBy the buyer who invites the contact; recorded so the four-eyes rule knows who created the account
	 * (review F-4)
	 */
	long insertSupplierContactUser(String email, String displayName, long companyId, long siteId, Long departmentId,
			String language, long createdBy) {
		return jdbc.queryForObject("""
				INSERT INTO user_account (email, display_name, company_id, site_id, department_id, preferred_language,
				                          is_demo, is_active, created_by_user_id)
				OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, 0, 1, ?)""", Long.class, email, displayName, companyId, siteId,
				departmentId, language, createdBy);
	}

	void grantSupplierContactRole(long userId) {
		Integer n = jdbc.queryForObject(
				"SELECT COUNT(*) FROM user_role WHERE user_id = ? AND role = 'SUPPLIER_CONTACT'", Integer.class, userId);
		if (n != null && n > 0) {
			return;
		}
		jdbc.update("INSERT INTO user_role (user_id, role, company_id, department_id) VALUES (?, 'SUPPLIER_CONTACT', NULL, NULL)",
				userId);
	}

	void bumpCredentialsVersion(long userId) {
		jdbc.update("UPDATE user_account SET credentials_version = credentials_version + 1, updated_at = SYSUTCDATETIME(), version = version + 1 WHERE id = ?",
				userId);
	}

	record CompanyHome(long companyId, long siteId, Long departmentId) {
	}

	Optional<CompanyHome> companyHome(long companyId) {
		return jdbc.query("""
				SELECT c.id,
				       (SELECT TOP 1 s.id FROM site s WHERE s.company_id = c.id ORDER BY s.id) AS site_id,
				       (SELECT TOP 1 d.id FROM department d WHERE d.company_id = c.id ORDER BY d.id) AS department_id
				FROM company c WHERE c.id = ?""", (rs, i) -> new CompanyHome(rs.getLong(1), rs.getLong(2),
				rs.getObject(3, Long.class)), companyId).stream().findFirst();
	}

	private static final RowMapper<ContextRow> CONTEXT = (rs, i) -> new ContextRow(rs.getLong("context_id"),
			rs.getString("context_type"), rs.getLong("group_id"), rs.getLong("buying_company_id"),
			rs.getLong("supplier_id"), rs.getLong("request_id"), rs.getString("business_status"));

	private static final RowMapper<ConversationRow> CONVERSATION = (rs, i) -> new ConversationRow(rs.getLong("id"),
			rs.getLong("group_id"), rs.getLong("buying_company_id"), rs.getLong("supplier_id"),
			rs.getString("context_type"), rs.getLong("context_id"), rs.getLong("request_id"), rs.getString("status"),
			rs.getLong("created_by_user_id"), instant(rs.getTimestamp("created_at")),
			instant(rs.getTimestamp("closed_at")), rs.getInt("version"));

	private static final RowMapper<MessagingViews.MessageView> MESSAGE = (rs, i) -> new MessagingViews.MessageView(
			rs.getLong("id"), rs.getString("audience"), rs.getString("body_text"), rs.getLong("sender_user_id"),
			rs.getString("display_name"), instant(rs.getTimestamp("created_at")));

	private static final RowMapper<ContactRow> CONTACT = (rs, i) -> new ContactRow(rs.getLong("id"),
			rs.getLong("supplier_id"), rs.getLong("user_id"), rs.getString("email"), rs.getString("display_label"),
			rs.getBoolean("is_active"), instant(rs.getTimestamp("invited_at")),
			instant(rs.getTimestamp("revoked_at")));

	private static Instant instant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
