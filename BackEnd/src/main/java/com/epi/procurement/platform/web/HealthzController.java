package com.epi.procurement.platform.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health on the application port, always (Sprint 2, P1.4). When the actuator moves to the private management port
 * (MANAGEMENT_SERVER_PORT, ADR-018), {@code /actuator/health} leaves the application port; the System status page
 * and the uptime check read {@code /healthz} instead. Same answer as the actuator's: the status only for anonymous
 * callers, the status of each component (never its details) for a signed-in user; 503 unless the status is UP.
 */
@RestController
class HealthzController {

	private final HealthEndpoint health;

	HealthzController(HealthEndpoint health) {
		this.health = health;
	}

	@GetMapping("/healthz")
	ResponseEntity<Map<String, Object>> healthz() {
		HealthDescriptor descriptor = health.health();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", descriptor.getStatus().getCode());
		if (signedIn() && descriptor instanceof CompositeHealthDescriptor composite) {
			Map<String, Object> components = new LinkedHashMap<>();
			composite.getComponents()
				.forEach((name, component) -> components.put(name, Map.of("status", component.getStatus().getCode())));
			body.put("components", components);
		}
		HttpStatus status = Status.UP.equals(descriptor.getStatus()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
		return ResponseEntity.status(status).body(body);
	}

	private static boolean signedIn() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken);
	}

}
