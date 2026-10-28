package com.epi.procurement.request;

/**
 * The status shown to users (Solution Guide v3.0 §8): one clear status per request, computed from the detailed
 * {@link RequestStatus}. CANCELLED is an addition for requests withdrawn before an order (no v3.0 equivalent).
 */
public enum UserStatus {

	DRAFT, SUBMITTED, RETURNED, REJECTED, APPROVED, ASSIGNED, QUOTING, AWARDED, ORDERED, IN_TRANSIT, RECEIVED, CLOSED,
	CANCELLED

}
