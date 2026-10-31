package com.epi.procurement.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SimplePdfTests {

	@Test
	void writesPdfHeaderAndEscapesParentheses() {
		byte[] pdf = SimplePdf.ofLines("Title (demo)", List.of("Line with (parens) and \\ slash"));
		String text = new String(pdf);
		assertThat(text).startsWith("%PDF-1.4");
		assertThat(text).contains("Title \\(demo\\)");
		assertThat(text).contains("Line with \\(parens\\)");
		assertThat(text).contains("%%EOF");
	}

}
