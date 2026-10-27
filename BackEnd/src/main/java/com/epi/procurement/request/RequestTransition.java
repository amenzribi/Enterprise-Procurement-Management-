package com.epi.procurement.request;

import java.time.Instant;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * One row of a request's stage timeline (S1DR-09), written with the status change and its audit event.
 */
@Entity
@Table(name = "purchase_request_transition")
class RequestTransition {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "purchase_request_transition_id")
	@SequenceGenerator(name = "purchase_request_transition_id", sequenceName = "seq_purchase_request_transition",
			allocationSize = 50)
	private Long id;

	@Column(name = "request_id", nullable = false, updatable = false)
	private Long requestId;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", length = 40, updatable = false)
	private RequestStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, length = 40, updatable = false)
	private RequestStatus toStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30, updatable = false)
	private Stage stage;

	@Column(name = "owner_user_id", updatable = false)
	private Long ownerUserId;

	@Column(name = "actor_user_id", nullable = false, updatable = false)
	private Long actorUserId;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "planned_due_at", updatable = false)
	private Instant plannedDueAt;

	@Nationalized
	@Column(length = 1000, updatable = false)
	private String comment;

	protected RequestTransition() {
	}

	RequestTransition(long requestId, RequestStatus fromStatus, RequestStatus toStatus, Long ownerUserId,
			long actorUserId, Instant occurredAt) {
		this.requestId = requestId;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.stage = toStatus.stage();
		this.ownerUserId = ownerUserId;
		this.actorUserId = actorUserId;
		this.occurredAt = occurredAt;
	}

	Long getRequestId() {
		return requestId;
	}

	RequestStatus getFromStatus() {
		return fromStatus;
	}

	RequestStatus getToStatus() {
		return toStatus;
	}

	Stage getStage() {
		return stage;
	}

	Long getOwnerUserId() {
		return ownerUserId;
	}

	Long getActorUserId() {
		return actorUserId;
	}

	Instant getOccurredAt() {
		return occurredAt;
	}

	Instant getPlannedDueAt() {
		return plannedDueAt;
	}

	String getComment() {
		return comment;
	}

}
