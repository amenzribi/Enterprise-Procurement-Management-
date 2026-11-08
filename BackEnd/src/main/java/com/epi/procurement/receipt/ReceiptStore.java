package com.epi.procurement.receipt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.epi.procurement.platform.persistence.UtcTimes;

@Repository
class ReceiptStore {

	private final JdbcTemplate jdbc;

	ReceiptStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	Optional<PoSnap> findPo(long requestId) {
		return jdbc.query("""
				SELECT id, request_id, status, buying_company_id, supplier_id, currency, total_amount
				FROM purchase_order WITH (UPDLOCK, ROWLOCK) WHERE request_id = ?""", PO, requestId)
			.stream()
			.findFirst();
	}

	List<PoLineSnap> poLines(long poId) {
		return jdbc.query("""
				SELECT id, request_line_id, line_no, description, quantity, unit FROM purchase_order_line
				WHERE purchase_order_id = ? ORDER BY line_no""", PO_LINE, poId);
	}

	BigDecimal shippedQuantity(long poLineId) {
		BigDecimal q = jdbc.queryForObject("""
				SELECT COALESCE(SUM(sl.quantity), 0) FROM shipment_line sl
				JOIN shipment s ON s.id = sl.shipment_id
				WHERE sl.purchase_order_line_id = ? AND s.status <> 'CANCELLED'""", BigDecimal.class, poLineId);
		return q == null ? BigDecimal.ZERO : q;
	}

	BigDecimal receivedQuantity(long poLineId) {
		BigDecimal q = jdbc.queryForObject("""
				SELECT COALESCE(SUM(grl.quantity_received), 0) FROM goods_receipt_line grl
				JOIN goods_receipt gr ON gr.id = grl.goods_receipt_id
				WHERE grl.purchase_order_line_id = ? AND gr.status <> 'CANCELLED'""", BigDecimal.class, poLineId);
		return q == null ? BigDecimal.ZERO : q;
	}

	BigDecimal acceptedQuantity(long poLineId) {
		BigDecimal q = jdbc.queryForObject("""
				SELECT COALESCE(SUM(grl.quantity_accepted), 0) FROM goods_receipt_line grl
				JOIN goods_receipt gr ON gr.id = grl.goods_receipt_id
				WHERE grl.purchase_order_line_id = ? AND gr.status = 'CONFIRMED'""", BigDecimal.class, poLineId);
		return q == null ? BigDecimal.ZERO : q;
	}

	boolean shipmentExists(long shipmentId, long requestId) {
		Integer n = jdbc.queryForObject(
				"SELECT COUNT(*) FROM shipment WHERE id = ? AND request_id = ? AND status <> 'CANCELLED'", Integer.class,
				shipmentId, requestId);
		return n != null && n > 0;
	}

	long insertReceipt(long groupId, long requestId, long poId, Long shipmentId, String number, String status,
			Instant receivedAt, long actorId, String note) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_goods_receipt", Long.class);
		jdbc.update("""
				INSERT INTO goods_receipt (id, group_id, request_id, purchase_order_id, shipment_id, number, status,
				                           received_at, received_by_user_id, note, created_at, version)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)""", id, groupId, requestId, poId, shipmentId, number, status,
				UtcTimes.db(receivedAt), actorId, note, UtcTimes.db(receivedAt));
		return id;
	}

	void insertLine(long receiptId, long poLineId, Long shipmentLineId, long requestLineId, short lineNo,
			String description, BigDecimal received, BigDecimal accepted, BigDecimal rejected, BigDecimal damaged,
			String condition, String note) {
		jdbc.update("""
				INSERT INTO goods_receipt_line (id, goods_receipt_id, purchase_order_line_id, shipment_line_id,
				    request_line_id, line_no, description, quantity_received, quantity_accepted, quantity_rejected,
				    quantity_damaged, condition_code, note)
				VALUES (NEXT VALUE FOR seq_goods_receipt_line, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", receiptId, poLineId,
				shipmentLineId, requestLineId, lineNo, description, received, accepted, rejected, damaged, condition,
				note);
	}

	void insertEvidence(long receiptId, String kind, String reference, String note, long actorId, Instant now) {
		jdbc.update("""
				INSERT INTO receipt_evidence (id, goods_receipt_id, kind, reference_number, note, uploaded_by_user_id,
				                              uploaded_at)
				VALUES (NEXT VALUE FOR seq_receipt_evidence, ?, ?, ?, ?, ?, ?)""", receiptId, kind, reference, note,
				actorId, UtcTimes.db(now));
	}

	int confirmReceipt(long receiptId, long actorId, Instant now, String note) {
		return jdbc.update("""
				UPDATE goods_receipt SET status = 'CONFIRMED', confirmed_at = ?, confirmed_by_user_id = ?,
				    note = COALESCE(?, note), version = version + 1 WHERE id = ? AND status = 'RECORDED'""",
				UtcTimes.db(now), actorId, note, receiptId);
	}

	List<ReceiptRow> receiptsForRequest(long requestId) {
		return jdbc.query("SELECT * FROM goods_receipt WHERE request_id = ? ORDER BY created_at DESC, id DESC", RECEIPT,
				requestId);
	}

	Optional<ReceiptRow> findReceipt(long id) {
		return jdbc.query("SELECT * FROM goods_receipt WHERE id = ?", RECEIPT, id).stream().findFirst();
	}

	List<LineRow> lines(long receiptId) {
		return jdbc.query("SELECT * FROM goods_receipt_line WHERE goods_receipt_id = ? ORDER BY line_no", LINE,
				receiptId);
	}

	List<EvidenceRow> evidence(long receiptId) {
		return jdbc.query("SELECT * FROM receipt_evidence WHERE goods_receipt_id = ? ORDER BY id", EVIDENCE, receiptId);
	}

	boolean hasServiceAcceptance(long requestId) {
		Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM service_acceptance WHERE request_id = ?", Integer.class,
				requestId);
		return n != null && n > 0;
	}

	void insertServiceAcceptance(long requestId, long actorId, Instant now, boolean ok, String comment) {
		jdbc.update("""
				INSERT INTO service_acceptance (id, request_id, accepted_by_user_id, accepted_at, performance_ok, comment)
				VALUES (NEXT VALUE FOR seq_service_acceptance, ?, ?, ?, ?, ?)""", requestId, actorId, UtcTimes.db(now),
				ok, comment);
	}

	Optional<InvoiceRow> findInvoice(long requestId) {
		return jdbc.query("SELECT * FROM supplier_invoice WHERE request_id = ?", INVOICE, requestId).stream()
			.findFirst();
	}

	long insertInvoice(long requestId, long poId, String number, BigDecimal amount, String currency, String docRef,
			boolean matchAttested, String paymentStatus, long actorId, Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_supplier_invoice", Long.class);
		jdbc.update("""
				INSERT INTO supplier_invoice (id, request_id, purchase_order_id, invoice_number, amount, currency,
				    document_reference, match_attested, payment_status, recorded_by_user_id, recorded_at, version)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)""", id, requestId, poId, number, amount, currency, docRef,
				matchAttested, paymentStatus, actorId, UtcTimes.db(now));
		return id;
	}

	void updateInvoicePayment(long invoiceId, String paymentStatus) {
		jdbc.update("UPDATE supplier_invoice SET payment_status = ?, version = version + 1 WHERE id = ?", paymentStatus,
				invoiceId);
	}

	boolean hasShortfallAcceptance(long requestId) {
		Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM shortfall_acceptance WHERE request_id = ?", Integer.class,
				requestId);
		return n != null && n > 0;
	}

	void insertShortfallAcceptance(long requestId, long actorId, Instant now, String reason) {
		jdbc.update("""
				INSERT INTO shortfall_acceptance (id, request_id, accepted_by_user_id, accepted_at, reason)
				VALUES (NEXT VALUE FOR seq_shortfall_acceptance, ?, ?, ?, ?)""", requestId, actorId, UtcTimes.db(now), reason);
	}

	Optional<ScoreRow> findScore(long requestId) {
		return jdbc.query("SELECT * FROM supplier_score WHERE request_id = ?", SCORE, requestId).stream().findFirst();
	}

	void insertScore(long groupId, long requestId, long supplierId, int rating, String comment, long actorId,
			Instant now) {
		jdbc.update("""
				INSERT INTO supplier_score (id, group_id, request_id, supplier_id, rating, comment, scored_by_user_id,
				                            scored_at)
				VALUES (NEXT VALUE FOR seq_supplier_score, ?, ?, ?, ?, ?, ?, ?)""", groupId, requestId, supplierId, rating,
				comment, actorId, UtcTimes.db(now));
	}

	private static final RowMapper<PoSnap> PO = (rs, i) -> new PoSnap(rs.getLong("id"), rs.getLong("request_id"),
			rs.getString("status"), rs.getLong("buying_company_id"), rs.getLong("supplier_id"), rs.getString("currency"),
			rs.getBigDecimal("total_amount"));

	private static final RowMapper<PoLineSnap> PO_LINE = (rs, i) -> new PoLineSnap(rs.getLong("id"),
			rs.getLong("request_line_id"), rs.getShort("line_no"), rs.getString("description"),
			rs.getBigDecimal("quantity"), rs.getString("unit"));

	private static final RowMapper<ReceiptRow> RECEIPT = (rs, i) -> new ReceiptRow(rs.getLong("id"),
			rs.getLong("request_id"), rs.getLong("purchase_order_id"),
			rs.getObject("shipment_id") == null ? null : rs.getLong("shipment_id"), rs.getString("number"),
			rs.getString("status"), 			UtcTimes.instant(rs, "received_at"), UtcTimes.instant(rs, "confirmed_at"),
			rs.getString("note"), rs.getInt("version"));

	private static final RowMapper<LineRow> LINE = (rs, i) -> new LineRow(rs.getLong("id"),
			rs.getLong("purchase_order_line_id"),
			rs.getObject("shipment_line_id") == null ? null : rs.getLong("shipment_line_id"),
			rs.getLong("request_line_id"), rs.getShort("line_no"), rs.getString("description"),
			rs.getBigDecimal("quantity_received"), rs.getBigDecimal("quantity_accepted"),
			rs.getBigDecimal("quantity_rejected"), rs.getBigDecimal("quantity_damaged"), rs.getString("condition_code"),
			rs.getString("note"));

	private static final RowMapper<EvidenceRow> EVIDENCE = (rs, i) -> new EvidenceRow(rs.getLong("id"),
			rs.getString("kind"), rs.getString("reference_number"), rs.getString("note"), rs.getString("file_name"),
			UtcTimes.instant(rs, "uploaded_at"));

	private static final RowMapper<InvoiceRow> INVOICE = (rs, i) -> new InvoiceRow(rs.getLong("id"),
			rs.getLong("request_id"), rs.getLong("purchase_order_id"), rs.getString("invoice_number"),
			rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("document_reference"),
			rs.getBoolean("match_attested"), rs.getString("payment_status"), UtcTimes.instant(rs, "recorded_at"),
			rs.getInt("version"));

	private static final RowMapper<ScoreRow> SCORE = (rs, i) -> new ScoreRow(rs.getLong("id"), rs.getInt("rating"));

	record PoSnap(long id, long requestId, String status, long buyingCompanyId, long supplierId, String currency,
			BigDecimal totalAmount) {
	}

	record PoLineSnap(long id, long requestLineId, short lineNo, String description, BigDecimal quantity, String unit) {
	}

	record ReceiptRow(long id, long requestId, long purchaseOrderId, Long shipmentId, String number, String status,
			Instant receivedAt, Instant confirmedAt, String note, int version) {
	}

	record LineRow(long id, long poLineId, Long shipmentLineId, long requestLineId, short lineNo, String description,
			BigDecimal quantityReceived, BigDecimal quantityAccepted, BigDecimal quantityRejected,
			BigDecimal quantityDamaged, String condition, String note) {
	}

	record EvidenceRow(long id, String kind, String referenceNumber, String note, String fileName, Instant uploadedAt) {
	}

	record InvoiceRow(long id, long requestId, long purchaseOrderId, String invoiceNumber, BigDecimal amount,
			String currency, String documentReference, boolean matchAttested, String paymentStatus, Instant recordedAt,
			int version) {
	}

	record ScoreRow(long id, int rating) {
	}

}
