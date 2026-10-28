package com.epi.procurement.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Antivirus port (spec §24, S2DR-11). Downloads are allowed only for files reported CLEAN. The development
 * implementation performs no scan and reports CLEAN, and says so in the log; production plugs the client's scanner
 * (e.g. ClamAV) behind this interface.
 */
interface AttachmentScanner {

	ScanResult scan(String fileName, byte[] content);

	enum ScanResult {
		CLEAN, INFECTED
	}

	@Component
	class NoOpScanner implements AttachmentScanner {

		private static final Logger log = LoggerFactory.getLogger(NoOpScanner.class);

		@Override
		public ScanResult scan(String fileName, byte[] content) {
			log.info("Attachment not scanned (no antivirus configured; development scanner reports CLEAN)");
			return ScanResult.CLEAN;
		}

	}

}
