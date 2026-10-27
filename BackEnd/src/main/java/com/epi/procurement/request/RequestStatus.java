package com.epi.procurement.request;

/**
 * One ordered status list shared by request headers and lines (S1DR-08). The declaration order is the rank, so
 * "least advanced line" can be computed for the header later (assumption A2). Header-only statuses cover the
 * phases before the order (approval, purchasing, sourcing, evaluation) plus REJECTED and CONTRACT_SIGNED.
 * Codes are stable; labels live in the frontend translation files. Sprint 1 implements DRAFT -> CREATED only.
 * "Request information" (spec §7.2) will be a flag on the approval step, not a status.
 */
enum RequestStatus {

	DRAFT(Stage.REQUEST, true),
	CREATED(Stage.REQUEST, true),
	IN_APPROVAL(Stage.APPROVAL, false),
	REVISION_REQUESTED(Stage.APPROVAL, false),
	REJECTED(Stage.APPROVAL, false),
	APPROVED(Stage.APPROVAL, false),
	IN_PURCHASING_REVIEW(Stage.PURCHASING, false),
	ASSIGNED_TO_BUYER(Stage.PURCHASING, false),
	SOURCING(Stage.SOURCING, false),
	QUOTES_RECEIVED(Stage.SOURCING, false),
	TECHNICAL_VALIDATION(Stage.EVALUATION, false),
	COMMERCIAL_VALIDATION(Stage.EVALUATION, false),
	MANAGEMENT_VALIDATION(Stage.EVALUATION, false),
	ORDERED(Stage.COMMITMENT, true),
	CONTRACT_SIGNED(Stage.COMMITMENT, false),
	ORDER_CONFIRMED(Stage.COMMITMENT, true),
	IN_PRODUCTION(Stage.SHIPMENT, true),
	READY(Stage.SHIPMENT, true),
	SHIPPED(Stage.SHIPMENT, true),
	AT_PORT(Stage.SHIPMENT, true),
	IN_CUSTOMS(Stage.SHIPMENT, true),
	IN_WAREHOUSE(Stage.SHIPMENT, true),
	DELIVERED(Stage.RECEIPT, true),
	PARTIALLY_RECEIVED(Stage.RECEIPT, true),
	NON_CONFORMING(Stage.RECEIPT, true),
	ACCEPTED(Stage.RECEIPT, true),
	INVOICED(Stage.FINANCE, true),
	PAYMENT_PENDING(Stage.FINANCE, true),
	PAID(Stage.FINANCE, true),
	CLOSED(Stage.CLOSURE, true),
	CANCELLED(Stage.CLOSURE, true);

	private final Stage stage;

	private final boolean lineStatus;

	RequestStatus(Stage stage, boolean lineStatus) {
		this.stage = stage;
		this.lineStatus = lineStatus;
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

	int rank() {
		return ordinal() + 1;
	}

}
