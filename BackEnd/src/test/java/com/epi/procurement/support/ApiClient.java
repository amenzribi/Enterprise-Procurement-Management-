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
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
			.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
			.header("Accept", "application/json");
		if (json != null) {
			request.header("Content-Type", "application/json");
		}
		if (sendCsrf && !method.equals("GET")) {
			cookie("XSRF-TOKEN").ifPresent(token -> request.header("X-XSRF-TOKEN", token));
		}
		try {
			HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
			return new Response(response.statusCode(), response.body(), response.headers().allValues("Set-Cookie"));
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private static String quote(String value) {
		return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	public record Response(int status, String body, List<String> setCookies) {

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
