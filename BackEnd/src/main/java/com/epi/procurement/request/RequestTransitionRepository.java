package com.epi.procurement.request;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface RequestTransitionRepository extends JpaRepository<RequestTransition, Long> {

	List<RequestTransition> findByRequestIdOrderByOccurredAtAscIdAsc(long requestId);

}
