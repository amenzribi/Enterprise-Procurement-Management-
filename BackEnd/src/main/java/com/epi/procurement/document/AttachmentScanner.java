package com.epi.procurement.document;

/**
 * Antivirus port (spec §24, S2DR-11, audit S-1). The application ships no implementation: a deployment plugs the
 * client's scanner behind this interface as a Spring bean.
 * <p>
 * Without a scanner nothing is ever reported clean. An uploaded file is stored as {@code PENDING} and stays in
 * quarantine (no download) unless {@code app.attachments.allow-unscanned-downloads=true}, which only the
 * development profiles set. A scanner that throws refuses the upload: the check fails closed.
 */
interface AttachmentScanner {

	/**
	 * @throws RuntimeException when the scan could not be completed; the upload is then refused
	 */
	ScanResult scan(String fileName, byte[] content);

	enum ScanResult {
		CLEAN, INFECTED
	}

}
