package com.epi.procurement.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class RequestModelTests {

	@Test
	void numbersAreFormattedWithSixDigitsAndWidenWithoutTruncation() {
		assertThat(RequestNumbering.format("DEMO-TN-PLT", 2026, 42)).isEqualTo("DEMO-TN-PLT-PR-2026-000042");
		assertThat(RequestNumbering.format("C1", 2027, 1_000_000)).isEqualTo("C1-PR-2027-1000000");
	}

	@Test
	void statusesAreOrderedAndLinesUseTheSharedProgression() {
		assertThat(RequestStatus.DRAFT.rank()).isEqualTo(1);
		assertThat(RequestStatus.ORDERED.rank()).isLessThan(RequestStatus.DELIVERED.rank());
		assertThat(RequestStatus.DELIVERED.rank()).isLessThan(RequestStatus.CLOSED.rank());
		List<String> lineStatuses = Arrays.stream(RequestStatus.values())
			.filter(RequestStatus::isLineStatus)
			.map(Enum::name)
			.toList();
		// Must match ck_purchase_request_line_status in V4.
		assertThat(lineStatuses).containsExactly("DRAFT", "CREATED", "ORDERED", "ORDER_CONFIRMED", "IN_PRODUCTION",
				"READY", "SHIPPED", "AT_PORT", "IN_CUSTOMS", "IN_WAREHOUSE", "DELIVERED", "PARTIALLY_RECEIVED",
				"NON_CONFORMING", "ACCEPTED", "INVOICED", "PAYMENT_PENDING", "PAID", "CLOSED", "CANCELLED");
		assertThat(RequestStatus.CREATED.stage()).isEqualTo(Stage.REQUEST);
		assertThat(RequestStatus.IN_APPROVAL.stage()).isEqualTo(Stage.APPROVAL);
	}

	@Test
	void integerDigitLimitMatchesDecimal19Scale4() {
		assertThat(RequestValidator.exceedsIntegerDigits(new BigDecimal("999999999999999.9999"))).isFalse();
		assertThat(RequestValidator.exceedsIntegerDigits(new BigDecimal("1000000000000000"))).isTrue();
		assertThat(RequestValidator.exceedsIntegerDigits(new BigDecimal("1E+3"))).isFalse();
	}

}
