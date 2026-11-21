package com.epi.procurement.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Audit S-2: the stored name's extension must match the type detected from the bytes. */
class ContentSnifferTests {

	private static final byte[] PDF_THEN_SCRIPT = "%PDF-1.7\r\n@echo off\r\ncalc.exe\r\n".getBytes(StandardCharsets.US_ASCII);

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0 };

	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0 };

	@Test
	void aScriptWithAPdfHeaderIsRefusedUnderAnExecutableName() {
		for (String name : new String[] { "invoice.bat", "invoice.cmd", "invoice.pdf.exe", "invoice.hta", "invoice.js",
				"invoice", "invoice.pdf.", "invoice.PDF.bat" }) {
			assertThat(ContentSniffer.detect(name, PDF_THEN_SCRIPT)).as(name).isEmpty();
		}
	}

	@Test
	void theMatchingExtensionIsAcceptedWhateverItsCase() {
		assertThat(ContentSniffer.detect("invoice.pdf", PDF_THEN_SCRIPT)).contains("application/pdf");
		assertThat(ContentSniffer.detect("Invoice.PDF", PDF_THEN_SCRIPT)).contains("application/pdf");
		assertThat(ContentSniffer.detect("photo.png", PNG)).contains("image/png");
		assertThat(ContentSniffer.detect("photo.jpg", JPEG)).contains("image/jpeg");
		assertThat(ContentSniffer.detect("photo.jpeg", JPEG)).contains("image/jpeg");
	}

	@Test
	void anImageUnderAnotherTypesExtensionIsRefused() {
		assertThat(ContentSniffer.detect("photo.pdf", PNG)).isEmpty();
		assertThat(ContentSniffer.detect("photo.png", JPEG)).isEmpty();
		assertThat(ContentSniffer.detect("photo.svg", PNG)).isEmpty();
	}

	@Test
	void plainTextKeepsItsOwnRules() {
		assertThat(ContentSniffer.detect("notes.txt", "hello".getBytes(StandardCharsets.UTF_8))).contains("text/plain");
		assertThat(ContentSniffer.detect("notes.bat", "hello".getBytes(StandardCharsets.UTF_8))).isEmpty();
	}

}
