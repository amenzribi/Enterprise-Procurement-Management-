package com.epi.procurement.system;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller behaviour. Authentication itself (anonymous callers get 401) is covered against the real security
 * configuration in the SQL Server integration tests.
 */
@WebMvcTest(SystemStatusController.class)
class SystemStatusControllerTests {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	DatabaseProbe databaseProbe;

	@Test
	@WithMockUser(roles = "ADMIN")
	void administratorsSeeTheDatabaseProductVersion() throws Exception {
		given(databaseProbe.probe(true))
			.willReturn(new SystemStatus.DatabaseStatus(SystemStatus.State.UP, "17.0.1000.7", "4"));

		mvc.perform(get("/api/v1/system/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.application").isNotEmpty())
			.andExpect(jsonPath("$.version").isNotEmpty())
			.andExpect(jsonPath("$.serverTime").isNotEmpty())
			.andExpect(jsonPath("$.database.status").value("UP"))
			.andExpect(jsonPath("$.database.productVersion").value("17.0.1000.7"))
			.andExpect(jsonPath("$.database.schemaVersion").value("4"));
	}

	@Test
	@WithMockUser(roles = "REQUESTER")
	void otherUsersDoNotRequestServerDetails() throws Exception {
		given(databaseProbe.probe(false)).willReturn(new SystemStatus.DatabaseStatus(SystemStatus.State.UP, null, "4"));

		mvc.perform(get("/api/v1/system/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.database.productVersion").doesNotExist())
			.andExpect(jsonPath("$.database.schemaVersion").value("4"));
		verify(databaseProbe).probe(false);
	}

	@Test
	@WithMockUser
	void reportsDatabaseDownWithoutFailingOrLeakingDetails() throws Exception {
		given(databaseProbe.probe(anyBoolean())).willReturn(SystemStatus.DatabaseStatus.down());

		mvc.perform(get("/api/v1/system/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.database.status").value("DOWN"))
			.andExpect(jsonPath("$.database.productVersion").doesNotExist())
			.andExpect(jsonPath("$.database.schemaVersion").doesNotExist())
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
	}

	@Test
	@WithMockUser
	void unexpectedErrorsBecomeProblemDetailsWithoutInternals() throws Exception {
		given(databaseProbe.probe(anyBoolean())).willThrow(new IllegalStateException("secret connection string"));

		mvc.perform(get("/api/v1/system/status"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.errorReference").isNotEmpty())
			.andExpect(jsonPath("$.code").value("internal-error"))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
	}

	@Test
	@WithMockUser
	void unknownApiPathsReturnAGenericNotFoundProblem() throws Exception {
		mvc.perform(get("/api/v1/does-not-exist"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.detail").value("The requested resource does not exist."))
			.andExpect(jsonPath("$.code").value("not-found"))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("static"))));
	}

}
