package com.epi.procurement.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_request_line")
class PurchaseRequestLine {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "purchase_request_line_id")
	@SequenceGenerator(name = "purchase_request_line_id", sequenceName = "seq_purchase_request_line", allocationSize = 50)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false, updatable = false)
	private PurchaseRequest request;

	@Column(name = "line_no", nullable = false)
	private short lineNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private RequestStatus status;

	@Nationalized
	@Column(length = 500)
	private String description;

	@Nationalized
	@Column(name = "item_reference", length = 60)
	private String itemReference;

	@Column(name = "line_kind", nullable = false, length = 10)
	private String lineKind = "GOODS";

	@Column(name = "service_confirmed_by_user_id")
	private Long serviceConfirmedByUserId;

	@Column(name = "service_confirmed_at")
	private java.time.Instant serviceConfirmedAt;

	@Column(precision = 19, scale = 4)
	private BigDecimal quantity;

	@Column(name = "unit_code", length = 10)
	private String unitCode;

	@Column(name = "estimated_unit_price", precision = 19, scale = 4)
	private BigDecimal estimatedUnitPrice;

	@Column(name = "line_total", nullable = false, precision = 19, scale = 4)
	private BigDecimal lineTotal = BigDecimal.ZERO;

	@Column(name = "required_date")
	private LocalDate requiredDate;

	@Nationalized
	@Column(name = "delivery_location", length = 300)
	private String deliveryLocation;

	protected PurchaseRequestLine() {
	}

	PurchaseRequestLine(PurchaseRequest request, short lineNo) {
		this.request = request;
		this.lineNo = lineNo;
		// Lines added while revising a returned request are already submitted lines (S2DR-04).
		this.status = request.getStatus() == RequestStatus.DRAFT ? RequestStatus.DRAFT : RequestStatus.CREATED;
	}

	Long getId() {
		return id;
	}

	short getLineNo() {
		return lineNo;
	}

	RequestStatus getStatus() {
		return status;
	}

	void setStatus(RequestStatus status) {
		this.status = status;
	}

	String getDescription() {
		return description;
	}

	void setDescription(String description) {
		this.description = description;
	}

	String getItemReference() {
		return itemReference;
	}

	void setItemReference(String itemReference) {
		this.itemReference = itemReference;
	}

	String getLineKind() {
		return lineKind;
	}

	void setLineKind(String lineKind) {
		if (!lineKind.equals(this.lineKind)) {
			clearServiceConfirmation();
		}
		this.lineKind = lineKind;
	}

	boolean isServiceConfirmed() {
		return serviceConfirmedAt != null;
	}

	void confirmService(long userId, java.time.Instant at) {
		this.serviceConfirmedByUserId = userId;
		this.serviceConfirmedAt = at;
	}

	void clearServiceConfirmation() {
		this.serviceConfirmedByUserId = null;
		this.serviceConfirmedAt = null;
	}

	BigDecimal getQuantity() {
		return quantity;
	}

	void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	String getUnitCode() {
		return unitCode;
	}

	void setUnitCode(String unitCode) {
		this.unitCode = unitCode;
	}

	BigDecimal getEstimatedUnitPrice() {
		return estimatedUnitPrice;
	}

	void setEstimatedUnitPrice(BigDecimal estimatedUnitPrice) {
		this.estimatedUnitPrice = estimatedUnitPrice;
	}

	BigDecimal getLineTotal() {
		return lineTotal;
	}

	void setLineTotal(BigDecimal lineTotal) {
		this.lineTotal = lineTotal;
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

}
