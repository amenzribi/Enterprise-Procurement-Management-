package com.epi.procurement.auditcenter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.epi.procurement.audit.AuditChainVerification;
import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.audit.AuditSearch;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.platform.web.ApiProblemException;

import tools.jackson.databind.json.JsonMapper;

/**
 * The global administrators' Audit Center (Sprint 2, P1.3). Read-only over the trail; every use is recorded in it:
 * a new search ({@code AUDIT_SEARCHED}), an export ({@code AUDIT_EXPORTED}), a verification
 * ({@code AUDIT_VERIFICATION_STARTED}, {@code AUDIT_VERIFICATION_COMPLETED}). Company administrators and every other
 * role are refused (403), as the URL rule already does.
 */
@Service
class AuditCenter {

	private static final Logger log = LoggerFactory.getLogger(AuditCenter.class);

	/** Most rows one export may contain; beyond it the file says so and the export is recorded as truncated. */
	static final int EXPORT_LIMIT = 50_000;

	/** Events per verification statement: short reads, far below the 30 s transaction limit. */
	static final int VERIFY_BATCH = 2_000;

	private final AuditSearch search;

	private final AuditLog auditLog;

	private final JsonMapper json;

	private final Clock clock;

	/**
	 * Spring Boot's application executor, by name: with background jobs on, the task scheduler is an executor too,
	 * and an unqualified request is ambiguous (the application did not start; found by the Sprint 2 browser run).
	 */
	static final String APPLICATION_EXECUTOR = "applicationTaskExecutor";

	private final AsyncTaskExecutor executor;

	private final AtomicReference<Verification> verification = new AtomicReference<>(Verification.none());

	AuditCenter(AuditSearch search, AuditLog auditLog, JsonMapper json, ObjectProvider<Clock> clock,
			@Qualifier(APPLICATION_EXECUTOR) ObjectProvider<AsyncTaskExecutor> executor) {
		this.search = search;
		this.auditLog = auditLog;
		this.json = json;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
		this.executor = executor.getIfAvailable();
	}

	AuditSearch.Page search(CurrentUser user, AuditSearch.Filter filter, AuditSearch.Cursor cursor, int size) {
		requireGlobalAdmin(user);
		if (cursor == null) {
			// A new search, not each following page.
			record(user, "AUDIT_SEARCHED", describe(filter, Map.of()));
		}
		return search.search(filter, cursor, size);
	}

	AuditSearch.Entry event(CurrentUser user, long id) {
		requireGlobalAdmin(user);
		return search.find(id).orElseThrow(ApiProblemException::notFound);
	}

	/** Writes the CSV of the events matching {@code filter}, newest first, at most {@link #EXPORT_LIMIT} rows. */
	ExportResult export(CurrentUser user, AuditSearch.Filter filter, Writer out) {
		requireGlobalAdmin(user);
		int rows = 0;
		boolean truncated = false;
		try {
			out.write('﻿');
			out.write(AuditCsv.HEADER);
			AuditSearch.Cursor cursor = null;
			do {
				AuditSearch.Page page = search.search(filter, cursor, AuditSearch.MAX_PAGE);
				for (AuditSearch.Entry entry : page.items()) {
					if (rows >= EXPORT_LIMIT) {
						truncated = true;
						break;
					}
					out.write(AuditCsv.row(entry));
					rows++;
				}
				cursor = page.next();
			}
			while (cursor != null && !truncated);
			if (truncated) {
				out.write(AuditCsv.truncationNote(EXPORT_LIMIT));
			}
			out.flush();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		record(user, "AUDIT_EXPORTED", describe(filter, Map.of("rows", rows, "truncated", truncated)));
		return new ExportResult(rows, truncated);
	}

	record ExportResult(int rows, boolean truncated) {
	}

	/** Starts a verification of every chain in the background; one at a time. */
	Verification startVerification(CurrentUser user) {
		requireGlobalAdmin(user);
		Verification current = verification.get();
		Verification started = Verification.started(user.displayName(), clock.instant());
		if (current.running() || !verification.compareAndSet(current, started)) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "verification-running",
					"A verification is already running. Wait for its result.");
		}
		record(user, "AUDIT_VERIFICATION_STARTED", null);
		Runnable run = () -> verifyAll(user, started);
		if (executor != null) {
			executor.execute(run);
		}
		else {
			Thread.ofVirtual().name("audit-verification").start(run);
		}
		return started;
	}

	Verification verification(CurrentUser user) {
		requireGlobalAdmin(user);
		return verification.get();
	}

	private void verifyAll(CurrentUser user, Verification started) {
		Verification state = started;
		try {
			var chains = auditLog.chainKeys();
			state = state.withChains(chains.size());
			verification.set(state);
			long checkedBefore = 0;
			for (String chain : chains) {
				long base = checkedBefore;
				Verification running = state;
				AuditChainVerification result = auditLog.verifyChainInBatches(chain, VERIFY_BATCH,
						checked -> verification.set(running.withProgress(base + checked)));
				checkedBefore += result.eventsChecked();
				state = state.withProgress(checkedBefore).chainDone();
				if (!result.valid()) {
					state = state.failed(chain, result.firstInvalidEventId(), clock.instant());
					break;
				}
			}
			if (state.running()) {
				state = state.succeeded(clock.instant());
			}
		}
		catch (RuntimeException ex) {
			log.error("Audit verification could not complete", ex);
			state = state.error(clock.instant());
		}
		verification.set(state);
		Map<String, Object> outcome = new LinkedHashMap<>();
		outcome.put("outcome", state.outcome());
		outcome.put("chains", state.chainsChecked());
		outcome.put("events", state.eventsChecked());
		if (state.invalidChain() != null) {
			outcome.put("invalidChain", state.invalidChain());
			outcome.put("firstInvalidEventId", state.firstInvalidEventId());
		}
		record(user, "AUDIT_VERIFICATION_COMPLETED", json.writeValueAsString(outcome));
	}

	/** Checks access without doing anything, for callers that must refuse before streaming a response. */
	void requireAccess(CurrentUser user) {
		requireGlobalAdmin(user);
	}

	private static void requireGlobalAdmin(CurrentUser user) {
		if (!user.isGlobalAdmin()) {
			throw ApiProblemException.forbidden();
		}
	}

	private void record(CurrentUser user, String action, String payload) {
		auditLog.record(new AuditRecord(user.id(), user.email(), null, "AUDIT_TRAIL", "audit", action, null, null, null,
				payload));
	}

	private String describe(AuditSearch.Filter filter, Map<String, Object> extra) {
		Map<String, Object> details = new LinkedHashMap<>();
		putIfPresent(details, "from", filter.from());
		putIfPresent(details, "to", filter.to());
		putIfPresent(details, "companyId", filter.companyId());
		putIfPresent(details, "actorUserId", filter.actorUserId());
		putIfPresent(details, "action", filter.action());
		putIfPresent(details, "entityType", filter.entityType());
		putIfPresent(details, "entityId", filter.entityId());
		details.putAll(extra);
		return json.writeValueAsString(details);
	}

	private static void putIfPresent(Map<String, Object> details, String key, Object value) {
		if (value != null) {
			details.put(key, value instanceof Instant instant ? instant.toString() : value);
		}
	}

}
