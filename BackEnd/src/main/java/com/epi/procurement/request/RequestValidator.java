package com.epi.procurement.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.platform.web.FieldViolation;

/**
 * Field rules for purchase requests. Field paths are property names, with 0-based line indexes
 * ({@code lines[0].quantity}); codes are stable keys the UI translates.
 * <ul>
 * <li>Saving a draft checks formats and references only, so incomplete drafts can be saved at any time.</li>
 * <li>Submitting additionally checks completeness and that required dates are not in the past in the requesting
 * company's time zone (S1-AC-05, decision Q1).</li>
 * </ul>
 */
@Component
class RequestValidator {

	static final int MAX_DECIMALS = 4;

	/** DECIMAL(19,4) leaves 15 integer digits. */
	static final int MAX_INTEGER_DIGITS = 15;

	private final OrganizationDirectory organization;

	private final UserDirectory users;

	private final RequestReferenceData reference;

	private final RequestAccessPolicy policy;

	RequestValidator(OrganizationDirectory organization, UserDirectory users, RequestReferenceData reference,
			RequestAccessPolicy policy) {
		this.organization = organization;
		this.users = users;
		this.reference = reference;
		this.policy = policy;
	}

	List<FieldViolation> validateDraft(CurrentUser user, OrganizationDirectory.CompanyRef company, RequestPayload p,
			int maxLines) {
		List<FieldViolation> errors = new ArrayList<>();
		notEditable(errors, "number", p.number());
		notEditable(errors, "status", p.status());
		notEditable(errors, "enteredById", p.enteredById());
		notEditable(errors, "chargedCompanyId", p.chargedCompanyId());
		notEditable(errors, "buyingCompanyId", p.buyingCompanyId());
		notEditable(errors, "origin", p.origin());
		notEditable(errors, "totalAmount", p.totalAmount());

		maxLength(errors, "title", p.title(), 200);
		maxLength(errors, "beneficiary", p.beneficiary(), 200);
		maxLength(errors, "project", p.project(), 100);
		maxLength(errors, "justification", p.justification(), 2000);
		maxLength(errors, "urgencyJustification", p.urgencyJustification(), 1000);
		maxLength(errors, "budgetLineRef", p.budgetLineRef(), 60);
		maxLength(errors, "equipmentRef", p.equipmentRef(), 100);
		maxLength(errors, "maintenanceOrderRef", p.maintenanceOrderRef(), 60);
		maxLength(errors, "deliveryLocation", p.deliveryLocation(), 300);

		if (p.categoryId() != null && !reference.isActiveCategory(company.groupId(), p.categoryId())) {
			errors.add(FieldViolation.of("categoryId", "invalid-value"));
		}
		if (p.priority() != null && parse(Priority.class, p.priority()).isEmpty()) {
			errors.add(FieldViolation.of("priority", "invalid-value"));
		}
		if (p.purchaseType() != null && parse(PurchaseType.class, p.purchaseType()).isEmpty()) {
			errors.add(FieldViolation.of("purchaseType", "invalid-value"));
		}
		if (p.currency() != null && reference.currency(p.currency()).isEmpty()) {
			errors.add(FieldViolation.of("currency", "invalid-value"));
		}
		belongsTo(errors, "departmentId", p.departmentId() == null ? null
				: organization.findDepartment(p.departmentId()).map(OrganizationDirectory.UnitRef::companyId), company);
		belongsTo(errors, "siteId", p.siteId() == null ? null
				: organization.findSite(p.siteId()).map(OrganizationDirectory.UnitRef::companyId), company);
		belongsTo(errors, "costCenterId", p.costCenterId() == null ? null
				: organization.findCostCenter(p.costCenterId()).map(OrganizationDirectory.UnitRef::companyId), company);
		belongsTo(errors, "beneficiarySiteId", p.beneficiarySiteId() == null ? null
				: organization.findSite(p.beneficiarySiteId()).map(OrganizationDirectory.UnitRef::companyId), company);

		if (p.requesterId() == null || p.requesterId() == user.id()) {
			// The requester belongs to the requesting company (spec §3.3).
			if (user.companyId() != company.id()) {
				errors.add(FieldViolation.of("requesterId", "requester-not-allowed"));
			}
		}
		else {
			boolean allowed = policy.canEnterOnBehalfIn(user, company.id()) && users.findUser(p.requesterId())
				.filter(u -> u.active() && u.companyId() == company.id())
				.isPresent();
			if (!allowed) {
				errors.add(FieldViolation.of("requesterId", "requester-not-allowed"));
			}
		}

		if (p.lines().size() > maxLines) {
			errors.add(FieldViolation.of("lines", "too-many-lines", "max", maxLines));
		}
		for (int i = 0; i < p.lines().size(); i++) {
			RequestPayload.LinePayload line = p.lines().get(i);
			String path = "lines[" + i + "].";
			if (line == null) {
				errors.add(FieldViolation.of("lines[" + i + "]", "required"));
				continue;
			}
			maxLength(errors, path + "description", line.description(), 500);
			maxLength(errors, path + "itemReference", line.itemReference(), 60);
			maxLength(errors, path + "deliveryLocation", line.deliveryLocation(), 300);
			if (line.quantity() != null) {
				if (line.quantity().signum() <= 0) {
					errors.add(FieldViolation.of(path + "quantity", "quantity-positive"));
				}
				decimalFormat(errors, path + "quantity", line.quantity());
			}
			if (line.estimatedUnitPrice() != null) {
				if (line.estimatedUnitPrice().signum() < 0) {
					errors.add(FieldViolation.of(path + "estimatedUnitPrice", "price-not-negative"));
				}
				decimalFormat(errors, path + "estimatedUnitPrice", line.estimatedUnitPrice());
			}
			if (line.unit() != null && !reference.unitExists(line.unit())) {
				errors.add(FieldViolation.of(path + "unit", "invalid-value"));
			}
			if (line.lineKind() != null && !"GOODS".equals(line.lineKind()) && !"SERVICE".equals(line.lineKind())) {
				errors.add(FieldViolation.of(path + "lineKind", "invalid-value"));
			}
		}
		return errors;
	}

	/**
	 * Completeness rules applied at submission, on the saved request.
	 */
	List<FieldViolation> validateSubmission(PurchaseRequest r, LocalDate today) {
		List<FieldViolation> errors = new ArrayList<>();
		required(errors, "title", StringUtils.hasText(r.getTitle()));
		required(errors, "categoryId", r.getCategoryId() != null);
		if (r.getCategoryId() != null && !reference.isActiveCategory(r.getGroupId(), r.getCategoryId())) {
			errors.add(FieldViolation.of("categoryId", "invalid-value"));
		}
		required(errors, "priority", r.getPriority() != null);
		required(errors, "purchaseType", r.getPurchaseType() != null);
		required(errors, "departmentId", r.getDepartmentId() != null);
		if (r.getRequiredDate() == null) {
			errors.add(FieldViolation.of("requiredDate", "required"));
		}
		else if (r.getRequiredDate().isBefore(today)) {
			errors.add(FieldViolation.of("requiredDate", "required-date-past"));
		}
		if (r.getPriority() == Priority.URGENT) {
			required(errors, "urgencyJustification", StringUtils.hasText(r.getUrgencyJustification()));
		}
		if (r.getLines().isEmpty()) {
			errors.add(FieldViolation.of("lines", "required"));
		}
		for (int i = 0; i < r.getLines().size(); i++) {
			PurchaseRequestLine line = r.getLines().get(i);
			String path = "lines[" + i + "].";
			required(errors, path + "description", StringUtils.hasText(line.getDescription()));
			required(errors, path + "quantity", line.getQuantity() != null);
			required(errors, path + "unit", line.getUnitCode() != null);
			required(errors, path + "estimatedUnitPrice", line.getEstimatedUnitPrice() != null);
			if (line.getRequiredDate() != null && line.getRequiredDate().isBefore(today)) {
				errors.add(FieldViolation.of(path + "requiredDate", "required-date-past"));
			}
		}
		return errors;
	}

	static <E extends Enum<E>> Optional<E> parse(Class<E> type, String value) {
		if (value == null) {
			return Optional.empty();
		}
		for (E constant : type.getEnumConstants()) {
			if (constant.name().equals(value)) {
				return Optional.of(constant);
			}
		}
		return Optional.empty();
	}

	static boolean exceedsIntegerDigits(BigDecimal value) {
		BigDecimal stripped = value.stripTrailingZeros();
		return stripped.precision() - stripped.scale() > MAX_INTEGER_DIGITS;
	}

	private static void decimalFormat(List<FieldViolation> errors, String field, BigDecimal value) {
		if (value.stripTrailingZeros().scale() > MAX_DECIMALS) {
			errors.add(FieldViolation.of(field, "max-decimals", "max", MAX_DECIMALS));
		}
		if (exceedsIntegerDigits(value)) {
			errors.add(FieldViolation.of(field, "too-large"));
		}
	}

	private static void notEditable(List<FieldViolation> errors, String field, Object value) {
		if (value != null) {
			errors.add(FieldViolation.of(field, "not-editable"));
		}
	}

	private static void maxLength(List<FieldViolation> errors, String field, String value, int max) {
		if (value != null && value.length() > max) {
			errors.add(FieldViolation.of(field, "too-long", "max", max));
		}
	}

	private static void required(List<FieldViolation> errors, String field, boolean present) {
		if (!present) {
			errors.add(FieldViolation.of(field, "required"));
		}
	}

	private static void belongsTo(List<FieldViolation> errors, String field, Optional<Long> ownerCompany,
			OrganizationDirectory.CompanyRef company) {
		if (ownerCompany != null && ownerCompany.filter(c -> c == company.id()).isEmpty()) {
			errors.add(FieldViolation.of(field, "not-in-company"));
		}
	}

}
