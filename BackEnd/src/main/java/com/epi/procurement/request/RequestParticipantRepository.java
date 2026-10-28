package com.epi.procurement.request;

import org.springframework.data.jpa.repository.JpaRepository;

interface RequestParticipantRepository extends JpaRepository<RequestParticipant, RequestParticipant.Key> {

}
