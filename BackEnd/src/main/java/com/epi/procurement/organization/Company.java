package com.epi.procurement.organization;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A legal entity of the group. Read-only in Sprint 0; administration arrives with the configuration module.
 */
@Entity
@Table(name = "company")
public class Company {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "company_id")
	@SequenceGenerator(name = "company_id", sequenceName = "seq_company", allocationSize = 50)
	private Long id;

	@Column(name = "group_id", nullable = false)
	private Long groupId;

	@Column(name = "parent_company_id")
	private Long parentCompanyId;

	@Nationalized
	@Column(nullable = false, length = 30)
	private String code;

	@Nationalized
	@Column(name = "legal_name", nullable = false, length = 200)
	private String legalName;

	@Enumerated(EnumType.STRING)
	@Column(name = "company_type", nullable = false, length = 20)
	private CompanyType type;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "country_code", nullable = false, length = 2)
	private String countryCode;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "currency_code", nullable = false, length = 3)
	private String currencyCode;

	@Column(name = "default_language", nullable = false, length = 5)
	private String defaultLanguage;

	@Column(name = "time_zone", nullable = false, length = 64)
	private String timeZone;

	@Nationalized
	@Column(name = "tax_id", length = 50)
	private String taxId;

	@Nationalized
	@Column(name = "erp_company_code", length = 30)
	private String erpCompanyCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "purchasing_mode", nullable = false, length = 20)
	private PurchasingMode purchasingMode;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private int version;

	protected Company() {
	}

	public Long getId() {
		return id;
	}

	public Long getGroupId() {
		return groupId;
	}

	public Long getParentCompanyId() {
		return parentCompanyId;
	}

	public String getCode() {
		return code;
	}

	public String getLegalName() {
		return legalName;
	}

	public CompanyType getType() {
		return type;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public String getCurrencyCode() {
		return currencyCode;
	}

	public String getDefaultLanguage() {
		return defaultLanguage;
	}

	public String getTimeZone() {
		return timeZone;
	}

	public String getTaxId() {
		return taxId;
	}

	public String getErpCompanyCode() {
		return erpCompanyCode;
	}

	public PurchasingMode getPurchasingMode() {
		return purchasingMode;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
