package com.epi.procurement.auditcenter;

import java.time.Instant;

/**
 * State of the latest audit-chain verification, as shown in the Audit Center. Immutable: each step makes a new value.
 * Outcomes: {@code NONE} (never run since start), {@code RUNNING}, {@code VALID}, {@code INVALID} (a chain failed;
 * {@code invalidChain} and {@code firstInvalidEventId} say where), {@code ERROR} (the run could not complete).
 */
record Verification(String outcome, String startedBy, Instant startedAt, Instant finishedAt, int chainsTotal,
		int chainsChecked, long eventsChecked, String invalidChain, Long firstInvalidEventId) {

	static Verification none() {
		return new Verification("NONE", null, null, null, 0, 0, 0, null, null);
	}

	static Verification started(String by, Instant at) {
		return new Verification("RUNNING", by, at, null, 0, 0, 0, null, null);
	}

	boolean running() {
		return "RUNNING".equals(outcome);
	}

	Verification withChains(int total) {
		return new Verification(outcome, startedBy, startedAt, finishedAt, total, chainsChecked, eventsChecked,
				invalidChain, firstInvalidEventId);
	}

	Verification withProgress(long events) {
		return new Verification(outcome, startedBy, startedAt, finishedAt, chainsTotal, chainsChecked, events,
				invalidChain, firstInvalidEventId);
	}

	Verification chainDone() {
		return new Verification(outcome, startedBy, startedAt, finishedAt, chainsTotal, chainsChecked + 1, eventsChecked,
				invalidChain, firstInvalidEventId);
	}

	Verification succeeded(Instant at) {
		return new Verification("VALID", startedBy, startedAt, at, chainsTotal, chainsChecked, eventsChecked, null, null);
	}

	Verification failed(String chain, Long eventId, Instant at) {
		return new Verification("INVALID", startedBy, startedAt, at, chainsTotal, chainsChecked, eventsChecked, chain,
				eventId);
	}

	Verification error(Instant at) {
		return new Verification("ERROR", startedBy, startedAt, at, chainsTotal, chainsChecked, eventsChecked, invalidChain,
				firstInvalidEventId);
	}

}
