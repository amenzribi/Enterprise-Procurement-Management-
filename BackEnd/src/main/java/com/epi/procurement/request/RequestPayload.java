package com.epi.procurement.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Body of {@code POST /api/v1/requests} (create draft) and {@code PUT /api/v1/requests/{id}} (replace draft).
 * <p>
 * A save replaces the whole request: header and the complete, ordered list of lines (S1DR-06). Fields the server
 * controls ({@code number}, {@code status}, {@code enteredById}, {@code chargedCompanyId}, {@code buyingCompanyId},
 * {@code origin}, {@code totalAmount}) are declared only so that a client trying to set them gets 422
 * {@code not-editable} instead of being silently ignored (mass assignment, S1DR-07). Line fields such as
 * {@code lineNo}, {@code lineTotal} or {@code status} are derived and ignored.
 * <p>
 * Lines are matched by {@code lines[].id} (S1CR-07): a line with the id of an existing line of this request updates
 * it, a line without an id is new, and existing lines whose id is not sent are deleted. Line numbers are stable
 * identifiers: kept lines keep theirs and new lines get the next free number (gaps are possible).
 *
 * @param version the version the client last read; required on update, ignored on create
 * @param requestingCompanyId optional, defaults to the user's company; another company requires ENTER_ON_BEHALF there
 * @param requesterId on create: optional, defaults to the signed-in user; on update: optional, omitted means
 * unchanged (S1CR-04). Another user than the signed-in one requires ENTER_ON_BEHALF
 */
record RequestPayload(Integer version, String title, Long categoryId, String priority, String purchaseType,
		String currency, Long requestingCompanyId, Long departmentId, Long siteId, Long costCenterId, Long requesterId,
		String beneficiary, Long beneficiarySiteId, String project, String justification, String urgencyJustification,
		String budgetLineRef, String equipmentRef, String maintenanceOrderRef, LocalDate requiredDate,
		String deliveryLocation, List<LinePayload> lines, Object number, Object status, Object enteredById,
		Object chargedCompanyId, Object buyingCompanyId, Object origin, Object totalAmount) {

	RequestPayload {
		lines = lines == null ? List.of() : lines;
	}

	RequestPayload withRequesterId(Long requester) {
		return new RequestPayload(version, title, categoryId, priority, purchaseType, currency, requestingCompanyId,
				departmentId, siteId, costCenterId, requester, beneficiary, beneficiarySiteId, project, justification,
				urgencyJustification, budgetLineRef, equipmentRef, maintenanceOrderRef, requiredDate, deliveryLocation,
				lines, number, status, enteredById, chargedCompanyId, buyingCompanyId, origin, totalAmount);
	}

	/**
	 * One line. {@code id} identifies an existing line of the request (null for a new line); {@code unit} is a
	 * unit-of-measure code from the form options; {@code lineKind} is GOODS or SERVICE (null keeps the line's kind, and
	 * a new line defaults to GOODS).
	 */
	record LinePayload(Long id, String description, String itemReference, BigDecimal quantity, String unit,
			BigDecimal estimatedUnitPrice, LocalDate requiredDate, String deliveryLocation, String lineKind) {
	}

	/**
	 * Body of {@code POST /api/v1/requests/{id}/submit}.
	 */
	record VersionPayload(Integer version) {
	}

}
