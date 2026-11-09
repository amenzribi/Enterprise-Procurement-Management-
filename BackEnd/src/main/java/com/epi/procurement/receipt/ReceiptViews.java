package com.epi.procurement.receipt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

record CreateReceiptBody(Integer requestVersion, Long shipmentId, String note, List<ReceiptLineBody> lines,
		List<EvidenceBody> evidence) {
}

record ReceiptLineBody(long purchaseOrderLineId, Long shipmentLineId, BigDecimal quantityReceived,
		BigDecimal quantityAccepted, BigDecimal quantityRejected, BigDecimal quantityDamaged, String condition,
		String note) {
}

record EvidenceBody(String kind, String referenceNumber, String note) {
}

record ConfirmReceiptBody(Integer version, String note) {
}

record ServiceAcceptanceBody(Integer requestVersion, Boolean performanceOk, String comment) {
}

record InvoiceBody(Integer requestVersion, String invoiceNumber, BigDecimal amount, String currency,
		String documentReference, Boolean matchAttested, String paymentStatus) {
}

record CloseBody(Integer requestVersion, Integer rating, String scoreComment, String waiverNote) {
}

record ReceiptView(long id, long requestId, long purchaseOrderId, Long shipmentId, String number, String status,
		Instant receivedAt, Instant confirmedAt, String note, int version, long receivedByUserId, List<ReceiptLineView> lines,
		List<EvidenceView> evidence) {
}

record ReceiptLineView(long id, long purchaseOrderLineId, Long shipmentLineId, long requestLineId, int lineNo,
		String description, BigDecimal quantityReceived, BigDecimal quantityAccepted, BigDecimal quantityRejected,
		BigDecimal quantityDamaged, String condition, String note) {
}

record EvidenceView(long id, String kind, String referenceNumber, String note, String fileName, Instant uploadedAt) {
}

record InvoiceView(long id, long requestId, long purchaseOrderId, String invoiceNumber, BigDecimal amount,
		String currency, String documentReference, boolean matchAttested, String paymentStatus, Instant recordedAt,
		int version) {
}

record ClosureView(boolean ready, List<String> missing, boolean closed, Integer rating) {
}

record ReceiptDossierView(List<ReceiptView> receipts, InvoiceView invoice, ClosureView closure,
		boolean serviceAccepted, boolean shortfallAccepted, PurchaseOrderSummary purchaseOrder) {
}

record PurchaseOrderSummary(String currency, BigDecimal total, List<PurchaseOrderLineSummary> lines) {
}

record PurchaseOrderLineSummary(long id, String description, BigDecimal quantity, String unit, BigDecimal received,
		BigDecimal receivable) {
}

record ShortfallBody(Integer requestVersion, String reason) {
}

record InvoicePaidBody(Integer requestVersion) {
}

record InvoiceAttestBody(Integer requestVersion) {
}
