package com.epi.procurement.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.epi.procurement.platform.web.ApiProblemException;

/**
 * ADR-013: the ClamAV adapter against a fake {@code clamd} that speaks the INSTREAM protocol. Every outcome other than
 * a clear verdict must refuse the upload (fail-closed).
 */
class ClamAvScannerTests {

	private ServerSocket server;

	private final AtomicReference<byte[]> received = new AtomicReference<>();

	@AfterEach
	void close() throws IOException {
		if (server != null) {
			server.close();
		}
	}

	/** Starts a one-connection fake clamd that reads the command and the stream, then answers {@code reply}. */
	private ClamAvScanner fakeClamd(Function<byte[], String> reply, Duration readTimeout) throws IOException {
		server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
		CompletableFuture.runAsync(() -> {
			try (Socket socket = server.accept()) {
				DataInputStream in = new DataInputStream(socket.getInputStream());
				String command = readCommand(in);
				byte[] content = command.equals("zINSTREAM") ? readStream(in) : new byte[0];
				received.set(content);
				String answer = reply.apply(content);
				if (answer != null) {
					socket.getOutputStream().write((answer + "\0").getBytes(StandardCharsets.US_ASCII));
					socket.getOutputStream().flush();
				}
				else {
					Thread.sleep(readTimeout.toMillis() * 3);
				}
			}
			catch (Exception ignored) {
				// The test asserts on the client side.
			}
		});
		return new ClamAvScanner("127.0.0.1", server.getLocalPort(), Duration.ofSeconds(2), readTimeout);
	}

	private static String readCommand(InputStream in) throws IOException {
		ByteArrayOutputStream command = new ByteArrayOutputStream();
		int b;
		while ((b = in.read()) > 0) {
			command.write(b);
		}
		return command.toString(StandardCharsets.US_ASCII);
	}

	private static byte[] readStream(DataInputStream in) throws IOException {
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		int length;
		while ((length = in.readInt()) > 0) {
			content.write(in.readNBytes(length));
		}
		return content.toByteArray();
	}

	@Test
	void aCleanFileIsReportedCleanAndIsSentWhole() throws Exception {
		byte[] file = new byte[200_000];
		for (int i = 0; i < file.length; i++) {
			file[i] = (byte) i;
		}
		ClamAvScanner scanner = fakeClamd(content -> "stream: OK", Duration.ofSeconds(5));

		assertThat(scanner.scan("a.pdf", file)).isEqualTo(AttachmentScanner.ScanResult.CLEAN);
		// Several 64 KiB chunks reassembled byte for byte.
		assertThat(received.get()).isEqualTo(file);
	}

	@Test
	void aSignatureFoundIsReportedInfected() throws Exception {
		ClamAvScanner scanner = fakeClamd(content -> "stream: Eicar-Test-Signature FOUND", Duration.ofSeconds(5));

		assertThat(scanner.scan("eicar.pdf", "X5O!P%@AP".getBytes(StandardCharsets.US_ASCII)))
			.isEqualTo(AttachmentScanner.ScanResult.INFECTED);
	}

	@Test
	void theSizeLimitIsNotAVerdict() throws Exception {
		ClamAvScanner scanner = fakeClamd(content -> "INSTREAM size limit exceeded. ERROR", Duration.ofSeconds(5));

		assertThatThrownBy(() -> scanner.scan("big.pdf", new byte[10])).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void anUnexpectedReplyIsNotAVerdict() throws Exception {
		ClamAvScanner scanner = fakeClamd(content -> "stream: something odd", Duration.ofSeconds(5));

		assertThatThrownBy(() -> scanner.scan("a.pdf", new byte[10])).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aScannerThatDoesNotAnswerTimesOut() throws Exception {
		ClamAvScanner scanner = fakeClamd(content -> null, Duration.ofMillis(300));

		assertThatThrownBy(() -> scanner.scan("a.pdf", new byte[10])).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void anUnreachableScannerFailsAndPingSaysSo() throws Exception {
		int closedPort;
		try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			closedPort = probe.getLocalPort();
		}
		ClamAvScanner scanner = new ClamAvScanner("127.0.0.1", closedPort, Duration.ofMillis(500), Duration.ofMillis(500));

		assertThatThrownBy(() -> scanner.scan("a.pdf", new byte[10])).isInstanceOf(IllegalStateException.class);
		assertThat(scanner.ping()).isFalse();
	}

	@Test
	void pingAnswersPong() throws Exception {
		ClamAvScanner scanner = fakeClamd(content -> "PONG", Duration.ofSeconds(5));

		assertThat(scanner.ping()).isTrue();
	}

	@Test
	void throughThePolicyEveryFailureRefusesTheUploadAndNothingIsCalledClean() throws Exception {
		int closedPort;
		try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			closedPort = probe.getLocalPort();
		}
		ScanPolicy policy = new ScanPolicy(
				new ClamAvScanner("127.0.0.1", closedPort, Duration.ofMillis(500), Duration.ofMillis(500)), false);

		assertThatThrownBy(() -> policy.statusFor("a.pdf", new byte[10])).isInstanceOf(ApiProblemException.class)
			.extracting(ex -> ((ApiProblemException) ex).getStatus().value())
			.isEqualTo(503);
		assertThat(policy.downloadable("PENDING")).isFalse();
	}

}
