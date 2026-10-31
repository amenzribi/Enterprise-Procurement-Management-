package com.epi.procurement.sourcing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

record RfqDetail(long id, long requestId, String kind, String status, String title, String comparisonCurrency,
		int version, Instant createdAt, List<RfqLineView> lines, List<RfqSupplierView> suppliers,
		List<QuoteView> quotes) {
}

record RfqLineView(long id, int lineNo, String description, BigDecimal quantity, String unit) {
}

record RfqSupplierView(long id, long supplierId, String code, String name) {
}

record QuoteView(long id, long rfqSupplierId, long supplierId, String supplierName, String status, String currency,
		LocalDate validUntil, Integer leadTimeDays, String paymentTerms, String incoterm, BigDecimal freightAmount,
		BigDecimal dutyAmount, BigDecimal otherAmount, BigDecimal merchandiseAmount, String notes, String source,
		Instant submittedAt, List<QuoteLineView> lines) {
}

record QuoteLineView(long rfqLineId, int lineNo, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineAmount,
		boolean priceMissing) {
}

record ComparisonRow(long quotationId, long supplierId, String supplierName, String currency, BigDecimal merchandise,
		BigDecimal freight, BigDecimal duty, BigDecimal other, BigDecimal landed, BigDecimal landedInComparisonCurrency,
		BigDecimal rate, LocalDate rateDate, List<String> missingFields) {
}

record ComparisonView(String comparisonCurrency, List<ComparisonRow> rows) {
}

record CreateRfqBody(String kind, List<Long> supplierIds) {
}

record QuoteBody(Long rfqSupplierId, String currency, LocalDate validUntil, Integer leadTimeDays, String paymentTerms,
		String incoterm, BigDecimal freightAmount, BigDecimal dutyAmount, BigDecimal otherAmount, String notes,
		List<QuoteLineBody> lines) {
}

record QuoteLineBody(long rfqLineId, BigDecimal quantity, BigDecimal unitPrice) {
}

record ValidateBody(Integer requestVersion, String comment) {
}

record LinkCreated(String token, String path, Instant expiresAt) {

	@Override
	public String toString() {
		return "LinkCreated[token=***,path=" + path + ",expiresAt=" + expiresAt + "]";
	}
}

record PublicSummaryRequest(String token) {

	@Override
	public String toString() {
		return "PublicSummaryRequest[token=***]";
	}
}

record PublicQuoteSubmit(String token, String currency, LocalDate validUntil, Integer leadTimeDays, String paymentTerms,
		String incoterm, BigDecimal freightAmount, BigDecimal dutyAmount, BigDecimal otherAmount, String notes,
		List<QuoteLineBody> lines) {

	@Override
	public String toString() {
		return "PublicQuoteSubmit[token=***,currency=" + currency + "]";
	}
}

record PublicSummary(String title, String kind, String supplierName, LocalDate expiresHint, List<RfqLineView> lines,
		boolean alreadySubmitted) {
}
