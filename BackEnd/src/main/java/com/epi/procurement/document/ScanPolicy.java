package com.epi.procurement.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import com.epi.procurement.platform.web.ApiProblemException;

/**
 * What the antivirus check means for an attachment (audit S-1). The rule is that nothing unchecked is ever
 * recorded or served as checked:
 * <ul>
 * <li>scanner says clean: stored {@code CLEAN}, downloadable;</li>
 * <li>scanner says infected: upload refused (422), nothing stored;</li>
 * <li>scanner fails: upload refused (503), nothing stored;</li>
 * <li>no scanner: stored {@code PENDING}, in quarantine unless unscanned downloads are explicitly allowed.</li>
 * </ul>
 */
final class ScanPolicy {

	private static final Logger log = LoggerFactory.getLogger(ScanPolicy.class);

	private final AttachmentScanner scanner;

	private final boolean allowUnscannedDownloads;

	/**
	 * @param scanner null when the deployment has no antivirus adapter
	 */
	ScanPolicy(AttachmentScanner scanner, boolean allowUnscannedDownloads) {
		this.scanner = scanner;
		this.allowUnscannedDownloads = allowUnscannedDownloads;
		if (scanner == null) {
			log.warn("No antivirus scanner is configured: uploaded files are stored as PENDING and {}",
					allowUnscannedDownloads
							? "can be downloaded unscanned (app.attachments.allow-unscanned-downloads=true)"
							: "stay in quarantine (no download)");
		}
	}

	/** The status to store for an upload, or an exception that refuses it. */
	String statusFor(String fileName, byte[] content) {
		if (scanner == null) {
			return "PENDING";
		}
		AttachmentScanner.ScanResult result;
		try {
			result = scanner.scan(fileName, content);
		}
		catch (RuntimeException ex) {
			log.error("Antivirus scan failed; the upload is refused", ex);
			throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "scan-unavailable",
					"The file could not be checked for viruses. Try again later.");
		}
		if (result != AttachmentScanner.ScanResult.CLEAN) {
			log.warn("Antivirus rejected an upload ({} bytes)", content.length);
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "file-infected",
					"The file was rejected by the antivirus.");
		}
		return "CLEAN";
	}

	boolean downloadable(String scanStatus) {
		return "CLEAN".equals(scanStatus) || allowUnscannedDownloads && "PENDING".equals(scanStatus);
	}

}
