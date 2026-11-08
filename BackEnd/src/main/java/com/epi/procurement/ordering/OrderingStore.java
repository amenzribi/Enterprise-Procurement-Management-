package com.epi.procurement.ordering;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.epi.procurement.platform.persistence.UtcTimes;

@Repository
class OrderingStore {

	private final JdbcTemplate jdbc;

	OrderingStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	Optional<AwardRow> findAwardByRequest(long requestId) {
		return jdbc.query("SELECT * FROM award WHERE request_id = ?", AWARD, requestId).stream().findFirst();
	}

	Optional<AwardRow> findAward(long id) {
		return jdbc.query("SELECT * FROM award WHERE id = ?", AWARD, id).stream().findFirst();
	}

	List<AwardLineRow> awardLines(long awardId) {
		return jdbc.query("SELECT * FROM award_line WHERE award_id = ? ORDER BY line_no", AWARD_LINE, awardId);
	}

	Optional<QuoteSnap> findSubmittedQuote(long quotationId) {
		return jdbc.query("""
				SELECT q.id, q.rfq_id, q.supplier_id, q.status, q.currency, q.merchandise_amount, q.freight_amount,
				       q.duty_amount, q.other_amount, q.payment_terms, q.incoterm, s.legal_name, s.is_active, r.request_id
				FROM quotation q
				JOIN supplier s ON s.id = q.supplier_id
				JOIN rfq r ON r.id = q.rfq_id
				WHERE q.id = ?""", QUOTE, quotationId).stream().findFirst();
	}

	List<QuoteLineSnap> quoteLines(long quotationId) {
		return jdbc.query("""
				SELECT ql.rfq_line_id, ql.quantity, ql.unit_price, ql.line_amount, rl.line_no, rl.description, rl.unit,
				       rl.request_line_id
				FROM quotation_line ql
				JOIN rfq_line rl ON rl.id = ql.rfq_line_id
				WHERE ql.quotation_id = ?
				ORDER BY rl.line_no""", QUOTE_LINE, quotationId);
	}

	long insertAward(long groupId, long requestId, long rfqId, long quotationId, long supplierId, long buyingCompanyId,
			long requestingCompanyId, long chargedCompanyId, String currency, BigDecimal merchandise, BigDecimal freight,
			BigDecimal duty, BigDecimal other, BigDecimal landed, String reason, long actorId, Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_award", Long.class);
		jdbc.update("""
				INSERT INTO award (id, group_id, request_id, rfq_id, quotation_id, supplier_id, buying_company_id,
				                   requesting_company_id, charged_company_id, currency, merchandise_amount, freight_amount,
				                   duty_amount, other_amount, landed_amount, reason, awarded_by_user_id, awarded_at, version)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)""", id, groupId, requestId, rfqId,
				quotationId, supplierId, buyingCompanyId, requestingCompanyId, chargedCompanyId, currency, merchandise,
				freight, duty, other, landed, reason, actorId, UtcTimes.db(now));
		return id;
	}

	void insertAwardLine(long awardId, long rfqLineId, long requestLineId, short lineNo, String description,
			BigDecimal quantity, String unit, BigDecimal unitPrice, BigDecimal lineAmount) {
		jdbc.update("""
				INSERT INTO award_line (id, award_id, rfq_line_id, request_line_id, line_no, description, quantity, unit,
				                        unit_price, line_amount)
				VALUES (NEXT VALUE FOR seq_award_line, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", awardId, rfqLineId, requestLineId,
				lineNo, description, quantity, unit, unitPrice, lineAmount);
	}

	void upsertPriceMemory(long groupId, long supplierId, Long categoryId, String descriptionNorm, BigDecimal unitPrice,
			String currency, String unit, long awardId, Instant awardedAt) {
		int updated = jdbc.update("""
				UPDATE price_memory SET unit_price = ?, category_id = ?, award_id = ?, awarded_at = ?
				WHERE group_id = ? AND supplier_id = ? AND description_norm = ? AND currency = ? AND unit = ?""", unitPrice,
				categoryId, awardId, UtcTimes.db(awardedAt), groupId, supplierId, descriptionNorm, currency, unit);
		if (updated == 0) {
			jdbc.update("""
					INSERT INTO price_memory (group_id, supplier_id, category_id, description_norm, unit_price, currency, unit,
					                          award_id, awarded_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", groupId, supplierId, categoryId, descriptionNorm, unitPrice,
					currency, unit, awardId, UtcTimes.db(awardedAt));
		}
	}

	boolean assignmentAccepted(long requestId, long buyerUserId) {
		Integer n = jdbc.queryForObject("""
				SELECT COUNT(*) FROM buyer_assignment WHERE request_id = ? AND buyer_user_id = ? AND status = 'ACCEPTED'""",
				Integer.class, requestId, buyerUserId);
		return n != null && n > 0;
	}

	Optional<PoRow> findPoByRequest(long requestId) {
		return jdbc.query("SELECT * FROM purchase_order WHERE request_id = ?", PO, requestId).stream().findFirst();
	}

	Optional<PoRow> findPo(long id) {
		return jdbc.query("SELECT * FROM purchase_order WHERE id = ?", PO, id).stream().findFirst();
	}

	List<PoLineRow> poLines(long poId) {
		return jdbc.query("SELECT * FROM purchase_order_line WHERE purchase_order_id = ? ORDER BY line_no", PO_LINE, poId);
	}

	List<MilestoneRow> milestones(long poId) {
		return jdbc.query("SELECT * FROM po_payment_milestone WHERE purchase_order_id = ? ORDER BY id", MILESTONE, poId);
	}

	long insertPo(long groupId, long awardId, long requestId, String number, String status, long buyingCompanyId,
			long requestingCompanyId, long chargedCompanyId, long supplierId, String currency, BigDecimal merchandise,
			BigDecimal freight, BigDecimal duty, BigDecimal other, BigDecimal total, String paymentTerms, String incoterm,
			LocalDate requiredDate, String deliveryAddress, long createdBy, Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_purchase_order", Long.class);
		jdbc.update("""
				INSERT INTO purchase_order (id, group_id, award_id, request_id, number, status, buying_company_id,
				                            requesting_company_id, charged_company_id, supplier_id, currency,
				                            merchandise_amount, freight_amount, duty_amount, other_amount, total_amount,
				                            payment_terms, incoterm, required_date, delivery_address, created_by_user_id,
				                            created_at, version)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)""", id, groupId, awardId,
				requestId, number, status, buyingCompanyId, requestingCompanyId, chargedCompanyId, supplierId, currency,
				merchandise, freight, duty, other, total, paymentTerms, incoterm, requiredDate, deliveryAddress, createdBy,
				UtcTimes.db(now));
		return id;
	}

	void insertPoLine(long poId, long awardLineId, long requestLineId, short lineNo, String description,
			BigDecimal quantity, String unit, BigDecimal unitPrice, BigDecimal lineAmount) {
		jdbc.update("""
				INSERT INTO purchase_order_line (id, purchase_order_id, award_line_id, request_line_id, line_no, description,
				                                 quantity, unit, unit_price, line_amount)
				VALUES (NEXT VALUE FOR seq_purchase_order_line, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", poId, awardLineId,
				requestLineId, lineNo, description, quantity, unit, unitPrice, lineAmount);
	}

	void insertMilestone(long poId, String label, LocalDate dueDate, BigDecimal amount, String currency, String status) {
		jdbc.update("""
				INSERT INTO po_payment_milestone (id, purchase_order_id, label, due_date, amount, currency, status)
				VALUES (NEXT VALUE FOR seq_po_milestone, ?, ?, ?, ?, ?, ?)""", poId, label, dueDate, amount, currency,
				status);
	}

	int issuePo(long poId, long actorId, Instant now) {
		return jdbc.update("""
				UPDATE purchase_order SET status = 'ISSUED', issued_at = ?, issued_by_user_id = ?,
				version = version + 1 WHERE id = ? AND status = 'DRAFT'""", UtcTimes.db(now), actorId, poId);
	}

	int confirmPo(long poId, String note, long actorId, Instant now) {
		return jdbc.update("""
				UPDATE purchase_order SET status = 'CONFIRMED', confirmed_at = ?, confirmed_by_user_id = ?,
				confirmation_note = ?, version = version + 1 WHERE id = ? AND status = 'ISSUED'""", UtcTimes.db(now),
				actorId, note, poId);
	}

	/** @return rows updated (0 when the PO is no longer a draft) */
	int updateDraftPo(long poId, String paymentTerms, String incoterm, LocalDate requiredDate, String deliveryAddress) {
		return jdbc.update("""
				UPDATE purchase_order SET payment_terms = ?, incoterm = ?, required_date = ?, delivery_address = ?,
				version = version + 1 WHERE id = ? AND status = 'DRAFT'""", paymentTerms, incoterm, requiredDate,
				deliveryAddress, poId);
	}

	/**
	 * Replaces milestones only while the PO is still DRAFT (guards issue races). Deletes are joined to
	 * {@code status = 'DRAFT'} so an issued PO cannot lose its milestones.
	 *
	 * @return false when the PO is not a draft
	 */
	boolean replaceMilestones(long poId, List<MilestoneBody> milestones) {
		jdbc.update("""
				DELETE m FROM po_payment_milestone m
				INNER JOIN purchase_order po ON po.id = m.purchase_order_id AND po.status = 'DRAFT'
				WHERE m.purchase_order_id = ?""", poId);
		Integer draft = jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE id = ? AND status = 'DRAFT'",
				Integer.class, poId);
		if (draft == null || draft == 0) {
			return false;
		}
		if (milestones == null) {
			return true;
		}
		for (MilestoneBody m : milestones) {
			insertMilestone(poId, m.label(), m.dueDate(), m.amount(), m.currency(),
					m.status() == null ? "PLANNED" : m.status());
		}
		return true;
	}

	boolean supplierActive(long supplierId) {
		Boolean active = jdbc.query("SELECT is_active FROM supplier WHERE id = ?", rs -> rs.next() && rs.getBoolean(1),
				supplierId);
		return Boolean.TRUE.equals(active);
	}

	String supplierName(long supplierId) {
		return jdbc.query("SELECT legal_name FROM supplier WHERE id = ?", rs -> rs.next() ? rs.getString(1) : "",
				supplierId);
	}

	record AwardRow(long id, long groupId, long requestId, long rfqId, long quotationId, long supplierId,
			long buyingCompanyId, long requestingCompanyId, long chargedCompanyId, String currency,
			BigDecimal merchandise, BigDecimal freight, BigDecimal duty, BigDecimal other, BigDecimal landed, String reason,
			Instant awardedAt, int version) {
	}

	record AwardLineRow(long id, long awardId, long rfqLineId, long requestLineId, short lineNo, String description,
			BigDecimal quantity, String unit, BigDecimal unitPrice, BigDecimal lineAmount) {
	}

	record QuoteSnap(long id, long rfqId, long supplierId, String status, String currency, BigDecimal merchandise,
			BigDecimal freight, BigDecimal duty, BigDecimal other, String paymentTerms, String incoterm, String supplierName,
			boolean supplierActive, long requestId) {
	}

	record QuoteLineSnap(long rfqLineId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineAmount, short lineNo,
			String description, String unit, long requestLineId) {
	}

	record PoRow(long id, long awardId, long requestId, String number, String status, long buyingCompanyId,
			long requestingCompanyId, long chargedCompanyId, long supplierId, String currency, BigDecimal merchandise,
			BigDecimal freight, BigDecimal duty, BigDecimal other, BigDecimal total, String paymentTerms, String incoterm,
			LocalDate requiredDate, String deliveryAddress, Instant createdAt, Instant issuedAt, Instant confirmedAt,
			String confirmationNote, int version) {
	}

	record PoLineRow(long id, long poId, long awardLineId, long requestLineId, short lineNo, String description,
			BigDecimal quantity, String unit, BigDecimal unitPrice, BigDecimal lineAmount) {
	}

	record MilestoneRow(long id, long poId, String label, LocalDate dueDate, BigDecimal amount, String currency,
			String status) {
	}

	private static final org.springframework.jdbc.core.RowMapper<AwardRow> AWARD = (rs, i) -> new AwardRow(rs.getLong("id"),
			rs.getLong("group_id"), rs.getLong("request_id"), rs.getLong("rfq_id"), rs.getLong("quotation_id"),
			rs.getLong("supplier_id"), rs.getLong("buying_company_id"), rs.getLong("requesting_company_id"),
			rs.getLong("charged_company_id"), rs.getString("currency"), rs.getBigDecimal("merchandise_amount"),
			rs.getBigDecimal("freight_amount"), rs.getBigDecimal("duty_amount"), rs.getBigDecimal("other_amount"),
			rs.getBigDecimal("landed_amount"), rs.getString("reason"), UtcTimes.instant(rs, "awarded_at"),
			rs.getInt("version"));

	private static final org.springframework.jdbc.core.RowMapper<AwardLineRow> AWARD_LINE = (rs,
			i) -> new AwardLineRow(rs.getLong("id"), rs.getLong("award_id"), rs.getLong("rfq_line_id"),
					rs.getLong("request_line_id"), rs.getShort("line_no"), rs.getString("description"),
					rs.getBigDecimal("quantity"), rs.getString("unit"), rs.getBigDecimal("unit_price"),
					rs.getBigDecimal("line_amount"));

	private static final org.springframework.jdbc.core.RowMapper<QuoteSnap> QUOTE = (rs, i) -> new QuoteSnap(rs.getLong("id"),
			rs.getLong("rfq_id"), rs.getLong("supplier_id"), rs.getString("status"), rs.getString("currency"),
			rs.getBigDecimal("merchandise_amount"), rs.getBigDecimal("freight_amount"), rs.getBigDecimal("duty_amount"),
			rs.getBigDecimal("other_amount"), rs.getString("payment_terms"), rs.getString("incoterm"),
			rs.getString("legal_name"), rs.getBoolean("is_active"), rs.getLong("request_id"));

	private static final org.springframework.jdbc.core.RowMapper<QuoteLineSnap> QUOTE_LINE = (rs,
			i) -> new QuoteLineSnap(rs.getLong("rfq_line_id"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
					rs.getBigDecimal("line_amount"), rs.getShort("line_no"), rs.getString("description"),
					rs.getString("unit"), rs.getLong("request_line_id"));

	private static final org.springframework.jdbc.core.RowMapper<PoRow> PO = (rs, i) -> new PoRow(rs.getLong("id"),
			rs.getLong("award_id"), rs.getLong("request_id"), rs.getString("number"), rs.getString("status"),
			rs.getLong("buying_company_id"), rs.getLong("requesting_company_id"), rs.getLong("charged_company_id"),
			rs.getLong("supplier_id"), rs.getString("currency"), rs.getBigDecimal("merchandise_amount"),
			rs.getBigDecimal("freight_amount"), rs.getBigDecimal("duty_amount"), rs.getBigDecimal("other_amount"),
			rs.getBigDecimal("total_amount"), rs.getString("payment_terms"), rs.getString("incoterm"),
			rs.getObject("required_date", LocalDate.class), rs.getString("delivery_address"),
			UtcTimes.instant(rs, "created_at"), UtcTimes.instant(rs, "issued_at"), UtcTimes.instant(rs, "confirmed_at"),
			rs.getString("confirmation_note"), rs.getInt("version"));

	private static final org.springframework.jdbc.core.RowMapper<PoLineRow> PO_LINE = (rs, i) -> new PoLineRow(
			rs.getLong("id"), rs.getLong("purchase_order_id"), rs.getLong("award_line_id"), rs.getLong("request_line_id"),
			rs.getShort("line_no"), rs.getString("description"), rs.getBigDecimal("quantity"), rs.getString("unit"),
			rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_amount"));

	private static final org.springframework.jdbc.core.RowMapper<MilestoneRow> MILESTONE = (rs,
			i) -> new MilestoneRow(rs.getLong("id"), rs.getLong("purchase_order_id"), rs.getString("label"),
					rs.getObject("due_date", LocalDate.class), rs.getBigDecimal("amount"), rs.getString("currency"),
					rs.getString("status"));
}
