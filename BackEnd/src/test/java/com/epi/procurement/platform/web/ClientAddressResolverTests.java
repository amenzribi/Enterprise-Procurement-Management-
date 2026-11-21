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
	void usesTheAddressAddedByTheTrustedProxy() {
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

	@Test
	void aForgedLeftmostAddressDoesNotReplaceTheOneTheProxySaw() {
		// Audit S-3: nginx, IIS ARR and cloud load balancers append the real peer to whatever the client sent.
		ClientAddressResolver resolver = ClientAddressResolver.forTrustedPeers(List.of("10.0.0.1"));
		for (String forged : new String[] { "1.2.3.4", "9.9.9.9, 8.8.8.8", "10.0.0.1", "garbage" }) {
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.setRemoteAddr("10.0.0.1");
			request.addHeader("X-Forwarded-For", forged + ", 203.0.113.50");
			assertThat(resolver.resolve(request)).as(forged).isEqualTo("203.0.113.50");
		}
	}

	@Test
	void trustedProxiesInTheChainAreSkippedFromTheRight() {
		ClientAddressResolver resolver = ClientAddressResolver.forTrustedPeers(List.of("10.0.0.1", "10.0.0.2"));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.1");
		request.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.50, 10.0.0.2");
		assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50");
	}

	@Test
	void severalHeaderLinesAreReadAsOneList() {
		ClientAddressResolver resolver = ClientAddressResolver.forTrustedPeers(List.of("10.0.0.1"));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.1");
		request.addHeader("X-Forwarded-For", "1.2.3.4");
		request.addHeader("X-Forwarded-For", "203.0.113.50:51234");
		assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50");
	}

	@Test
	void theForwardedHeaderIsAlsoReadFromTheRight() {
		ClientAddressResolver resolver = ClientAddressResolver.forTrustedPeers(List.of("127.0.0.1"));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("127.0.0.1");
		request.addHeader("Forwarded", "for=1.2.3.4, for=\"[2001:db8::7]:4711\";proto=https");
		assertThat(resolver.resolve(request)).isEqualTo("2001:db8::7");
	}

}
