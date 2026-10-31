package com.epi.procurement.assistant;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.assistant")
public record AssistantSettings(String mode, Duration timeout, int rateLimitPerMinute, int maxQuestionChars,
		int maxContextChars) {

	public AssistantSettings {
		if (mode == null || mode.isBlank()) {
			mode = "disabled";
		}
		if (timeout == null) {
			timeout = Duration.ofSeconds(20);
		}
		if (rateLimitPerMinute <= 0) {
			rateLimitPerMinute = 30;
		}
		if (maxQuestionChars <= 0) {
			maxQuestionChars = 2000;
		}
		if (maxContextChars <= 0) {
			maxContextChars = 12000;
		}
	}

	public enum Mode {
		DISABLED, DEMO, LIVE
	}

	public Mode resolvedMode() {
		return switch (mode.trim().toLowerCase()) {
			case "demo" -> Mode.DEMO;
			case "live" -> Mode.LIVE;
			default -> Mode.DISABLED;
		};
	}

}
