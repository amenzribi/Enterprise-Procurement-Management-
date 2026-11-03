package com.epi.procurement.request;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Linked purchasing evidence for administrative inspection (counts and light listings). Lives in the request module
 * so everyday visibility rules stay untouched while admins see cross-module references.
 */
@Component
class RequestAdminLinks {

	private final JdbcTemplate jdbc;

	RequestAdminLinks(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	RequestAdminActions.LinkCounts counts(long requestId) {
		int approvals = count("SELECT COUNT(*) FROM approval_task WHERE request_id = ?", requestId);
		int rfqs = count("SELECT COUNT(*) FROM rfq WHERE request_id = ?", requestId);
		int quotations = count("""
				SELECT COUNT(*) FROM quotation q
				JOIN rfq r ON r.id = q.rfq_id WHERE r.request_id = ?""", requestId);
		int awards = count("SELECT COUNT(*) FROM award WHERE request_id = ?", requestId);
		int pos = count("SELECT COUNT(*) FROM purchase_order WHERE request_id = ?", requestId);
		int shipments = count("SELECT COUNT(*) FROM shipment WHERE request_id = ?", requestId);
		int receipts = count("SELECT COUNT(*) FROM goods_receipt WHERE request_id = ?", requestId);
		int invoices = count("SELECT COUNT(*) FROM supplier_invoice WHERE request_id = ?", requestId);
		return new RequestAdminActions.LinkCounts(approvals, rfqs, quotations, awards, pos, shipments, receipts,
				invoices);
	}

	List<RequestAdminViews.LinkedItem> items(long requestId) {
		List<RequestAdminViews.LinkedItem> out = new ArrayList<>();
		jdbc.query("""
				SELECT id, status, stage_name FROM approval_task WHERE request_id = ?
				ORDER BY id DESC""", (rs, i) -> out.add(new RequestAdminViews.LinkedItem("APPROVAL_TASK",
				rs.getLong("id"), rs.getString("stage_name"), rs.getString("status"))), requestId);
		jdbc.query("SELECT id, title, status FROM rfq WHERE request_id = ? ORDER BY id",
				(rs, i) -> out.add(new RequestAdminViews.LinkedItem("RFQ", rs.getLong("id"), rs.getString("title"),
						rs.getString("status"))),
				requestId);
		jdbc.query("""
				SELECT q.id, q.status, CAST(q.id AS VARCHAR(40)) AS ref FROM quotation q
				JOIN rfq r ON r.id = q.rfq_id WHERE r.request_id = ? ORDER BY q.id""",
				(rs, i) -> out.add(new RequestAdminViews.LinkedItem("QUOTATION", rs.getLong("id"), rs.getString("ref"),
						rs.getString("status"))),
				requestId);
		jdbc.query("""
				SELECT id, CAST(id AS VARCHAR(40)) AS ref, 'AWARDED' AS status FROM award
				WHERE request_id = ? ORDER BY id""",
				(rs, i) -> out.add(new RequestAdminViews.LinkedItem("AWARD", rs.getLong("id"), rs.getString("ref"),
						rs.getString("status"))),
				requestId);
		jdbc.query("SELECT id, number, status FROM purchase_order WHERE request_id = ? ORDER BY id",
				(rs, i) -> out.add(new RequestAdminViews.LinkedItem("PURCHASE_ORDER", rs.getLong("id"),
						rs.getString("number"), rs.getString("status"))),
				requestId);
		jdbc.query("SELECT id, number, status FROM shipment WHERE request_id = ? ORDER BY id",
				(rs, i) -> out.add(new RequestAdminViews.LinkedItem("SHIPMENT", rs.getLong("id"), rs.getString("number"),
						rs.getString("status"))),
				requestId);
		jdbc.query("SELECT id, number, status FROM goods_receipt WHERE request_id = ? ORDER BY id",
				(rs, i) -> out.add(new RequestAdminViews.LinkedItem("RECEIPT", rs.getLong("id"), rs.getString("number"),
						rs.getString("status"))),
				requestId);
		jdbc.query("""
				SELECT id, invoice_number, payment_status FROM supplier_invoice
				WHERE request_id = ? ORDER BY id""",
				(rs, i) -> out.add(new RequestAdminViews.LinkedItem("INVOICE", rs.getLong("id"),
						rs.getString("invoice_number"), rs.getString("payment_status"))),
				requestId);
		return List.copyOf(out);
	}

	private int count(String sql, long requestId) {
		Integer n = jdbc.queryForObject(sql, Integer.class, requestId);
		return n == null ? 0 : n;
	}

}
