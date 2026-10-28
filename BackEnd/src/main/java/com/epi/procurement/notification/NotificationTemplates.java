package com.epi.procurement.notification;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders notifications from the AR/FR/EN message bundles in {@code notification/messages*.properties}. Arabic
 * e-mails are right to left with fonts that mail clients have; numbers, codes and amounts are isolated with
 * {@code <bdi>} so they keep their order inside Arabic text (S2DR-13).
 */
@Component
class NotificationTemplates {

	private final MessageSource messages;

	private final String baseUrl;

	NotificationTemplates(@Value("${app.public-base-url:http://localhost:4200}") String baseUrl) {
		ResourceBundleMessageSource source = new ResourceBundleMessageSource();
		source.setBasename("notification/messages");
		source.setDefaultEncoding("UTF-8");
		source.setFallbackToSystemLocale(false);
		this.messages = source;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	Rendered render(Notice notice, Locale locale) {
		boolean rtl = "ar".equals(locale.getLanguage());
		String subject = fill(text("mail." + notice.kind() + ".subject", locale), notice.params(), false);
		String intro = fill(text("mail." + notice.kind() + ".intro", locale), notice.params(), false);
		String introHtml = fill(HtmlUtils.htmlEscape(text("mail." + notice.kind() + ".intro", locale)), notice.params(),
				true);

		StringBuilder plain = new StringBuilder(intro).append("\n\n");
		StringBuilder html = new StringBuilder();
		String font = rtl ? "Tahoma, Arial, sans-serif" : "Segoe UI, Arial, sans-serif";
		html.append("<!DOCTYPE html><html lang=\"").append(locale.getLanguage()).append("\" dir=\"")
			.append(rtl ? "rtl" : "ltr").append("\"><head><meta charset=\"UTF-8\"></head><body style=\"font-family:")
			.append(font).append(";color:#1f2933;font-size:14px;\">");
		html.append("<p>").append(introHtml).append("</p>");

		List<String[]> rows = List.of(new String[] { "number", "mail.label.number" },
				new String[] { "title", "mail.label.title" }, new String[] { "requester", "mail.label.requester" },
				new String[] { "company", "mail.label.company" }, new String[] { "amount", "mail.label.amount" },
				new String[] { "requiredDate", "mail.label.requiredDate" }, new String[] { "stage", "mail.label.stage" });
		StringBuilder table = new StringBuilder();
		for (String[] row : rows) {
			String value = notice.params().get(row[0]);
			if (value == null || value.isBlank()) {
				continue;
			}
			String label = text(row[1], locale);
			table.append("<tr><th align=\"").append(rtl ? "right" : "left")
				.append("\" style=\"padding:4px 12px;color:#52606d;font-weight:600;\">").append(HtmlUtils.htmlEscape(label))
				.append("</th><td style=\"padding:4px 12px;\"><bdi>").append(HtmlUtils.htmlEscape(value))
				.append("</bdi></td></tr>");
			plain.append(label).append(": ").append(value).append('\n');
		}
		if (!table.isEmpty()) {
			html.append("<table role=\"presentation\" style=\"border-collapse:collapse;\">").append(table).append("</table>");
		}
		if (!notice.lines().isEmpty()) {
			html.append("<p style=\"margin:12px 0 4px;font-weight:600;\">")
				.append(HtmlUtils.htmlEscape(text("mail.label.lines", locale))).append("</p><ul>");
			plain.append('\n').append(text("mail.label.lines", locale)).append(":\n");
			notice.lines().stream().limit(5).forEach(line -> {
				html.append("<li><bdi>").append(HtmlUtils.htmlEscape(line)).append("</bdi></li>");
				plain.append("- ").append(line).append('\n');
			});
			html.append("</ul>");
		}
		String quoted = notice.params().get("text");
		if (quoted != null && !quoted.isBlank()) {
			html.append("<blockquote style=\"margin:12px 0;padding:8px 12px;background:#f1f5f9;border-")
				.append(rtl ? "right" : "left").append(":3px solid #0b6f6e;\">").append(HtmlUtils.htmlEscape(quoted))
				.append("</blockquote>");
			plain.append('\n').append(quoted).append('\n');
		}
		if (!notice.actions().isEmpty()) {
			html.append("<p style=\"margin:20px 0;\">");
			boolean primary = true;
			for (Notice.Action action : notice.actions()) {
				String url = absolute(action.url());
				String label = text(action.labelKey(), locale);
				html.append("<a href=\"").append(HtmlUtils.htmlEscape(url))
					.append("\" style=\"display:inline-block;margin:0 6px 8px 0;padding:10px 18px;border-radius:6px;")
					.append("text-decoration:none;font-weight:600;")
					.append(primary ? "background:#0b6f6e;color:#ffffff;" : "background:#ffffff;color:#0b6f6e;border:1px solid #0b6f6e;")
					.append("\">").append(HtmlUtils.htmlEscape(label)).append("</a>");
				plain.append('\n').append(label).append(": ").append(url);
				primary = false;
			}
			html.append("</p>");
		}
		String footer = text("mail.footer", locale);
		html.append("<p style=\"color:#7b8794;font-size:12px;\">").append(HtmlUtils.htmlEscape(footer)).append("</p>");
		html.append("</body></html>");
		plain.append("\n\n").append(footer);
		return new Rendered(subject, html.toString(), plain.toString(), intro);
	}

	private String absolute(String url) {
		return url.startsWith("http://") || url.startsWith("https://") ? url : baseUrl + url;
	}

	private String text(String code, Locale locale) {
		try {
			return messages.getMessage(code, null, locale);
		}
		catch (NoSuchMessageException ex) {
			return messages.getMessage(code, null, code, Locale.ENGLISH);
		}
	}

	private static String fill(String template, Map<String, String> params, boolean escape) {
		String result = template;
		for (Map.Entry<String, String> entry : params.entrySet()) {
			String value = entry.getValue() == null ? "" : entry.getValue();
			result = result.replace("{" + entry.getKey() + "}", escape ? "<bdi>" + HtmlUtils.htmlEscape(value) + "</bdi>"
					: value);
		}
		return result;
	}

	record Rendered(String subject, String html, String text, String inAppBody) {
	}

}
