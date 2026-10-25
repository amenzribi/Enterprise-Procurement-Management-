package com.epi.procurement;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

	@Test
	void moduleBoundariesAreRespected() {
		ApplicationModules.of(ProcurementApplication.class).verify();
	}

}
