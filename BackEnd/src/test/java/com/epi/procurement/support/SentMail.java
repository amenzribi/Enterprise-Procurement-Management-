package com.epi.procurement.support;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * Reads the e-mails the test profile writes as .eml files (file mail mode), so tests follow the link a person
 * actually receives instead of one the test makes itself (Sprint 2: set-up, reset and e-mail correction mails had
 * carried a raw placeholder since Sprint 10 while every test passed).
 */
public final class SentMail {

	private static final Path DIRECTORY = Path.of("target/test-mail");

	private SentMail() {
	}

	/** The body (all parts) of the newest e-mail to {@code address}. */
	public static String newestTo(String address) throws Exception {
		List<Path> files;
		try (Stream<Path> list = Files.list(DIRECTORY)) {
			files = list.sorted(Comparator.comparing(Path::toString).reversed()).toList();
		}
		for (Path file : files) {
			try (InputStream in = Files.newInputStream(file)) {
				MimeMessage message = new MimeMessage(Session.getInstance(new Properties()), in);
				if (!address.equalsIgnoreCase(message.getAllRecipients()[0].toString())) {
					continue;
				}
				Multipart parts = (Multipart) message.getContent();
				StringBuilder body = new StringBuilder();
				for (int i = 0; i < parts.getCount(); i++) {
					body.append(parts.getBodyPart(i).getContent());
				}
				return body.toString();
			}
		}
		throw new AssertionError("No e-mail to " + address + " in " + DIRECTORY);
	}

	/**
	 * The token of the {@code /<prefix>#token} link in the newest e-mail to {@code address}; fails when the e-mail
	 * still carries an unresolved placeholder or has no such link.
	 */
	public static String linkToken(String address, String prefix) throws Exception {
		String body = newestTo(address);
		if (body.contains("{{link:")) {
			throw new AssertionError("Unresolved link placeholder in the e-mail to " + address);
		}
		Matcher found = Pattern.compile("/" + Pattern.quote(prefix) + "#([A-Za-z0-9_-]{20,512})").matcher(body);
		if (!found.find()) {
			throw new AssertionError("No /" + prefix + "# link in the e-mail to " + address);
		}
		return found.group(1);
	}

}
