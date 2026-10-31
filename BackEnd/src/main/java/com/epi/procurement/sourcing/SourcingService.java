package com.epi.procurement.sourcing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.Role;
import com.epi.procurement.notification.Notice;
import com.epi.procurement.notification.Notifications;
import com.epi.procurement.organization.ExchangeRates;
import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;
import com.epi.procurement.request.RequestSnapshot;
import com.epi.procurement.request.RequestWorkflow;

@Service
class SourcingService {

	private static final String ENTITY = "PURCHASE_REQUEST";

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final Duration LINK_TTL = Duration.ofDays(14);

	private final SourcingStore store;

	private final RequestWorkflow requests;

	private final OrganizationDirectory organization;

	private final ExchangeRates rates;

	private final AuditLog auditLog;

	private final Notifications notifications;

	private final Clock clock;

	SourcingService(SourcingStore store, RequestWorkflow requests, OrganizationDirectory organization, ExchangeRates rates,
			AuditLog auditLog, Notifications notifications, ObjectProvider<Clock> clock) {
		this.store = store;
		this.requests = requests;
		this.organization = organization;
		this.rates = rates;
		this.auditLog = auditLog;
		this.notifications = notifications;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@Transactional(readOnly = true)
	RfqDetail getForRequest(CurrentUser user, long requestId) {
		RequestSnapshot request = requests.requireVisible(user, requestId);
		requireSourcingActor(user, request);
		return store.findByRequest(requestId).map(this::detail).orElse(null);
	}

	@Transactional
	RfqDetail create(CurrentUser user, long requestId, CreateRfqBody body) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireCanOpen(user, request);
		if (store.findByRequest(requestId).isPresent()) {
			throw ApiProblemException.conflict("rfq-exists", "An RFQ already exists for this request.");
		}
		if (body == null || body.supplierIds() == null || body.supplierIds().isEmpty()) {
			throw new ValidationFailedException(List.of(FieldViolation.of("supplierIds", "required")));
		}
		String kind = body.kind() == null ? "" : body.kind().strip().toUpperCase();
		if (!"QUICK".equals(kind) && !"STANDARD".equals(kind)) {
			throw new ValidationFailedException(List.of(FieldViolation.of("kind", "invalid")));
		}
		OrganizationDirectory.CompanyRef buying = organization.findCompany(request.buyingCompanyId())
			.orElseThrow(ApiProblemException::notFound);
		Instant now = clock.instant();
		long rfqId = store.insertRfq(request.groupId(), requestId, request.buyingCompanyId(), kind,
				request.title() == null ? "RFQ" : request.title(), buying.currencyCode(), user.id(), now);
		for (RequestSnapshot.LineSnapshot line : request.lines()) {
			store.insertLine(rfqId, line.id(), (short) line.lineNo(), line.description(), line.quantity(), line.unit());
		}
		for (Long supplierId : body.supplierIds().stream().distinct().toList()) {
			if (supplierId == null || !store.supplierVisible(user.groupId(), user.companyId(), supplierId)) {
				throw new ValidationFailedException(List.of(FieldViolation.of("supplierIds", "not-eligible")));
			}
			store.insertSupplier(rfqId, supplierId, now);
		}
		requests.enterSourcing(requestId, user.id(), now);
		RequestSnapshot after = requests.currentLocked(requestId);
		audit(user, after, "RFQ_CREATED", after.status(), null,
				"{\"rfqId\":" + rfqId + ",\"kind\":\"" + kind + "\"}");
		notifyAuthors(after, "RFQ_CREATED", user);
		return detail(store.findRfq(rfqId).orElseThrow());
	}

	@Transactional
	LinkCreated issueLink(CurrentUser user, long rfqId, long rfqSupplierId) {
		SourcingStore.RfqRow rfq = store.findRfq(rfqId).orElseThrow(ApiProblemException::notFound);
		RequestSnapshot request = requests.requireVisible(user, rfq.requestId());
		requireSourcingActor(user, request);
		if (!"OPEN".equals(rfq.status())) {
			throw ApiProblemException.conflict("rfq-closed", "This RFQ no longer accepts quotes.");
		}
		SourcingStore.SupplierRow supplier = store.supplier(rfqSupplierId).orElseThrow(ApiProblemException::notFound);
		if (supplier.rfqId() != rfqId) {
			throw ApiProblemException.notFound();
		}
		Instant now = clock.instant();
		store.expireActiveTokens(rfqSupplierId, now);
		byte[] raw = new byte[32];
		RANDOM.nextBytes(raw);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		Instant expires = now.plus(LINK_TTL);
		store.insertToken(hash(token), rfqSupplierId, expires, now);
		audit(user, request, "QUOTE_LINK_ISSUED", request.status(), null,
				"{\"rfqSupplierId\":" + rfqSupplierId + "}");
		return new LinkCreated(token, "/q#" + token, expires);
	}

	@Transactional
	QuoteView submitBuyerQuote(CurrentUser user, long rfqId, QuoteBody body) {
		SourcingStore.RfqRow rfq = store.findRfq(rfqId).orElseThrow(ApiProblemException::notFound);
		RequestSnapshot request = requests.requireVisibleForUpdate(user, rfq.requestId());
		requireSourcingActor(user, request);
		if (body == null || body.rfqSupplierId() == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("rfqSupplierId", "required")));
		}
		QuoteView quote = saveQuote(rfq, body, "BUYER", user.id(), clock.instant());
		requests.markQuotesReceived(request.id(), user.id(), clock.instant());
		RequestSnapshot after = requests.currentLocked(request.id());
		audit(user, after, "QUOTE_SUBMITTED", after.status(), null,
				"{\"quotationId\":" + quote.id() + ",\"source\":\"BUYER\"}");
		return quote;
	}

	@Transactional(readOnly = true)
	PublicSummary publicSummary(String token) {
		TokenContext ctx = requireValidToken(token);
		SourcingStore.RfqRow rfq = store.findRfq(ctx.supplier().rfqId()).orElseThrow(ApiProblemException::notFound);
		boolean submitted = store.findQuoteByRfqSupplier(ctx.supplier().id()).isPresent();
		List<RfqLineView> lines = store.lines(rfq.id())
			.stream()
			.map(l -> new RfqLineView(l.id(), l.lineNo(), l.description(), l.quantity(), l.unit()))
			.toList();
		return new PublicSummary(rfq.title(), rfq.kind(), ctx.supplier().name(),
				ctx.token().expiresAt().atZone(java.time.ZoneOffset.UTC).toLocalDate(), lines, submitted);
	}

	@Transactional
	PublicSummary publicSubmit(PublicQuoteSubmit body) {
		if (body == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("token", "required")));
		}
		TokenContext ctx = requireValidToken(body.token());
		SourcingStore.RfqRow rfq = store.findRfq(ctx.supplier().rfqId()).orElseThrow(ApiProblemException::notFound);
		if (!"OPEN".equals(rfq.status())) {
			// Neutral 404 for anonymous callers (S4SL-08) — do not reveal RFQ state.
			throw ApiProblemException.notFound();
		}
		Instant now = clock.instant();
		QuoteBody quoteBody = new QuoteBody(ctx.supplier().id(), body.currency(), body.validUntil(), body.leadTimeDays(),
				body.paymentTerms(), body.incoterm(), body.freightAmount(), body.dutyAmount(), body.otherAmount(),
				body.notes(), body.lines());
		QuoteView quote = saveQuote(rfq, quoteBody, "SUPPLIER_LINK", null, now);
		store.markTokenUsed(ctx.token().id(), now);
		requests.markQuotesReceived(rfq.requestId(), rfq.createdByUserId(), now);
		RequestSnapshot after = requests.find(rfq.requestId()).orElseThrow();
		auditLog.record(new AuditRecord(null, "supplier-link", rfq.buyingCompanyId(), ENTITY,
				Long.toString(rfq.requestId()), "QUOTE_SUBMITTED", after.status(), after.status(), null,
				"{\"quotationId\":" + quote.id() + ",\"source\":\"SUPPLIER_LINK\"}"));
		return publicSummary(body.token());
	}

	@Transactional(readOnly = true)
	ComparisonView comparison(CurrentUser user, long rfqId) {
		SourcingStore.RfqRow rfq = store.findRfq(rfqId).orElseThrow(ApiProblemException::notFound);
		RequestSnapshot request = requests.requireVisible(user, rfq.requestId());
		requireSourcingActor(user, request);
		LocalDate today = LocalDate.now(clock);
		List<ComparisonRow> rows = new ArrayList<>();
		Map<Long, String> names = store.suppliers(rfqId)
			.stream()
			.collect(java.util.stream.Collectors.toMap(SourcingStore.SupplierRow::supplierId, SourcingStore.SupplierRow::name,
					(a, b) -> a));
		for (SourcingStore.QuoteRow quote : store.quotes(rfqId)) {
			List<String> missing = new ArrayList<>();
			for (SourcingStore.QuoteLineRow line : store.quoteLines(quote.id())) {
				if (line.unitPrice() == null) {
					missing.add("line:" + line.rfqLineId());
				}
			}
			BigDecimal landed = nz(quote.merchandise()).add(nz(quote.freight())).add(nz(quote.duty())).add(nz(quote.other()));
			var conversion = rates.convert(rfq.groupId(), landed, quote.currency(), rfq.comparisonCurrency(), today)
				.orElseThrow(() -> new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "missing-exchange-rate",
						"No exchange rate to convert the quotation currency to the comparison currency."));
			rows.add(new ComparisonRow(quote.id(), quote.supplierId(), names.getOrDefault(quote.supplierId(), ""),
					quote.currency(), quote.merchandise(), quote.freight(), quote.duty(), quote.other(), landed,
					conversion.amount(), conversion.rate(), conversion.rateDate(), missing));
		}
		return new ComparisonView(rfq.comparisonCurrency(), rows);
	}

	@Transactional
	RfqDetail close(CurrentUser user, long rfqId) {
		SourcingStore.RfqRow rfq = store.findRfq(rfqId).orElseThrow(ApiProblemException::notFound);
		RequestSnapshot request = requests.requireVisible(user, rfq.requestId());
		requireSourcingActor(user, request);
		store.closeRfq(rfqId, clock.instant());
		audit(user, request, "RFQ_CLOSED", request.status(), null, "{\"rfqId\":" + rfqId + "}");
		return detail(store.findRfq(rfqId).orElseThrow());
	}

	@Transactional
	RequestSnapshot validateTechnical(CurrentUser user, long requestId, ValidateBody body) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireSourcingActor(user, request);
		requireComment(body);
		requireRequestVersion(body, request);
		Instant now = clock.instant();
		requests.enterTechnicalValidation(requestId, user.id(), now);
		RequestSnapshot after = requests.currentLocked(requestId);
		audit(user, after, "TECHNICAL_VALIDATION", after.status(), body.comment().strip(), null);
		notifyAuthors(after, "TECHNICAL_VALIDATION", user);
		return after;
	}

	@Transactional
	RequestSnapshot validateCommercial(CurrentUser user, long requestId, ValidateBody body) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireSourcingActor(user, request);
		requireComment(body);
		requireRequestVersion(body, request);
		Instant now = clock.instant();
		requests.enterCommercialValidation(requestId, user.id(), now);
		RequestSnapshot after = requests.currentLocked(requestId);
		audit(user, after, "COMMERCIAL_VALIDATION", after.status(), body.comment().strip(), null);
		notifyAuthors(after, "COMMERCIAL_VALIDATION", user);
		return after;
	}

	private QuoteView saveQuote(SourcingStore.RfqRow rfq, QuoteBody body, String source, Long actorUserId, Instant now) {
		if (!"OPEN".equals(rfq.status())) {
			throw ApiProblemException.conflict("rfq-closed", "This RFQ no longer accepts quotes.");
		}
		SourcingStore.SupplierRow supplier = store.supplier(body.rfqSupplierId()).orElseThrow(ApiProblemException::notFound);
		if (supplier.rfqId() != rfq.id()) {
			throw ApiProblemException.notFound();
		}
		if (!StringUtils.hasText(body.currency()) || !rates.currencyExists(body.currency().strip().toUpperCase())) {
			throw new ValidationFailedException(List.of(FieldViolation.of("currency", "invalid")));
		}
		String currency = body.currency().strip().toUpperCase();
		List<SourcingStore.LineRow> rfqLines = store.lines(rfq.id());
		if (body.lines() == null || body.lines().isEmpty()) {
			throw new ValidationFailedException(List.of(FieldViolation.of("lines", "required")));
		}
		BigDecimal merchandise = BigDecimal.ZERO;
		List<QuoteLineBody> normalized = new ArrayList<>();
		for (SourcingStore.LineRow rfqLine : rfqLines) {
			QuoteLineBody match = body.lines()
				.stream()
				.filter(l -> l.rfqLineId() == rfqLine.id())
				.findFirst()
				.orElse(new QuoteLineBody(rfqLine.id(), rfqLine.quantity(), null));
			BigDecimal qty = match.quantity() == null ? rfqLine.quantity() : match.quantity();
			if (qty == null || qty.signum() <= 0) {
				throw new ValidationFailedException(List.of(FieldViolation.of("lines", "invalid-quantity")));
			}
			BigDecimal lineAmount = null;
			if (match.unitPrice() != null) {
				if (match.unitPrice().signum() < 0) {
					throw new ValidationFailedException(List.of(FieldViolation.of("lines", "invalid-price")));
				}
				int minor = rates.minorUnits(currency);
				lineAmount = match.unitPrice().multiply(qty).setScale(minor, RoundingMode.HALF_UP);
				merchandise = merchandise.add(lineAmount);
			}
			normalized.add(new QuoteLineBody(rfqLine.id(), qty, match.unitPrice()));
		}
		Long existing = store.findQuoteByRfqSupplier(supplier.id()).map(SourcingStore.QuoteRow::id).orElse(null);
		long quoteId = store.upsertQuote(existing, rfq.id(), supplier.id(), supplier.supplierId(), currency,
				body.validUntil(), body.leadTimeDays(), trim(body.paymentTerms()), trim(body.incoterm()),
				nz(body.freightAmount()), nz(body.dutyAmount()), nz(body.otherAmount()), merchandise, trim(body.notes()),
				source, actorUserId, now);
		for (QuoteLineBody line : normalized) {
			BigDecimal lineAmount = line.unitPrice() == null ? null
					: line.unitPrice()
						.multiply(line.quantity())
						.setScale(rates.minorUnits(currency), RoundingMode.HALF_UP);
			store.insertQuoteLine(quoteId, line.rfqLineId(), line.quantity(), line.unitPrice(), lineAmount);
		}
		return toQuoteView(store.findQuoteByRfqSupplier(supplier.id()).orElseThrow(), supplier.name(), rfqLines);
	}

	private RfqDetail detail(SourcingStore.RfqRow rfq) {
		List<SourcingStore.LineRow> lines = store.lines(rfq.id());
		List<RfqLineView> lineViews = lines.stream()
			.map(l -> new RfqLineView(l.id(), l.lineNo(), l.description(), l.quantity(), l.unit()))
			.toList();
		List<RfqSupplierView> suppliers = store.suppliers(rfq.id())
			.stream()
			.map(s -> new RfqSupplierView(s.id(), s.supplierId(), s.code(), s.name()))
			.toList();
		List<QuoteView> quotes = store.quotes(rfq.id())
			.stream()
			.map(q -> {
				String name = suppliers.stream()
					.filter(s -> s.supplierId() == q.supplierId())
					.map(RfqSupplierView::name)
					.findFirst()
					.orElse("");
				return toQuoteView(q, name, lines);
			})
			.toList();
		return new RfqDetail(rfq.id(), rfq.requestId(), rfq.kind(), rfq.status(), rfq.title(), rfq.comparisonCurrency(),
				rfq.version(), rfq.createdAt(), lineViews, suppliers, quotes);
	}

	private QuoteView toQuoteView(SourcingStore.QuoteRow quote, String supplierName, List<SourcingStore.LineRow> rfqLines) {
		Map<Long, Integer> lineNos = rfqLines.stream()
			.collect(java.util.stream.Collectors.toMap(SourcingStore.LineRow::id, l -> (int) l.lineNo()));
		List<QuoteLineView> lines = store.quoteLines(quote.id())
			.stream()
			.map(l -> new QuoteLineView(l.rfqLineId(), lineNos.getOrDefault(l.rfqLineId(), 0), l.quantity(), l.unitPrice(),
					l.lineAmount(), l.unitPrice() == null))
			.toList();
		return new QuoteView(quote.id(), quote.rfqSupplierId(), quote.supplierId(), supplierName, quote.status(),
				quote.currency(), quote.validUntil(), quote.leadTimeDays(), quote.paymentTerms(), quote.incoterm(),
				quote.freight(), quote.duty(), quote.other(), quote.merchandise(), quote.notes(), quote.source(),
				quote.submittedAt(), lines);
	}

	private TokenContext requireValidToken(String token) {
		if (!StringUtils.hasText(token)) {
			throw ApiProblemException.notFound();
		}
		String raw = token.strip();
		if (raw.length() < 20 || raw.length() > 100) {
			throw ApiProblemException.notFound();
		}
		SourcingStore.TokenRow row = store.findToken(hash(raw)).orElseThrow(ApiProblemException::notFound);
		if (row.expiresAt().isBefore(clock.instant())) {
			throw ApiProblemException.notFound();
		}
		SourcingStore.SupplierRow supplier = store.supplier(row.rfqSupplierId()).orElseThrow(ApiProblemException::notFound);
		return new TokenContext(row, supplier);
	}

	private void requireCanOpen(CurrentUser user, RequestSnapshot request) {
		if (!"ASSIGNED_TO_BUYER".equals(request.status())) {
			throw ApiProblemException.conflict("invalid-state", "The request is not ready for sourcing.");
		}
		boolean manager = user.hasRoleIn(Role.PURCHASING_MANAGER, request.buyingCompanyId());
		boolean acceptedBuyer = request.assignedBuyerUserId() != null && request.assignedBuyerUserId() == user.id()
				&& store.assignmentAccepted(request.id(), user.id());
		if (!manager && !acceptedBuyer) {
			throw ApiProblemException.forbidden();
		}
	}

	private void requireSourcingActor(CurrentUser user, RequestSnapshot request) {
		if (!user.hasRoleIn(Role.BUYER, request.buyingCompanyId())
				&& !user.hasRoleIn(Role.PURCHASING_MANAGER, request.buyingCompanyId())) {
			throw ApiProblemException.forbidden();
		}
		if (user.hasRoleIn(Role.BUYER, request.buyingCompanyId())
				&& !user.hasRoleIn(Role.PURCHASING_MANAGER, request.buyingCompanyId())
				&& (request.assignedBuyerUserId() == null || request.assignedBuyerUserId() != user.id())) {
			throw ApiProblemException.forbidden();
		}
	}

	private static void requireComment(ValidateBody body) {
		if (body == null || !StringUtils.hasText(body.comment())) {
			throw new ValidationFailedException(List.of(FieldViolation.of("comment", "required")));
		}
	}

	private static void requireRequestVersion(ValidateBody body, RequestSnapshot request) {
		if (body.requestVersion() == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("requestVersion", "required")));
		}
		if (body.requestVersion() != request.version()) {
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

	/** Hex fingerprint for rate-limit buckets (never log alongside the raw token). */
	static String tokenFingerprint(String token) {
		byte[] digest = hash(token);
		StringBuilder hex = new StringBuilder(32);
		for (int i = 0; i < 16; i++) {
			hex.append(String.format("%02x", digest[i]));
		}
		return hex.toString();
	}

	private static byte[] hash(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static BigDecimal nz(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private static String trim(String value) {
		return StringUtils.hasText(value) ? value.strip() : null;
	}

	private record TokenContext(SourcingStore.TokenRow token, SourcingStore.SupplierRow supplier) {
	}
}
