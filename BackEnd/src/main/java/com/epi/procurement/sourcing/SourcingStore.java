package com.epi.procurement.sourcing;

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
class SourcingStore {

	private final JdbcTemplate jdbc;

	SourcingStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	Optional<RfqRow> findByRequest(long requestId) {
		return jdbc.query("SELECT * FROM rfq WHERE request_id = ?", RFQ, requestId).stream().findFirst();
	}

	Optional<RfqRow> findRfq(long id) {
		return jdbc.query("SELECT * FROM rfq WHERE id = ?", RFQ, id).stream().findFirst();
	}

	long insertRfq(long groupId, long requestId, long buyingCompanyId, String kind, String title, String currency,
			long createdBy, Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_rfq", Long.class);
		jdbc.update("""
				INSERT INTO rfq (id, group_id, request_id, buying_company_id, kind, status, title, comparison_currency,
				                 created_by_user_id, created_at, version)
				VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, 0)""", id, groupId, requestId, buyingCompanyId, kind, title,
				currency, createdBy, UtcTimes.db(now));
		return id;
	}

	void insertLine(long rfqId, long requestLineId, short lineNo, String description, BigDecimal quantity, String unit) {
		jdbc.update("""
				INSERT INTO rfq_line (id, rfq_id, request_line_id, line_no, description, quantity, unit)
				VALUES (NEXT VALUE FOR seq_rfq_line, ?, ?, ?, ?, ?, ?)""", rfqId, requestLineId, lineNo, description,
				quantity, unit);
	}

	void insertSupplier(long rfqId, long supplierId, Instant now) {
		jdbc.update("""
				INSERT INTO rfq_supplier (id, rfq_id, supplier_id, invited_at)
				VALUES (NEXT VALUE FOR seq_rfq_supplier, ?, ?, ?)""", rfqId, supplierId, UtcTimes.db(now));
	}

	List<LineRow> lines(long rfqId) {
		return jdbc.query("""
				SELECT id, rfq_id, request_line_id, line_no, description, quantity, unit FROM rfq_line
				WHERE rfq_id = ? ORDER BY line_no""", LINE, rfqId);
	}

	List<SupplierRow> suppliers(long rfqId) {
		return jdbc.query("""
				SELECT rs.id, rs.rfq_id, rs.supplier_id, s.code, s.legal_name, rs.invited_at
				FROM rfq_supplier rs JOIN supplier s ON s.id = rs.supplier_id
				WHERE rs.rfq_id = ? ORDER BY s.legal_name""", SUPPLIER, rfqId);
	}

	Optional<SupplierRow> supplier(long rfqSupplierId) {
		return jdbc.query("""
				SELECT rs.id, rs.rfq_id, rs.supplier_id, s.code, s.legal_name, rs.invited_at
				FROM rfq_supplier rs JOIN supplier s ON s.id = rs.supplier_id WHERE rs.id = ?""", SUPPLIER, rfqSupplierId)
			.stream()
			.findFirst();
	}

	void insertToken(byte[] hash, long rfqSupplierId, Instant expiresAt, Instant now) {
		jdbc.update("""
				INSERT INTO quote_link_token (id, token_hash, rfq_supplier_id, expires_at, created_at)
				VALUES (NEXT VALUE FOR seq_quote_link_token, ?, ?, ?, ?)""", hash, rfqSupplierId, UtcTimes.db(expiresAt),
				UtcTimes.db(now));
	}

	Optional<TokenRow> findToken(byte[] hash) {
		return jdbc.query("""
				SELECT id, token_hash, rfq_supplier_id, expires_at, used_at FROM quote_link_token WHERE token_hash = ?""",
				TOKEN, hash).stream().findFirst();
	}

	void markTokenUsed(long id, Instant now) {
		jdbc.update("UPDATE quote_link_token SET used_at = ? WHERE id = ? AND used_at IS NULL", UtcTimes.db(now), id);
	}

	/** S4SL-09 / S9-AC-03: expire every still-valid token for this invitation before issuing a replacement. */
	int expireActiveTokens(long rfqSupplierId, Instant now) {
		return jdbc.update("""
				UPDATE quote_link_token SET expires_at = ? WHERE rfq_supplier_id = ? AND expires_at > ?""",
				UtcTimes.db(now), rfqSupplierId, UtcTimes.db(now));
	}

	Optional<QuoteRow> findQuoteByRfqSupplier(long rfqSupplierId) {
		return jdbc.query("SELECT * FROM quotation WHERE rfq_supplier_id = ?", QUOTE, rfqSupplierId).stream().findFirst();
	}

	List<QuoteRow> quotes(long rfqId) {
		return jdbc.query("SELECT * FROM quotation WHERE rfq_id = ? AND status = 'SUBMITTED' ORDER BY id", QUOTE, rfqId);
	}

	long upsertQuote(Long existingId, long rfqId, long rfqSupplierId, long supplierId, String currency,
			LocalDate validUntil, Integer leadTimeDays, String paymentTerms, String incoterm, BigDecimal freight,
			BigDecimal duty, BigDecimal other, BigDecimal merchandise, String notes, String source, Long actorUserId,
			Instant now) {
		if (existingId != null) {
			jdbc.update("""
					UPDATE quotation SET currency = ?, valid_until = ?, lead_time_days = ?, payment_terms = ?, incoterm = ?,
					freight_amount = ?, duty_amount = ?, other_amount = ?, merchandise_amount = ?, notes = ?, status = 'SUBMITTED',
					source = ?, submitted_at = ?, submitted_by_user_id = ?, version = version + 1 WHERE id = ?""", currency,
					validUntil, leadTimeDays, paymentTerms, incoterm, freight, duty, other, merchandise, notes, source,
					UtcTimes.db(now), actorUserId, existingId);
			jdbc.update("DELETE FROM quotation_line WHERE quotation_id = ?", existingId);
			return existingId;
		}
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_quotation", Long.class);
		jdbc.update("""
				INSERT INTO quotation (id, rfq_id, rfq_supplier_id, supplier_id, status, currency, valid_until, lead_time_days,
				                       payment_terms, incoterm, freight_amount, duty_amount, other_amount, merchandise_amount,
				                       notes, source, submitted_at, submitted_by_user_id, version)
				VALUES (?, ?, ?, ?, 'SUBMITTED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)""", id, rfqId, rfqSupplierId,
				supplierId, currency, validUntil, leadTimeDays, paymentTerms, incoterm, freight, duty, other, merchandise,
				notes, source, UtcTimes.db(now), actorUserId);
		return id;
	}

	void insertQuoteLine(long quotationId, long rfqLineId, BigDecimal quantity, BigDecimal unitPrice,
			BigDecimal lineAmount) {
		jdbc.update("""
				INSERT INTO quotation_line (id, quotation_id, rfq_line_id, quantity, unit_price, line_amount)
				VALUES (NEXT VALUE FOR seq_quotation_line, ?, ?, ?, ?, ?)""", quotationId, rfqLineId, quantity, unitPrice,
				lineAmount);
	}

	List<QuoteLineRow> quoteLines(long quotationId) {
		return jdbc.query("""
				SELECT id, quotation_id, rfq_line_id, quantity, unit_price, line_amount FROM quotation_line
				WHERE quotation_id = ?""", QUOTE_LINE, quotationId);
	}

	void closeRfq(long rfqId, Instant now) {
		jdbc.update("UPDATE rfq SET status = 'CLOSED', closed_at = ?, version = version + 1 WHERE id = ?", UtcTimes.db(now),
				rfqId);
	}

	boolean assignmentAccepted(long requestId, long buyerUserId) {
		Integer n = jdbc.queryForObject("""
				SELECT COUNT(*) FROM buyer_assignment WHERE request_id = ? AND buyer_user_id = ? AND status = 'ACCEPTED'""",
				Integer.class, requestId, buyerUserId);
		return n != null && n > 0;
	}

	boolean supplierVisible(long groupId, long companyId, long supplierId) {
		Integer n = jdbc.queryForObject("""
				SELECT COUNT(*) FROM supplier s WHERE s.id = ? AND s.group_id = ? AND s.is_active = 1
				AND (s.visibility = 'SHARED' OR s.owning_company_id = ?
				     OR EXISTS (SELECT 1 FROM supplier_company sc WHERE sc.supplier_id = s.id AND sc.company_id = ?))""",
				Integer.class, supplierId, groupId, companyId, companyId);
		return n != null && n > 0;
	}

	record RfqRow(long id, long groupId, long requestId, long buyingCompanyId, String kind, String status, String title,
			String comparisonCurrency, long createdByUserId, Instant createdAt, Instant closedAt, int version) {
	}

	record LineRow(long id, long rfqId, long requestLineId, short lineNo, String description, BigDecimal quantity,
			String unit) {
	}

	record SupplierRow(long id, long rfqId, long supplierId, String code, String name, Instant invitedAt) {
	}

	record TokenRow(long id, byte[] hash, long rfqSupplierId, Instant expiresAt, Instant usedAt) {
	}

	record QuoteRow(long id, long rfqId, long rfqSupplierId, long supplierId, String status, String currency,
			LocalDate validUntil, Integer leadTimeDays, String paymentTerms, String incoterm, BigDecimal freight,
			BigDecimal duty, BigDecimal other, BigDecimal merchandise, String notes, String source, Instant submittedAt,
			Long submittedByUserId, int version) {
	}

	record QuoteLineRow(long id, long quotationId, long rfqLineId, BigDecimal quantity, BigDecimal unitPrice,
			BigDecimal lineAmount) {
	}

	private static final org.springframework.jdbc.core.RowMapper<RfqRow> RFQ = (rs, i) -> new RfqRow(rs.getLong("id"),
			rs.getLong("group_id"), rs.getLong("request_id"), rs.getLong("buying_company_id"), rs.getString("kind"),
			rs.getString("status"), rs.getString("title"), rs.getString("comparison_currency"),
			rs.getLong("created_by_user_id"), UtcTimes.instant(rs, "created_at"), UtcTimes.instant(rs, "closed_at"),
			rs.getInt("version"));

	private static final org.springframework.jdbc.core.RowMapper<LineRow> LINE = (rs, i) -> new LineRow(rs.getLong("id"),
			rs.getLong("rfq_id"), rs.getLong("request_line_id"), rs.getShort("line_no"), rs.getString("description"),
			rs.getBigDecimal("quantity"), rs.getString("unit"));

	private static final org.springframework.jdbc.core.RowMapper<SupplierRow> SUPPLIER = (rs,
			i) -> new SupplierRow(rs.getLong("id"), rs.getLong("rfq_id"), rs.getLong("supplier_id"), rs.getString("code"),
					rs.getString("legal_name"), UtcTimes.instant(rs, "invited_at"));

	private static final org.springframework.jdbc.core.RowMapper<TokenRow> TOKEN = (rs, i) -> new TokenRow(rs.getLong("id"),
			rs.getBytes("token_hash"), rs.getLong("rfq_supplier_id"), UtcTimes.instant(rs, "expires_at"),
			UtcTimes.instant(rs, "used_at"));

	private static final org.springframework.jdbc.core.RowMapper<QuoteRow> QUOTE = (rs, i) -> new QuoteRow(rs.getLong("id"),
			rs.getLong("rfq_id"), rs.getLong("rfq_supplier_id"), rs.getLong("supplier_id"), rs.getString("status"),
			rs.getString("currency"), rs.getObject("valid_until", LocalDate.class),
			rs.getObject("lead_time_days", Integer.class), rs.getString("payment_terms"), rs.getString("incoterm"),
			rs.getBigDecimal("freight_amount"), rs.getBigDecimal("duty_amount"), rs.getBigDecimal("other_amount"),
			rs.getBigDecimal("merchandise_amount"), rs.getString("notes"), rs.getString("source"),
			UtcTimes.instant(rs, "submitted_at"), rs.getObject("submitted_by_user_id", Long.class), rs.getInt("version"));

	private static final org.springframework.jdbc.core.RowMapper<QuoteLineRow> QUOTE_LINE = (rs,
			i) -> new QuoteLineRow(rs.getLong("id"), rs.getLong("quotation_id"), rs.getLong("rfq_line_id"),
					rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_amount"));
}
