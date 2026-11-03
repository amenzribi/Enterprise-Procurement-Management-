package com.epi.procurement.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import com.epi.procurement.organization.PurchasingMode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A purchase request (spec §6.1, §23) with its lines. Charged and buying company are set by the server only
 * (S1DR-07); the number is assigned at submission (A1).
 */
@Entity
@Table(name = "purchase_request")
class PurchaseRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "purchase_request_id")
	@SequenceGenerator(name = "purchase_request_id", sequenceName = "seq_purchase_request", allocationSize = 50)
	private Long id;

	@Column(name = "group_id", nullable = false)
	private Long groupId;

	@Nationalized
	@Column(length = 40)
	private String number;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private RequestStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Stage stage;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Origin origin = Origin.INTERNAL;

	@Nationalized
	@Column(length = 200)
	private String title;

	@Column(name = "category_id")
	private Long categoryId;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private Priority priority;

	@Enumerated(EnumType.STRING)
	@Column(name = "purchase_type", length = 20)
	private PurchaseType purchaseType;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "currency_code", nullable = false, length = 3)
	private String currencyCode;

	@Column(name = "requesting_company_id", nullable = false)
	private Long requestingCompanyId;

	@Column(name = "charged_company_id", nullable = false)
	private Long chargedCompanyId;

	@Column(name = "buying_company_id", nullable = false)
	private Long buyingCompanyId;

	@Column(name = "buying_company_proposed", nullable = false)
	private boolean buyingCompanyProposed = true;

	@Column(name = "assigned_buyer_user_id")
	private Long assignedBuyerUserId;

	@Column(name = "assigned_at")
	private Instant assignedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "purchase_mode", length = 20)
	private PurchasingMode purchaseMode;

	@Column(name = "department_id")
	private Long departmentId;

	@Column(name = "site_id")
	private Long siteId;

	@Column(name = "cost_center_id")
	private Long costCenterId;

	@Column(name = "requester_user_id", nullable = false)
	private Long requesterUserId;

	@Column(name = "entered_by_user_id", nullable = false, updatable = false)
	private Long enteredByUserId;

	@Column(name = "updated_by_user_id", nullable = false)
	private Long updatedByUserId;

	@Nationalized
	@Column(length = 200)
	private String beneficiary;

	@Column(name = "beneficiary_site_id")
	private Long beneficiarySiteId;

	@Nationalized
	@Column(length = 100)
	private String project;

	@Nationalized
	@Column(length = 2000)
	private String justification;

	@Nationalized
	@Column(name = "urgency_justification", length = 1000)
	private String urgencyJustification;

	@Nationalized
	@Column(name = "budget_line_ref", length = 60)
	private String budgetLineRef;

	@Nationalized
	@Column(name = "equipment_ref", length = 100)
	private String equipmentRef;

	@Nationalized
	@Column(name = "maintenance_order_ref", length = 60)
	private String maintenanceOrderRef;

	@Column(name = "required_date")
	private LocalDate requiredDate;

	@Nationalized
	@Column(name = "delivery_location", length = 300)
	private String deliveryLocation;

	@Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal totalAmount = BigDecimal.ZERO;

	@Column(name = "approval_amount", precision = 19, scale = 4)
	private BigDecimal approvalAmount;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "approval_currency", length = 3)
	private String approvalCurrency;

	@Column(name = "approval_rate", precision = 19, scale = 8)
	private BigDecimal approvalRate;

	@Column(name = "approval_rate_date")
	private LocalDate approvalRateDate;

	@Column(name = "approval_rule_id")
	private Long approvalRuleId;

	@Column(name = "round_no", nullable = false)
	private int roundNo;

	@Column(name = "current_owner_user_id", nullable = false)
	private Long currentOwnerUserId;

	@Column(name = "owner_since", nullable = false)
	private Instant ownerSince;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "archived_at")
	private Instant archivedAt;

	@Column(name = "archived_by_user_id")
	private Long archivedByUserId;

	@Nationalized
	@Column(name = "archive_reason", length = 1000)
	private String archiveReason;

	@Version
	private int version;

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("lineNo")
	private List<PurchaseRequestLine> lines = new ArrayList<>();

	protected PurchaseRequest() {
	}

	PurchaseRequest(long groupId, long requestingCompanyId, long chargedCompanyId, long buyingCompanyId,
			String currencyCode, long requesterUserId, long enteredByUserId, Instant now) {
		this.groupId = groupId;
		this.requestingCompanyId = requestingCompanyId;
		this.chargedCompanyId = chargedCompanyId;
		this.buyingCompanyId = buyingCompanyId;
		this.currencyCode = currencyCode;
		this.requesterUserId = requesterUserId;
		this.enteredByUserId = enteredByUserId;
		this.updatedByUserId = enteredByUserId;
		this.status = RequestStatus.DRAFT;
		this.stage = RequestStatus.DRAFT.stage();
		this.currentOwnerUserId = requesterUserId;
		this.ownerSince = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/**
	 * Moves to a new status; the owner and owner-since change only when the owner changes.
	 */
	void transitionTo(RequestStatus next, long owner, Instant now) {
		this.status = next;
		this.stage = next.stage();
		assignOwner(owner, now);
	}

	void assignOwner(long owner, Instant now) {
		if (currentOwnerUserId == null || currentOwnerUserId != owner) {
			this.currentOwnerUserId = owner;
			this.ownerSince = now;
		}
	}

	void markSubmitted(String number, Instant now) {
		this.number = number;
		this.submittedAt = now;
	}

	/**
	 * Starts a new approval round (submission or resubmission after a return).
	 */
	int nextRound() {
		return ++roundNo;
	}

	void recordApprovalBasis(BigDecimal amount, String currency, BigDecimal rate, LocalDate rateDate, long ruleId) {
		this.approvalAmount = amount;
		this.approvalCurrency = currency;
		this.approvalRate = rate;
		this.approvalRateDate = rateDate;
		this.approvalRuleId = ruleId;
	}

	int getRoundNo() {
		return roundNo;
	}

	BigDecimal getApprovalAmount() {
		return approvalAmount;
	}

	String getApprovalCurrency() {
		return approvalCurrency;
	}

	void touch(long userId, Instant now) {
		this.updatedByUserId = userId;
		this.updatedAt = now;
	}

	void archive(long actorUserId, String reason, Instant now) {
		this.archivedAt = now;
		this.archivedByUserId = actorUserId;
		this.archiveReason = reason;
		touch(actorUserId, now);
	}

	void restoreFromArchive(long actorUserId, Instant now) {
		this.archivedAt = null;
		this.archivedByUserId = null;
		this.archiveReason = null;
		touch(actorUserId, now);
	}

	boolean isArchived() {
		return archivedAt != null;
	}

	Instant getArchivedAt() {
		return archivedAt;
	}

	Long getArchivedByUserId() {
		return archivedByUserId;
	}

	String getArchiveReason() {
		return archiveReason;
	}

	Long getId() {
		return id;
	}

	Long getGroupId() {
		return groupId;
	}

	String getNumber() {
		return number;
	}

	RequestStatus getStatus() {
		return status;
	}

	Stage getStage() {
		return stage;
	}

	Origin getOrigin() {
		return origin;
	}

	String getTitle() {
		return title;
	}

	void setTitle(String title) {
		this.title = title;
	}

	Long getCategoryId() {
		return categoryId;
	}

	void setCategoryId(Long categoryId) {
		this.categoryId = categoryId;
	}

	Priority getPriority() {
		return priority;
	}

	void setPriority(Priority priority) {
		this.priority = priority;
	}

	PurchaseType getPurchaseType() {
		return purchaseType;
	}

	void setPurchaseType(PurchaseType purchaseType) {
		this.purchaseType = purchaseType;
	}

	String getCurrencyCode() {
		return currencyCode;
	}

	void setCurrencyCode(String currencyCode) {
		this.currencyCode = currencyCode;
	}

	Long getRequestingCompanyId() {
		return requestingCompanyId;
	}

	Long getChargedCompanyId() {
		return chargedCompanyId;
	}

	Long getBuyingCompanyId() {
		return buyingCompanyId;
	}

	/**
	 * Server-derived companies (S1DR-07); only called when the requesting company changes.
	 */
	void setCompanies(long requesting, long charged, long buying) {
		this.requestingCompanyId = requesting;
		this.chargedCompanyId = charged;
		this.buyingCompanyId = buying;
		this.buyingCompanyProposed = true;
	}

	boolean isBuyingCompanyProposed() {
		return buyingCompanyProposed;
	}

	Long getAssignedBuyerUserId() {
		return assignedBuyerUserId;
	}

	Instant getAssignedAt() {
		return assignedAt;
	}

	PurchasingMode getPurchaseMode() {
		return purchaseMode;
	}

	void assignBuyer(Long buyerUserId, Instant now) {
		this.assignedBuyerUserId = buyerUserId;
		this.assignedAt = buyerUserId == null ? null : now;
	}

	void confirmPurchaseMode(PurchasingMode mode, long buyingCompanyId) {
		this.purchaseMode = mode;
		this.buyingCompanyId = buyingCompanyId;
		this.buyingCompanyProposed = false;
	}

	Long getDepartmentId() {
		return departmentId;
	}

	void setDepartmentId(Long departmentId) {
		this.departmentId = departmentId;
	}

	Long getSiteId() {
		return siteId;
	}

	void setSiteId(Long siteId) {
		this.siteId = siteId;
	}

	Long getCostCenterId() {
		return costCenterId;
	}

	void setCostCenterId(Long costCenterId) {
		this.costCenterId = costCenterId;
	}

	Long getRequesterUserId() {
		return requesterUserId;
	}

	void setRequesterUserId(Long requesterUserId) {
		this.requesterUserId = requesterUserId;
	}

	Long getEnteredByUserId() {
		return enteredByUserId;
	}

	Long getUpdatedByUserId() {
		return updatedByUserId;
	}

	String getBeneficiary() {
		return beneficiary;
	}

	void setBeneficiary(String beneficiary) {
		this.beneficiary = beneficiary;
	}

	Long getBeneficiarySiteId() {
		return beneficiarySiteId;
	}

	void setBeneficiarySiteId(Long beneficiarySiteId) {
		this.beneficiarySiteId = beneficiarySiteId;
	}

	String getProject() {
		return project;
	}

	void setProject(String project) {
		this.project = project;
	}

	String getJustification() {
		return justification;
	}

	void setJustification(String justification) {
		this.justification = justification;
	}

	String getUrgencyJustification() {
		return urgencyJustification;
	}

	void setUrgencyJustification(String urgencyJustification) {
		this.urgencyJustification = urgencyJustification;
	}

	String getBudgetLineRef() {
		return budgetLineRef;
	}

	void setBudgetLineRef(String budgetLineRef) {
		this.budgetLineRef = budgetLineRef;
	}

	String getEquipmentRef() {
		return equipmentRef;
	}

	void setEquipmentRef(String equipmentRef) {
		this.equipmentRef = equipmentRef;
	}

	String getMaintenanceOrderRef() {
		return maintenanceOrderRef;
	}

	void setMaintenanceOrderRef(String maintenanceOrderRef) {
		this.maintenanceOrderRef = maintenanceOrderRef;
	}

	LocalDate getRequiredDate() {
		return requiredDate;
	}

	void setRequiredDate(LocalDate requiredDate) {
		this.requiredDate = requiredDate;
	}

	String getDeliveryLocation() {
		return deliveryLocation;
	}

	void setDeliveryLocation(String deliveryLocation) {
		this.deliveryLocation = deliveryLocation;
	}

	BigDecimal getTotalAmount() {
		return totalAmount;
	}

	void setTotalAmount(BigDecimal totalAmount) {
		this.totalAmount = totalAmount;
	}

	Long getCurrentOwnerUserId() {
		return currentOwnerUserId;
	}

	Instant getOwnerSince() {
		return ownerSince;
	}

	Instant getSubmittedAt() {
		return submittedAt;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}

	int getVersion() {
		return version;
	}

	List<PurchaseRequestLine> getLines() {
		return lines;
	}

}
