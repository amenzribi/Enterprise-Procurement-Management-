package com.epi.procurement.request;

/**
 * One ordered status list shared by request headers and lines (S1DR-08). The declaration order is the rank, so
 * "least advanced line" can be computed for the header later (assumption A2). Header-only statuses cover the
 * phases before the order (approval, purchasing, sourcing, evaluation) plus REJECTED and CONTRACT_SIGNED.
 * Codes are stable; labels live in the frontend translation files. Each code maps to one of the user-facing
 * statuses of the Solution Guide v3.0 §8 ({@link UserStatus}). Sprint 1 submissions are CREATED; from Sprint 2 a
 * submission enters IN_APPROVAL. "Ask a question" (spec §7.2) is a flag on the approval task, not a status.
 */
public enum RequestStatus {

	DRAFT(Stage.REQUEST, true, UserStatus.DRAFT),
	CREATED(Stage.REQUEST, true, UserStatus.SUBMITTED),
	IN_APPROVAL(Stage.APPROVAL, false, UserStatus.SUBMITTED),
	REVISION_REQUESTED(Stage.APPROVAL, false, UserStatus.RETURNED),
	REJECTED(Stage.APPROVAL, false, UserStatus.REJECTED),
	APPROVED(Stage.APPROVAL, false, UserStatus.APPROVED),
	IN_PURCHASING_REVIEW(Stage.PURCHASING, false, UserStatus.APPROVED),
	ASSIGNED_TO_BUYER(Stage.PURCHASING, false, UserStatus.ASSIGNED),
	PURCHASING_RETURNED(Stage.PURCHASING, false, UserStatus.RETURNED),
	SOURCING(Stage.SOURCING, false, UserStatus.QUOTING),
	QUOTES_RECEIVED(Stage.SOURCING, false, UserStatus.QUOTING),
	TECHNICAL_VALIDATION(Stage.EVALUATION, false, UserStatus.QUOTING),
	COMMERCIAL_VALIDATION(Stage.EVALUATION, false, UserStatus.QUOTING),
	MANAGEMENT_VALIDATION(Stage.EVALUATION, false, UserStatus.QUOTING),
	AWARDED(Stage.EVALUATION, false, UserStatus.AWARDED),
	ORDERED(Stage.COMMITMENT, true, UserStatus.ORDERED),
	CONTRACT_SIGNED(Stage.COMMITMENT, false, UserStatus.ORDERED),
	ORDER_CONFIRMED(Stage.COMMITMENT, true, UserStatus.ORDERED),
	IN_PRODUCTION(Stage.SHIPMENT, true, UserStatus.ORDERED),
	READY(Stage.SHIPMENT, true, UserStatus.ORDERED),
	SHIPPED(Stage.SHIPMENT, true, UserStatus.IN_TRANSIT),
	AT_PORT(Stage.SHIPMENT, true, UserStatus.IN_TRANSIT),
	IN_CUSTOMS(Stage.SHIPMENT, true, UserStatus.IN_TRANSIT),
	IN_WAREHOUSE(Stage.SHIPMENT, true, UserStatus.IN_TRANSIT),
	DELIVERED(Stage.RECEIPT, true, UserStatus.RECEIVED),
	PARTIALLY_RECEIVED(Stage.RECEIPT, true, UserStatus.RECEIVED),
	NON_CONFORMING(Stage.RECEIPT, true, UserStatus.RECEIVED),
	ACCEPTED(Stage.RECEIPT, true, UserStatus.RECEIVED),
	INVOICED(Stage.FINANCE, true, UserStatus.RECEIVED),
	PAYMENT_PENDING(Stage.FINANCE, true, UserStatus.RECEIVED),
	PAID(Stage.FINANCE, true, UserStatus.RECEIVED),
	CLOSED(Stage.CLOSURE, true, UserStatus.CLOSED),
	CANCELLED(Stage.CLOSURE, true, UserStatus.CANCELLED);

	private final Stage stage;

	private final boolean lineStatus;

	private final UserStatus userStatus;

	RequestStatus(Stage stage, boolean lineStatus, UserStatus userStatus) {
		this.stage = stage;
		this.lineStatus = lineStatus;
		this.userStatus = userStatus;
	}

	UserStatus userStatus() {
		return userStatus;
	}

	Stage stage() {
		return stage;
	}

	/**
	 * Whether lines can carry this status (headers can carry every status).
	 */
	boolean isLineStatus() {
		return lineStatus;
	}

	public int rank() {
		return ordinal() + 1;
	}

}
