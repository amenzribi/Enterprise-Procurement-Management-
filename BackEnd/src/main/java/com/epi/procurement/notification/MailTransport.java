package com.epi.procurement.notification;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

/**
 * Sends one rendered e-mail. Selected by {@code app.mail.mode}:
 * <ul>
 * <li>{@code file} (default, development and tests): writes an {@code .eml} file to {@code app.mail.directory}
 * (default {@code storage/mail}) - no external service is contacted.</li>
 * <li>{@code smtp} (production): Spring Mail with {@code spring.mail.*} (MAIL_HOST, MAIL_PORT, MAIL_USERNAME,
 * MAIL_PASSWORD environment variables).</li>
 * <li>{@code failing}: always fails (test hook A3, to exercise outbox retries).</li>
 * </ul>
 */
interface MailTransport {

	void send(OutgoingMail mail) throws Exception;

	record OutgoingMail(long outboxId, String messageId, String to, String subject, String html, String text) {
	}

	/**
	 * A MIME message keeping the outbox's stable Message-ID (javax/jakarta mail would otherwise generate a new one).
	 */
	final class StableIdMessage extends MimeMessage {

		private final String messageId;

		StableIdMessage(Session session, String messageId) {
			super(session);
			this.messageId = messageId;
		}

		@Override
		protected void updateMessageID() throws MessagingException {
			setHeader("Message-ID", messageId);
		}

		static StableIdMessage build(Session session, String from, OutgoingMail mail) throws MessagingException {
			StableIdMessage message = new StableIdMessage(session, mail.messageId());
			message.setFrom(new InternetAddress(from));
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(mail.to()));
			message.setSubject(mail.subject(), "UTF-8");
			MimeMultipart alternative = new MimeMultipart("alternative");
			MimeBodyPart text = new MimeBodyPart();
			text.setText(mail.text(), "UTF-8");
			MimeBodyPart html = new MimeBodyPart();
			html.setContent(mail.html(), "text/html; charset=UTF-8");
			alternative.addBodyPart(text);
			alternative.addBodyPart(html);
			message.setContent(alternative);
			message.saveChanges();
			return message;
		}

	}

	@Configuration(proxyBeanMethods = false)
	class MailTransportConfiguration {

		private static final Logger log = LoggerFactory.getLogger(MailTransportConfiguration.class);

		@Bean
		MailTransport mailTransport(@Value("${app.mail.mode:}") String mode,
				@Value("${app.mail.directory:storage/mail}") String directory,
				@Value("${app.mail.from:no-reply@procurement.local}") String from,
				org.springframework.beans.factory.ObjectProvider<JavaMailSender> sender) {
			switch (mode) {
				case "smtp" -> {
					JavaMailSender mailSender = sender.getObject();
					return mail -> {
						Session session = mailSender instanceof JavaMailSenderImpl impl ? impl.getSession()
								: Session.getInstance(new Properties());
						mailSender.send(StableIdMessage.build(session, from, mail));
					};
				}
				case "failing" -> {
					log.warn("app.mail.mode=failing: every e-mail send fails (test hook)");
					return mail -> {
						throw new IllegalStateException("Mail transport configured to fail");
					};
				}
				case "file" -> {
					Path dir = Path.of(directory).toAbsolutePath();
					log.info("E-mails are written as .eml files to {} (app.mail.mode=file)", dir);
					DateTimeFormatter stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
					return mail -> {
						Files.createDirectories(dir);
						Path file = dir.resolve(stamp.format(java.time.Instant.now()) + "-" + mail.outboxId() + ".eml");
						try (OutputStream out = Files.newOutputStream(file)) {
							StableIdMessage.build(Session.getInstance(new Properties()), from, mail).writeTo(out);
						}
						catch (IOException ex) {
							throw ex;
						}
					};
				}
				default -> throw new IllegalStateException("app.mail.mode (MAIL_MODE) must be smtp or file, not '"
						+ mode + "'");
			}
		}

	}

}
