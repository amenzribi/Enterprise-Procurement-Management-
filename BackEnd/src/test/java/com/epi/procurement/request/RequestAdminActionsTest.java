package com.epi.procurement.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.epi.procurement.request.RequestAdminActions.Action;
import com.epi.procurement.request.RequestAdminActions.LinkCounts;

class RequestAdminActionsTest {

	@Test
	void draftWithoutLinksMayBeDeletedAndCorrected() {
		PurchaseRequest draft = draft("Cleanup draft");
		var matrix = RequestAdminActions.evaluate(draft, emptyLinks());
		assertThat(matrix.allows(Action.DELETE_DRAFT)).isTrue();
		assertThat(matrix.allows(Action.CORRECT_DRAFT)).isTrue();
		assertThat(matrix.allows(Action.CANCEL)).isFalse();
		assertThat(matrix.allows(Action.ARCHIVE)).isFalse();
	}

	@Test
	void protectedNumberNeverHardDeleted() {
		PurchaseRequest draft = draft("UAT");
		draft.markSubmitted(RequestAdminActions.PROTECTED_NUMBER, Instant.parse("2026-01-01T00:00:00Z"));
		// Force DRAFT after number for matrix guard (defensive).
		var matrix = RequestAdminActions.evaluate(draft, emptyLinks());
		assertThat(matrix.of(Action.DELETE_DRAFT).reasonCode()).isEqualTo("protected-record");
	}

	@Test
	void inApprovalMayReturnOrCancelNotDelete() {
		PurchaseRequest request = draft("In approval");
		request.markSubmitted("DEMO-TN-PLT-PR-2026-009999", Instant.parse("2026-01-02T00:00:00Z"));
		request.transitionTo(RequestStatus.IN_APPROVAL, 1L, Instant.parse("2026-01-02T00:00:00Z"));
		var matrix = RequestAdminActions.evaluate(request, emptyLinks());
		assertThat(matrix.allows(Action.RETURN_FOR_REVISION)).isTrue();
		assertThat(matrix.allows(Action.CANCEL)).isTrue();
		assertThat(matrix.allows(Action.DELETE_DRAFT)).isFalse();
		assertThat(matrix.allows(Action.CORRECT_DRAFT)).isFalse();
	}

	@Test
	void orderedOrPoBlocksCancelAndDelete() {
		PurchaseRequest request = draft("Ordered");
		request.markSubmitted("DEMO-TN-PLT-PR-2026-008888", Instant.parse("2026-01-03T00:00:00Z"));
		request.transitionTo(RequestStatus.ORDERED, 1L, Instant.parse("2026-01-03T00:00:00Z"));
		var matrix = RequestAdminActions.evaluate(request, new LinkCounts(0, 0, 0, 0, 1, 0, 0, 0));
		assertThat(matrix.allows(Action.CANCEL)).isFalse();
		assertThat(matrix.of(Action.CANCEL).reasonCode()).isEqualTo("commitment-exists");
		assertThat(matrix.allows(Action.DELETE_DRAFT)).isFalse();
		assertThat(matrix.allows(Action.ARCHIVE)).isFalse();
	}

	@Test
	void closedMayArchiveAndRestoreCycle() {
		PurchaseRequest request = draft("Closed");
		request.markSubmitted("DEMO-TN-PLT-PR-2026-007777", Instant.parse("2026-01-04T00:00:00Z"));
		request.transitionTo(RequestStatus.CLOSED, 1L, Instant.parse("2026-01-04T00:00:00Z"));
		var open = RequestAdminActions.evaluate(request, emptyLinks());
		assertThat(open.allows(Action.ARCHIVE)).isTrue();
		assertThat(open.allows(Action.CANCEL)).isFalse();
		request.archive(9L, "cleanup lists", Instant.parse("2026-01-05T00:00:00Z"));
		var archived = RequestAdminActions.evaluate(request, emptyLinks());
		assertThat(archived.allows(Action.RESTORE)).isTrue();
		assertThat(archived.allows(Action.ARCHIVE)).isFalse();
	}

	@Test
	void draftWithApprovalEvidenceCannotBeDeleted() {
		PurchaseRequest draft = draft("Has task");
		var matrix = RequestAdminActions.evaluate(draft, new LinkCounts(1, 0, 0, 0, 0, 0, 0, 0));
		assertThat(matrix.allows(Action.DELETE_DRAFT)).isFalse();
		assertThat(matrix.of(Action.DELETE_DRAFT).reasonCode()).isEqualTo("has-dependencies");
	}

	private static PurchaseRequest draft(String title) {
		Instant now = Instant.parse("2026-01-01T00:00:00Z");
		PurchaseRequest request = new PurchaseRequest(1L, 10L, 10L, 10L, "TND", 100L, 100L, now);
		request.setTitle(title);
		request.setTotalAmount(BigDecimal.TEN);
		return request;
	}

	private static LinkCounts emptyLinks() {
		return new LinkCounts(0, 0, 0, 0, 0, 0, 0, 0);
	}

}
