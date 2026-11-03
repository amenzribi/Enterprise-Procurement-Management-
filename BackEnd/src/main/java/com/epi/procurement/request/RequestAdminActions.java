package com.epi.procurement.request;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Explicit administrative action matrix for GLOBAL_ADMIN request administration. Ordinary business roles never use
 * this matrix; it does not grant buyer/approver powers.
 *
 * <p>Principles: hard-delete only eligible drafts; cancel before commitment; archive terminal history; never invent
 * PO/financial reversal; never reuse numbers.
 */
final class RequestAdminActions {

	/** Protected demo UAT record — never hard-deleted by cleanup or admin delete. */
	static final String PROTECTED_NUMBER = "DEMO-TN-PLT-PR-2026-000490";

	static final int MAX_CLEANUP_BATCH = 50;

	enum Action {
		VIEW, ARCHIVE, RESTORE, CANCEL, DELETE_DRAFT, RETURN_FOR_REVISION, CORRECT_DRAFT
	}

	record Decision(Action action, boolean allowed, String reasonCode, String explanation) {
	}

	record Matrix(List<Decision> decisions) {
		boolean allows(Action action) {
			return decisions.stream().anyMatch(d -> d.action() == action && d.allowed());
		}

		Decision of(Action action) {
			return decisions.stream().filter(d -> d.action() == action).findFirst()
				.orElse(new Decision(action, false, "unknown-action", "Action is not defined for this request."));
		}

		List<String> allowedCodes() {
			return decisions.stream().filter(Decision::allowed).map(d -> d.action().name()).toList();
		}
	}

	record LinkCounts(int approvals, int rfqs, int quotations, int awards, int purchaseOrders, int shipments,
			int receipts, int invoices) {
		boolean hasBusinessEvidence() {
			return approvals > 0 || rfqs > 0 || quotations > 0 || awards > 0 || purchaseOrders > 0 || shipments > 0
					|| receipts > 0 || invoices > 0;
		}

		boolean hasCommitmentOrLater() {
			return awards > 0 || purchaseOrders > 0 || shipments > 0 || receipts > 0 || invoices > 0;
		}
	}

	private RequestAdminActions() {
	}

	static Matrix evaluate(PurchaseRequest request, LinkCounts links) {
		List<Decision> decisions = new ArrayList<>();
		decisions.add(new Decision(Action.VIEW, true, "ok", "Visible within the administrator's installation group."));

		boolean archived = request.isArchived();
		RequestStatus status = request.getStatus();
		boolean protectedNumber = PROTECTED_NUMBER.equals(request.getNumber());

		decisions.add(archiveDecision(status, archived));
		decisions.add(restoreDecision(archived));
		decisions.add(cancelDecision(status, archived, links));
		decisions.add(deleteDecision(request, status, archived, links, protectedNumber));
		decisions.add(returnDecision(status, archived));
		decisions.add(correctDraftDecision(status, archived));
		return new Matrix(List.copyOf(decisions));
	}

	private static Decision archiveDecision(RequestStatus status, boolean archived) {
		if (archived) {
			return new Decision(Action.ARCHIVE, false, "already-archived", "The request is already archived.");
		}
		Set<RequestStatus> archivable = EnumSet.of(RequestStatus.CLOSED, RequestStatus.CANCELLED, RequestStatus.REJECTED);
		if (!archivable.contains(status)) {
			return new Decision(Action.ARCHIVE, false, "status-not-terminal",
					"Archive is only for closed, cancelled or rejected requests. Cancel or complete the workflow first.");
		}
		return new Decision(Action.ARCHIVE, true, "ok",
				"Removes the request from everyday active lists while preserving history. Does not cancel orders or reverse receipts.");
	}

	private static Decision restoreDecision(boolean archived) {
		if (!archived) {
			return new Decision(Action.RESTORE, false, "not-archived", "Only archived requests can be restored.");
		}
		return new Decision(Action.RESTORE, true, "ok",
				"Returns the request to normal lists. Does not reopen a cancelled or closed business state.");
	}

	private static Decision cancelDecision(RequestStatus status, boolean archived, LinkCounts links) {
		if (archived) {
			return new Decision(Action.CANCEL, false, "archived", "Restore before cancelling, or leave archived history as-is.");
		}
		if (status == RequestStatus.DRAFT) {
			return new Decision(Action.CANCEL, false, "use-delete",
					"Drafts are removed with Delete when eligible, not Cancel.");
		}
		if (status == RequestStatus.CANCELLED) {
			return new Decision(Action.CANCEL, false, "already-cancelled", "The request is already cancelled.");
		}
		if (status == RequestStatus.CLOSED) {
			return new Decision(Action.CANCEL, false, "already-closed",
					"Closed requests are archived, not cancelled. Cancellation would rewrite completed history.");
		}
		if (status.rank() >= RequestStatus.ORDERED.rank() || links.hasCommitmentOrLater()) {
			return new Decision(Action.CANCEL, false, "commitment-exists",
					"Ordered / shipped / received / invoiced requests cannot be hard-cancelled here. No PO cancellation or financial reversal workflow is implemented; use existing operational corrections outside this admin tool.");
		}
		return new Decision(Action.CANCEL, true, "ok",
				"Ends the workflow, cancels pending approvals and public approval links, preserves completed decisions and the request number.");
	}

	private static Decision deleteDecision(PurchaseRequest request, RequestStatus status, boolean archived,
			LinkCounts links, boolean protectedNumber) {
		if (protectedNumber) {
			return new Decision(Action.DELETE_DRAFT, false, "protected-record",
					"This reference is protected (UAT pump seals) and must not be permanently deleted.");
		}
		if (archived) {
			return new Decision(Action.DELETE_DRAFT, false, "archived",
					"Archived records are retained. Restore only if a draft somehow needs correction; otherwise keep history.");
		}
		if (status != RequestStatus.DRAFT) {
			return new Decision(Action.DELETE_DRAFT, false, "not-draft",
					"Permanent deletion is only for drafts without retained business dependencies. Use Cancel or Archive for submitted work.");
		}
		if (links.hasBusinessEvidence()) {
			return new Decision(Action.DELETE_DRAFT, false, "has-dependencies",
					"Linked approvals or purchasing evidence exists; permanent deletion would destroy the audit chain.");
		}
		if (request.getNumber() != null && !request.getNumber().isBlank()) {
			// Submitted numbers are never on drafts in normal flow; defend anyway.
			return new Decision(Action.DELETE_DRAFT, false, "has-number",
					"A business number was assigned; numbers are never reused, so hard-delete is blocked.");
		}
		return new Decision(Action.DELETE_DRAFT, true, "ok",
				"Permanently removes an eligible draft and its lines/transitions. An administrative audit event is kept.");
	}

	private static Decision returnDecision(RequestStatus status, boolean archived) {
		if (archived) {
			return new Decision(Action.RETURN_FOR_REVISION, false, "archived", "Archived requests cannot be returned.");
		}
		if (status != RequestStatus.IN_APPROVAL && status != RequestStatus.CREATED) {
			return new Decision(Action.RETURN_FOR_REVISION, false, "wrong-status",
					"Administrative return is only for requests in approval (or legacy CREATED awaiting circuit).");
		}
		return new Decision(Action.RETURN_FOR_REVISION, true, "ok",
				"Returns the request for author revision through the normal workflow; pending approval tasks are cancelled. Does not bypass self-approval rules on resubmission.");
	}

	private static Decision correctDraftDecision(RequestStatus status, boolean archived) {
		if (archived) {
			return new Decision(Action.CORRECT_DRAFT, false, "archived", "Archived drafts cannot be corrected here.");
		}
		if (status != RequestStatus.DRAFT) {
			return new Decision(Action.CORRECT_DRAFT, false, "not-draft",
					"Administrative field correction is limited to drafts. For submitted requests use Return, Cancel or Archive — not silent rewrite.");
		}
		return new Decision(Action.CORRECT_DRAFT, true, "ok",
				"Allows limited administrative correction of draft title and justification with an audited reason.");
	}

}
