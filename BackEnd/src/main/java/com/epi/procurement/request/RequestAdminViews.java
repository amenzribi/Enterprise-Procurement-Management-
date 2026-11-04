package com.epi.procurement.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTOs for request administration ({@code /api/v1/admin/requests}).
 */
final class RequestAdminViews {

	private RequestAdminViews() {
	}

	record ActionAvailability(String action, boolean allowed, String reasonCode, String explanation) {
	}

	record LinkedCounts(int approvals, int rfqs, int quotations, int awards, int purchaseOrders, int shipments,
			int receipts, int invoices) {
	}

	record LinkedItem(String type, long id, String reference, String status) {
	}

	record LineView(Long id, short lineNo, String description, String itemReference, BigDecimal quantity, String unit,
			BigDecimal estimatedUnitPrice, BigDecimal lineTotal, LocalDate requiredDate, String deliveryLocation) {
	}

	record Summary(long id, String number, String title, String status, String userStatus, String stage,
			boolean archived, boolean removed, BigDecimal totalAmount, String currency, Instant updatedAt,
			Instant submittedAt, Instant archivedAt, Instant removedAt, Ref company, UserRef requester,
			List<String> allowedActions, List<ActionAvailability> actionMatrix, LinkedCounts links) {
	}

	record Page(List<Summary> items, int page, int size, long totalItems, int totalPages) {
	}

	record Detail(long id, String number, String title, String status, String userStatus, String stage, int version,
			boolean archived, boolean removed, Instant archivedAt, Long archivedByUserId, String archiveReason,
			Instant removedAt, Long removedByUserId, String removeReason, BigDecimal totalAmount, String currency,
			String justification, Long categoryId, String priority, String purchaseType, Long departmentId, Long siteId,
			Long costCenterId, LocalDate requiredDate, Instant createdAt, Instant updatedAt, Instant submittedAt,
			Ref company, UserRef requester, UserRef enteredBy, UserRef currentOwner, List<LineView> lines,
			List<ActionAvailability> actions, LinkedCounts linkCounts, List<LinkedItem> linkedItems,
			String nextStepSummary, String workflowNote, String administrativeStatus) {
	}

	record MutationResult(Detail detail, String message) {
	}

	record ReasonPayload(Integer version, String reason) {
	}

	/** Full administrative correction body (draft or submitted). */
	record AdminCorrectionPayload(Integer version, String reason, String title, Long categoryId, String priority,
			String purchaseType, String currency, Long departmentId, Long siteId, Long costCenterId,
			String justification, String urgencyJustification, LocalDate requiredDate, String deliveryLocation,
			String beneficiary, Long beneficiarySiteId, String project, String budgetLineRef, String equipmentRef,
			String maintenanceOrderRef, List<LinePayload> lines) {
		AdminCorrectionPayload {
			lines = lines == null ? List.of() : lines;
		}
	}

	record LinePayload(Long id, String description, String itemReference, BigDecimal quantity, String unit,
			BigDecimal estimatedUnitPrice, LocalDate requiredDate, String deliveryLocation) {
	}

	record CleanupFilter(String q, Long companyId, String status, Boolean archived, Boolean removed, Boolean demoOnly,
			Boolean draftsOnly, List<Long> ids) {
	}

	record CleanupCandidate(long id, String number, String title, String status, boolean archived, boolean removed,
			Ref company, LinkedCounts links, List<ActionAvailability> actions, String recommendedAction,
			String blockReason) {
	}

	record CleanupPreview(String previewToken, Instant expiresAt, int candidateCount, int deletableCount,
			int cancellableCount, int archivableCount, int blockedCount, List<CleanupCandidate> candidates,
			String impactSummary) {
	}

	record CleanupExecutePayload(String previewToken, List<Long> ids, String confirmation, String reason) {
	}

	record CleanupItemResult(long id, String number, String outcome, String detail) {
	}

	record CleanupExecuteResult(int processed, int succeeded, int skipped, List<CleanupItemResult> results) {
	}

	record Ref(long id, String code, String name) {
	}

	record UserRef(long id, String displayName) {
	}

}
