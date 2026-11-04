package com.epi.procurement.request;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Administrative action matrix for request administration.
 *
 * <p>{@code GLOBAL_ADMIN} may use the full matrix. Company {@code ADMIN} may only use draft inspection/correction and
 * draft hard-delete when in company scope — elevated submitted actions are denied at the service layer.
 */
final class RequestAdminActions {

	static final String PROTECTED_NUMBER = "DEMO-TN-PLT-PR-2026-000490";

	static final int MAX_CLEANUP_BATCH = 50;

	enum Action {
		VIEW, ARCHIVE, RESTORE, CANCEL, DELETE_DRAFT, RETURN_FOR_REVISION, CORRECT_DRAFT, CORRECT_SUBMITTED, REMOVE,
		RESTORE_REMOVED
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
		boolean archived = request.isArchived();
		boolean removed = request.isRemoved();
		RequestStatus status = request.getStatus();
		boolean protectedNumber = PROTECTED_NUMBER.equals(request.getNumber());

		decisions.add(new Decision(Action.VIEW, true, "ok", "Visible within the administrator's authorised scope."));
		decisions.add(archiveDecision(status, archived, removed));
		decisions.add(restoreDecision(archived, removed));
		decisions.add(cancelDecision(status, archived, removed, links));
		decisions.add(deleteDecision(request, status, archived, removed, links, protectedNumber));
		decisions.add(returnDecision(status, archived, removed));
		decisions.add(correctDraftDecision(status, archived, removed));
		decisions.add(correctSubmittedDecision(status, archived, removed, links));
		decisions.add(removeDecision(status, archived, removed, links));
		decisions.add(restoreRemovedDecision(removed, archived));
		return new Matrix(List.copyOf(decisions));
	}

	private static Decision archiveDecision(RequestStatus status, boolean archived, boolean removed) {
		if (removed) {
			return new Decision(Action.ARCHIVE, false, "removed",
					"Administratively removed requests are inspected from the Removed filter. Clear removal or leave history as-is.");
		}
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

	private static Decision restoreDecision(boolean archived, boolean removed) {
		if (removed) {
			return new Decision(Action.RESTORE, false, "removed", "Use Restore removed for administratively removed requests.");
		}
		if (!archived) {
			return new Decision(Action.RESTORE, false, "not-archived", "Only archived requests can be restored.");
		}
		return new Decision(Action.RESTORE, true, "ok",
				"Returns the request to normal lists. Does not reopen a cancelled or closed business state.");
	}

	private static Decision cancelDecision(RequestStatus status, boolean archived, boolean removed, LinkCounts links) {
		if (removed) {
			return new Decision(Action.CANCEL, false, "removed", "Removed requests cannot be cancelled again.");
		}
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
					"Ordered / shipped / received / invoiced requests cannot be cancelled here. No PO cancellation or financial reversal workflow is implemented.");
		}
		return new Decision(Action.CANCEL, true, "ok",
				"Ends the workflow, cancels pending approvals and public approval links, preserves completed decisions and the request number.");
	}

	private static Decision deleteDecision(PurchaseRequest request, RequestStatus status, boolean archived,
			boolean removed, LinkCounts links, boolean protectedNumber) {
		if (protectedNumber) {
			return new Decision(Action.DELETE_DRAFT, false, "protected-record",
					"This reference is protected (UAT pump seals) and must not be permanently deleted.");
		}
		if (removed) {
			return new Decision(Action.DELETE_DRAFT, false, "removed",
					"Administratively removed requests keep their history. Permanent deletion is not available.");
		}
		if (archived) {
			return new Decision(Action.DELETE_DRAFT, false, "archived",
					"Archived records are retained. Restore only if a draft somehow needs correction; otherwise keep history.");
		}
		if (status != RequestStatus.DRAFT) {
			return new Decision(Action.DELETE_DRAFT, false, "not-draft",
					"Permanent deletion is only for drafts. Use Cancel, administrative Remove, or Archive for submitted work.");
		}
		if (links.hasBusinessEvidence()) {
			return new Decision(Action.DELETE_DRAFT, false, "has-dependencies",
					"Linked approvals or purchasing evidence exists; permanent deletion would destroy the audit chain.");
		}
		if (request.getNumber() != null && !request.getNumber().isBlank()) {
			return new Decision(Action.DELETE_DRAFT, false, "has-number",
					"A business number was assigned; numbers are never reused, so hard-delete is blocked.");
		}
		return new Decision(Action.DELETE_DRAFT, true, "ok",
				"Permanently removes an eligible draft and its lines/transitions. An administrative audit event is kept.");
	}

	private static Decision returnDecision(RequestStatus status, boolean archived, boolean removed) {
		if (removed || archived) {
			return new Decision(Action.RETURN_FOR_REVISION, false, removed ? "removed" : "archived",
					"Return for revision is only for active requests in approval.");
		}
		if (status != RequestStatus.IN_APPROVAL && status != RequestStatus.CREATED) {
			return new Decision(Action.RETURN_FOR_REVISION, false, "wrong-status",
					"Administrative return is only for requests in approval (or legacy CREATED awaiting circuit).");
		}
		return new Decision(Action.RETURN_FOR_REVISION, true, "ok",
				"Returns the request for author revision; pending approval tasks are cancelled. Does not bypass self-approval rules on resubmission.");
	}

	private static Decision correctDraftDecision(RequestStatus status, boolean archived, boolean removed) {
		if (removed || archived) {
			return new Decision(Action.CORRECT_DRAFT, false, removed ? "removed" : "archived",
					"Only active drafts can be corrected here.");
		}
		if (status != RequestStatus.DRAFT) {
			return new Decision(Action.CORRECT_DRAFT, false, "not-draft",
					"Use Correct submitted request for in-approval changes, or Return for author revision.");
		}
		return new Decision(Action.CORRECT_DRAFT, true, "ok",
				"Administrative correction of draft header and lines with a mandatory audited reason.");
	}

	private static Decision correctSubmittedDecision(RequestStatus status, boolean archived, boolean removed,
			LinkCounts links) {
		if (removed || archived) {
			return new Decision(Action.CORRECT_SUBMITTED, false, removed ? "removed" : "archived",
					"Only active submitted requests can receive an administrative correction.");
		}
		if (status == RequestStatus.DRAFT) {
			return new Decision(Action.CORRECT_SUBMITTED, false, "is-draft", "Use Correct draft for drafts.");
		}
		if (status.rank() >= RequestStatus.ORDERED.rank() || links.hasCommitmentOrLater()) {
			return new Decision(Action.CORRECT_SUBMITTED, false, "commitment-exists",
					"No versioned amendment workflow for awards, purchase orders, shipments, receipts or invoices. Material fields cannot be rewritten here; snapshots stay intact.");
		}
		if (status != RequestStatus.IN_APPROVAL && status != RequestStatus.REVISION_REQUESTED
				&& status != RequestStatus.CREATED && status.rank() >= RequestStatus.IN_PURCHASING_REVIEW.rank()) {
			return new Decision(Action.CORRECT_SUBMITTED, false, "wrong-status",
					"Administrative correction with a new approval round is supported before purchasing commitment. Later stages are blocked.");
		}
		return new Decision(Action.CORRECT_SUBMITTED, true, "ok",
				"Applies audited field/line changes, invalidates pending approvals and unused links, recalculates approval matching and starts a new approval round. Prior decisions remain historical.");
	}

	private static Decision removeDecision(RequestStatus status, boolean archived, boolean removed, LinkCounts links) {
		if (removed) {
			return new Decision(Action.REMOVE, false, "already-removed", "The request is already administratively removed.");
		}
		if (archived) {
			return new Decision(Action.REMOVE, false, "archived", "Archived terminal history stays under Archive, not Remove.");
		}
		if (status == RequestStatus.DRAFT) {
			return new Decision(Action.REMOVE, false, "use-delete", "Use permanent Delete for eligible drafts.");
		}
		if (status.rank() >= RequestStatus.ORDERED.rank() || links.hasCommitmentOrLater()) {
			return new Decision(Action.REMOVE, false, "commitment-exists",
					"Requests with awards/PO/shipment/receipt/invoice cannot be logically removed here — that would orphan operational records. Use operational corrections or Archive when terminal.");
		}
		return new Decision(Action.REMOVE, true, "ok",
				"Logical deletion: hides from active work, cancels pending tasks and links, retains number, revisions and decisions. Distinct from Archive and permanent Delete.");
	}

	private static Decision restoreRemovedDecision(boolean removed, boolean archived) {
		if (archived) {
			return new Decision(Action.RESTORE_REMOVED, false, "archived", "Use Restore for archived terminal history.");
		}
		if (!removed) {
			return new Decision(Action.RESTORE_REMOVED, false, "not-removed", "Only administratively removed requests can be restored this way.");
		}
		return new Decision(Action.RESTORE_REMOVED, true, "ok",
				"Clears administrative removal so the request can appear again. Does not recreate obsolete pending approvals — correct or resubmit to start a new round if work must continue.");
	}

}
