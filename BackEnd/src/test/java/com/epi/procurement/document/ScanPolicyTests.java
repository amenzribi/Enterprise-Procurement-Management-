package com.epi.procurement.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.epi.procurement.document.AttachmentScanner.ScanResult;
import com.epi.procurement.platform.web.ApiProblemException;

/** Audit S-1: nothing unchecked is recorded or served as checked. */
class ScanPolicyTests {

	private static final byte[] FILE = { 1, 2, 3 };

	@Test
	void withoutAScannerAFileIsPendingAndQuarantined() {
		ScanPolicy policy = new ScanPolicy(null, false);
		assertThat(policy.statusFor("a.pdf", FILE)).isEqualTo("PENDING");
		assertThat(policy.downloadable("PENDING")).isFalse();
		assertThat(policy.downloadable("CLEAN")).isTrue();
	}

	@Test
	void unscannedDownloadsNeedTheExplicitSetting() {
		ScanPolicy policy = new ScanPolicy(null, true);
		assertThat(policy.downloadable("PENDING")).isTrue();
		assertThat(policy.downloadable("INFECTED")).isFalse();
		assertThat(policy.downloadable(null)).isFalse();
	}

	@Test
	void aCleanVerdictIsStoredAsClean() {
		assertThat(new ScanPolicy((name, content) -> ScanResult.CLEAN, false).statusFor("a.pdf", FILE))
			.isEqualTo("CLEAN");
	}

	@Test
	void anInfectedFileIsRefused() {
		assertThatThrownBy(() -> new ScanPolicy((name, content) -> ScanResult.INFECTED, true).statusFor("a.pdf", FILE))
			.isInstanceOfSatisfying(ApiProblemException.class, ex -> {
				assertThat(ex.getStatus().value()).isEqualTo(422);
				assertThat(ex.getCode()).isEqualTo("file-infected");
			});
	}

	@Test
	void aFailingOrSilentScannerRefusesTheUpload() {
		assertThatThrownBy(() -> new ScanPolicy((name, content) -> {
			throw new IllegalStateException("clamd unreachable");
		}, true).statusFor("a.pdf", FILE)).isInstanceOfSatisfying(ApiProblemException.class, ex -> {
			assertThat(ex.getStatus().value()).isEqualTo(503);
			assertThat(ex.getCode()).isEqualTo("scan-unavailable");
		});
		assertThatThrownBy(() -> new ScanPolicy((name, content) -> null, true).statusFor("a.pdf", FILE))
			.isInstanceOf(ApiProblemException.class);
	}

}
