package com.epi.procurement.system;

/**
 * Checks database connectivity without ever throwing.
 */
interface DatabaseProbe {

	/**
	 * @param includeDetails whether to include the server product version (administrators only - S1-AC-13)
	 */
	SystemStatus.DatabaseStatus probe(boolean includeDetails);

}
