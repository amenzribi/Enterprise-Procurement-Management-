package com.epi.procurement.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Audit S-14: a cell must never be read as a formula by a spreadsheet program. */
class ErpExportCsvTests {

	@Test
	void valuesThatStartLikeAFormulaAreNeutralised() {
		assertThat(ErpExportService.csv("=1+1")).isEqualTo("'=1+1");
		assertThat(ErpExportService.csv("+33 1 23")).isEqualTo("'+33 1 23");
		assertThat(ErpExportService.csv("-2")).isEqualTo("'-2");
		assertThat(ErpExportService.csv("@SUM(A1)")).isEqualTo("'@SUM(A1)");
		assertThat(ErpExportService.csv("\t=1")).isEqualTo("'\t=1");
		assertThat(ErpExportService.csv("=HYPERLINK(\"http://x\",\"a,b\")"))
			.isEqualTo("\"'=HYPERLINK(\"\"http://x\"\",\"\"a,b\"\")\"");
	}

	@Test
	void ordinaryValuesAreUnchanged() {
		assertThat(ErpExportService.csv("PR-2026-0001")).isEqualTo("PR-2026-0001");
		assertThat(ErpExportService.csv("Pumps, spare")).isEqualTo("\"Pumps, spare\"");
		assertThat(ErpExportService.csv("a\rb")).isEqualTo("\"a\rb\"");
		assertThat(ErpExportService.csv("")).isEmpty();
		assertThat(ErpExportService.csv(null)).isEmpty();
	}

}
