package com.epi.procurement.approval;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.request.RequestSnapshot;
import com.epi.procurement.request.RequestWorkflow;

/**
 * One-time, idempotent startup job (S2DR-01, QA2-R7): Sprint 1 requests still CREATED enter an approval circuit
 * using the rule and the exchange rate valid on their submission date. Requests with no matching rule, rate or
 * approver stay CREATED, are logged, and appear in the administrators' "awaiting circuit" list. Runs after the demo
 * users are seeded.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.approval.legacy-job.enabled", havingValue = "true", matchIfMissing = true)
class LegacyCircuitJob implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(LegacyCircuitJob.class);

	private final RequestWorkflow requests;

	private final ApprovalEngine engine;

	private final OrganizationDirectory organization;

	private final TransactionTemplate transaction;

	private final Clock clock;

	LegacyCircuitJob(RequestWorkflow requests, ApprovalEngine engine, OrganizationDirectory organization,
			PlatformTransactionManager transactionManager, ObjectProvider<Clock> clock) {
		this.requests = requests;
		this.engine = engine;
		this.organization = organization;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@Override
	public void run(ApplicationArguments args) {
		runOnce();
	}

	/**
	 * @return ids of the requests that are still waiting for a circuit
	 */
	List<Long> runOnce() {
		List<Long> waiting = new ArrayList<>();
		for (long id : requests.legacySubmittedIds()) {
			try {
				transaction.executeWithoutResult(status -> {
					RequestSnapshot request = requests.lockForApproval(id);
					Instant now = clock.instant();
					String zone = organization.findCompany(request.requestingCompanyId())
						.map(OrganizationDirectory.CompanyRef::timeZone)
						.orElse("UTC");
					Instant submitted = request.submittedAt() != null ? request.submittedAt() : now;
					int round = requests.enterApprovalFromLegacy(id, now);
					engine.start(id, round, now, LocalDate.ofInstant(submitted, ZoneId.of(zone)), false);
				});
			}
			catch (ApiProblemException ex) {
				waiting.add(id);
				log.warn("Request {} submitted before Sprint 2 is still waiting for an approval circuit: {}", id,
						ex.getCode());
			}
		}
		return waiting;
	}

}
