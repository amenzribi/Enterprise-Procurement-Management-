package com.epi.procurement.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

/**
 * Sprint 1 database safety (audit DB-3, DB-4): a request blocked by another transaction's lock gets an answer
 * within the lock timeout instead of waiting without limit, and expired tokens are purged after their retention.
 */
class DatabaseWaitsIntegrationTests extends SqlServerTestSupport {

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	ExpiredTokenPurge purge;

	@Value("${app.database.lock-timeout-ms}")
	long lockTimeoutMs;

	@Test
	void aRequestBlockedByAnotherTransactionIsAnsweredBusyWithinTheLockTimeout() throws Exception {
		ApiClient admin = signedIn("global.admin@demo.local");
		long claire = userId("requester.fr@demo.local");
		admin.get("/api/v1/admin/accounts/" + claire).expectStatus(200);

		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Thread holder = new Thread(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			// An uncommitted write, as left by a stuck transaction.
			jdbc.update("UPDATE user_account SET display_name = display_name WHERE id = ?", claire);
			locked.countDown();
			try {
				release.await(lockTimeoutMs * 4, TimeUnit.MILLISECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}));
		holder.start();
		try {
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			long started = System.nanoTime();
			ApiClient.Response blocked = admin.get("/api/v1/admin/accounts/" + claire);
			long waitedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

			blocked.expectStatus(503);
			assertThat(blocked.code()).isEqualTo("busy");
			assertThat(blocked.header("Retry-After")).isEqualTo("2");
			assertThat(blocked.body()).doesNotContain("SQLServerException", "Lock request");
			assertThat(waitedMs).isBetween(lockTimeoutMs - 500, lockTimeoutMs + 4000);
		}
		finally {
			release.countDown();
			holder.join(TimeUnit.SECONDS.toMillis(20));
		}
		// The connection that timed out is usable again and the application recovers at once.
		admin.get("/api/v1/admin/accounts/" + claire).expectStatus(200);
	}

	@Test
	void everyPooledConnectionCarriesTheLockTimeout() {
		for (int i = 0; i < 15; i++) {
			assertThat(jdbc.queryForObject("SELECT @@LOCK_TIMEOUT", Long.class)).isEqualTo(lockTimeoutMs);
		}
	}

	@Test
	void tokensAreDeletedOnlyAfterTheRetentionThatFollowsTheirExpiry() {
		Instant now = clock.instant();
		long user = userId("requester.fr@demo.local");
		String old = insertResetToken(user, now.minus(Duration.ofDays(91)));
		String recent = insertResetToken(user, now.minus(Duration.ofDays(89)));
		String live = insertResetToken(user, now.plus(Duration.ofHours(1)));

		Map<String, Integer> deleted = purge.purge();

		assertThat(deleted).containsOnlyKeys("approval_link_token", "quote_link_token", "password_reset_token");
		assertThat(deleted.get("password_reset_token")).isGreaterThanOrEqualTo(1);
		assertThat(tokenExists(old)).isFalse();
		assertThat(tokenExists(recent)).isTrue();
		assertThat(tokenExists(live)).isTrue();
		// Running again changes nothing.
		assertThat(purge.purge().get("password_reset_token")).isZero();
		jdbc.update("DELETE FROM password_reset_token WHERE token_hash IN (?, ?)", recent, live);
	}

	private String insertResetToken(long user, Instant expiresAt) {
		String hash = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
		jdbc.update("INSERT INTO password_reset_token (user_id, token_hash, purpose, expires_at, created_at) "
				+ "VALUES (?, ?, 'RESET', ?, ?)", user, hash, UtcTimes.db(expiresAt),
				UtcTimes.db(expiresAt.minus(Duration.ofDays(1))));
		return hash;
	}

	private boolean tokenExists(String hash) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_token WHERE token_hash = ?", Integer.class,
				hash) > 0;
	}

}
