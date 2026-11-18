package com.epi.procurement.request;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.platform.web.ApiProblemException;

/**
 * What internal messaging may know about a request, for one user (docs/architecture/internal-messaging.md). Every
 * call goes through {@link RequestService#get}, so it applies exactly the visibility rule of the request screens
 * ({@link RequestAccessPolicy}): a request the user may not see is "absent", never partly described.
 */
@Component
public class RequestMessagingQueries {

	private final RequestService requests;

	RequestMessagingQueries(RequestService requests) {
		this.requests = requests;
	}

	/** The fields a request card shows, and the companies that bound a request discussion. */
	public record RequestCard(long id, String number, String title, String status, String userStatus,
			long requestingCompanyId, Long buyingCompanyId) {

		public boolean awaitingApproval() {
			return "IN_APPROVAL".equals(status);
		}

	}

	/** The request as this user may see it, or empty when it does not exist or is outside the user's access. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public Optional<RequestCard> visibleTo(CurrentUser user, long requestId) {
		try {
			RequestViews.Detail d = requests.get(user, requestId);
			return Optional.of(new RequestCard(d.id(), d.number(), d.title(), d.status(), d.userStatus(),
					d.requestingCompany().id(), d.buyingCompany() == null ? null : d.buyingCompany().id()));
		}
		catch (ApiProblemException ex) {
			if (ex.getStatus() == HttpStatus.NOT_FOUND || ex.getStatus() == HttpStatus.FORBIDDEN) {
				return Optional.empty();
			}
			throw ex;
		}
	}

}
