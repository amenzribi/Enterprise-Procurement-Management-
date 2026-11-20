package com.epi.procurement.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The background trigger of the live message stream heartbeat (keeps streams open, ends those of signed-out users). Exists only when background jobs are on ({@code app.jobs.enabled}, default
 * true): Spring Modulith's moments auto-configuration enables scheduling for the whole application, so a
 * {@code @Scheduled} method on an always-present bean ran even with jobs off (Sprint 2). The work itself stays in
 * {@link InternalMessageStream}, which the QA hook and the tests call directly.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
class InternalMessageStreamHeartbeat {

	private final InternalMessageStream stream;

	InternalMessageStreamHeartbeat(InternalMessageStream stream) {
		this.stream = stream;
	}

	@Scheduled(fixedDelay = InternalMessageStream.HEARTBEAT_SECONDS * 1000L,
			initialDelay = InternalMessageStream.HEARTBEAT_SECONDS * 1000L)
	void beat() {
		stream.heartbeat();
	}

}
