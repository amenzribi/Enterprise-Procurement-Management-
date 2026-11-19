package com.epi.procurement.document;

/**
 * Antivirus port (spec §24, S2DR-11, audit S-1). {@link ClamAvScanner} implements it when {@code APP_CLAMAV_HOST} is
 * set (ADR-013); a deployment may plug another scanner behind this interface as a Spring bean.
 * <p>
 * Without a scanner nothing is ever reported clean. An uploaded file is stored as {@code PENDING} and stays in
 * quarantine (no download) unless {@code app.attachments.allow-unscanned-downloads=true}, which only the
 * development profiles set. A scanner that throws refuses the upload: the check fails closed. In uat, staging,
 * preprod and production a scanner is mandatory and unscanned downloads are refused at startup.
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
