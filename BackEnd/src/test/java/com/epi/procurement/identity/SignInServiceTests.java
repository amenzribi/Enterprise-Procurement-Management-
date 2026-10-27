package com.epi.procurement.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.support.MutableClock;

class SignInServiceTests {

	private static final String ADDRESS = "10.0.0.1";

	private final PasswordEncoder encoder = spy(PasswordEncoderFactories.createDelegatingPasswordEncoder());

	private final CredentialStore credentials = mock(CredentialStore.class);

	private final AuditLog auditLog = mock(AuditLog.class);

	private final MutableClock clock = new MutableClock();

	private final SecuritySettings.Login limits = new SecuritySettings.Login(5, 50, Duration.ofMinutes(15),
			Duration.ofMinutes(15));

	private String hash;

	@BeforeEach
	void setUp() {
		hash = encoder.encode("correct-password");
	}

	private SignInService service(boolean demoActive) {
		return new SignInService(credentials, encoder, new LoginThrottle(limits, clock), auditLog, demoActive);
	}

	private void account(String email, boolean active, boolean demo) {
		given(credentials.findByEmail(email))
			.willReturn(Optional.of(new CredentialStore.StoredCredential(7L, email, hash, active, demo)));
	}

	@Test
	void correctPasswordSignsInAndIsAudited() {
		account("user@example.com", true, false);

		assertThat(service(false).signIn(" User@Example.com ", "correct-password", ADDRESS))
			.contains(new AuthenticatedUser(7L, "user@example.com"));
		verify(auditLog).record(argThat(r -> r.action().equals("SIGNED_IN") && r.actorUserId() == 7L));
	}

	@Test
	void wrongPasswordFailsWithoutAudit() {
		account("user@example.com", true, false);

		assertThat(service(false).signIn("user@example.com", "wrong", ADDRESS)).isEmpty();
		verify(auditLog, never()).record(any());
	}

	@Test
	void unknownEmailStillComparesAHashSoTimingDoesNotRevealTheAccount() {
		given(credentials.findByEmail(anyString())).willReturn(Optional.empty());
		SignInService service = service(false);

		assertThat(service.signIn("nobody@example.com", "whatever", ADDRESS)).isEmpty();
		verify(encoder).matches(eq("whatever"), anyString());
	}

	@Test
	void inactiveAccountsCannotSignIn() {
		account("user@example.com", false, false);

		assertThat(service(false).signIn("user@example.com", "correct-password", ADDRESS)).isEmpty();
	}

	@Test
	void demoAccountsSignInOnlyWhenTheDemoProfileIsActive() {
		account("demo@demo.local", true, true);

		assertThat(service(false).signIn("demo@demo.local", "correct-password", ADDRESS)).isEmpty();
		assertThat(service(true).signIn("demo@demo.local", "correct-password", ADDRESS)).isPresent();
	}

	@Test
	void fiveFailuresBlockThePairEvenWithTheCorrectPasswordUntilTheBlockEnds() {
		account("user@example.com", true, false);
		SignInService service = service(false);

		for (int i = 0; i < 5; i++) {
			assertThat(service.signIn("user@example.com", "wrong", ADDRESS)).isEmpty();
		}
		assertThat(service.signIn("user@example.com", "correct-password", ADDRESS)).isEmpty();
		verify(auditLog, times(1)).record(argThat(r -> r.action().equals("SIGN_IN_THROTTLED")));

		// Another address is not affected (no lock-out of the account by a third party - S1DR-03).
		assertThat(service.signIn("user@example.com", "correct-password", "10.0.0.2")).isPresent();

		clock.advance(Duration.ofMinutes(16));
		assertThat(service.signIn("user@example.com", "correct-password", ADDRESS)).isPresent();
	}

	@Test
	void successResetsTheFailureCount() {
		account("user@example.com", true, false);
		SignInService service = service(false);

		for (int i = 0; i < 4; i++) {
			service.signIn("user@example.com", "wrong", ADDRESS);
		}
		assertThat(service.signIn("user@example.com", "correct-password", ADDRESS)).isPresent();
		service.signIn("user@example.com", "wrong", ADDRESS);
		assertThat(service.signIn("user@example.com", "correct-password", ADDRESS)).isPresent();
	}

	@Test
	void tooManyFailuresFromOneAddressBlockThatAddress() {
		given(credentials.findByEmail(anyString())).willReturn(Optional.empty());
		account("victim@example.com", true, false);
		SignInService service = new SignInService(credentials, encoder,
				new LoginThrottle(new SecuritySettings.Login(5, 10, Duration.ofMinutes(15), Duration.ofMinutes(15)), clock),
				auditLog, false);

		for (int i = 0; i < 10; i++) {
			service.signIn("probe" + i + "@example.com", "x", ADDRESS);
		}
		assertThat(service.signIn("victim@example.com", "correct-password", ADDRESS)).isEmpty();
		assertThat(service.signIn("victim@example.com", "correct-password", "10.0.0.9")).isPresent();
	}

}
