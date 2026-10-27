package com.epi.procurement.request;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * Unscoped access to purchase requests. Only {@link ScopedRequests} may use it (architecture test, S1-AC-14,
 * S1DR-19): every other lookup goes through the visibility rules.
 */
interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long>, JpaSpecificationExecutor<PurchaseRequest> {

	/**
	 * Loads the request with an update lock held until commit; the first lock of the submit lock order (request row
	 * -> document counter -> audit chain head, S1DR-10).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from PurchaseRequest r where r.id = :id")
	Optional<PurchaseRequest> findByIdForUpdate(@Param("id") long id);

}
