package com.epi.procurement.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTests {

	@Test
	void ignoresForwardedHeadersWhenPeerIsNotTrusted() {
		ClientAddressResolver resolver = ClientAddressResolver.forTrustedPeers(List.of("10.0.0.1"));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.50");
		request.addHeader("X-Forwarded-For", "198.51.100.7");
		assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50");
	}

	@Test
	void usesLeftmostXForwardedForWhenPeerIsTrusted() {
		ClientAddressResolver resolver = ClientAddressResolver.forTrustedPeers(List.of("10.0.0.1"));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.1");
		request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.1");
		assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
	}

	@Test
	void parsesForwardedHeaderForWhenTrusted() {
		ClientAddressResolver resolver = ClientAddressResolver.forTrustedPeers(List.of("127.0.0.1"));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("127.0.0.1");
		request.addHeader("Forwarded", "for=198.51.100.9;proto=https");
		assertThat(resolver.resolve(request)).isEqualTo("198.51.100.9");
	}

	@Test
	void emptyTrustedListNeverHonoursSpoofedHeaders() {
		ClientAddressResolver resolver = ClientAddressResolver.forTrustedPeers(List.of());
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("127.0.0.1");
		request.addHeader("X-Forwarded-For", "198.51.100.1");
		assertThat(resolver.resolve(request)).isEqualTo("127.0.0.1");
	}

}
