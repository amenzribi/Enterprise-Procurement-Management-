package com.epi.procurement.request;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.platform.web.ApiProblemException;

import jakarta.persistence.criteria.Predicate;

/**
 * Read-only request lookups for the internal assistant (Sprint 12). Always applies {@link ScopedRequests}.
 */
@Service
public class RequestAssistantQueries {

	private static final int MAX = 20;

	private final RequestService requests;

	RequestAssistantQueries(RequestService requests) {
		this.requests = requests;
	}

	public record RequestCard(long id, String number, String title, String status, LocalDate requiredDate) {
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public Optional<RequestCard> find(CurrentUser user, long id) {
		try {
			RequestViews.Detail d = requests.get(user, id);
			return Optional.of(new RequestCard(d.id(), d.number(), d.title(), d.status(), d.requiredDate()));
		}
		catch (ApiProblemException ex) {
			if (ex.getStatus() == HttpStatus.NOT_FOUND) {
				return Optional.empty();
			}
			throw ex;
		}
	}

	public List<RequestCard> search(CurrentUser user, String statusCode, String q) {
		List<RequestStatus> statuses = new ArrayList<>();
		if (StringUtils.hasText(statusCode)) {
			RequestValidator.parse(RequestStatus.class, statusCode.trim().toUpperCase(Locale.ROOT))
				.ifPresent(statuses::add);
		}
		Specification<PurchaseRequest> filter = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (!statuses.isEmpty()) {
				predicates.add(root.get("status").in(statuses));
			}
			if (StringUtils.hasText(q)) {
				String pattern = "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
					.replace("_", "\\_") + "%";
				predicates.add(cb.or(cb.like(cb.lower(root.get("number")), pattern, '\\'),
						cb.like(cb.lower(root.get("title")), pattern, '\\')));
			}
			return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
		};
		return requests.search(user, filter, PageRequest.of(0, MAX, Sort.by(Sort.Direction.DESC, "updatedAt")))
			.items()
			.stream()
			.map(s -> new RequestCard(s.id(), s.number(), s.title(), s.status(), s.requiredDate()))
			.toList();
	}

}
