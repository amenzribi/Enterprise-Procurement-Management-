package com.epi.procurement.ordering;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.Role;
import com.epi.procurement.platform.web.ApiProblemException;

/**
 * Read-only PO lookups for the internal assistant (Sprint 12). Access checked via {@link OrderingService}.
 */
@Service
public class OrderingAssistantQueries {

	private final OrderingService ordering;

	private final JdbcTemplate jdbc;

	OrderingAssistantQueries(OrderingService ordering, DataSource dataSource) {
		this.ordering = ordering;
		this.jdbc = new JdbcTemplate(dataSource);
	}

	public record PoSummary(long id, long requestId, String number, String status, long supplierId, String supplierName,
			Instant issuedAt, Instant confirmedAt) {
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public Optional<PoSummary> findForRequest(CurrentUser user, long requestId) {
		try {
			PurchaseOrderView po = ordering.getPo(user, requestId);
			if (po == null) {
				return Optional.empty();
			}
			return Optional.of(toSummary(po));
		}
		catch (ApiProblemException ex) {
			if (ex.getStatus() == HttpStatus.NOT_FOUND || ex.getStatus() == HttpStatus.FORBIDDEN) {
				return Optional.empty();
			}
			throw ex;
		}
	}

	public List<PoSummary> listAwaitingSupplierConfirmation(CurrentUser user) {
		List<Long> companies = buyingCompanies(user);
		if (companies.isEmpty()) {
			return List.of();
		}
		String in = String.join(",", companies.stream().map(String::valueOf).toList());
		List<PoSummary> rows = jdbc.query("""
				SELECT p.id, p.request_id, p.number, p.status, p.supplier_id, s.legal_name, p.issued_at, p.confirmed_at
				FROM purchase_order p
				JOIN supplier s ON s.id = p.supplier_id
				WHERE p.status = 'ISSUED' AND p.buying_company_id IN (""" + in + ") ORDER BY p.id DESC", (rs, i) -> {
			java.sql.Timestamp issued = rs.getTimestamp("issued_at");
			java.sql.Timestamp confirmed = rs.getTimestamp("confirmed_at");
			return new PoSummary(rs.getLong("id"), rs.getLong("request_id"), rs.getString("number"),
					rs.getString("status"), rs.getLong("supplier_id"), rs.getString("legal_name"),
					issued == null ? null : issued.toInstant(), confirmed == null ? null : confirmed.toInstant());
		});
		if (user.hasRole(Role.PURCHASING_MANAGER)) {
			return rows.stream().filter(po -> canAccess(user, po.requestId())).limit(20).toList();
		}
		return rows.stream().filter(po -> isAssignedBuyer(user.id(), po.requestId())).limit(20).toList();
	}

	private boolean canAccess(CurrentUser user, long requestId) {
		return findForRequest(user, requestId).isPresent();
	}

	private boolean isAssignedBuyer(long userId, long requestId) {
		Integer n = jdbc.queryForObject("""
				SELECT COUNT(*) FROM buyer_assignment
				WHERE request_id = ? AND buyer_user_id = ? AND status IN ('ASSIGNED', 'ACCEPTED')""", Integer.class,
				requestId, userId);
		return n != null && n > 0;
	}

	private static List<Long> buyingCompanies(CurrentUser user) {
		List<Long> ids = new ArrayList<>();
		user.grants().forEach(g -> {
			if ((g.role() == Role.BUYER || g.role() == Role.PURCHASING_MANAGER) && g.companyId() != null) {
				ids.add(g.companyId());
			}
		});
		return ids;
	}

	private static PoSummary toSummary(PurchaseOrderView po) {
		return new PoSummary(po.id(), po.requestId(), po.number(), po.status(), po.supplierId(), po.supplierName(),
				po.issuedAt(), po.confirmedAt());
	}

}
