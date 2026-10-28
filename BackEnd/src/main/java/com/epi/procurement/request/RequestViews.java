package com.epi.procurement.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Response bodies of the request API. Amounts are decimal numbers rounded to the currency's minor units; instants
 * are ISO-8601 UTC; dates are ISO-8601 calendar dates.
 */
final class RequestViews {

	private RequestViews() {
	}

	record Ref(long id, String code, String name) {
	}

	record UserRef(long id, String displayName) {
	}

	record LocalizedRef(long id, String code, String nameEn, String nameFr) {
	}

	record Summary(long id, String number, String title, String status, String userStatus, String stage,
			BigDecimal totalAmount,
			String currency, LocalDate requiredDate, Instant updatedAt, Instant submittedAt, UserRef requester,
			Ref requestingCompany, List<String> actions) {
	}

	record Page<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
	}

	record Line(long id, int lineNo, String status, String description, String itemReference, BigDecimal quantity,
			String unit, BigDecimal estimatedUnitPrice, BigDecimal lineTotal, LocalDate requiredDate,
			String deliveryLocation) {
	}

	record Transition(String fromStatus, String toStatus, String stage, UserRef owner, UserRef actor,
			Instant occurredAt) {
	}

	record Detail(long id, String number, String status, String userStatus, String stage, int version, int roundNo,
			String origin, String title,
			LocalizedRef category, String priority, String purchaseType, String currency, int currencyMinorUnits,
			Ref requestingCompany, Ref chargedCompany, Ref buyingCompany, boolean buyingCompanyProposed,
			Ref department, Ref site, Ref costCenter, UserRef requester, UserRef enteredBy, UserRef updatedBy,
			String beneficiary, Ref beneficiarySite, String project, String justification,
			String urgencyJustification, String budgetLineRef, String equipmentRef, String maintenanceOrderRef,
			LocalDate requiredDate, String deliveryLocation, BigDecimal totalAmount, List<Line> lines,
			UserRef currentOwner, Instant ownerSince, Instant submittedAt, Instant createdAt, Instant updatedAt,
			List<Transition> transitions, List<String> actions) {
	}

	record StatusInfo(String code, int rank, String stage, boolean lineStatus, String userStatus) {
	}

	record CurrencyOption(String code, String nameEn, String nameFr, int minorUnits) {
	}

	record UnitOption(String code, String nameEn, String nameFr) {
	}

	record DepartmentOption(long id, String code, String name, Long siteId) {
	}

	record CompanyOption(long id, String code, String name, String currency, String timeZone,
			boolean canEnterOnBehalf, List<DepartmentOption> departments, List<Ref> sites, List<Ref> costCenters,
			List<RequesterOption> requesters, Ref chargedCompany, Ref buyingCompany, boolean buyingCompanyProposed) {
	}

	record RequesterOption(long id, String displayName, Long departmentId) {
	}

	record Defaults(Long requestingCompanyId, Long departmentId, Long siteId, String currency, long requesterId) {
	}

	record FormOptions(List<CurrencyOption> currencies, List<UnitOption> units, List<LocalizedRef> categories,
			List<String> priorities, List<String> purchaseTypes, int maxLines, Defaults defaults,
			List<CompanyOption> companies) {
	}

}
