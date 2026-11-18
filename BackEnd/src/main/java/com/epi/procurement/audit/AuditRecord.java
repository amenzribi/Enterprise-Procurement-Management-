package com.epi.procurement.audit;

import java.util.Objects;

/**
 * An auditable action to append to the trail. Status fields are optional and describe a state transition.
 */
public record AuditRecord(Long actorUserId, String actorLabel, Long companyId, String entityType, String entityId,
		String action, String fromStatus, String toStatus, String comment, String payloadJson) {

	public AuditRecord {
		Objects.requireNonNull(actorLabel, "actorLabel");
		Objects.requireNonNull(entityType, "entityType");
		Objects.requireNonNull(entityId, "entityId");
		Objects.requireNonNull(action, "action");
	}

	/**
	 * A JSON string literal for a text value placed in {@code payloadJson}. Values that come from a user (an e-mail
	 * address, a supplier code) must go through this, so a quote or backslash cannot break or extend the stored
	 * payload (audit S-13).
	 */
	public static String jsonString(String value) {
		if (value == null) {
			return "null";
		}
		StringBuilder json = new StringBuilder(value.length() + 2).append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"' -> json.append("\\\"");
				case '\\' -> json.append("\\\\");
				case '\n' -> json.append("\\n");
				case '\r' -> json.append("\\r");
				case '\t' -> json.append("\\t");
				default -> {
					if (c < 0x20 || c == 0x2028 || c == 0x2029) {
						json.append(String.format("\\u%04x", (int) c));
					}
					else {
						json.append(c);
					}
				}
			}
		}
		return json.append('"').toString();
	}

}
