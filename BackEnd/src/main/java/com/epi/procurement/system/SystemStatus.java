package com.epi.procurement.system;

import java.time.OffsetDateTime;

/**
 * Snapshot returned by {@code GET /api/v1/system/status}.
 */
public record SystemStatus(String application, String version, OffsetDateTime serverTime, DatabaseStatus database) {

	public enum State {
		UP, DOWN
	}

	/**
	 * Database connectivity. {@code productVersion} and {@code schemaVersion} are null when the database is down;
	 * {@code productVersion} is also null unless {@code app.status.expose-details} is enabled.
	 */
	public record DatabaseStatus(State status, String productVersion, String schemaVersion) {

		static DatabaseStatus down() {
			return new DatabaseStatus(State.DOWN, null, null);
		}

	}

}
