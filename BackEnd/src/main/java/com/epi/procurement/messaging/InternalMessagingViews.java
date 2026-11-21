package com.epi.procurement.messaging;

import java.time.Instant;
import java.util.List;

/** JSON shapes of the internal messaging API (docs/architecture/internal-messaging.md). */
final class InternalMessagingViews {

	private InternalMessagingViews() {
	}

	/** An employee: name, department and the role codes held in their company. Never an e-mail address. */
	record Person(long id, String displayName, String department, List<String> roles) {
	}

	/**
	 * A request card as one viewer may see it. When the viewer cannot see the request, {@code available} is false and
	 * every other field is null: not even the id or reference is disclosed.
	 */
	record RequestCard(boolean available, Long id, String number, String title, String status, String userStatus) {

		static RequestCard unavailable() {
			return new RequestCard(false, null, null, null, null, null);
		}

	}

	/**
	 * TEXT: {@code bodyText} is what the sender wrote. SYSTEM: {@code bodyText} is an event code
	 * ({@code PARTICIPANT_ADDED}, {@code DISCUSSION_STARTED}) and {@code subject} the person it is about.
	 */
	record Message(long id, long conversationId, String kind, Person sender, Person subject, boolean mine,
			String bodyText, RequestCard request, String clientMessageId, Instant createdAt) {
	}

	record MessagePage(List<Message> items, boolean hasOlder) {
	}

	/** {@code counterpart} for a direct conversation; {@code request} for a request discussion. */
	record ConversationSummary(long id, String kind, Person counterpart, RequestCard request, int participantCount,
			String lastMessageKind, String lastMessagePreview, Person lastMessageSender, Instant lastMessageAt,
			int unread) {
	}

	record ConversationDetail(long id, String kind, Person counterpart, RequestCard request, List<Person> participants,
			boolean canAddParticipants, Long lastReadMessageId, Instant createdAt) {
	}

	record Unread(int total) {
	}

	/** Result of a send. {@code cardWithheld}: the text was delivered but the request card was not (see the service). */
	record SendResult(Message message, boolean duplicate, boolean cardWithheld) {
	}

	record Approver(Person person, String stageName, Person onBehalfOf) {
	}

	/**
	 * {@code state}: AWAITING (approvers listed), NOT_IN_APPROVAL, NO_APPROVER (in approval but no pending task) or
	 * SELF_ONLY (the caller is the only current approver).
	 */
	record Approvers(String state, List<Approver> approvers) {
	}

	/**
	 * {@code conversation} only for a participant. {@code exists} tells a non-participant who can see the request that
	 * a discussion exists, so they ask to be added instead of trying to start a second one.
	 */
	record Discussion(ConversationDetail conversation, boolean exists, boolean canStart) {
	}

	// Requests ---------------------------------------------------------------------------------------------------

	record OpenDirect(Long userId) {
	}

	record Send(String bodyText, String clientMessageId, Long requestId) {
	}

	record MarkRead(Long messageId) {
	}

	record AddParticipant(Long userId) {
	}

	record MessageApprover(Long recipientUserId, String bodyText, String clientMessageId) {
	}

	/** Where "Message approver" put the message: a direct conversation, or the request discussion. */
	record ApproverMessageResult(long conversationId, String kind, Message message, boolean duplicate) {
	}

}
