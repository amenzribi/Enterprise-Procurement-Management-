package com.epi.procurement.system;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemStatusController.class)
class SystemStatusControllerTests {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	DatabaseProbe databaseProbe;

	@Test
	void reportsDatabaseUpWithSchemaVersion() throws Exception {
		given(databaseProbe.probe())
			.willReturn(new SystemStatus.DatabaseStatus(SystemStatus.State.UP, "17.0.1000.7", "2"));

		mvc.perform(get("/api/v1/system/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.application").isNotEmpty())
			.andExpect(jsonPath("$.version").isNotEmpty())
			.andExpect(jsonPath("$.serverTime").isNotEmpty())
			.andExpect(jsonPath("$.database.status").value("UP"))
			.andExpect(jsonPath("$.database.productVersion").value("17.0.1000.7"))
			.andExpect(jsonPath("$.database.schemaVersion").value("2"));
	}

	@Test
	void reportsDatabaseDownWithoutFailingOrLeakingDetails() throws Exception {
		given(databaseProbe.probe()).willReturn(SystemStatus.DatabaseStatus.down());

		mvc.perform(get("/api/v1/system/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.database.status").value("DOWN"))
			.andExpect(jsonPath("$.database.productVersion").doesNotExist())
			.andExpect(jsonPath("$.database.schemaVersion").doesNotExist())
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
	}

	@Test
	void unexpectedErrorsBecomeProblemDetailsWithoutInternals() throws Exception {
		given(databaseProbe.probe()).willThrow(new IllegalStateException("secret connection string"));

		mvc.perform(get("/api/v1/system/status"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.errorReference").isNotEmpty())
			.andExpect(jsonPath("$.code").value("internal-error"))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
	}

	@Test
	void unknownApiPathsReturnAGenericNotFoundProblem() throws Exception {
		mvc.perform(get("/api/v1/does-not-exist"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.detail").value("The requested resource does not exist."))
			.andExpect(jsonPath("$.code").value("not-found"))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("static"))));
	}

}
