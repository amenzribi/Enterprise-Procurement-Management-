package com.epi.procurement.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.ApiProblemException;

import jakarta.persistence.criteria.Predicate;

@RestController
@RequestMapping("/api/v1/requests")
class RequestController {

	static final int MAX_PAGE_SIZE = 100;

	/** Sortable fields exposed by the API, mapped to entity properties (S1DR-18). */
	private static final Map<String, String> SORTABLE = Map.of("number", "number", "title", "title", "status",
			"status", "totalAmount", "totalAmount", "requiredDate", "requiredDate", "updatedAt", "updatedAt",
			"createdAt", "createdAt", "submittedAt", "submittedAt");

	private final RequestService service;

	private final CurrentUserProvider currentUser;

	RequestController(RequestService service, CurrentUserProvider currentUser) {
		this.service = service;
		this.currentUser = currentUser;
	}

	@GetMapping("/form-options")
	RequestViews.FormOptions formOptions() {
		return service.formOptions(currentUser.require());
	}

	@GetMapping("/statuses")
	List<RequestViews.StatusInfo> statuses() {
		return Arrays.stream(RequestStatus.values())
			.map(s -> new RequestViews.StatusInfo(s.name(), s.rank(), s.stage().name(), s.isLineStatus()))
			.toList();
	}

	/**
	 * @param scope {@code mine} (default: requests I requested or entered) or {@code visible} (everything I may see)
	 * @param mine legacy alias: {@code mine=false} is {@code scope=visible}
	 * @param status comma-separated status codes
	 * @param q text matched against number and title
	 * @param sort {@code field,asc|desc}; fields: number, title, status, totalAmount, requiredDate, updatedAt,
	 * createdAt, submittedAt (default updatedAt,desc)
	 */
	@GetMapping
	RequestViews.Page<RequestViews.Summary> search(@RequestParam(required = false) String scope,
			@RequestParam(required = false) Boolean mine, @RequestParam(required = false) String status,
			@RequestParam(required = false) String q, @RequestParam(required = false) String sort,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		CurrentUser user = currentUser.require();
		boolean onlyMine = resolveMine(scope, mine);
		List<RequestStatus> statuses = parseStatuses(status);
		if (page < 0) {
			throw ApiProblemException.invalidParameter("page");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw ApiProblemException.invalidParameter("size");
		}
		Pageable pageable = PageRequest.of(page, size, parseSort(sort).and(Sort.by(Sort.Direction.DESC, "id")));
		return service.search(user, filter(user, onlyMine, statuses, q), pageable);
	}

	@GetMapping("/{id}")
	RequestViews.Detail get(@PathVariable long id) {
		return service.get(currentUser.require(), id);
	}

	@PostMapping
	ResponseEntity<RequestViews.Detail> create(@RequestBody RequestPayload payload) {
		RequestViews.Detail created = service.create(currentUser.require(), payload);
		return ResponseEntity.created(URI.create("/api/v1/requests/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	RequestViews.Detail update(@PathVariable long id, @RequestBody RequestPayload payload) {
		return service.update(currentUser.require(), id, payload);
	}

	@PostMapping("/{id}/submit")
	RequestViews.Detail submit(@PathVariable long id, @RequestBody RequestPayload.VersionPayload body) {
		return service.submit(currentUser.require(), id, body.version());
	}

	/**
	 * Discards a draft (DRAFT only). {@code version} is the version the client last read.
	 */
	@DeleteMapping("/{id}")
	ResponseEntity<Void> discard(@PathVariable long id, @RequestParam(required = false) Integer version) {
		service.discard(currentUser.require(), id, version);
		return ResponseEntity.noContent().build();
	}

	private static boolean resolveMine(String scope, Boolean mine) {
		if (scope == null) {
			return mine == null || mine;
		}
		return switch (scope) {
			case "mine" -> true;
			case "visible" -> false;
			default -> throw ApiProblemException.invalidParameter("scope");
		};
	}

	private static List<RequestStatus> parseStatuses(String status) {
		List<RequestStatus> statuses = new ArrayList<>();
		if (!StringUtils.hasText(status)) {
			return statuses;
		}
		for (String code : status.split(",")) {
			statuses.add(RequestValidator.parse(RequestStatus.class, code.trim())
				.orElseThrow(() -> ApiProblemException.invalidParameter("status")));
		}
		return statuses;
	}

	private static Sort parseSort(String sort) {
		if (!StringUtils.hasText(sort)) {
			return Sort.by(Sort.Direction.DESC, "updatedAt");
		}
		String[] parts = sort.split(",");
		String property = SORTABLE.get(parts[0].trim());
		if (property == null || parts.length > 2) {
			throw ApiProblemException.invalidParameter("sort");
		}
		Sort.Direction direction = Sort.Direction.ASC;
		if (parts.length == 2) {
			direction = switch (parts[1].trim().toLowerCase(Locale.ROOT)) {
				case "asc" -> Sort.Direction.ASC;
				case "desc" -> Sort.Direction.DESC;
				default -> throw ApiProblemException.invalidParameter("sort");
			};
		}
		return Sort.by(direction, property);
	}

	private static Specification<PurchaseRequest> filter(CurrentUser user, boolean onlyMine,
			List<RequestStatus> statuses, String q) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (onlyMine) {
				predicates.add(cb.or(cb.equal(root.get("requesterUserId"), user.id()),
						cb.equal(root.get("enteredByUserId"), user.id())));
			}
			if (!statuses.isEmpty()) {
				predicates.add(root.get("status").in(statuses));
			}
			if (StringUtils.hasText(q)) {
				String pattern = "%" + escapeLike(q.strip().toLowerCase(Locale.ROOT)) + "%";
				predicates.add(cb.or(cb.like(cb.lower(root.get("number")), pattern, '\\'),
						cb.like(cb.lower(root.get("title")), pattern, '\\')));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
	}

}
