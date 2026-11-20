package com.epi.procurement.platform.web;

import com.epi.procurement.platform.persistence.UtcTimes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import tools.jackson.databind.json.JsonMapper;

/**
 * Idempotent state-changing actions (S2-AC-13, DR-21). A client sends an {@code Idempotency-Key} header; the first
 * successful result is stored for 24 h and returned again for any replay with the same key and the same request.
 * Reusing a key for a different request gives 422 {@code idempotency-key-reused}. Without a key the action simply
 * runs. Failed actions are not stored, so a retry after an error runs again.
 * <p>
 * The action runs in its own transaction and the record is written right after it commits; a concurrent duplicate
 * either loses the business check (e.g. task no longer pending) or the record insert, and then receives the stored
 * result.
 */
@Component
public class Idempotency {

	public static final String HEADER = "Idempotency-Key";

	private static final Duration RETENTION = Duration.ofHours(24);

	private final JdbcTemplate jdbc;

	private final JsonMapper json;

	private final Clock clock;

	Idempotency(DataSource dataSource, JsonMapper json, ObjectProvider<Clock> clock) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.json = json;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	/**
	 * @param actorKey who acts (e.g. {@code user:42}); keys are scoped per actor
	 * @param key the client's Idempotency-Key header, may be null
	 * @param fingerprintSource method, path and body of the request
	 */
	public ResponseEntity<?> execute(String actorKey, String key, String fingerprintSource,
			Supplier<ResponseEntity<?>> action) {
		if (!StringUtils.hasText(key)) {
			return action.get();
		}
		if (key.length() > 100) {
			throw ApiProblemException.invalidParameter(HEADER);
		}
		String fingerprint = sha256(fingerprintSource);
		ResponseEntity<?> stored = stored(actorKey, key, fingerprint);
		if (stored != null) {
			return stored;
		}
		try {
			ResponseEntity<?> result = action.get();
			try {
				jdbc.update("""
						INSERT INTO idempotency_record (actor_key, idempotency_key, fingerprint, http_status, response_json,
						                                created_at)
						VALUES (?, ?, ?, ?, ?, ?)""", actorKey, key, fingerprint, result.getStatusCode().value(),
						result.getBody() == null ? null : json.writeValueAsString(result.getBody()),
						UtcTimes.db(clock.instant()));
			}
			catch (DuplicateKeyException ex) {
				ResponseEntity<?> first = stored(actorKey, key, fingerprint);
				return first != null ? first : result;
			}
			return result;
		}
		catch (ApiProblemException ex) {
			// A concurrent duplicate may have succeeded while this one lost the business check: replay its result.
			ResponseEntity<?> first = stored(actorKey, key, fingerprint);
			if (first != null) {
				return first;
			}
			throw ex;
		}
	}

	/**
	 * Deletes records past the 24 h retention (S2CR-14); returns the number deleted. Run hourly by
	 * {@link IdempotencyPurgeJob}.
	 */
	public int purge() {
		return jdbc.update("DELETE FROM idempotency_record WHERE created_at < ?",
				UtcTimes.db(clock.instant().minus(RETENTION)));
	}

	private ResponseEntity<?> stored(String actorKey, String key, String fingerprint) {
		List<Object[]> rows = jdbc.query("""
				SELECT fingerprint, http_status, response_json FROM idempotency_record
				WHERE actor_key = ? AND idempotency_key = ? AND created_at >= ?""",
				(rs, i) -> new Object[] { rs.getString(1), rs.getInt(2), rs.getString(3) }, actorKey, key,
				UtcTimes.db(clock.instant().minus(RETENTION)));
		if (rows.isEmpty()) {
			return null;
		}
		Object[] row = rows.get(0);
		if (!fingerprint.equals(row[0])) {
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "idempotency-key-reused",
					"This Idempotency-Key was already used for a different request.");
		}
		ResponseEntity.BodyBuilder builder = ResponseEntity.status((Integer) row[1]);
		return row[2] == null ? builder.build() : builder.contentType(MediaType.APPLICATION_JSON).body(row[2]);
	}

	private static String sha256(String text) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
