package com.epi.procurement.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Decides the content type from the bytes, never from the client (S2DR-11). Allowed: PDF, PNG, JPEG, GIF, WebP,
 * Office Open XML documents without macros (.docx, .xlsx, .pptx), plain text and CSV. Refused: SVG, HTML, scripts,
 * executables, macro-enabled and legacy binary Office formats, archives.
 */
final class ContentSniffer {

	private ContentSniffer() {
	}

	static Optional<String> detect(String fileName, byte[] content) {
		if (content.length == 0) {
			return Optional.empty();
		}
		String extension = extension(fileName);
		if (startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
			return Optional.of("application/pdf");
		}
		if (startsWith(content, new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A })) {
			return Optional.of("image/png");
		}
		if (startsWith(content, new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF })) {
			return Optional.of("image/jpeg");
		}
		if (startsWith(content, "GIF87a".getBytes(StandardCharsets.US_ASCII))
				|| startsWith(content, "GIF89a".getBytes(StandardCharsets.US_ASCII))) {
			return Optional.of("image/gif");
		}
		if (content.length > 12 && startsWith(content, "RIFF".getBytes(StandardCharsets.US_ASCII))
				&& new String(content, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
			return Optional.of("image/webp");
		}
		if (startsWith(content, new byte[] { 'P', 'K', 3, 4 })) {
			return officeType(extension, content);
		}
		if ((extension.equals("txt") || extension.equals("csv")) && isPlainText(content)) {
			return Optional.of(extension.equals("csv") ? "text/csv" : "text/plain");
		}
		return Optional.empty();
	}

	private static Optional<String> officeType(String extension, byte[] content) {
		String type = switch (extension) {
			case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
			case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
			case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
			default -> null;
		};
		if (type == null) {
			return Optional.empty();
		}
		boolean contentTypes = false;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
			ZipEntry entry;
			int entries = 0;
			while ((entry = zip.getNextEntry()) != null) {
				if (++entries > 5000) {
					return Optional.empty();
				}
				String name = entry.getName().toLowerCase(Locale.ROOT);
				if (name.equals("[content_types].xml")) {
					contentTypes = true;
				}
				if (name.endsWith("vbaproject.bin") || name.endsWith(".bin") && name.contains("activex")) {
					return Optional.empty();
				}
			}
		}
		catch (IOException | IllegalArgumentException ex) {
			return Optional.empty();
		}
		return contentTypes ? Optional.of(type) : Optional.empty();
	}

	private static boolean isPlainText(byte[] content) {
		int limit = Math.min(content.length, 8192);
		int start = content.length >= 3 && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB
				&& (content[2] & 0xFF) == 0xBF ? 3 : 0;
		for (int i = 0; i < limit; i++) {
			if (content[i] == 0) {
				return false;
			}
		}
		String head = new String(content, start, Math.min(limit, content.length) - start, StandardCharsets.UTF_8)
			.stripLeading()
			.toLowerCase(Locale.ROOT);
		return !(head.startsWith("<") || head.contains("<script") || head.contains("<svg") || head.contains("<html"));
	}

	static String extension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static boolean startsWith(byte[] content, byte[] prefix) {
		if (content.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (content[i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}

}
