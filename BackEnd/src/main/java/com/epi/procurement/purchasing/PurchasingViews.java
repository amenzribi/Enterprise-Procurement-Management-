package com.epi.procurement.purchasing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

record QueuePage(List<QueueItem> items, int page, int size, long totalItems, int totalPages) {
}

record QueueItem(long requestId, int version, String number, String title, String status, String userStatus,
		Ref requester, Ref buyingCompany, Ref requestingCompany, BigDecimal amount, String currency, String category,
		String priority, String purchaseType, String purchaseMode, boolean buyingCompanyProposed, Ref assignedBuyer,
		LocalDate requiredDate, Instant submittedAt, List<String> completenessGaps) {
}

record Ref(long id, String code, String name) {
}

record BuyerOption(long id, String displayName, String email, int openAssignments, boolean suggested) {
}

record Assignment(long id, long buyerUserId, String buyerName, long assignedByUserId, String assignedByName,
		String status, String reason, Instant assignedAt, Instant decidedAt) {
}

record ClassificationBody(Integer version, java.util.List<Long> reclassifyAsGoods, String comment) {
}

record ActionBody(Integer version, Long buyerUserId, String purchaseMode, String reason, String question) {
}

record FilterOptions(List<Ref> companies, List<Ref> categories, List<Ref> buyers, List<Ref> requesters,
		List<String> countries) {
}

record QueueFilter(String q, Long buyingCompanyId, Long categoryId, String status, String priority, String purchaseType,
		String purchaseMode, Long buyerUserId, Long requesterUserId, java.time.LocalDate requiredFrom,
		java.time.LocalDate requiredTo, String countryCode, java.math.BigDecimal amountMin,
		java.math.BigDecimal amountMax, Boolean complete, String sort, boolean descending, int page, int size) {
}
