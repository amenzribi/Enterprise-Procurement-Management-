package com.epi.procurement.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

record StatusCount(String userStatus, long count) {
}

record CurrencyTotal(String currency, BigDecimal amount, long requestCount) {
}

record CompanyCount(String companyCode, String companyName, long count) {
}

record PipelineView(List<StatusCount> byUserStatus, List<CurrencyTotal> openAmountsByCurrency,
		List<CompanyCount> byRequestingCompany, boolean groupView) {
}

record ManagementRow(long id, String number, String title, String userStatus, String status, String ownerName,
		Instant ownerSince, LocalDate requiredDate, BigDecimal totalAmount, String currency, boolean late) {
}

record ManagementPage(List<ManagementRow> items, int page, int size, long totalElements) {
}
