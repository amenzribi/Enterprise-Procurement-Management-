package com.epi.procurement.request;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUserProvider;

/**
 * GLOBAL_ADMIN request administration — installation/group scoped. Separate from ordinary request, approval and
 * purchasing APIs.
 */
@RestController
@RequestMapping("/api/v1/admin/requests")
class RequestAdminController {

	private final RequestAdminService service;

	private final CurrentUserProvider currentUser;

	RequestAdminController(RequestAdminService service, CurrentUserProvider currentUser) {
		this.service = service;
		this.currentUser = currentUser;
	}

	@GetMapping
	RequestAdminViews.Page search(@RequestParam(required = false) String q,
			@RequestParam(required = false) Long companyId, @RequestParam(required = false) String status,
			@RequestParam(required = false) Boolean archived, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.search(currentUser.require(), q, companyId, status, archived, page, size);
	}

	@GetMapping("/{id}")
	RequestAdminViews.Detail get(@PathVariable long id) {
		return service.get(currentUser.require(), id);
	}

	@PostMapping("/{id}/archive")
	RequestAdminViews.MutationResult archive(@PathVariable long id, @RequestBody RequestAdminViews.ReasonPayload body) {
		return service.archive(currentUser.require(), id, body);
	}

	@PostMapping("/{id}/restore")
	RequestAdminViews.MutationResult restore(@PathVariable long id, @RequestBody RequestAdminViews.ReasonPayload body) {
		return service.restore(currentUser.require(), id, body);
	}

	@PostMapping("/{id}/cancel")
	RequestAdminViews.MutationResult cancel(@PathVariable long id, @RequestBody RequestAdminViews.ReasonPayload body) {
		return service.cancel(currentUser.require(), id, body);
	}

	@PostMapping("/{id}/return-for-revision")
	RequestAdminViews.MutationResult returnForRevision(@PathVariable long id,
			@RequestBody RequestAdminViews.ReasonPayload body) {
		return service.returnForRevision(currentUser.require(), id, body);
	}

	@PostMapping("/{id}/correct-draft")
	RequestAdminViews.MutationResult correctDraft(@PathVariable long id,
			@RequestBody RequestAdminViews.DraftCorrectionPayload body) {
		return service.correctDraft(currentUser.require(), id, body);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> deleteDraft(@PathVariable long id, @RequestParam(required = false) Integer version,
			@RequestParam String reason) {
		service.deleteDraft(currentUser.require(), id, version, reason);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/cleanup/preview")
	RequestAdminViews.CleanupPreview preview(@RequestBody(required = false) RequestAdminViews.CleanupFilter filter) {
		return service.previewCleanup(currentUser.require(), filter == null ? new RequestAdminViews.CleanupFilter(null,
				null, null, null, null, null, null) : filter);
	}

	@PostMapping("/cleanup/execute")
	RequestAdminViews.CleanupExecuteResult execute(@RequestBody RequestAdminViews.CleanupExecutePayload body) {
		return service.executeCleanup(currentUser.require(), body);
	}

	@GetMapping("/action-matrix")
	Map<String, Object> actionMatrixDoc() {
		RequestAdminService.requireRequestAdmin(currentUser.require());
		return Map.of("protectedNumber", RequestAdminActions.PROTECTED_NUMBER, "maxCleanupBatch",
				RequestAdminActions.MAX_CLEANUP_BATCH, "actions",
				Map.of("DELETE", "Permanent removal — eligible drafts only", "CANCEL",
						"Ends supported pre-commitment workflow; preserves number and completed decisions", "ARCHIVE",
						"Hides from active lists; preserves history; does not reverse PO/receipt/payment", "RESTORE",
						"Returns to normal lists without reopening cancelled business states"));
	}

}
