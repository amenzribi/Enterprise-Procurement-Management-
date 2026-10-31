package com.epi.procurement.platform.web;

import java.util.Arrays;
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
 * is used so arbitrary clients cannot spoof their address.
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
		String forwarded = firstForwarded(request);
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

	private static String firstForwarded(HttpServletRequest request) {
		String xff = request.getHeader("X-Forwarded-For");
		if (StringUtils.hasText(xff)) {
			return xff.split(",")[0].trim();
		}
		String forwarded = request.getHeader("Forwarded");
		if (StringUtils.hasText(forwarded)) {
			for (String part : forwarded.split(";")) {
				String trimmed = part.trim();
				if (trimmed.toLowerCase(Locale.ROOT).startsWith("for=")) {
					String value = trimmed.substring(4).trim();
					if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
						value = value.substring(1, value.length() - 1);
					}
					if (value.startsWith("[") && value.contains("]")) {
						value = value.substring(1, value.indexOf(']'));
					}
					else {
						int firstColon = value.indexOf(':');
						int lastColon = value.lastIndexOf(':');
						if (firstColon > 0 && firstColon == lastColon) {
							value = value.substring(0, firstColon);
						}
					}
					return value;
				}
			}
		}
		return null;
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
