-- Sprint 3: purchasing queue, buyer assignment, purchase mode, suppliers, import jobs.
-- PURCHASING_RETURNED is a purchasing completeness return; it must not restart approval.

ALTER TABLE purchase_request DROP CONSTRAINT ck_purchase_request_status;
ALTER TABLE purchase_request ADD CONSTRAINT ck_purchase_request_status CHECK (status IN (
    'DRAFT', 'CREATED', 'IN_APPROVAL', 'REVISION_REQUESTED', 'REJECTED', 'APPROVED', 'IN_PURCHASING_REVIEW',
    'ASSIGNED_TO_BUYER', 'PURCHASING_RETURNED', 'SOURCING', 'QUOTES_RECEIVED', 'TECHNICAL_VALIDATION',
    'COMMERCIAL_VALIDATION', 'MANAGEMENT_VALIDATION', 'AWARDED', 'ORDERED', 'CONTRACT_SIGNED', 'ORDER_CONFIRMED',
    'IN_PRODUCTION', 'READY', 'SHIPPED', 'AT_PORT', 'IN_CUSTOMS', 'IN_WAREHOUSE', 'DELIVERED', 'PARTIALLY_RECEIVED',
    'NON_CONFORMING', 'ACCEPTED', 'INVOICED', 'PAYMENT_PENDING', 'PAID', 'CLOSED', 'CANCELLED'));

ALTER TABLE purchase_request ADD
    assigned_buyer_user_id BIGINT        NULL CONSTRAINT fk_purchase_request_assigned_buyer REFERENCES user_account (id),
    assigned_at            DATETIME2(3)  NULL,
    purchase_mode          VARCHAR(20)   NULL,
    CONSTRAINT ck_purchase_request_purchase_mode CHECK (purchase_mode IS NULL
        OR purchase_mode IN ('DIRECT', 'CENTRALIZED', 'ON_BEHALF'));

CREATE INDEX ix_purchase_request_buying_status ON purchase_request (buying_company_id, status);
CREATE INDEX ix_purchase_request_assigned_buyer ON purchase_request (assigned_buyer_user_id, status);

CREATE SEQUENCE seq_buyer_assignment AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE TABLE buyer_assignment (
    id                   BIGINT        NOT NULL CONSTRAINT pk_buyer_assignment PRIMARY KEY
                                       CONSTRAINT df_buyer_assignment_id DEFAULT (NEXT VALUE FOR seq_buyer_assignment),
    request_id           BIGINT        NOT NULL CONSTRAINT fk_buyer_assignment_request REFERENCES purchase_request (id),
    buying_company_id    BIGINT        NOT NULL CONSTRAINT fk_buyer_assignment_company REFERENCES company (id),
    buyer_user_id        BIGINT        NOT NULL CONSTRAINT fk_buyer_assignment_buyer REFERENCES user_account (id),
    assigned_by_user_id  BIGINT        NOT NULL CONSTRAINT fk_buyer_assignment_actor REFERENCES user_account (id),
    status               VARCHAR(20)   NOT NULL,
    reason               NVARCHAR(1000) NULL,
    assigned_at          DATETIME2(3)  NOT NULL CONSTRAINT df_buyer_assignment_assigned_at DEFAULT SYSUTCDATETIME(),
    decided_at           DATETIME2(3)  NULL,
    CONSTRAINT ck_buyer_assignment_status CHECK (status IN ('ASSIGNED', 'ACCEPTED', 'RETURNED', 'SUPERSEDED'))
);
CREATE INDEX ix_buyer_assignment_request ON buyer_assignment (request_id, assigned_at);
CREATE INDEX ix_buyer_assignment_buyer ON buyer_assignment (buyer_user_id, status);

CREATE SEQUENCE seq_supplier AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE TABLE supplier (
    id                 BIGINT        NOT NULL CONSTRAINT pk_supplier PRIMARY KEY
                                     CONSTRAINT df_supplier_id DEFAULT (NEXT VALUE FOR seq_supplier),
    group_id           BIGINT        NOT NULL CONSTRAINT fk_supplier_group REFERENCES organization_group (id),
    owning_company_id  BIGINT        NULL     CONSTRAINT fk_supplier_owner REFERENCES company (id),
    code               NVARCHAR(40)  NOT NULL,
    legal_name         NVARCHAR(200) NOT NULL,
    country_code       CHAR(2)       NOT NULL CONSTRAINT fk_supplier_country REFERENCES country (code),
    visibility         VARCHAR(20)   NOT NULL,
    tax_id             NVARCHAR(50)  NULL,
    erp_code           NVARCHAR(40)  NULL,
    email              NVARCHAR(200) NULL,
    city               NVARCHAR(100) NULL,
    is_active          BIT           NOT NULL CONSTRAINT df_supplier_is_active DEFAULT 1,
    created_at         DATETIME2(3)  NOT NULL CONSTRAINT df_supplier_created_at DEFAULT SYSUTCDATETIME(),
    updated_at         DATETIME2(3)  NOT NULL CONSTRAINT df_supplier_updated_at DEFAULT SYSUTCDATETIME(),
    version            INT           NOT NULL CONSTRAINT df_supplier_version DEFAULT 0,
    CONSTRAINT uq_supplier_group_code UNIQUE (group_id, code),
    CONSTRAINT ck_supplier_visibility CHECK (visibility IN ('SHARED', 'COMPANY')),
    CONSTRAINT ck_supplier_company_scope CHECK (
        (visibility = 'SHARED' AND owning_company_id IS NULL)
        OR (visibility = 'COMPANY' AND owning_company_id IS NOT NULL))
);
CREATE UNIQUE INDEX uq_supplier_group_tax ON supplier (group_id, tax_id) WHERE tax_id IS NOT NULL;
CREATE INDEX ix_supplier_group_name ON supplier (group_id, legal_name);

CREATE TABLE supplier_company (
    supplier_id BIGINT NOT NULL CONSTRAINT fk_supplier_company_supplier REFERENCES supplier (id) ON DELETE CASCADE,
    company_id  BIGINT NOT NULL CONSTRAINT fk_supplier_company_company REFERENCES company (id),
    CONSTRAINT pk_supplier_company PRIMARY KEY (supplier_id, company_id)
);

CREATE TABLE supplier_category (
    supplier_id BIGINT NOT NULL CONSTRAINT fk_supplier_category_supplier REFERENCES supplier (id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL,
    CONSTRAINT pk_supplier_category PRIMARY KEY (supplier_id, category_id)
);

CREATE TABLE request_supplier_ref (
    request_id  BIGINT NOT NULL CONSTRAINT fk_request_supplier_request REFERENCES purchase_request (id),
    supplier_id BIGINT NOT NULL CONSTRAINT fk_request_supplier_supplier REFERENCES supplier (id),
    CONSTRAINT pk_request_supplier_ref PRIMARY KEY (request_id, supplier_id)
);

CREATE SEQUENCE seq_import_job AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE TABLE import_job (
    id              BIGINT        NOT NULL CONSTRAINT pk_import_job PRIMARY KEY
                                  CONSTRAINT df_import_job_id DEFAULT (NEXT VALUE FOR seq_import_job),
    group_id        BIGINT        NOT NULL CONSTRAINT fk_import_job_group REFERENCES organization_group (id),
    company_id      BIGINT        NULL     CONSTRAINT fk_import_job_company REFERENCES company (id),
    kind            VARCHAR(20)   NOT NULL,
    file_name       NVARCHAR(200) NOT NULL,
    file_hash       CHAR(64)      NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    created_by      BIGINT        NOT NULL CONSTRAINT fk_import_job_user REFERENCES user_account (id),
    created_at      DATETIME2(3)  NOT NULL CONSTRAINT df_import_job_created_at DEFAULT SYSUTCDATETIME(),
    completed_at    DATETIME2(3)  NULL,
    accepted_rows   INT           NOT NULL CONSTRAINT df_import_job_accepted DEFAULT 0,
    rejected_rows   INT           NOT NULL CONSTRAINT df_import_job_rejected DEFAULT 0,
    error_summary   NVARCHAR(1000) NULL,
    CONSTRAINT ck_import_job_kind CHECK (kind IN ('SUPPLIER', 'ORGANISATION')),
    CONSTRAINT ck_import_job_status CHECK (status IN ('VALIDATING', 'COMMITTED', 'FAILED'))
);
CREATE INDEX ix_import_job_group ON import_job (group_id, created_at);

CREATE SEQUENCE seq_import_row AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE TABLE import_row (
    id          BIGINT         NOT NULL CONSTRAINT pk_import_row PRIMARY KEY
                               CONSTRAINT df_import_row_id DEFAULT (NEXT VALUE FOR seq_import_row),
    job_id      BIGINT         NOT NULL CONSTRAINT fk_import_row_job REFERENCES import_job (id) ON DELETE CASCADE,
    row_no      INT            NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    entity_id   BIGINT         NULL,
    payload     NVARCHAR(MAX)  NULL,
    errors      NVARCHAR(1000) NULL,
    CONSTRAINT ck_import_row_status CHECK (status IN ('ACCEPTED', 'REJECTED')),
    CONSTRAINT uq_import_row_job_no UNIQUE (job_id, row_no)
);
