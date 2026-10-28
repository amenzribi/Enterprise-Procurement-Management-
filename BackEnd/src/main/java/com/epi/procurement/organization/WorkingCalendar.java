package com.epi.procurement.organization;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Working week of a company in its own time zone (S2DR-10), used for approval SLAs: only working minutes count.
 */
public record WorkingCalendar(ZoneId zone, Set<DayOfWeek> days, LocalTime start, LocalTime end) {

	private static final int MAX_DAYS = 3660;

	public static WorkingCalendar of(String zone, String days, LocalTime start, LocalTime end) {
		Set<DayOfWeek> parsed = EnumSet.noneOf(DayOfWeek.class);
		for (String day : days.split(",")) {
			String code = day.strip().toUpperCase();
			for (DayOfWeek candidate : DayOfWeek.values()) {
				if (candidate.name().startsWith(code)) {
					parsed.add(candidate);
				}
			}
		}
		if (parsed.isEmpty()) {
			parsed = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
		}
		return new WorkingCalendar(ZoneId.of(zone), parsed, start, end);
	}

	/**
	 * The instant reached after {@code minutes} working minutes from {@code from}.
	 */
	public Instant addWorkingMinutes(Instant from, long minutes) {
		ZonedDateTime cursor = from.atZone(zone);
		long remaining = minutes;
		for (int i = 0; i < MAX_DAYS; i++) {
			LocalDate day = cursor.toLocalDate();
			if (days.contains(day.getDayOfWeek())) {
				ZonedDateTime open = day.atTime(start).atZone(zone);
				ZonedDateTime close = day.atTime(end).atZone(zone);
				ZonedDateTime begin = cursor.isBefore(open) ? open : cursor;
				if (begin.isBefore(close)) {
					long available = Duration.between(begin, close).toMinutes();
					if (remaining <= available) {
						return begin.plusMinutes(remaining).toInstant();
					}
					remaining -= available;
				}
			}
			cursor = day.plusDays(1).atStartOfDay(zone);
		}
		throw new IllegalStateException("Working calendar has no working time");
	}

	/**
	 * Working minutes between two instants (0 when {@code to} is not after {@code from}).
	 */
	public long workingMinutesBetween(Instant from, Instant to) {
		if (!to.isAfter(from)) {
			return 0;
		}
		ZonedDateTime cursor = from.atZone(zone);
		ZonedDateTime limit = to.atZone(zone);
		long total = 0;
		for (int i = 0; i < MAX_DAYS && cursor.isBefore(limit); i++) {
			LocalDate day = cursor.toLocalDate();
			if (days.contains(day.getDayOfWeek())) {
				ZonedDateTime open = day.atTime(start).atZone(zone);
				ZonedDateTime close = day.atTime(end).atZone(zone);
				ZonedDateTime begin = cursor.isBefore(open) ? open : cursor;
				ZonedDateTime finish = limit.isBefore(close) ? limit : close;
				if (begin.isBefore(finish)) {
					total += Duration.between(begin, finish).toMinutes();
				}
			}
			cursor = day.plusDays(1).atStartOfDay(zone);
		}
		return total;
	}

}
