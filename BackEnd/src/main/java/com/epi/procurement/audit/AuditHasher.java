package com.epi.procurement.audit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Computes the SHA-256 hash of an audit event from a fixed, documented canonical serialization (DR-04).
 * <p>
 * Canonical form: the fields below, in this exact order, each written as
 * {@code <UTF-8 byte length>:<value>|}, or {@code -1:|} for null, then UTF-8 encoded and hashed:
 * <ol>
 * <li>chain key</li>
 * <li>chain sequence (decimal)</li>
 * <li>previous hash (lower-case hex)</li>
 * <li>occurred at, ISO-8601 UTC with exactly three fraction digits ({@code 2026-10-24T09:15:00.000Z})</li>
 * <li>actor user id (decimal)</li>
 * <li>actor label</li>
 * <li>company id (decimal)</li>
 * <li>entity type</li>
 * <li>entity id</li>
 * <li>action</li>
 * <li>from status</li>
 * <li>to status</li>
 * <li>comment</li>
 * <li>payload JSON, exactly as stored</li>
 * </ol>
 * Decimal amounts placed in future fields must use {@link #decimal(BigDecimal)} (plain notation, scale 4).
 * Changing this format invalidates every stored hash, so it must only ever be extended by a versioned scheme.
 */
final class AuditHasher {

	static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
		.withZone(ZoneOffset.UTC);

	private AuditHasher() {
	}

	static byte[] hash(String chainKey, long chainSeq, byte[] previousHash, Instant occurredAt, AuditRecord record) {
		StringBuilder canonical = new StringBuilder(256);
		append(canonical, chainKey);
		append(canonical, Long.toString(chainSeq));
		append(canonical, previousHash == null ? null : HexFormat.of().formatHex(previousHash));
		append(canonical, TIMESTAMP.format(occurredAt));
		append(canonical, record.actorUserId() == null ? null : record.actorUserId().toString());
		append(canonical, record.actorLabel());
		append(canonical, record.companyId() == null ? null : record.companyId().toString());
		append(canonical, record.entityType());
		append(canonical, record.entityId());
		append(canonical, record.action());
		append(canonical, record.fromStatus());
		append(canonical, record.toStatus());
		append(canonical, record.comment());
		append(canonical, record.payloadJson());
		try {
			return MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every Java platform", ex);
		}
	}

	static String decimal(BigDecimal value) {
		return value == null ? null : value.setScale(4, RoundingMode.HALF_UP).toPlainString();
	}

	static String chainKey(Long companyId) {
		return companyId == null ? "global" : "company:" + companyId;
	}

	private static void append(StringBuilder target, String value) {
		if (value == null) {
			target.append("-1:");
		}
		else {
			target.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
		}
		target.append('|');
	}

}
