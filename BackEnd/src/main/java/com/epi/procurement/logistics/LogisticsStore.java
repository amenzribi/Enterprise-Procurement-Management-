package com.epi.procurement.logistics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.epi.procurement.platform.persistence.UtcTimes;

@Repository
class LogisticsStore {

	private final JdbcTemplate jdbc;

	LogisticsStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	Optional<PoSnap> findConfirmedPo(long requestId) {
		return jdbc.query("""
				SELECT id, request_id, status, buying_company_id, number FROM purchase_order WITH (UPDLOCK, ROWLOCK)
				WHERE request_id = ?""", PO, requestId).stream().findFirst();
	}

	List<PoLineSnap> poLines(long poId) {
		return jdbc.query("""
				SELECT id, request_line_id, line_no, description, quantity, unit FROM purchase_order_line
				WHERE purchase_order_id = ? ORDER BY line_no""", PO_LINE, poId);
	}

	BigDecimal shippedQuantity(long poLineId) {
		BigDecimal q = jdbc.queryForObject("""
				SELECT COALESCE(SUM(quantity), 0) FROM shipment_line sl
				JOIN shipment s ON s.id = sl.shipment_id
				WHERE sl.purchase_order_line_id = ? AND s.status <> 'CANCELLED'""", BigDecimal.class, poLineId);
		return q == null ? BigDecimal.ZERO : q;
	}

	List<ShipmentRow> shipmentsForRequest(long requestId) {
		return jdbc.query("SELECT * FROM shipment WHERE request_id = ? ORDER BY id", SHIPMENT, requestId);
	}

	Optional<ShipmentRow> findShipment(long id) {
		return jdbc.query("SELECT * FROM shipment WHERE id = ?", SHIPMENT, id).stream().findFirst();
	}

	long insertShipment(long groupId, long requestId, long poId, String number, String kind, String status,
			long buyingCompanyId, String carrier, String tracking, String origin, String destination, long actorId,
			Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_shipment", Long.class);
		jdbc.update("""
				INSERT INTO shipment (id, group_id, request_id, purchase_order_id, number, kind, status, buying_company_id,
				                      carrier_name, tracking_reference, origin_place, destination_place, created_by_user_id,
				                      created_at, version)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)""", id, groupId, requestId, poId, number, kind, status,
				buyingCompanyId, carrier, tracking, origin, destination, actorId, UtcTimes.db(now));
		return id;
	}

	void insertLine(long shipmentId, long poLineId, long requestLineId, short lineNo, String description,
			BigDecimal quantity, String unit) {
		jdbc.update("""
				INSERT INTO shipment_line (id, shipment_id, purchase_order_line_id, request_line_id, line_no, description,
				                           quantity, unit)
				VALUES (NEXT VALUE FOR seq_shipment_line, ?, ?, ?, ?, ?, ?, ?)""", shipmentId, poLineId, requestLineId,
				lineNo, description, quantity, unit);
	}

	void insertMilestone(long shipmentId, String code, short sortOrder, LocalDate planned) {
		jdbc.update("""
				INSERT INTO shipment_milestone (id, shipment_id, code, sort_order, planned_date)
				VALUES (NEXT VALUE FOR seq_shipment_milestone, ?, ?, ?, ?)""", shipmentId, code, sortOrder, planned);
	}

	List<ShipmentLineRow> lines(long shipmentId) {
		return jdbc.query("SELECT * FROM shipment_line WHERE shipment_id = ? ORDER BY line_no", LINE, shipmentId);
	}

	List<MilestoneRow> milestones(long shipmentId) {
		return jdbc.query("SELECT * FROM shipment_milestone WHERE shipment_id = ? ORDER BY sort_order", MILESTONE,
				shipmentId);
	}

	Optional<MilestoneRow> milestone(long shipmentId, long milestoneId) {
		return jdbc.query("SELECT * FROM shipment_milestone WHERE shipment_id = ? AND id = ?", MILESTONE, shipmentId,
				milestoneId).stream().findFirst();
	}

	void updateMilestone(long milestoneId, LocalDate planned, LocalDate actual, String note) {
		jdbc.update("""
				UPDATE shipment_milestone SET planned_date = ?, actual_date = ?, note = ? WHERE id = ?""", planned, actual,
				note, milestoneId);
	}

	void bumpShipment(long shipmentId, String status) {
		jdbc.update("UPDATE shipment SET status = ?, version = version + 1 WHERE id = ?", status, shipmentId);
	}

	List<DocumentRow> documents(long shipmentId) {
		return jdbc.query("SELECT * FROM shipment_document WHERE shipment_id = ? ORDER BY id", DOCUMENT, shipmentId);
	}

	long insertDocument(long shipmentId, String kind, String reference, String note, String storageKey, String fileName,
			String contentType, Long sizeBytes, long actorId, Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_shipment_document", Long.class);
		jdbc.update("""
				INSERT INTO shipment_document (id, shipment_id, kind, reference_number, note, storage_key, file_name,
				                               content_type, size_bytes, uploaded_by_user_id, uploaded_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, shipmentId, kind, reference, note, storageKey, fileName,
				contentType, sizeBytes, actorId, UtcTimes.db(now));
		return id;
	}

	boolean assignmentAccepted(long requestId, long buyerUserId) {
		Integer n = jdbc.queryForObject("""
				SELECT COUNT(*) FROM buyer_assignment WHERE request_id = ? AND buyer_user_id = ? AND status = 'ACCEPTED'""",
				Integer.class, requestId, buyerUserId);
		return n != null && n > 0;
	}

	record PoSnap(long id, long requestId, String status, long buyingCompanyId, String number) {
	}

	record PoLineSnap(long id, long requestLineId, short lineNo, String description, BigDecimal quantity, String unit) {
	}

	record ShipmentRow(long id, long groupId, long requestId, long purchaseOrderId, String number, String kind,
			String status, long buyingCompanyId, String carrierName, String trackingReference, String originPlace,
			String destinationPlace, Instant createdAt, int version) {
	}

	record ShipmentLineRow(long id, long shipmentId, long poLineId, long requestLineId, short lineNo, String description,
			BigDecimal quantity, String unit) {
	}

	record MilestoneRow(long id, long shipmentId, String code, short sortOrder, LocalDate plannedDate,
			LocalDate actualDate, String note) {
	}

	record DocumentRow(long id, long shipmentId, String kind, String referenceNumber, String note, String fileName,
			Instant uploadedAt) {
	}

	private static final RowMapper<PoSnap> PO = (rs, i) -> new PoSnap(rs.getLong("id"), rs.getLong("request_id"),
			rs.getString("status"), rs.getLong("buying_company_id"), rs.getString("number"));

	private static final RowMapper<PoLineSnap> PO_LINE = (rs, i) -> new PoLineSnap(rs.getLong("id"),
			rs.getLong("request_line_id"), rs.getShort("line_no"), rs.getString("description"),
			rs.getBigDecimal("quantity"), rs.getString("unit"));

	private static final RowMapper<ShipmentRow> SHIPMENT = (rs, i) -> new ShipmentRow(rs.getLong("id"),
			rs.getLong("group_id"), rs.getLong("request_id"), rs.getLong("purchase_order_id"), rs.getString("number"),
			rs.getString("kind"), rs.getString("status"), rs.getLong("buying_company_id"), rs.getString("carrier_name"),
			rs.getString("tracking_reference"), rs.getString("origin_place"), rs.getString("destination_place"),
			UtcTimes.instant(rs, "created_at"), rs.getInt("version"));

	private static final RowMapper<ShipmentLineRow> LINE = (rs, i) -> new ShipmentLineRow(rs.getLong("id"),
			rs.getLong("shipment_id"), rs.getLong("purchase_order_line_id"), rs.getLong("request_line_id"),
			rs.getShort("line_no"), rs.getString("description"), rs.getBigDecimal("quantity"), rs.getString("unit"));

	private static final RowMapper<MilestoneRow> MILESTONE = (rs, i) -> new MilestoneRow(rs.getLong("id"),
			rs.getLong("shipment_id"), rs.getString("code"), rs.getShort("sort_order"),
			rs.getObject("planned_date", LocalDate.class), rs.getObject("actual_date", LocalDate.class),
			rs.getString("note"));

	private static final RowMapper<DocumentRow> DOCUMENT = (rs, i) -> new DocumentRow(rs.getLong("id"),
			rs.getLong("shipment_id"), rs.getString("kind"), rs.getString("reference_number"), rs.getString("note"),
			rs.getString("file_name"), UtcTimes.instant(rs, "uploaded_at"));
}
