package com.epi.procurement.request;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.platform.persistence.UtcTimes;

/**
 * Request-module SQL for the logistics confirmed-PO inbox. Keeps {@code purchase_request} table access inside
 * this module (architecture rule S1CR-08) while logistics applies role/company filters on the result.
 */
@Service
public class RequestLogisticsQueries {

	private final JdbcTemplate jdbc;

	public RequestLogisticsQueries(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	public record ConfirmedPoInboxRow(long poId, String poNumber, String poStatus, Instant poCreatedAt,
			long buyingCompanyId, long requestId, String requestNumber, String requestTitle, String requestStatus,
			Long shipmentId, String shipmentNumber, String shipmentStatus, String shipmentKind) {
	}

	@Transactional(readOnly = true)
	public List<ConfirmedPoInboxRow> confirmedPoInbox(long groupId, String q) {
		String text = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
		boolean filter = !text.isEmpty();
		String like = "%" + text.replace("%", "[%]").replace("_", "[_]") + "%";
		String sql = """
				SELECT po.id AS po_id, po.number AS po_number, po.status AS po_status, po.created_at AS po_created_at,
				       po.buying_company_id,
				       pr.id AS request_id, pr.number AS request_number, pr.title AS request_title, pr.status AS request_status,
				       s.id AS shipment_id, s.number AS shipment_number, s.status AS shipment_status, s.kind AS shipment_kind
				FROM purchase_order po
				JOIN purchase_request pr ON pr.id = po.request_id
				OUTER APPLY (
				  SELECT TOP 1 id, number, status, kind FROM shipment
				  WHERE request_id = pr.id AND status <> 'CANCELLED'
				  ORDER BY created_at DESC, id DESC
				) s
				WHERE pr.group_id = ? AND po.status = 'CONFIRMED'
				""" + (filter
				? " AND (LOWER(pr.number) LIKE ? OR LOWER(po.number) LIKE ? OR LOWER(ISNULL(s.number,'')) LIKE ? OR LOWER(pr.title) LIKE ?)"
				: "") + """
				 ORDER BY po.created_at DESC, po.id DESC""";
		if (filter) {
			return jdbc.query(sql, INBOX, groupId, like, like, like, like);
		}
		return jdbc.query(sql, INBOX, groupId);
	}

	private static final RowMapper<ConfirmedPoInboxRow> INBOX = (rs, i) -> new ConfirmedPoInboxRow(rs.getLong("po_id"),
			rs.getString("po_number"), rs.getString("po_status"), UtcTimes.instant(rs, "po_created_at"),
			rs.getLong("buying_company_id"), rs.getLong("request_id"), rs.getString("request_number"),
			rs.getString("request_title"), rs.getString("request_status"), rs.getObject("shipment_id", Long.class),
			rs.getString("shipment_number"), rs.getString("shipment_status"), rs.getString("shipment_kind"));
}
