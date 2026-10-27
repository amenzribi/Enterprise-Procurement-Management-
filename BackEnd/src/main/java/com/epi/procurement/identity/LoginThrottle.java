package com.epi.procurement.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sign-in throttling instead of account locking (S1DR-03): failures are counted per (e-mail, client address) and per
 * client address; reaching a limit blocks that key for a while. An attacker can therefore not lock a known account
 * out from another address, and the caller never learns whether an account exists or is throttled.
 * <p>
 * State is held in memory, which matches the single-node deployment of the MVP (ADR-003); it resets on restart.
 * E-mails are stored only as hashes. The map is bounded (S1CR-01): counters whose window and block have passed are
 * swept regularly, and if a spray of distinct keys still exceeds {@link #MAX_ENTRIES}, the oldest are dropped.
 */
final class LoginThrottle {

	private final SecuritySettings.Login settings;

	private final Clock clock;

	static final int MAX_ENTRIES = 10_000;

	private static final int SWEEP_EVERY = 500;

	private final Map<String, Counter> counters = new ConcurrentHashMap<>();

	private final AtomicInteger recordedSinceSweep = new AtomicInteger();

	LoginThrottle(SecuritySettings.Login settings, Clock clock) {
		this.settings = settings;
		this.clock = clock;
	}

	boolean isBlocked(String email, String address) {
		Instant now = clock.instant();
		return blocked(accountKey(email, address), now) || blocked(addressKey(address), now);
	}

	/**
	 * Records a failure.
	 * @return true when this failure activated a block for the (e-mail, address) pair
	 */
	boolean recordFailure(String email, String address) {
		Instant now = clock.instant();
		if (recordedSinceSweep.incrementAndGet() >= SWEEP_EVERY || counters.size() >= MAX_ENTRIES) {
			recordedSinceSweep.set(0);
			sweep(now);
		}
		record(addressKey(address), settings.maxFailuresPerAddress(), now);
		return record(accountKey(email, address), settings.maxFailuresPerAccount(), now);
	}

	void recordSuccess(String email, String address) {
		counters.remove(accountKey(email, address));
	}

	int size() {
		return counters.size();
	}

	/**
	 * Drops counters that no longer matter (no failure in the window, no running block); then, if still too many,
	 * the least recently used ones.
	 */
	private void sweep(Instant now) {
		Instant windowStart = now.minus(settings.window());
		counters.entrySet().removeIf(entry -> {
			Counter counter = entry.getValue();
			synchronized (counter) {
				boolean blocking = counter.blockedUntil != null && now.isBefore(counter.blockedUntil);
				boolean recent = !counter.failures.isEmpty() && !counter.failures.peekLast().isBefore(windowStart);
				return !blocking && !recent;
			}
		});
		int excess = counters.size() - MAX_ENTRIES + MAX_ENTRIES / 10;
		if (excess > 0 && counters.size() >= MAX_ENTRIES) {
			counters.entrySet()
				.stream()
				.sorted(Comparator.comparing(entry -> entry.getValue().lastSeen))
				.limit(excess)
				.map(Map.Entry::getKey)
				.toList()
				.forEach(counters::remove);
		}
	}

	static String hashEmail(String email) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 8);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private boolean blocked(String key, Instant now) {
		Counter counter = counters.get(key);
		if (counter == null) {
			return false;
		}
		synchronized (counter) {
			return counter.blockedUntil != null && now.isBefore(counter.blockedUntil);
		}
	}

	private boolean record(String key, int limit, Instant now) {
		Counter counter = counters.computeIfAbsent(key, k -> new Counter());
		synchronized (counter) {
			Instant windowStart = now.minus(settings.window());
			while (!counter.failures.isEmpty() && counter.failures.peekFirst().isBefore(windowStart)) {
				counter.failures.removeFirst();
			}
			counter.failures.addLast(now);
			counter.lastSeen = now;
			boolean alreadyBlocked = counter.blockedUntil != null && now.isBefore(counter.blockedUntil);
			if (!alreadyBlocked && counter.failures.size() >= limit) {
				counter.blockedUntil = now.plus(settings.blockDuration());
				counter.failures.clear();
				return true;
			}
			return false;
		}
	}

	private static String accountKey(String email, String address) {
		return "account:" + hashEmail(email) + "|" + address;
	}

	private static String addressKey(String address) {
		return "address:" + address;
	}

	private static final class Counter {

		private final Deque<Instant> failures = new ArrayDeque<>();

		private Instant blockedUntil;

		private volatile Instant lastSeen = Instant.MIN;

	}

}
