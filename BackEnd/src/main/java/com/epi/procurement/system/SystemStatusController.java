package com.epi.procurement.system;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
class SystemStatusController {

	private final DatabaseProbe databaseProbe;

	private final String applicationName;

	private final String version;

	private final Clock clock;

	SystemStatusController(DatabaseProbe databaseProbe, ObjectProvider<BuildProperties> buildProperties,
			ObjectProvider<Clock> clock) {
		this.databaseProbe = databaseProbe;
		BuildProperties build = buildProperties.getIfAvailable();
		this.applicationName = build != null ? build.getName() : "procurement";
		this.version = build != null ? build.getVersion() : "dev";
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	/**
	 * Always answers 200 so clients can display partial status; database failures are reported as DOWN.
	 */
	@GetMapping("/status")
	SystemStatus status() {
		return new SystemStatus(applicationName, version, OffsetDateTime.now(clock), databaseProbe.probe());
	}

}
