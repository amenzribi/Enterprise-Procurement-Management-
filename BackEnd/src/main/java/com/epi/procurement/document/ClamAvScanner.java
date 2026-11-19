package com.epi.procurement.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Antivirus adapter for ClamAV's {@code clamd} daemon (ADR-013), speaking its {@code INSTREAM} protocol over TCP:
 * the file is sent in length-prefixed chunks, then {@code clamd} answers {@code stream: OK} or
 * {@code stream: <signature> FOUND}. No library is involved.
 * <p>
 * It fails closed: anything other than a clear answer (connection refused, timeout, size limit, an unknown reply)
 * throws, and {@link ScanPolicy} then refuses the upload (503). Nothing is ever reported clean without
 * {@code clamd} saying so. {@code clamd} listens on the loopback interface of the application host only.
 */
final class ClamAvScanner implements AttachmentScanner {

	private static final int CHUNK = 64 * 1024;

	/** Longest reply accepted from {@code clamd}: a verdict is a short line. */
	private static final int MAX_REPLY = 4 * 1024;

	private final String host;

	private final int port;

	private final Duration connectTimeout;

	private final Duration readTimeout;

	ClamAvScanner(String host, int port, Duration connectTimeout, Duration readTimeout) {
		this.host = host;
		this.port = port;
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
	}

	@Override
	public ScanResult scan(String fileName, byte[] content) {
		String reply = exchange("zINSTREAM\0", content);
		if (reply.endsWith("OK") && !reply.contains("FOUND")) {
			return ScanResult.CLEAN;
		}
		if (reply.endsWith("FOUND")) {
			return ScanResult.INFECTED;
		}
		// "INSTREAM size limit exceeded. ERROR" and anything unexpected: not a verdict.
		throw new IllegalStateException("clamd did not give a verdict: " + reply);
	}

	/** True when {@code clamd} answers {@code PING} with {@code PONG}; used by the health check. */
	boolean ping() {
		try {
			return "PONG".equals(exchange("zPING\0", null));
		}
		catch (RuntimeException ex) {
			return false;
		}
	}

	private String exchange(String command, byte[] content) {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), (int) connectTimeout.toMillis());
			socket.setSoTimeout((int) readTimeout.toMillis());
			OutputStream out = socket.getOutputStream();
			out.write(command.getBytes(StandardCharsets.US_ASCII));
			if (content != null) {
				for (int offset = 0; offset < content.length; offset += CHUNK) {
					int length = Math.min(CHUNK, content.length - offset);
					out.write(ByteBuffer.allocate(4).putInt(length).array());
					out.write(content, offset, length);
				}
				out.write(new byte[4]);
			}
			out.flush();
			return readReply(socket.getInputStream());
		}
		catch (IOException ex) {
			throw new IllegalStateException("clamd unavailable at " + host + ":" + port + " (" + ex.getClass().getSimpleName()
					+ ")", ex);
		}
	}

	private static String readReply(InputStream in) throws IOException {
		ByteArrayOutputStream reply = new ByteArrayOutputStream();
		int b;
		while ((b = in.read()) != -1 && b != 0) {
			if (reply.size() >= MAX_REPLY) {
				throw new IOException("reply too long");
			}
			reply.write(b);
		}
		if (b == -1 && reply.size() == 0) {
			throw new IOException("connection closed without a reply");
		}
		return reply.toString(StandardCharsets.US_ASCII).trim();
	}

}
