package com.epi.procurement.system;

/**
 * Checks database connectivity without ever throwing.
 */
interface DatabaseProbe {

	SystemStatus.DatabaseStatus probe();

}
