package com.epi.procurement.dashboard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.audit.AuditEventView;
import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.Role;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.identity.UserSummary;
import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.request.RequestSnapshot;
import com.epi.procurement.request.RequestWorkflow;
import com.epi.procurement.request.UserStatus;

@Service
class DashboardService {

	private static final Set<String> TERMINAL = Set.of("CLOSED", "CANCELLED", "REJECTED");

	private final RequestWorkflow requests;

	private final UserDirectory users;

	private final OrganizationDirectory organization;

	private final AuditLog auditLog;

	private final Clock clock;

	DashboardService(RequestWorkflow requests, UserDirectory users, OrganizationDirectory organization, AuditLog auditLog,
			ObjectProvider<Clock> clock) {
		this.requests = requests;
		this.users = users;
		this.organization = organization;
		this.auditLog = auditLog;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@Transactional(readOnly = true)
	PipelineView pipeline(CurrentUser user) {
		requireDashboard(user);
		List<RequestSnapshot> all = loadVisible(user);
		Map<String, Long> byStatus = new LinkedHashMap<>();
		for (UserStatus status : UserStatus.values()) {
			byStatus.put(status.name(), 0L);
		}
		Map<String, BigDecimal> amounts = new LinkedHashMap<>();
		Map<String, Long> amountCounts = new LinkedHashMap<>();
		Map<Long, Long> byCompany = new LinkedHashMap<>();
		LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
		for (RequestSnapshot request : all) {
			String us = request.userStatus() == null ? "SUBMITTED" : request.userStatus().name();
			byStatus.merge(us, 1L, Long::sum);
			if (!TERMINAL.contains(request.status()) && request.totalAmount() != null && request.currency() != null) {
				amounts.merge(request.currency(), request.totalAmount(), BigDecimal::add);
				amountCounts.merge(request.currency(), 1L, Long::sum);
			}
			byCompany.merge(request.requestingCompanyId(), 1L, Long::sum);
		}
		List<StatusCount> statusCounts = byStatus.entrySet()
			.stream()
			.filter(e -> e.getValue() > 0)
			.map(e -> new StatusCount(e.getKey(), e.getValue()))
			.toList();
		List<CurrencyTotal> currencyTotals = amounts.entrySet()
			.stream()
			.map(e -> new CurrencyTotal(e.getKey(), e.getValue(), amountCounts.getOrDefault(e.getKey(), 0L)))
			.toList();
		boolean groupView = user.hasRole(Role.GROUP_MANAGEMENT);
		List<CompanyCount> companyCounts = List.of();
		if (groupView) {
			companyCounts = byCompany.entrySet().stream().map(e -> {
				OrganizationDirectory.CompanyRef company = organization.findCompany(e.getKey()).orElse(null);
				String code = company == null ? String.valueOf(e.getKey()) : company.code();
				String name = company == null ? code : company.name();
				return new CompanyCount(code, name, e.getValue());
			}).toList();
		}
		return new PipelineView(statusCounts, currencyTotals, companyCounts, groupView);
	}

	@Transactional(readOnly = true)
	ManagementPage management(CurrentUser user, int page, int size) {
		requireDashboard(user);
		int safeSize = size < 1 || size > 100 ? 20 : size;
		int safePage = Math.max(0, page);
		Page<RequestSnapshot> visible = requests.searchVisible(user, safePage, safeSize);
		LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
		Set<Long> ownerIds = visible.getContent()
			.stream()
			.map(RequestSnapshot::currentOwnerUserId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		Map<Long, UserSummary> owners = users.findUsers(ownerIds);
		List<ManagementRow> rows = visible.getContent().stream().map(request -> {
			UserSummary owner = owners.get(request.currentOwnerUserId());
			String ownerName = owner == null ? ("#" + request.currentOwnerUserId()) : owner.displayName();
			boolean late = request.requiredDate() != null && request.requiredDate().isBefore(today)
					&& !TERMINAL.contains(request.status());
			String us = request.userStatus() == null ? null : request.userStatus().name();
			return new ManagementRow(request.id(), request.number(), request.title(), us, request.status(), ownerName,
					request.ownerSince(), request.requiredDate(), request.totalAmount(), request.currency(), late);
		}).toList();
		return new ManagementPage(rows, visible.getNumber(), visible.getSize(), visible.getTotalElements());
	}

	@Transactional(readOnly = true)
	byte[] auditPackPdf(CurrentUser user, long requestId) {
		RequestSnapshot request = requests.requireVisible(user, requestId);
		List<AuditEventView> events = auditLog.listForEntity("PURCHASE_REQUEST", Long.toString(requestId));
		List<String> lines = new ArrayList<>();
		lines.add("Request: " + Objects.toString(request.number(), "-") + " — " + Objects.toString(request.title(), ""));
		lines.add("Status: " + request.status() + " / userStatus="
				+ (request.userStatus() == null ? "-" : request.userStatus().name()));
		lines.add("Currency/amount: " + request.currency() + " " + request.totalAmount());
		lines.add("Buying company id: " + request.buyingCompanyId() + " | Charged: " + request.chargedCompanyId());
		lines.add("");
		lines.add("Audit events (" + events.size() + ")");
		for (AuditEventView event : events) {
			lines.add(event.occurredAt() + " | " + event.action() + " | " + event.actorLabel() + " | "
					+ Objects.toString(event.fromStatus(), "-") + " -> " + Objects.toString(event.toStatus(), "-")
					+ (event.comment() == null ? "" : " | " + event.comment()));
		}
		return SimplePdf.ofLines("EPI audit pack", lines);
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

	private static void requireDashboard(CurrentUser user) {
		if (!(user.hasRole(Role.DEPARTMENT_MANAGER) || user.hasRole(Role.BUYER) || user.hasRole(Role.PURCHASING_MANAGER)
				|| user.hasRole(Role.GROUP_MANAGEMENT) || user.hasRole(Role.ADMIN))) {
			throw ApiProblemException.forbidden();
		}
	}

}
