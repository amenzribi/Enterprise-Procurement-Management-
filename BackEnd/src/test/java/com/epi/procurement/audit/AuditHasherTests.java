package com.epi.procurement.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class AuditHasherTests {

	private static final Instant AT = Instant.parse("2026-10-24T09:15:00Z");

	private static final AuditRecord RECORD = new AuditRecord(7L, "Jane Doe", 3L, "PURCHASE_REQUEST", "PR-1",
			"APPROVE", "IN_APPROVAL", "APPROVED", "Looks good", "{\"amount\":\"100.0000\"}");

	@Test
	void hashIsDeterministicSha256() {
		byte[] first = AuditHasher.hash("company:3", 1, null, AT, RECORD);
		byte[] second = AuditHasher.hash("company:3", 1, null, AT, RECORD);

		assertThat(first).hasSize(32).isEqualTo(second);
	}

	@Test
	void everyFieldContributesToTheHash() {
		byte[] base = AuditHasher.hash("company:3", 2, new byte[32], AT, RECORD);

		assertThat(AuditHasher.hash("company:4", 2, new byte[32], AT, RECORD)).isNotEqualTo(base);
		assertThat(AuditHasher.hash("company:3", 3, new byte[32], AT, RECORD)).isNotEqualTo(base);
		assertThat(AuditHasher.hash("company:3", 2, HexFormat.of().parseHex("01".repeat(32)), AT, RECORD))
			.isNotEqualTo(base);
		assertThat(AuditHasher.hash("company:3", 2, new byte[32], AT.plusMillis(1), RECORD)).isNotEqualTo(base);
		AuditRecord changedComment = new AuditRecord(7L, "Jane Doe", 3L, "PURCHASE_REQUEST", "PR-1", "APPROVE",
				"IN_APPROVAL", "APPROVED", "Looks good!", "{\"amount\":\"100.0000\"}");
		assertThat(AuditHasher.hash("company:3", 2, new byte[32], AT, changedComment)).isNotEqualTo(base);
	}

	@Test
	void nullAndEmptyValuesAreDistinguished() {
		AuditRecord withNull = new AuditRecord(null, "System", null, "SYSTEM", "1", "START", null, null, null, null);
		AuditRecord withEmpty = new AuditRecord(null, "System", null, "SYSTEM", "1", "START", "", null, null, null);

		assertThat(AuditHasher.hash("global", 1, null, AT, withNull))
			.isNotEqualTo(AuditHasher.hash("global", 1, null, AT, withEmpty));
	}

	@Test
	void fieldBoundariesCannotBeShifted() {
		AuditRecord a = new AuditRecord(null, "ab", null, "c", "1", "X", null, null, null, null);
		AuditRecord b = new AuditRecord(null, "a", null, "bc", "1", "X", null, null, null, null);

		assertThat(AuditHasher.hash("global", 1, null, AT, a)).isNotEqualTo(AuditHasher.hash("global", 1, null, AT, b));
	}

	@Test
	void canonicalTimestampAlwaysHasMillisecondsInUtc() {
		assertThat(AuditHasher.TIMESTAMP.format(AT)).isEqualTo("2026-10-24T09:15:00.000Z");
		assertThat(AuditHasher.TIMESTAMP.format(Instant.parse("2026-01-02T03:04:05.120Z")))
			.isEqualTo("2026-01-02T03:04:05.120Z");
	}

	@Test
	void decimalsUseFixedScale() {
		assertThat(AuditHasher.decimal(new BigDecimal("100"))).isEqualTo("100.0000");
		assertThat(AuditHasher.decimal(new BigDecimal("1E+3"))).isEqualTo("1000.0000");
		assertThat(AuditHasher.decimal(new BigDecimal("0.12345"))).isEqualTo("0.1235");
	}

	@Test
	void chainKeyIsPerCompanyWithGlobalFallback() {
		assertThat(AuditHasher.chainKey(42L)).isEqualTo("company:42");
		assertThat(AuditHasher.chainKey(null)).isEqualTo("global");
	}

}
