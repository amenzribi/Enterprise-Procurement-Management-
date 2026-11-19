package com.epi.procurement.auditcenter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.epi.procurement.audit.AuditSearch;

/**
 * CSV of audit events (RFC 4180, comma, CRLF, every field quoted). A cell that a spreadsheet would read as a formula
 * ({@code = + - @}, tab, carriage return at the start) is prefixed with an apostrophe, so an exported comment can
 * never run in the reader's spreadsheet.
 */
final class AuditCsv {

	static final String HEADER = line(List.of("id", "occurred_at_utc", "chain", "chain_seq", "company_id", "company",
			"actor_user_id", "actor", "action", "entity_type", "entity_id", "from_status", "to_status", "comment",
			"details_json"));

	private AuditCsv() {
	}

	static String row(AuditSearch.Entry e) {
		return line(Stream.of(Long.toString(e.id()), e.occurredAt().toString(), e.chainKey(), Long.toString(e.chainSeq()),
				text(e.companyId()), e.companyName(), text(e.actorUserId()), e.actorLabel(), e.action(), e.entityType(),
				e.entityId(), e.fromStatus(), e.toStatus(), e.comment(), e.payloadJson()).toList());
	}

	static String truncationNote(int limit) {
		return line(List.of("", "", "", "", "", "", "", "", "", "", "", "", "",
				"Export stopped at " + limit + " rows; narrow the filters to export the rest.", ""));
	}

	private static String text(Long value) {
		return value == null ? "" : value.toString();
	}

	private static String line(List<String> cells) {
		return cells.stream().map(AuditCsv::cell).collect(Collectors.joining(",")) + "\r\n";
	}

	static String cell(String value) {
		if (value == null) {
			return "\"\"";
		}
		String safe = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
		return "\"" + safe.replace("\"", "\"\"") + "\"";
	}

}
