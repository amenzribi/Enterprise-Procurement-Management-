package com.epi.procurement.messaging;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.platform.web.ApiProblemException;

/**
 * Read-only messaging lookups for the internal assistant (Sprint 12). Never widens supplier portal rights.
 */
@Service
public class MessagingAssistantQueries {

	private final MessagingService messaging;

	MessagingAssistantQueries(MessagingService messaging) {
		this.messaging = messaging;
	}

	public record ConversationCard(long id, long requestId, String contextType, String supplierCode) {
	}

	public record MessageSnippet(long id, String audience, String bodyText, String senderDisplayName) {
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public Optional<ConversationCard> findConversation(CurrentUser user, long conversationId) {
		try {
			var d = messaging.getBuyer(user, conversationId);
			return Optional.of(new ConversationCard(d.id(), d.requestId(), d.contextType(), d.supplierCode()));
		}
		catch (ApiProblemException ex) {
			if (ex.getStatus() == HttpStatus.NOT_FOUND || ex.getStatus() == HttpStatus.FORBIDDEN) {
				return Optional.empty();
			}
			throw ex;
		}
	}

	public List<MessageSnippet> messages(CurrentUser user, long conversationId, boolean externalOnly) {
		return messaging.messagesBuyer(user, conversationId, null)
			.items()
			.stream()
			.filter(m -> !externalOnly || "EXTERNAL".equals(m.audience()))
			.map(m -> new MessageSnippet(m.id(), m.audience(), m.bodyText(), m.senderDisplayName()))
			.limit(40)
			.toList();
	}

	public List<ConversationCard> listForRequest(CurrentUser user, long requestId) {
		return messaging.listBuyer(user, null)
			.stream()
			.filter(c -> c.requestId() == requestId)
			.map(c -> new ConversationCard(c.id(), c.requestId(), c.contextType(), c.supplierCode()))
			.toList();
	}

}
