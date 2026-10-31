package com.epi.procurement.assistant;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.ClientAddressResolver;
import com.epi.procurement.platform.web.Idempotency;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/assistant")
@EnableConfigurationProperties(AssistantSettings.class)
class AssistantController {

	private final AssistantService assistant;

	private final CurrentUserProvider currentUser;

	private final Idempotency idempotency;

	private final ClientAddressResolver addresses;

	AssistantController(AssistantService assistant, CurrentUserProvider currentUser, Idempotency idempotency,
			ClientAddressResolver addresses) {
		this.assistant = assistant;
		this.currentUser = currentUser;
		this.idempotency = idempotency;
		this.addresses = addresses;
	}

	@GetMapping("/status")
	AssistantViews.StatusResponse status() {
		return assistant.status(currentUser.require());
	}

	@GetMapping("/threads")
	List<AssistantViews.ThreadSummary> threads() {
		return assistant.threads(currentUser.require());
	}

	@PostMapping("/ask")
	ResponseEntity<?> ask(@RequestBody AssistantViews.AskRequest body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key, HttpServletRequest request) {
		var user = currentUser.require();
		String addr = addresses.resolve(request);
		return idempotency.execute("user:" + user.id(), key, "assistant-ask|" + body.question(),
				() -> ResponseEntity.ok(assistant.ask(user, body, addr)));
	}

}
