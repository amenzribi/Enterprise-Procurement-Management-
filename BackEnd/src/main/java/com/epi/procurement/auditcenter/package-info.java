/**
 * Audit Center (Sprint 2, P1.3): the global administrators' read-only view of the whole audit trail. Searches it with
 * server-side filters and keyset paging, exports it to CSV within a row limit, and verifies the hash chains in the
 * background, in batches. Every use is itself recorded in the trail. Nothing here can change an audit event.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Audit Center")
package com.epi.procurement.auditcenter;
