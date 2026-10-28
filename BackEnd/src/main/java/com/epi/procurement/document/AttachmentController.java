package com.epi.procurement.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;

/**
 * Attachments API (S2-AC-12). Upload is {@code multipart/form-data} with a {@code file} part and an optional
 * {@code lineId}; downloads are always served as attachments with {@code nosniff}.
 */
@RestController
@RequestMapping("/api/v1")
class AttachmentController {

	private final Attachments attachments;

	private final CurrentUserProvider currentUser;

	AttachmentController(Attachments attachments, CurrentUserProvider currentUser) {
		this.attachments = attachments;
		this.currentUser = currentUser;
	}

	@GetMapping("/requests/{id}/attachments")
	List<Attachments.AttachmentView> list(@PathVariable long id) {
		return attachments.list(currentUser.require(), id);
	}

	@PostMapping(path = "/requests/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<Attachments.AttachmentView> upload(@PathVariable long id,
			@RequestPart(name = "file", required = false) MultipartFile file,
			@RequestParam(name = "lineId", required = false) Long lineId) {
		if (file == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("file", "required")));
		}
		byte[] content;
		try {
			content = file.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		Attachments.AttachmentView view = attachments.upload(currentUser.require(), id, lineId,
				file.getOriginalFilename(), content);
		return ResponseEntity.created(URI.create("/api/v1/attachments/" + view.id())).body(view);
	}

	@GetMapping("/attachments/{id}/content")
	ResponseEntity<byte[]> download(@PathVariable long id) {
		Attachments.Download download = attachments.download(currentUser.require(), id);
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(download.fileName(), java.nio.charset.StandardCharsets.UTF_8)
						.build()
						.toString())
			.header("X-Content-Type-Options", "nosniff")
			.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
			.contentType(MediaType.parseMediaType(download.contentType()))
			.body(download.content());
	}

	@DeleteMapping("/attachments/{id}")
	ResponseEntity<Void> delete(@PathVariable long id) {
		attachments.delete(currentUser.require(), id);
		return ResponseEntity.noContent().build();
	}

}
