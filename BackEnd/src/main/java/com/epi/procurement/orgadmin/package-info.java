/**
 * HTTP adapter for organisation administration. JDBC writes stay in the organization module so it does not depend
 * on identity (identity already depends on organization).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Organisation administration")
package com.epi.procurement.orgadmin;
