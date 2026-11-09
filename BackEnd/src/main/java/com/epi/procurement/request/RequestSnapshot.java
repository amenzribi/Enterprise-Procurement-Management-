package com.epi.procurement.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A read-only view of a purchase request for other modules (approval, document). Obtained through
 * {@link RequestWorkflow}, which applies the caller's visibility or documents why it does not.
 */
public record RequestSnapshot(long id, String number, String title, String status, UserStatus userStatus,
		long groupId, long requestingCompanyId, long chargedCompanyId, long buyingCompanyId, Long departmentId,
		long requesterUserId, long enteredByUserId, String currency, BigDecimal totalAmount, Long categoryId,
		String purchaseType, String priority, LocalDate requiredDate, Instant submittedAt, int roundNo,
		long currentOwnerUserId, Instant ownerSince, int version, Long assignedBuyerUserId,
		String purchaseMode, boolean buyingCompanyProposed, List<LineSnapshot> lines) {

	public boolean isAuthor(long userId) {
		return requesterUserId == userId || enteredByUserId == userId;
	}

	public record LineSnapshot(long id, int lineNo, String description, BigDecimal quantity, String unit, String lineKind,
			boolean serviceConfirmed) {

		public boolean service() {
			return "SERVICE".equals(lineKind);
		}
	}

	public int serviceLineCount() {
		return (int) lines.stream().filter(LineSnapshot::service).count();
	}

	/** A service line still waits for purchasing's decision, so the order cannot be awarded (D-10). */
	public boolean classificationPending() {
		return lines.stream().anyMatch(l -> l.service() && !l.serviceConfirmed());
	}

}
