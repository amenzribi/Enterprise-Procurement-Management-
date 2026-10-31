package com.epi.procurement.assistant;

import java.util.List;

/**
 * Provider-neutral model port (ADR-012). Implementations must not call external services unless explicitly
 * configured for {@code live} after an approved hosting decision.
 */
public interface AssistantModel {

	record Prompt(String system, String userQuestion, String retrievedDataBlock, List<String> allowedIntents) {
	}

	record Completion(String answerMarkdown, String intent, boolean includesDraft, String draftAudience,
			String draftBody) {
	}

	Completion complete(Prompt prompt);

}
