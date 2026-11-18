package com.epi.procurement.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/** Audit S-13: user text cannot break or extend an audit payload. */
class AuditRecordJsonTests {

	@Test
	void hostileTextStaysOneJsonString() throws Exception {
		JsonMapper mapper = JsonMapper.builder().build();
		for (String value : new String[] { "a@b.co", "x\",\"role\":\"GLOBAL_ADMIN", "back\slash", "line\nbreak\ttab\r",
				"\u0001 ", "عربي é" }) {
			String payload = "{\"email\":" + AuditRecord.jsonString(value) + "}";
			var tree = mapper.readTree(payload);
			assertThat(tree.size()).as(payload).isEqualTo(1);
			assertThat(tree.get("email").asString()).isEqualTo(value);
		}
		assertThat(AuditRecord.jsonString(null)).isEqualTo("null");
	}

}
