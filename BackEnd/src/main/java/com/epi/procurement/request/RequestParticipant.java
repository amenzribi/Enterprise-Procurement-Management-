package com.epi.procurement.request;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * A user taking part in a request beyond its authors (approver, delegate). Request visibility includes participants
 * (S2-AC-13): an approver sees a request while they have or had a task on it.
 */
@Entity
@Table(name = "purchase_request_participant")
@IdClass(RequestParticipant.Key.class)
class RequestParticipant {

	@Id
	@Column(name = "request_id")
	private Long requestId;

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(nullable = false, length = 30)
	private String reason;

	@Column(name = "added_at", nullable = false)
	private Instant addedAt;

	protected RequestParticipant() {
	}

	RequestParticipant(long requestId, long userId, String reason, Instant addedAt) {
		this.requestId = requestId;
		this.userId = userId;
		this.reason = reason;
		this.addedAt = addedAt;
	}

	static class Key implements Serializable {

		private Long requestId;

		private Long userId;

		Key() {
		}

		Key(Long requestId, Long userId) {
			this.requestId = requestId;
			this.userId = userId;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Key key && Objects.equals(requestId, key.requestId) && Objects.equals(userId, key.userId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(requestId, userId);
		}

	}

}
