package com.epi.procurement.dashboard;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal PDF writer for plain-text audit packs (no third-party PDF library — ADR-003).
 */
final class SimplePdf {

	private SimplePdf() {
	}

	static byte[] ofLines(String title, List<String> lines) {
		List<String> content = new ArrayList<>();
		content.add(title == null ? "Audit pack" : title);
		content.add("");
		content.addAll(lines);
		StringBuilder stream = new StringBuilder();
		stream.append("BT /F1 10 Tf 50 780 Td 14 TL\n");
		for (String line : content) {
			String safe = escape(line);
			stream.append("(").append(safe).append(") '\n");
		}
		stream.append("ET");
		byte[] streamBytes = stream.toString().getBytes(StandardCharsets.US_ASCII);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			write(out, "%PDF-1.4\n");
			int[] offsets = new int[5];
			offsets[1] = out.size();
			write(out, "1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n");
			offsets[2] = out.size();
			write(out, "2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n");
			offsets[3] = out.size();
			write(out, "3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
					+ "/Resources<< /Font<< /F1 5 0 R >> >> >>endobj\n");
			offsets[4] = out.size();
			write(out, "4 0 obj<< /Length " + streamBytes.length + " >>stream\n");
			out.write(streamBytes);
			write(out, "\nendstream endobj\n");
			offsets[0] = out.size(); // reuse slot for font at index — write font as obj 5
			int fontOffset = out.size();
			write(out, "5 0 obj<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>endobj\n");
			int xref = out.size();
			write(out, "xref\n0 6\n");
			write(out, "0000000000 65535 f \n");
			write(out, String.format("%010d 00000 n \n", offsets[1]));
			write(out, String.format("%010d 00000 n \n", offsets[2]));
			write(out, String.format("%010d 00000 n \n", offsets[3]));
			write(out, String.format("%010d 00000 n \n", offsets[4]));
			write(out, String.format("%010d 00000 n \n", fontOffset));
			write(out, "trailer<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF");
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException("PDF build failed", ex);
		}
	}

	private static String escape(String line) {
		if (line == null) {
			return "";
		}
		String truncated = line.length() > 110 ? line.substring(0, 107) + "..." : line;
		return truncated.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replaceAll("[^\\x20-\\x7E]", "?");
	}

	private static void write(ByteArrayOutputStream out, String text) throws IOException {
		out.write(text.getBytes(StandardCharsets.US_ASCII));
	}

}
