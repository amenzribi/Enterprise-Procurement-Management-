package com.epi.procurement.assistant;

import java.time.Instant;
import java.util.List;

final class AssistantViews {

	private AssistantViews() {
	}

	record StatusResponse(String mode, boolean available, String label) {
	}

	record AskRequest(Long threadId, String question, Long requestId, Long conversationId) {
	}

	record SourceRef(String type, long id, String label, String path) {
	}

	record DraftSuggestion(String audience, String bodyText, Long conversationId, String label) {
	}

	record AskResponse(long threadId, long turnId, String mode, String answerMarkdown, String intent,
			List<SourceRef> sources, DraftSuggestion draft, Instant answeredAt, long durationMs) {
	}

	record ThreadSummary(long id, String title, Instant updatedAt) {
	}

}
