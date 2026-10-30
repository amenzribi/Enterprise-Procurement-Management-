package com.epi.procurement.logistics;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.Idempotency;

@RestController
@RequestMapping("/api/v1/logistics")
class LogisticsController {

	private final LogisticsService logistics;

	private final CurrentUserProvider currentUser;

	private final Idempotency idempotency;

	LogisticsController(LogisticsService logistics, CurrentUserProvider currentUser, Idempotency idempotency) {
		this.logistics = logistics;
		this.currentUser = currentUser;
		this.idempotency = idempotency;
	}

	@GetMapping("/requests/{requestId}/shipments")
	List<ShipmentView> list(@PathVariable long requestId) {
		return logistics.list(currentUser.require(), requestId);
	}

	@PostMapping("/requests/{requestId}/shipments")
	ResponseEntity<?> create(@PathVariable long requestId, @RequestBody(required = false) CreateShipmentBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		CreateShipmentBody payload = body == null
				? new CreateShipmentBody(null, null, null, null, null, null, null, null)
				: body;
		return idempotency.execute("user:" + user.id(), key, "shipment-create|" + requestId + "|" + payload,
				() -> ResponseEntity.ok(logistics.create(user, requestId, payload)));
	}

	@GetMapping("/shipments/{shipmentId}")
	ShipmentView get(@PathVariable long shipmentId) {
		return logistics.get(currentUser.require(), shipmentId);
	}

	@PutMapping("/shipments/{shipmentId}/milestones/{milestoneId}")
	ShipmentView updateMilestone(@PathVariable long shipmentId, @PathVariable long milestoneId,
			@RequestBody MilestoneUpdateBody body) {
		return logistics.updateMilestone(currentUser.require(), shipmentId, milestoneId, body);
	}

	@PostMapping("/shipments/{shipmentId}/documents")
	DocumentView addDocument(@PathVariable long shipmentId, @RequestBody DocumentBody body) {
		return logistics.addDocument(currentUser.require(), shipmentId, body);
	}

}
