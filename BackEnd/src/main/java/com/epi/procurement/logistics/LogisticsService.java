package com.epi.procurement.logistics;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.Role;
import com.epi.procurement.identity.RoleGrant;
import com.epi.procurement.notification.Notice;
import com.epi.procurement.notification.Notifications;
import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;
import com.epi.procurement.request.RequestLogisticsQueries;
import com.epi.procurement.request.RequestSnapshot;
import com.epi.procurement.request.RequestStatus;
import com.epi.procurement.request.RequestWorkflow;

@Service
class LogisticsService {

	private static final String ENTITY = "PURCHASE_REQUEST";

	private static final List<String> LOCAL = List.of("ORDER_CONFIRMED", "DISPATCHED", "IN_TRANSIT_LOCAL", "DELIVERED");

	private static final List<String> INTERNATIONAL = List.of("IN_PRODUCTION", "READY", "SHIPPED", "AT_PORT",
			"IN_CUSTOMS", "IN_WAREHOUSE", "DELIVERED");

	private static final Set<String> DOC_KINDS = Set.of("COMMERCIAL_INVOICE", "PACKING_LIST", "CERTIFICATE_OF_ORIGIN",
			"BILL_OF_LADING", "AIR_WAYBILL", "CUSTOMS", "DELIVERY_NOTE", "CMR", "OTHER");

	private static final Map<String, RequestStatus> STATUS_MAP = Map.ofEntries(
			Map.entry("ORDER_CONFIRMED", RequestStatus.ORDER_CONFIRMED),
			Map.entry("DISPATCHED", RequestStatus.SHIPPED), Map.entry("IN_TRANSIT_LOCAL", RequestStatus.SHIPPED),
			Map.entry("IN_PRODUCTION", RequestStatus.IN_PRODUCTION), Map.entry("READY", RequestStatus.READY),
			Map.entry("SHIPPED", RequestStatus.SHIPPED), Map.entry("AT_PORT", RequestStatus.AT_PORT),
			Map.entry("IN_CUSTOMS", RequestStatus.IN_CUSTOMS), Map.entry("IN_WAREHOUSE", RequestStatus.IN_WAREHOUSE),
			Map.entry("DELIVERED", RequestStatus.DELIVERED));

	private final LogisticsStore store;

	private final ShipmentNumbering numbering;

	private final RequestWorkflow requests;

	private final OrganizationDirectory organization;

	private final AuditLog auditLog;

	private final Notifications notifications;

	private final Clock clock;

	private final RequestLogisticsQueries requestLogistics;

	LogisticsService(LogisticsStore store, ShipmentNumbering numbering, RequestWorkflow requests,
			RequestLogisticsQueries requestLogistics, OrganizationDirectory organization, AuditLog auditLog,
			Notifications notifications, ObjectProvider<Clock> clock) {
		this.store = store;
		this.numbering = numbering;
		this.requests = requests;
		this.requestLogistics = requestLogistics;
		this.organization = organization;
		this.auditLog = auditLog;
		this.notifications = notifications;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@Transactional(readOnly = true)
	LogisticsInboxPage inbox(CurrentUser user, String q, int page, int size) {
		if (!user.hasRole(Role.PURCHASING_MANAGER) && !user.hasRole(Role.BUYER)) {
			throw ApiProblemException.forbidden();
		}
		int safePage = Math.max(0, page);
		int safeSize = size < 1 || size > 100 ? 20 : size;
		RequestLogisticsQueries.ConfirmedPoInboxPage result = requestLogistics.confirmedPoInbox(user.groupId(),
				inboxCompanies(user), q, safePage, safeSize);
		List<LogisticsInboxItem> items = result.rows().stream()
			.map(row -> new LogisticsInboxItem(row.requestId(), row.requestNumber(), row.requestTitle(),
					row.requestStatus(), row.poId(), row.poNumber(), row.poStatus(), row.shipmentId(),
					row.shipmentNumber(), row.shipmentStatus(), row.shipmentKind(), row.poCreatedAt(),
					"DELIVERED".equals(row.shipmentStatus()) || "DELIVERED".equals(row.requestStatus())))
			.toList();
		int total = (int) result.total();
		return new LogisticsInboxPage(items, safePage, safeSize, total, total == 0 ? 0 : (total + safeSize - 1) / safeSize);
	}

	/** Buying companies of the user's purchasing grants; {@code null} when a grant is not company-bound. */
	private static Set<Long> inboxCompanies(CurrentUser user) {
		Set<Long> companies = new HashSet<>();
		for (Role role : List.of(Role.PURCHASING_MANAGER, Role.BUYER)) {
			for (RoleGrant grant : user.grants(role)) {
				if (grant.companyId() == null) {
					return null;
				}
				companies.add(grant.companyId());
			}
		}
		return companies;
	}

	@Transactional(readOnly = true)
	List<ShipmentView> list(CurrentUser user, long requestId) {
		RequestSnapshot request = requests.requireVisible(user, requestId);
		requireViewer(user, request);
		LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
		return store.shipmentsForRequest(requestId).stream().map(s -> toView(s, today)).toList();
	}

	@Transactional(readOnly = true)
	ShipmentView get(CurrentUser user, long shipmentId) {
		LogisticsStore.ShipmentRow shipment = store.findShipment(shipmentId).orElseThrow(ApiProblemException::notFound);
		RequestSnapshot request = requests.requireVisible(user, shipment.requestId());
		requireViewer(user, request);
		return toView(shipment, LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
	}

	@Transactional
	ShipmentView create(CurrentUser user, long requestId, CreateShipmentBody body) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireActor(user, request);
		requireRequestVersion(body == null ? null : body.requestVersion(), request);
		LogisticsStore.PoSnap po = store.findConfirmedPo(requestId).orElseThrow(ApiProblemException::notFound);
		if (!"CONFIRMED".equals(po.status())) {
			throw ApiProblemException.conflict("po-not-confirmed", "Confirm the purchase order before shipping.");
		}
		String kind = resolveKind(body, request.purchaseType());
		Map<Long, String> lineKinds = requestLogistics.lineKinds(requestId);
		List<LogisticsStore.PoLineSnap> poLines = store.poLines(po.id())
			.stream()
			.map(l -> l.withKind(lineKinds.get(l.requestLineId())))
			.toList();
		Map<Long, LogisticsStore.PoLineSnap> byId = new LinkedHashMap<>();
		for (LogisticsStore.PoLineSnap line : poLines) {
			byId.put(line.id(), line);
		}
		List<ShipmentLineBody> requested = body == null || body.lines() == null || body.lines().isEmpty()
				? poLines.stream().filter(l -> !l.service()).map(l -> new ShipmentLineBody(l.id(), l.quantity())).toList()
				: body.lines();
		if (requested.isEmpty()) {
			if (body == null || body.lines() == null || body.lines().isEmpty()) {
				throw ApiProblemException.conflict("no-goods-lines", "This order has no goods to ship.");
			}
			throw new ValidationFailedException(List.of(FieldViolation.of("lines", "required")));
		}
		Instant now = clock.instant();
		OrganizationDirectory.CompanyRef buying = organization.findCompany(request.buyingCompanyId())
			.orElseThrow(ApiProblemException::notFound);
		String number = numbering.next(buying.id(), buying.code(), LocalDate.ofInstant(now, ZoneOffset.UTC).getYear());
		long shipmentId = store.insertShipment(request.groupId(), requestId, po.id(), number, kind, "PLANNED",
				request.buyingCompanyId(), trim(body == null ? null : body.carrierName()),
				trim(body == null ? null : body.trackingReference()), trim(body == null ? null : body.originPlace()),
				trim(body == null ? null : body.destinationPlace()), user.id(), now);
		short lineNo = 1;
		for (ShipmentLineBody lineBody : requested) {
			LogisticsStore.PoLineSnap poLine = byId.get(lineBody.purchaseOrderLineId());
			if (poLine == null) {
				throw new ValidationFailedException(List.of(FieldViolation.of("lines", "unknown-po-line")));
			}
			if (poLine.service()) {
				throw ApiProblemException.conflict("service-line", "A service line is accepted, not shipped or received.");
			}
			BigDecimal qty = lineBody.quantity() == null ? poLine.quantity() : lineBody.quantity();
			if (qty == null || qty.signum() <= 0) {
				throw new ValidationFailedException(List.of(FieldViolation.of("lines", "invalid-quantity")));
			}
			BigDecimal already = store.shippedQuantity(poLine.id());
			if (already.add(qty).compareTo(poLine.quantity()) > 0) {
				throw ApiProblemException.conflict("quantity-exceeded",
						"Shipped quantity would exceed the purchase-order line.");
			}
			store.insertLine(shipmentId, poLine.id(), poLine.requestLineId(), lineNo++, poLine.description(), qty,
					poLine.unit());
		}
		List<String> template = "INTERNATIONAL".equals(kind) ? INTERNATIONAL : LOCAL;
		Map<String, LocalDate> plans = new LinkedHashMap<>();
		if (body != null && body.milestonePlans() != null) {
			for (MilestonePlanBody plan : body.milestonePlans()) {
				if (plan != null && StringUtils.hasText(plan.code())) {
					plans.put(plan.code().strip().toUpperCase(Locale.ROOT), plan.plannedDate());
				}
			}
		}
		short order = 1;
		for (String code : template) {
			store.insertMilestone(shipmentId, code, order++, plans.get(code));
		}
		RequestSnapshot after = requests.currentLocked(requestId);
		audit(user, after, "SHIPMENT_CREATED", after.status(), null,
				"{\"shipmentId\":" + shipmentId + ",\"number\":\"" + number + "\",\"kind\":\"" + kind + "\"}");
		notifyAuthors(after, "SHIPMENT_CREATED", user);
		return toView(store.findShipment(shipmentId).orElseThrow(), LocalDate.ofInstant(now, ZoneOffset.UTC));
	}

	@Transactional
	ShipmentView updateMilestone(CurrentUser user, long shipmentId, long milestoneId, MilestoneUpdateBody body) {
		LogisticsStore.ShipmentRow shipment = store.findShipment(shipmentId).orElseThrow(ApiProblemException::notFound);
		RequestSnapshot request = requests.requireVisibleForUpdate(user, shipment.requestId());
		requireActor(user, request);
		if (body != null && body.version() != null && body.version() != shipment.version()) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "stale-version",
					"The shipment was changed by someone else. Reload it and try again.");
		}
		LogisticsStore.MilestoneRow milestone = store.milestone(shipmentId, milestoneId)
			.orElseThrow(ApiProblemException::notFound);
		LocalDate planned = body != null && body.plannedDate() != null ? body.plannedDate() : milestone.plannedDate();
		LocalDate actual = body != null && body.actualDate() != null ? body.actualDate() : milestone.actualDate();
		if (milestone.actualDate() != null && actual != null && actual.isBefore(milestone.actualDate())
				&& (body == null || !StringUtils.hasText(body.note()))) {
			throw new ValidationFailedException(List.of(FieldViolation.of("actualDate", "note-required")));
		}
		String note = body != null && StringUtils.hasText(body.note()) ? body.note().strip() : milestone.note();
		store.updateMilestone(milestoneId, planned, actual, note);
		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
		if (actual != null) {
			RequestStatus mapped = STATUS_MAP.get(milestone.code());
			if (mapped != null) {
				requests.enterShipmentStatus(request.id(), mapped, user.id(), now, "milestone-" + milestone.code());
			}
			boolean allDone = store.milestones(shipmentId).stream()
				.allMatch(m -> m.id() == milestoneId ? actual != null : m.actualDate() != null);
			store.bumpShipment(shipmentId, allDone ? "DELIVERED" : "IN_PROGRESS");
		}
		else {
			store.bumpShipment(shipmentId, "IN_PROGRESS");
		}
		if (planned != null && actual == null && planned.isBefore(today)) {
			notifyAuthors(requests.currentLocked(request.id()), "SHIPMENT_DELAY", user);
		}
		RequestSnapshot after = requests.currentLocked(request.id());
		audit(user, after, "SHIPMENT_MILESTONE_UPDATED", after.status(), note,
				"{\"shipmentId\":" + shipmentId + ",\"milestone\":\"" + milestone.code() + "\"}");
		return toView(store.findShipment(shipmentId).orElseThrow(), today);
	}

	@Transactional
	DocumentView addDocument(CurrentUser user, long shipmentId, DocumentBody body) {
		LogisticsStore.ShipmentRow shipment = store.findShipment(shipmentId).orElseThrow(ApiProblemException::notFound);
		RequestSnapshot request = requests.requireVisibleForUpdate(user, shipment.requestId());
		requireActor(user, request);
		if (body == null || !StringUtils.hasText(body.kind())) {
			throw new ValidationFailedException(List.of(FieldViolation.of("kind", "required")));
		}
		String kind = body.kind().strip().toUpperCase(Locale.ROOT);
		if (!DOC_KINDS.contains(kind)) {
			throw new ValidationFailedException(List.of(FieldViolation.of("kind", "invalid")));
		}
		Instant now = clock.instant();
		long id = store.insertDocument(shipmentId, kind, trim(body.referenceNumber()), trim(body.note()), null, null,
				null, null, user.id(), now);
		audit(user, request, "SHIPMENT_DOCUMENT_ADDED", request.status(), null,
				"{\"shipmentId\":" + shipmentId + ",\"documentId\":" + id + ",\"kind\":\"" + kind + "\"}");
		return store.documents(shipmentId).stream().filter(d -> d.id() == id).findFirst()
			.map(d -> new DocumentView(d.id(), d.kind(), d.referenceNumber(), d.note(), d.fileName(), d.uploadedAt()))
			.orElseThrow();
	}

	private ShipmentView toView(LogisticsStore.ShipmentRow shipment, LocalDate today) {
		List<ShipmentLineView> lines = store.lines(shipment.id())
			.stream()
			.map(l -> new ShipmentLineView(l.id(), l.poLineId(), l.requestLineId(), l.lineNo(), l.description(),
					l.quantity(), l.unit()))
			.toList();
		List<MilestoneView> milestones = store.milestones(shipment.id()).stream().map(m -> {
			boolean late = m.plannedDate() != null && m.actualDate() == null && m.plannedDate().isBefore(today);
			return new MilestoneView(m.id(), m.code(), m.sortOrder(), m.plannedDate(), m.actualDate(), m.note(), late);
		}).toList();
		List<DocumentView> documents = store.documents(shipment.id())
			.stream()
			.map(d -> new DocumentView(d.id(), d.kind(), d.referenceNumber(), d.note(), d.fileName(), d.uploadedAt()))
			.toList();
		return new ShipmentView(shipment.id(), shipment.requestId(), shipment.purchaseOrderId(), shipment.number(),
				shipment.kind(), shipment.status(), shipment.carrierName(), shipment.trackingReference(),
				shipment.originPlace(), shipment.destinationPlace(), shipment.createdAt(), shipment.version(), lines,
				milestones, documents);
	}

	private static String resolveKind(CreateShipmentBody body, String purchaseType) {
		if (body != null && StringUtils.hasText(body.kind())) {
			String kind = body.kind().strip().toUpperCase(Locale.ROOT);
			if (!"LOCAL".equals(kind) && !"INTERNATIONAL".equals(kind)) {
				throw new ValidationFailedException(List.of(FieldViolation.of("kind", "invalid")));
			}
			return kind;
		}
		return "INTERNATIONAL".equalsIgnoreCase(purchaseType) ? "INTERNATIONAL" : "LOCAL";
	}

	private void requireActor(CurrentUser user, RequestSnapshot request) {
		boolean manager = user.hasRoleIn(Role.PURCHASING_MANAGER, request.buyingCompanyId());
		boolean acceptedBuyer = request.assignedBuyerUserId() != null && request.assignedBuyerUserId() == user.id()
				&& store.assignmentAccepted(request.id(), user.id());
		if (!manager && !acceptedBuyer) {
			throw ApiProblemException.forbidden();
		}
	}

	private void requireViewer(CurrentUser user, RequestSnapshot request) {
		if (request.isAuthor(user.id())) {
			return;
		}
		requireActor(user, request);
	}

	private static void requireRequestVersion(Integer version, RequestSnapshot request) {
		if (version == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("requestVersion", "required")));
		}
		if (version != request.version()) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "stale-version",
					"The request was changed by someone else. Reload it and try again.");
		}
	}

	private void audit(CurrentUser user, RequestSnapshot request, String action, String toStatus, String comment,
			String payload) {
		auditLog.record(new AuditRecord(user.id(), user.displayName(), request.buyingCompanyId(), ENTITY,
				Long.toString(request.id()), action, request.status(), toStatus, comment, payload));
	}

	private void notifyAuthors(RequestSnapshot request, String kind, CurrentUser actor) {
		notifications.enqueue(new Notice(kind, request.requesterUserId(), request.id(),
				Map.of("number", Objects.toString(request.number(), ""), "title", Objects.toString(request.title(), ""),
						"actor", actor.displayName()),
				List.of(), List.of(new Notice.Action("mail.action.open", "/requests/" + request.id())),
				"/requests/" + request.id()));
	}

	private static String trim(String value) {
		return StringUtils.hasText(value) ? value.strip() : null;
	}

}
