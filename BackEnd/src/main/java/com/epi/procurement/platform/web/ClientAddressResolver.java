package com.epi.procurement.platform.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client address for rate limiting (S2CR-08 / S9-AC-01). Forwarded headers are honoured only when
 * the immediate TCP peer is listed in {@code app.security.trusted-proxies}; otherwise {@code getRemoteAddr()}
 * is used so arbitrary clients cannot spoof their address. Behind a trusted proxy the forwarded list is read from
 * the right, because the left of the list is whatever the client chose to send.
 */
@Component
public class ClientAddressResolver {

	private final Set<String> trustedProxies;

	@Autowired
	public ClientAddressResolver(@Value("${app.security.trusted-proxies:}") String trustedProxiesCsv) {
		this(parseCsv(trustedProxiesCsv));
	}

	/** Unit-test factory (not a Spring bean constructor). */
	static ClientAddressResolver forTrustedPeers(List<String> trustedProxies) {
		return new ClientAddressResolver(trustedProxies == null ? List.of() : trustedProxies);
	}

	private ClientAddressResolver(List<String> trustedProxies) {
		this.trustedProxies = trustedProxies.stream()
			.filter(StringUtils::hasText)
			.map(ClientAddressResolver::normalise)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toUnmodifiableSet());
	}

	public String resolve(HttpServletRequest request) {
		String remote = normalise(request.getRemoteAddr());
		if (remote.isEmpty() || !trustedProxies.contains(remote)) {
			return remote.isEmpty() ? "unknown" : remote;
		}
		String forwarded = clientFromForwarded(request);
		if (StringUtils.hasText(forwarded)) {
			return normalise(forwarded);
		}
		return remote;
	}

	public boolean trusts(String peerAddress) {
		return trustedProxies.contains(normalise(peerAddress));
	}

	private static List<String> parseCsv(String csv) {
		if (!StringUtils.hasText(csv)) {
			return List.of();
		}
		return Arrays.stream(csv.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
	}

	/**
	 * The client address as seen by the trusted proxies (audit S-3). Proxies append the address of the peer they
	 * received the request from, so the list is read from the right: the first address that is not itself a trusted
	 * proxy is the one our own infrastructure vouches for. Anything further left was supplied by the client and can
	 * be forged. When every entry is a trusted proxy, the left-most one is used.
	 */
	private String clientFromForwarded(HttpServletRequest request) {
		List<String> hops = forwardedHops(request);
		for (int i = hops.size() - 1; i >= 0; i--) {
			String hop = normalise(hops.get(i));
			if (!hop.isEmpty() && !trustedProxies.contains(hop)) {
				return hop;
			}
		}
		return hops.isEmpty() ? null : hops.get(0);
	}

	/** Addresses in the order the proxies added them: X-Forwarded-For, or the for= elements of Forwarded. */
	private static List<String> forwardedHops(HttpServletRequest request) {
		List<String> hops = new ArrayList<>();
		Enumeration<String> xff = request.getHeaders("X-Forwarded-For");
		while (xff != null && xff.hasMoreElements()) {
			for (String value : xff.nextElement().split(",")) {
				if (StringUtils.hasText(value)) {
					hops.add(hostOf(value.trim()));
				}
			}
		}
		if (!hops.isEmpty()) {
			return hops;
		}
		Enumeration<String> forwarded = request.getHeaders("Forwarded");
		while (forwarded != null && forwarded.hasMoreElements()) {
			for (String element : forwarded.nextElement().split(",")) {
				for (String part : element.split(";")) {
					String trimmed = part.trim();
					if (trimmed.toLowerCase(Locale.ROOT).startsWith("for=")) {
						hops.add(hostOf(trimmed.substring(4).trim()));
					}
				}
			}
		}
		return hops;
	}

	/** Removes quotes, IPv6 brackets and a port from one forwarded element. */
	private static String hostOf(String element) {
		String value = element;
		if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
			value = value.substring(1, value.length() - 1);
		}
		if (value.startsWith("[") && value.contains("]")) {
			return value.substring(1, value.indexOf(']'));
		}
		int firstColon = value.indexOf(':');
		if (firstColon > 0 && firstColon == value.lastIndexOf(':')) {
			return value.substring(0, firstColon);
		}
		return value;
	}

	private static String normalise(String address) {
		if (address == null) {
			return "";
		}
		String value = address.trim();
		if (value.startsWith("[") && value.endsWith("]") && value.length() > 2) {
			value = value.substring(1, value.length() - 1);
		}
		if (value.toLowerCase(Locale.ROOT).startsWith("::ffff:")) {
			value = value.substring(value.lastIndexOf(':') + 1);
		}
		if ("0:0:0:0:0:0:0:1".equals(value) || "::1".equals(value)) {
			return "127.0.0.1";
		}
		return value;
	}

}
