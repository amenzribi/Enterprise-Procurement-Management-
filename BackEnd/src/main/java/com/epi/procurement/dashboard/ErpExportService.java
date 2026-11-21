package com.epi.procurement.dashboard;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.Role;
import com.epi.procurement.identity.RoleGrant;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.request.RequestSnapshot;
import com.epi.procurement.request.RequestStatus;
import com.epi.procurement.request.RequestWorkflow;

@Service
class ErpExportService {

	private static final Set<RequestStatus> REQUEST_FROM = Set.of(RequestStatus.APPROVED, RequestStatus.IN_PURCHASING_REVIEW,
			RequestStatus.ASSIGNED_TO_BUYER, RequestStatus.PURCHASING_RETURNED, RequestStatus.SOURCING,
			RequestStatus.QUOTES_RECEIVED, RequestStatus.TECHNICAL_VALIDATION, RequestStatus.COMMERCIAL_VALIDATION,
			RequestStatus.MANAGEMENT_VALIDATION, RequestStatus.AWARDED, RequestStatus.ORDERED,
			RequestStatus.CONTRACT_SIGNED, RequestStatus.ORDER_CONFIRMED, RequestStatus.IN_PRODUCTION,
			RequestStatus.READY, RequestStatus.SHIPPED, RequestStatus.AT_PORT, RequestStatus.IN_CUSTOMS,
			RequestStatus.IN_WAREHOUSE, RequestStatus.DELIVERED, RequestStatus.PARTIALLY_RECEIVED,
			RequestStatus.NON_CONFORMING, RequestStatus.ACCEPTED, RequestStatus.INVOICED,
			RequestStatus.PAYMENT_PENDING, RequestStatus.PAID, RequestStatus.CLOSED);

	private final RequestWorkflow requests;

	private final JdbcTemplate jdbc;

	ErpExportService(RequestWorkflow requests, DataSource dataSource) {
		this.requests = requests;
		this.jdbc = new JdbcTemplate(dataSource);
	}

	@Transactional(readOnly = true)
	byte[] exportRequestsCsv(CurrentUser user) {
		requireExporter(user);
		Set<Long> buyingScope = buyingCompanyScope(user);
		StringBuilder csv = new StringBuilder();
		csv.append("number,title,status,userStatus,currency,totalAmount,buyingCompanyId,chargedCompanyId,requestingCompanyId\n");
		for (RequestSnapshot request : loadVisible(user)) {
			RequestStatus status = RequestStatus.valueOf(request.status());
			if (!REQUEST_FROM.contains(status)) {
				continue;
			}
			if (buyingScope != null && !buyingScope.contains(request.buyingCompanyId())) {
				continue;
			}
			csv.append(csv(request.number())).append(',').append(csv(request.title())).append(',')
				.append(csv(request.status())).append(',')
				.append(csv(request.userStatus() == null ? "" : request.userStatus().name())).append(',')
				.append(csv(request.currency())).append(',').append(amount(request.totalAmount())).append(',')
				.append(request.buyingCompanyId()).append(',').append(request.chargedCompanyId()).append(',')
				.append(request.requestingCompanyId()).append('\n');
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	@Transactional(readOnly = true)
	byte[] exportPurchaseOrdersCsv(CurrentUser user) {
		requireExporter(user);
		Set<Long> buyingScope = buyingCompanyScope(user);
		Map<Long, String> visibleRequestNumbers = new HashMap<>();
		for (RequestSnapshot request : loadVisible(user)) {
			visibleRequestNumbers.put(request.id(), request.number() == null ? "" : request.number());
		}
		StringBuilder sql = new StringBuilder("""
				SELECT number, status, currency, total_amount, buying_company_id, charged_company_id, request_id
				FROM purchase_order
				WHERE status IN ('ISSUED', 'CONFIRMED')
				""");
		List<Object> args = new ArrayList<>();
		if (buyingScope != null) {
			if (buyingScope.isEmpty()) {
				return headerPo().getBytes(StandardCharsets.UTF_8);
			}
			sql.append(" AND buying_company_id IN (");
			sql.append(buyingScope.stream().map(id -> "?").collect(Collectors.joining(",")));
			sql.append(")");
			args.addAll(buyingScope);
		}
		sql.append(" ORDER BY id");
		StringBuilder csv = new StringBuilder(headerPo());
		jdbc.query(sql.toString(), rs -> {
			long requestId = rs.getLong("request_id");
			String requestNumber = visibleRequestNumbers.get(requestId);
			if (requestNumber == null) {
				return;
			}
			csv.append(csv(rs.getString("number"))).append(',').append(csv(rs.getString("status"))).append(',')
				.append(csv(rs.getString("currency"))).append(',').append(amount(rs.getBigDecimal("total_amount")))
				.append(',').append(rs.getLong("buying_company_id")).append(',')
				.append(rs.getLong("charged_company_id")).append(',').append(csv(requestNumber)).append('\n');
		}, args.toArray());
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	private List<RequestSnapshot> loadVisible(CurrentUser user) {
		List<RequestSnapshot> all = new ArrayList<>();
		int page = 0;
		Page<RequestSnapshot> slice;
		do {
			slice = requests.searchVisible(user, page++, 200);
			all.addAll(slice.getContent());
		}
		while (slice.hasNext() && page < 25);
		return all;
	}

	/**
	 * {@code null} = group/admin may export every buying company they can see via request visibility;
	 * otherwise restrict to these ids.
	 */
	private static Set<Long> buyingCompanyScope(CurrentUser user) {
		if (user.hasRole(Role.ADMIN) || user.hasRole(Role.GROUP_MANAGEMENT)) {
			return null;
		}
		return user.grants(Role.PURCHASING_MANAGER)
			.stream()
			.map(RoleGrant::companyId)
			.filter(id -> id != null)
			.collect(Collectors.toSet());
	}

	private static void requireExporter(CurrentUser user) {
		if (!(user.hasRole(Role.PURCHASING_MANAGER) || user.hasRole(Role.ADMIN) || user.hasRole(Role.GROUP_MANAGEMENT))) {
			throw ApiProblemException.forbidden();
		}
	}

	private static String headerPo() {
		return "poNumber,status,currency,totalAmount,buyingCompanyId,chargedCompanyId,requestNumber\n";
	}

	static String csv(String value) {
		if (value == null) {
			return "";
		}
		String text = value;
		// A cell that starts like a formula is run by spreadsheet programs when the export is opened (audit S-14).
		if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
			text = "'" + text;
		}
		String escaped = text.replace("\"", "\"\"");
		if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
			return "\"" + escaped + "\"";
		}
		return escaped;
	}

	private static String amount(BigDecimal value) {
		return value == null ? "" : value.toPlainString();
	}

}
