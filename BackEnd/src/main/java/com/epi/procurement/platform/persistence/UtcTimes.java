package com.epi.procurement.platform.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Instants in plain JDBC code: DATETIME2 columns hold UTC (DR-17), so values go through {@link LocalDateTime} in
 * UTC. {@code java.sql.Timestamp} must not be used: it converts with the JVM's default time zone.
 */
public final class UtcTimes {

	private UtcTimes() {
	}

	public static LocalDateTime db(Instant instant) {
		return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	public static Instant instant(ResultSet rs, String column) throws SQLException {
		LocalDateTime value = rs.getObject(column, LocalDateTime.class);
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}

}
