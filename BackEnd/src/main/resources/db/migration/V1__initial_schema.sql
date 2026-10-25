-- Sprint 0 initial schema: reference data, organisation, user accounts, business numbering, audit.
-- Conventions (see docs/reviews/sprint-0-design-review.md, DR-17/DR-18):
--   * BIGINT keys drawn from one SEQUENCE per table. Sequences used by JPA entities increment by 50 to match
--     Hibernate's pooled optimizer (allocationSize = 50); the DEFAULT also lets plain SQL inserts work.
--   * Instants are DATETIME2(3) holding UTC (java.time.Instant); calendar dates will be DATE (LocalDate).
--   * NVARCHAR for user text; DECIMAL(19,4) for money (from Sprint 1).
--   * company_id on every company-scoped table, with composite foreign keys so a child row can only
--     reference a parent of the same company.

CREATE TABLE country (
    code        CHAR(2)       NOT NULL CONSTRAINT pk_country PRIMARY KEY,
    name_en     NVARCHAR(100) NOT NULL,
    name_fr     NVARCHAR(100) NOT NULL,
    CONSTRAINT ck_country_code CHECK (code = UPPER(code) AND LEN(code) = 2)
);

CREATE TABLE currency (
    code        CHAR(3)       NOT NULL CONSTRAINT pk_currency PRIMARY KEY,
    name_en     NVARCHAR(100) NOT NULL,
    name_fr     NVARCHAR(100) NOT NULL,
    minor_units TINYINT       NOT NULL,
    CONSTRAINT ck_currency_code CHECK (code = UPPER(code) AND LEN(code) = 3),
    CONSTRAINT ck_currency_minor_units CHECK (minor_units BETWEEN 0 AND 4)
);

CREATE SEQUENCE seq_organization_group AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_company            AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_site               AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_department         AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_cost_center        AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_user_account       AS BIGINT START WITH 1 INCREMENT BY 50;
-- The audit trail is written only through set-based SQL (AuditLog), where DEFAULT NEXT VALUE FOR is
-- evaluated in the server without a round trip, so its sequence increments by 1.
CREATE SEQUENCE seq_audit_event        AS BIGINT START WITH 1 INCREMENT BY 1;

CREATE TABLE organization_group (
    id                        BIGINT NOT NULL CONSTRAINT pk_organization_group PRIMARY KEY
                                     CONSTRAINT df_organization_group_id DEFAULT (NEXT VALUE FOR seq_organization_group),
    code                      NVARCHAR(30)  NOT NULL CONSTRAINT uq_organization_group_code UNIQUE,
    name                      NVARCHAR(200) NOT NULL,
    consolidation_currency    CHAR(3)       NOT NULL CONSTRAINT fk_organization_group_currency REFERENCES currency (code),
    created_at                DATETIME2(3)  NOT NULL CONSTRAINT df_organization_group_created_at DEFAULT SYSUTCDATETIME(),
    updated_at                DATETIME2(3)  NOT NULL CONSTRAINT df_organization_group_updated_at DEFAULT SYSUTCDATETIME(),
    version                   INT NOT NULL CONSTRAINT df_organization_group_version DEFAULT 0
);

CREATE TABLE company (
    id                 BIGINT NOT NULL CONSTRAINT pk_company PRIMARY KEY
                              CONSTRAINT df_company_id DEFAULT (NEXT VALUE FOR seq_company),
    group_id           BIGINT        NOT NULL CONSTRAINT fk_company_group REFERENCES organization_group (id),
    parent_company_id  BIGINT        NULL     CONSTRAINT fk_company_parent REFERENCES company (id),
    code               NVARCHAR(30)  NOT NULL,
    legal_name         NVARCHAR(200) NOT NULL,
    company_type       VARCHAR(20)   NOT NULL,
    country_code       CHAR(2)       NOT NULL CONSTRAINT fk_company_country REFERENCES country (code),
    currency_code      CHAR(3)       NOT NULL CONSTRAINT fk_company_currency REFERENCES currency (code),
    default_language   VARCHAR(5)    NOT NULL CONSTRAINT df_company_default_language DEFAULT 'en',
    time_zone          VARCHAR(64)   NOT NULL,
    tax_id             NVARCHAR(50)  NULL,
    erp_company_code   NVARCHAR(30)  NULL,
    purchasing_mode    VARCHAR(20)   NOT NULL,
    is_active          BIT           NOT NULL CONSTRAINT df_company_is_active DEFAULT 1,
    created_at         DATETIME2(3)  NOT NULL CONSTRAINT df_company_created_at DEFAULT SYSUTCDATETIME(),
    updated_at         DATETIME2(3)  NOT NULL CONSTRAINT df_company_updated_at DEFAULT SYSUTCDATETIME(),
    version            INT NOT NULL CONSTRAINT df_company_version DEFAULT 0,
    CONSTRAINT uq_company_group_code UNIQUE (group_id, code),
    CONSTRAINT ck_company_type CHECK (company_type IN ('PARENT', 'SUBSIDIARY', 'PURCHASING_CENTER')),
    CONSTRAINT ck_company_language CHECK (default_language IN ('en', 'fr')),
    CONSTRAINT ck_company_purchasing_mode CHECK (purchasing_mode IN ('DIRECT', 'CENTRALIZED', 'ON_BEHALF', 'MIXED')),
    CONSTRAINT ck_company_not_own_parent CHECK (parent_company_id IS NULL OR parent_company_id <> id)
);

-- ERP company codes are unique within a group when present (DR-28).
CREATE UNIQUE INDEX uq_company_group_erp_code ON company (group_id, erp_company_code) WHERE erp_company_code IS NOT NULL;

CREATE TABLE site (
    id            BIGINT NOT NULL CONSTRAINT pk_site PRIMARY KEY
                         CONSTRAINT df_site_id DEFAULT (NEXT VALUE FOR seq_site),
    company_id    BIGINT        NOT NULL CONSTRAINT fk_site_company REFERENCES company (id),
    code          NVARCHAR(30)  NOT NULL,
    name          NVARCHAR(200) NOT NULL,
    site_type     VARCHAR(20)   NOT NULL,
    country_code  CHAR(2)       NOT NULL CONSTRAINT fk_site_country REFERENCES country (code),
    city          NVARCHAR(100) NULL,
    is_active     BIT           NOT NULL CONSTRAINT df_site_is_active DEFAULT 1,
    created_at    DATETIME2(3)  NOT NULL CONSTRAINT df_site_created_at DEFAULT SYSUTCDATETIME(),
    updated_at    DATETIME2(3)  NOT NULL CONSTRAINT df_site_updated_at DEFAULT SYSUTCDATETIME(),
    version       INT NOT NULL CONSTRAINT df_site_version DEFAULT 0,
    CONSTRAINT uq_site_company_code UNIQUE (company_id, code),
    CONSTRAINT uq_site_company_id UNIQUE (company_id, id),
    CONSTRAINT ck_site_type CHECK (site_type IN ('HEAD_OFFICE', 'PLANT', 'WAREHOUSE', 'PROJECT_SITE', 'BRANCH'))
);

CREATE TABLE department (
    id            BIGINT NOT NULL CONSTRAINT pk_department PRIMARY KEY
                         CONSTRAINT df_department_id DEFAULT (NEXT VALUE FOR seq_department),
    company_id    BIGINT        NOT NULL CONSTRAINT fk_department_company REFERENCES company (id),
    site_id       BIGINT        NULL,
    code          NVARCHAR(30)  NOT NULL,
    name          NVARCHAR(200) NOT NULL,
    is_active     BIT           NOT NULL CONSTRAINT df_department_is_active DEFAULT 1,
    created_at    DATETIME2(3)  NOT NULL CONSTRAINT df_department_created_at DEFAULT SYSUTCDATETIME(),
    updated_at    DATETIME2(3)  NOT NULL CONSTRAINT df_department_updated_at DEFAULT SYSUTCDATETIME(),
    version       INT NOT NULL CONSTRAINT df_department_version DEFAULT 0,
    CONSTRAINT uq_department_company_code UNIQUE (company_id, code),
    CONSTRAINT uq_department_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_department_site_same_company FOREIGN KEY (company_id, site_id) REFERENCES site (company_id, id)
);

CREATE TABLE cost_center (
    id            BIGINT NOT NULL CONSTRAINT pk_cost_center PRIMARY KEY
                         CONSTRAINT df_cost_center_id DEFAULT (NEXT VALUE FOR seq_cost_center),
    company_id    BIGINT        NOT NULL CONSTRAINT fk_cost_center_company REFERENCES company (id),
    code          NVARCHAR(30)  NOT NULL,
    name          NVARCHAR(200) NOT NULL,
    erp_code      NVARCHAR(30)  NULL,
    is_active     BIT           NOT NULL CONSTRAINT df_cost_center_is_active DEFAULT 1,
    created_at    DATETIME2(3)  NOT NULL CONSTRAINT df_cost_center_created_at DEFAULT SYSUTCDATETIME(),
    updated_at    DATETIME2(3)  NOT NULL CONSTRAINT df_cost_center_updated_at DEFAULT SYSUTCDATETIME(),
    version       INT NOT NULL CONSTRAINT df_cost_center_version DEFAULT 0,
    CONSTRAINT uq_cost_center_company_code UNIQUE (company_id, code)
);

CREATE TABLE user_account (
    id                  BIGINT NOT NULL CONSTRAINT pk_user_account PRIMARY KEY
                               CONSTRAINT df_user_account_id DEFAULT (NEXT VALUE FOR seq_user_account),
    external_subject    NVARCHAR(255) NULL,
    email               NVARCHAR(320) NOT NULL CONSTRAINT uq_user_account_email UNIQUE,
    display_name        NVARCHAR(200) NOT NULL,
    company_id          BIGINT        NOT NULL CONSTRAINT fk_user_account_company REFERENCES company (id),
    site_id             BIGINT        NULL,
    department_id       BIGINT        NULL,
    preferred_language  VARCHAR(5)    NOT NULL CONSTRAINT df_user_account_language DEFAULT 'en',
    is_active           BIT           NOT NULL CONSTRAINT df_user_account_is_active DEFAULT 1,
    created_at          DATETIME2(3)  NOT NULL CONSTRAINT df_user_account_created_at DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2(3)  NOT NULL CONSTRAINT df_user_account_updated_at DEFAULT SYSUTCDATETIME(),
    version             INT NOT NULL CONSTRAINT df_user_account_version DEFAULT 0,
    CONSTRAINT ck_user_account_language CHECK (preferred_language IN ('en', 'fr')),
    CONSTRAINT fk_user_account_site_same_company FOREIGN KEY (company_id, site_id) REFERENCES site (company_id, id),
    CONSTRAINT fk_user_account_department_same_company FOREIGN KEY (company_id, department_id) REFERENCES department (company_id, id)
);

CREATE UNIQUE INDEX uq_user_account_external_subject ON user_account (external_subject) WHERE external_subject IS NOT NULL;

-- Gap-free business numbers per company, year and document kind (e.g. PR-2026-000123). Used from Sprint 1.
CREATE TABLE document_counter (
    company_id   BIGINT       NOT NULL CONSTRAINT fk_document_counter_company REFERENCES company (id),
    year         SMALLINT     NOT NULL,
    kind         VARCHAR(30)  NOT NULL,
    next_value   BIGINT       NOT NULL CONSTRAINT df_document_counter_next_value DEFAULT 1,
    CONSTRAINT pk_document_counter PRIMARY KEY (company_id, year, kind),
    CONSTRAINT ck_document_counter_next_value CHECK (next_value >= 1),
    CONSTRAINT ck_document_counter_year CHECK (year BETWEEN 2000 AND 2999)
);

-- Append-only, hash-chained audit trail (spec §23, §24; DR-03/DR-04). There is one chain per company
-- ('company:<id>') plus a 'global' chain for events without a company. Each row's hash covers its content
-- and the previous row's hash in the same chain, so any modification or deletion is detectable.
CREATE TABLE audit_chain_head (
    chain_key    VARCHAR(40)  NOT NULL CONSTRAINT pk_audit_chain_head PRIMARY KEY,
    last_seq     BIGINT       NOT NULL,
    last_hash    BINARY(32)   NULL,
    updated_at   DATETIME2(3) NOT NULL CONSTRAINT df_audit_chain_head_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT ck_audit_chain_head_seq CHECK (last_seq >= 0),
    CONSTRAINT ck_audit_chain_head_hash CHECK ((last_seq = 0 AND last_hash IS NULL) OR (last_seq > 0 AND last_hash IS NOT NULL))
);

CREATE TABLE audit_event (
    id              BIGINT NOT NULL CONSTRAINT pk_audit_event PRIMARY KEY
                           CONSTRAINT df_audit_event_id DEFAULT (NEXT VALUE FOR seq_audit_event),
    chain_key       VARCHAR(40)   NOT NULL CONSTRAINT fk_audit_event_chain REFERENCES audit_chain_head (chain_key),
    chain_seq       BIGINT        NOT NULL,
    occurred_at     DATETIME2(3)  NOT NULL,
    actor_user_id   BIGINT        NULL CONSTRAINT fk_audit_event_actor REFERENCES user_account (id),
    actor_label     NVARCHAR(200) NOT NULL,
    company_id      BIGINT        NULL CONSTRAINT fk_audit_event_company REFERENCES company (id),
    entity_type     VARCHAR(60)   NOT NULL,
    entity_id       NVARCHAR(64)  NOT NULL,
    action          VARCHAR(60)   NOT NULL,
    from_status     VARCHAR(60)   NULL,
    to_status       VARCHAR(60)   NULL,
    comment         NVARCHAR(2000) NULL,
    payload_json    NVARCHAR(MAX) NULL,
    previous_hash   BINARY(32)    NULL,
    hash            BINARY(32)    NOT NULL CONSTRAINT uq_audit_event_hash UNIQUE,
    -- A chain can never fork: one event per position.
    CONSTRAINT uq_audit_event_chain_seq UNIQUE (chain_key, chain_seq),
    CONSTRAINT ck_audit_event_chain_seq CHECK (chain_seq >= 1),
    CONSTRAINT ck_audit_event_link CHECK ((chain_seq = 1 AND previous_hash IS NULL) OR (chain_seq > 1 AND previous_hash IS NOT NULL)),
    CONSTRAINT ck_audit_event_payload_json CHECK (payload_json IS NULL OR ISJSON(payload_json) = 1)
);

CREATE INDEX ix_audit_event_entity ON audit_event (entity_type, entity_id, chain_seq);
CREATE INDEX ix_audit_event_company ON audit_event (company_id, occurred_at);
GO

CREATE TRIGGER trg_audit_event_append_only ON audit_event
INSTEAD OF UPDATE, DELETE
AS
BEGIN
    THROW 51000, 'audit_event is append-only: updates and deletes are not permitted.', 1;
END;
GO
