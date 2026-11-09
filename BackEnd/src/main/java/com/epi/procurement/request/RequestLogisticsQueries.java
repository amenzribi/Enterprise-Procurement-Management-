package com.epi.procurement.request;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.platform.persistence.UtcTimes;

/**
 * Request-module SQL for the logistics confirmed-PO inbox. Keeps {@code purchase_request} table access inside
 * this module (architecture rule S1CR-08) with the role/company scope applied in the query.
 */
@Service
public class RequestLogisticsQueries {

	private final JdbcTemplate jdbc;

	public RequestLogisticsQueries(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	/** Whether each line of a request is GOODS or SERVICE, by request line id. */
	@Transactional(readOnly = true)
	public Map<Long, String> lineKinds(long requestId) {
		Map<Long, String> kinds = new HashMap<>();
		jdbc.query("SELECT id, line_kind FROM purchase_request_line WHERE request_id = ?",
				(RowCallbackHandler) rs -> kinds.put(rs.getLong("id"), rs.getString("line_kind")), requestId);
		return kinds;
	}

	public record ConfirmedPoInboxRow(long poId, String poNumber, String poStatus, Instant poCreatedAt,
			long buyingCompanyId, long requestId, String requestNumber, String requestTitle, String requestStatus,
			Long shipmentId, String shipmentNumber, String shipmentStatus, String shipmentKind) {
	}

	public record ConfirmedPoInboxPage(List<ConfirmedPoInboxRow> rows, long total) {
	}

	/**
	 * One page of confirmed purchase orders. {@code buyingCompanyIds} restricts the buying company; {@code null} means
	 * every company of the group.
	 */
	@Transactional(readOnly = true)
	public ConfirmedPoInboxPage confirmedPoInbox(long groupId, Collection<Long> buyingCompanyIds, String q, int page,
			int size) {
		if (buyingCompanyIds != null && buyingCompanyIds.isEmpty()) {
			return new ConfirmedPoInboxPage(List.of(), 0);
		}
		String text = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
		boolean filter = !text.isEmpty();
		String like = "%" + text.replace("%", "[%]").replace("_", "[_]") + "%";
		List<Object> args = new ArrayList<>();
		args.add(groupId);
		StringBuilder from = new StringBuilder("""
				FROM purchase_order po
				JOIN purchase_request pr ON pr.id = po.request_id
				OUTER APPLY (
				  SELECT TOP 1 id, number, status, kind FROM shipment
				  WHERE request_id = pr.id AND status <> 'CANCELLED'
				  ORDER BY created_at DESC, id DESC
				) s
				WHERE pr.group_id = ? AND po.status = 'CONFIRMED'
				""");
		if (buyingCompanyIds != null) {
			from.append(" AND po.buying_company_id IN (")
				.append(String.join(",", Collections.nCopies(buyingCompanyIds.size(), "?")))
				.append(")");
			args.addAll(buyingCompanyIds);
		}
		if (filter) {
			from.append(" AND (LOWER(pr.number) LIKE ? OR LOWER(po.number) LIKE ? OR LOWER(ISNULL(s.number,'')) LIKE ?"
					+ " OR LOWER(pr.title) LIKE ?)");
			args.addAll(List.of(like, like, like, like));
		}
		Long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(page * size);
		pageArgs.add(size);
		List<ConfirmedPoInboxRow> rows = jdbc.query("""
				SELECT po.id AS po_id, po.number AS po_number, po.status AS po_status, po.created_at AS po_created_at,
				       po.buying_company_id,
				       pr.id AS request_id, pr.number AS request_number, pr.title AS request_title, pr.status AS request_status,
				       s.id AS shipment_id, s.number AS shipment_number, s.status AS shipment_status, s.kind AS shipment_kind
				""" + from + " ORDER BY po.created_at DESC, po.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", INBOX,
				pageArgs.toArray());
		return new ConfirmedPoInboxPage(rows, total == null ? 0 : total);
	}

	private static final RowMapper<ConfirmedPoInboxRow> INBOX = (rs, i) -> new ConfirmedPoInboxRow(rs.getLong("po_id"),
			rs.getString("po_number"), rs.getString("po_status"), UtcTimes.instant(rs, "po_created_at"),
			rs.getLong("buying_company_id"), rs.getLong("request_id"), rs.getString("request_number"),
			rs.getString("request_title"), rs.getString("request_status"), rs.getObject("shipment_id", Long.class),
			rs.getString("shipment_number"), rs.getString("shipment_status"), rs.getString("shipment_kind"));
}
