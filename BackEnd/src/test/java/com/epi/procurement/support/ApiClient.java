package com.epi.procurement.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import com.jayway.jsonpath.JsonPath;

/**
 * A browser-like HTTP client for the real server: keeps cookies (session and XSRF-TOKEN) and echoes the CSRF token
 * in the {@code X-XSRF-TOKEN} header on state-changing requests, exactly as Angular's HttpClient does.
 */
public final class ApiClient {

	private final String base;

	private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

	private final HttpClient http;

	private boolean sendCsrf = true;

	ApiClient(int port) {
		this.base = "http://localhost:" + port;
		this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
	}

	/** Stops sending the CSRF header (to test CSRF protection). */
	public ApiClient withoutCsrfHeader() {
		this.sendCsrf = false;
		return this;
	}

	public Response csrf() {
		return send("GET", "/api/v1/auth/csrf", null);
	}

	public Response login(String email, String password) {
		if (cookie("XSRF-TOKEN").isEmpty()) {
			csrf();
		}
		return send("POST", "/api/v1/auth/login",
				"{\"email\":" + quote(email) + ",\"password\":" + quote(password) + "}");
	}

	public Response logout() {
		return send("POST", "/api/v1/auth/logout", null);
	}

	public Response get(String path) {
		return send("GET", path, null);
	}

	public Response post(String path, String json) {
		return send("POST", path, json);
	}

	public Response put(String path, String json) {
		return send("PUT", path, json);
	}

	public Response delete(String path) {
		return send("DELETE", path, null);
	}

	public Optional<String> cookie(String name) {
		return cookies.getCookieStore()
			.getCookies()
			.stream()
			.filter(c -> c.getName().equals(name))
			.map(HttpCookie::getValue)
			.findFirst();
	}

	/** Replaces a cookie value, e.g. to replay an old session id. */
	public void setCookie(String name, String value) {
		List<HttpCookie> existing = cookies.getCookieStore()
			.getCookies()
			.stream()
			.filter(c -> c.getName().equals(name))
			.toList();
		existing.forEach(c -> cookies.getCookieStore().remove(null, c));
		HttpCookie cookie = new HttpCookie(name, value);
		cookie.setPath("/");
		cookie.setVersion(0);
		cookies.getCookieStore().add(URI.create(base), cookie);
	}

	public Response send(String method, String path, String json) {
		return send(method, path, json, java.util.Map.of());
	}

	/** POST with extra headers, e.g. {@code Idempotency-Key}. */
	public Response post(String path, String json, java.util.Map<String, String> headers) {
		return send("POST", path, json, headers);
	}

	/** Multipart upload of one file (and an optional lineId), like the browser's FormData. */
	public Response upload(String path, String fileName, String contentType, byte[] content, Long lineId) {
		String boundary = "----test" + System.nanoTime();
		java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
		java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
		String crlf = "\r\n";
		try {
			if (lineId != null) {
				body.write(("--" + boundary + crlf + "Content-Disposition: form-data; name=\"lineId\"" + crlf + crlf + lineId
						+ crlf).getBytes(utf8));
			}
			body.write(("--" + boundary + crlf + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName
					+ "\"" + crlf + "Content-Type: " + contentType + crlf + crlf).getBytes(utf8));
			body.write(content);
			body.write((crlf + "--" + boundary + "--" + crlf).getBytes(utf8));
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
			.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
			.header("Accept", "application/json")
			.header("Content-Type", "multipart/form-data; boundary=" + boundary);
		if (sendCsrf) {
			cookie("XSRF-TOKEN").ifPresent(token -> request.header("X-XSRF-TOKEN", token));
		}
		return execute(request);
	}

	public Response send(String method, String path, String json, java.util.Map<String, String> headers) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
			.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
			.header("Accept", "application/json");
		headers.forEach(request::header);
		if (json != null) {
			request.header("Content-Type", "application/json");
		}
		if (sendCsrf && !method.equals("GET")) {
			cookie("XSRF-TOKEN").ifPresent(token -> request.header("X-XSRF-TOKEN", token));
		}
		return execute(request);
	}

	private Response execute(HttpRequest.Builder request) {
		try {
			HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
			return new Response(response.statusCode(), response.body(), response.headers().allValues("Set-Cookie"),
					response.headers().map());
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * Opens a Server-Sent Events stream with this client's session and collects its lines on a background thread.
	 * Close it when done.
	 */
	public LiveStream openStream(String path) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
			.header("Accept", "text/event-stream")
			.GET()
			.build();
		LiveStream stream = new LiveStream();
		java.util.concurrent.CompletableFuture<HttpResponse<java.util.stream.Stream<String>>> future = http
			.sendAsync(request, HttpResponse.BodyHandlers.ofLines());
		Thread reader = new Thread(() -> {
			try {
				HttpResponse<java.util.stream.Stream<String>> response = future.get();
				stream.status = response.statusCode();
				stream.body = response.body();
				response.body().forEach(stream.lines::add);
			}
			catch (Exception ex) {
				// Closed by the test or by the server.
			}
			finally {
				stream.lines.add(LiveStream.CLOSED);
			}
		}, "test-sse-reader");
		reader.setDaemon(true);
		reader.start();
		return stream;
	}

	public static final class LiveStream implements AutoCloseable {

		static final String CLOSED = "<<closed>>";

		final java.util.concurrent.BlockingQueue<String> lines = new java.util.concurrent.LinkedBlockingQueue<>();

		volatile int status;

		volatile java.util.stream.Stream<String> body;

		private final List<String> seen = new java.util.ArrayList<>();

		/** Waits for a line containing {@code text}; returns it, or null after the timeout or when the stream ends. */
		public String await(String text, java.time.Duration timeout) {
			long deadline = System.nanoTime() + timeout.toNanos();
			try {
				while (System.nanoTime() < deadline) {
					String line = lines.poll(Math.max(1, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
					if (line == null || line.equals(CLOSED)) {
						if (line != null) {
							seen.add(line);
						}
						return null;
					}
					seen.add(line);
					if (line.contains(text)) {
						return line;
					}
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return null;
		}

		/** True once the server has ended the stream. */
		public boolean awaitClosed(java.time.Duration timeout) {
			if (seen.contains(CLOSED)) {
				return true;
			}
			await(CLOSED + "never", timeout);
			return seen.contains(CLOSED);
		}

		public int status() {
			return status;
		}

		@Override
		public void close() {
			java.util.stream.Stream<String> b = body;
			if (b != null) {
				b.close();
			}
		}

	}

	private static String quote(String value) {
		return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	public record Response(int status, String body, List<String> setCookies,
			java.util.Map<String, List<String>> headers) {

		public String header(String name) {
			return headers.entrySet()
				.stream()
				.filter(e -> e.getKey().equalsIgnoreCase(name))
				.map(e -> String.join(",", e.getValue()))
				.findFirst()
				.orElse(null);
		}

		public Response expectStatus(int expected) {
			assertThat(status).as("HTTP status; body: %s", body).isEqualTo(expected);
			return this;
		}

		public <T> T json(String path) {
			return JsonPath.read(body, path);
		}

		public String code() {
			return json("$.code");
		}

		public long id() {
			return ((Number) json("$.id")).longValue();
		}

		public int version() {
			return ((Number) json("$.version")).intValue();
		}

	}

}
