package com.epi.procurement.importdata;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.Idempotency;

@RestController
@RequestMapping("/api/v1/imports")
class ImportController {

	private final ImportService imports;

	private final CurrentUserProvider currentUser;

	private final Idempotency idempotency;

	ImportController(ImportService imports, CurrentUserProvider currentUser, Idempotency idempotency) {
		this.imports = imports;
		this.currentUser = currentUser;
		this.idempotency = idempotency;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<?> create(@RequestParam String kind, @RequestParam(required = false) Long companyId,
			@RequestParam("file") MultipartFile file,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key,
				"import|" + kind + "|" + companyId + "|" + file.getOriginalFilename() + "|" + file.getSize(),
				() -> ResponseEntity.ok(imports.importCsv(user, kind, companyId, file)));
	}

	@GetMapping("/{id}")
	ImportService.JobResult get(@PathVariable long id) {
		return imports.get(currentUser.require(), id);
	}
}
