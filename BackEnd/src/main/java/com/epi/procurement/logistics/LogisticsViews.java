package com.epi.procurement.logistics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

record CreateShipmentBody(Integer requestVersion, String kind, String carrierName, String trackingReference,
		String originPlace, String destinationPlace, List<ShipmentLineBody> lines, List<MilestonePlanBody> milestonePlans) {
}

record ShipmentLineBody(long purchaseOrderLineId, BigDecimal quantity) {
}

record MilestonePlanBody(String code, LocalDate plannedDate) {
}

record MilestoneUpdateBody(Integer version, LocalDate plannedDate, LocalDate actualDate, String note) {
}

record DocumentBody(String kind, String referenceNumber, String note) {
}

record ShipmentView(long id, long requestId, long purchaseOrderId, String number, String kind, String status,
		String carrierName, String trackingReference, String originPlace, String destinationPlace, Instant createdAt,
		int version, List<ShipmentLineView> lines, List<MilestoneView> milestones, List<DocumentView> documents) {
}

record ShipmentLineView(long id, long purchaseOrderLineId, long requestLineId, int lineNo, String description,
		BigDecimal quantity, String unit) {
}

record MilestoneView(long id, String code, int sortOrder, LocalDate plannedDate, LocalDate actualDate, String note,
		boolean late) {
}

record DocumentView(long id, String kind, String referenceNumber, String note, String fileName, Instant uploadedAt) {
}

record LogisticsInboxItem(long requestId, String requestNumber, String requestTitle, String requestStatus, long poId,
		String poNumber, String poStatus, Long shipmentId, String shipmentNumber, String shipmentStatus,
		String shipmentKind, Instant poCreatedAt, boolean readyForReceipt) {
}

record LogisticsInboxPage(List<LogisticsInboxItem> items, int page, int size, long totalItems, int totalPages) {
}
