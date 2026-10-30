package com.epi.procurement.ordering;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

record AwardBody(Integer requestVersion, Long quotationId, String reason) {
}

record AwardView(long id, long requestId, long quotationId, long supplierId, String supplierName, String currency,
		BigDecimal merchandiseAmount, BigDecimal freightAmount, BigDecimal dutyAmount, BigDecimal otherAmount,
		BigDecimal landedAmount, String reason, Instant awardedAt, List<AwardLineView> lines) {
}

record AwardLineView(long id, int lineNo, String description, BigDecimal quantity, String unit, BigDecimal unitPrice,
		BigDecimal lineAmount) {
}

record PoCreateBody(Integer requestVersion, String paymentTerms, String incoterm, LocalDate requiredDate,
		String deliveryAddress, List<MilestoneBody> milestones) {
}

record MilestoneBody(String label, LocalDate dueDate, BigDecimal amount, String currency, String status) {
}

record PoActionBody(Integer version, String confirmationNote) {
}

record PurchaseOrderView(long id, long awardId, long requestId, String number, String status, long supplierId,
		String supplierName, String currency, BigDecimal merchandiseAmount, BigDecimal freightAmount,
		BigDecimal dutyAmount, BigDecimal otherAmount, BigDecimal totalAmount, String paymentTerms, String incoterm,
		LocalDate requiredDate, String deliveryAddress, Instant createdAt, Instant issuedAt, Instant confirmedAt,
		String confirmationNote, int version, List<PoLineView> lines, List<MilestoneView> milestones) {
}

record PoLineView(long id, int lineNo, String description, BigDecimal quantity, String unit, BigDecimal unitPrice,
		BigDecimal lineAmount) {
}

record MilestoneView(long id, String label, LocalDate dueDate, BigDecimal amount, String currency, String status) {
}
