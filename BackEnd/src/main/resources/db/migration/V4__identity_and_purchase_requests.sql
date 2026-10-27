-- Sprint 1: local credentials, role grants, units of measure, request categories and purchase requests.
-- See docs/product/sprint-1.md (design review outcomes S1DR-05/07/08/09/14/16/17).

-- Identity ------------------------------------------------------------------------------------------------------

-- Demo accounts can only sign in while the "demo" profile is active (S1DR-05).
ALTER TABLE user_account ADD is_demo BIT NOT NULL CONSTRAINT df_user_account_is_demo DEFAULT 0;

-- Local password credential, kept apart from the account so OIDC users can exist without one.
CREATE TABLE user_credential (
    user_id              BIGINT        NOT NULL CONSTRAINT pk_user_credential PRIMARY KEY
                                       CONSTRAINT fk_user_credential_user REFERENCES user_account (id),
    password_hash        VARCHAR(200)  NOT NULL,
    password_changed_at  DATETIME2(3)  NOT NULL CONSTRAINT df_user_credential_changed_at DEFAULT SYSUTCDATETIME()
);

CREATE SEQUENCE seq_user_role AS BIGINT START WITH 1 INCREMENT BY 50;

-- Role grant scoped to a company (and optionally a department). company_id NULL = every company of the user's
-- group, allowed only for group-wide roles (S1DR-14). Role codes are validated in code, not by a CHECK list, so
-- the remaining spec §20 roles can be added without a migration.
CREATE TABLE user_role (
    id             BIGINT       NOT NULL CONSTRAINT pk_user_role PRIMARY KEY
                                CONSTRAINT df_user_role_id DEFAULT (NEXT VALUE FOR seq_user_role),
    user_id        BIGINT       NOT NULL CONSTRAINT fk_user_role_user REFERENCES user_account (id),
    role           VARCHAR(40)  NOT NULL,
    company_id     BIGINT       NULL     CONSTRAINT fk_user_role_company REFERENCES company (id),
    department_id  BIGINT       NULL,
    created_at     DATETIME2(3) NOT NULL CONSTRAINT df_user_role_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_user_role_department_same_company FOREIGN KEY (company_id, department_id) REFERENCES department (company_id, id),
    CONSTRAINT ck_user_role_code CHECK (role = UPPER(role) AND LEN(role) > 0),
    CONSTRAINT ck_user_role_department_needs_company CHECK (department_id IS NULL OR company_id IS NOT NULL),
    CONSTRAINT ck_user_role_group_wide CHECK (company_id IS NOT NULL OR role IN ('GROUP_MANAGEMENT', 'ADMIN', 'AUDITOR'))
);

-- SQL Server treats NULLs as equal in a unique index, so a grant cannot be duplicated.
CREATE UNIQUE INDEX uq_user_role_grant ON user_role (user_id, role, company_id, department_id);

-- Reference data ------------------------------------------------------------------------------------------------

-- Units of measure as codes (not free text) so they can be mapped to ERP units later (S1DR-17).
CREATE TABLE unit_of_measure (
    code        VARCHAR(10)   NOT NULL CONSTRAINT pk_unit_of_measure PRIMARY KEY,
    name_en     NVARCHAR(60)  NOT NULL,
    name_fr     NVARCHAR(60)  NOT NULL,
    sort_order  SMALLINT      NOT NULL,
    CONSTRAINT ck_unit_of_measure_code CHECK (code = UPPER(code))
);

INSERT INTO unit_of_measure (code, name_en, name_fr, sort_order) VALUES
    ('EA',   N'Each',          N'Unité',           10),
    ('PCE',  N'Piece',         N'Pièce',           20),
    ('SET',  N'Set',           N'Ensemble',        30),
    ('PAIR', N'Pair',          N'Paire',           40),
    ('BOX',  N'Box',           N'Boîte',           50),
    ('ROLL', N'Roll',          N'Rouleau',         60),
    ('LOT',  N'Lot',           N'Lot',             70),
    ('KG',   N'Kilogram',      N'Kilogramme',      80),
    ('T',    N'Tonne',         N'Tonne',           90),
    ('M',    N'Metre',         N'Mètre',          100),
    ('M2',   N'Square metre',  N'Mètre carré',    110),
    ('M3',   N'Cubic metre',   N'Mètre cube',     120),
    ('L',    N'Litre',         N'Litre',          130),
    ('H',    N'Hour',          N'Heure',          140),
    ('DAY',  N'Day',           N'Jour',           150);

CREATE SEQUENCE seq_request_category AS BIGINT START WITH 1 INCREMENT BY 50;

-- Purchase categories are defined per group (spec §6.1 "Classement").
CREATE TABLE request_category (
    id          BIGINT        NOT NULL CONSTRAINT pk_request_category PRIMARY KEY
                              CONSTRAINT df_request_category_id DEFAULT (NEXT VALUE FOR seq_request_category),
    group_id    BIGINT        NOT NULL CONSTRAINT fk_request_category_group REFERENCES organization_group (id),
    code        NVARCHAR(30)  NOT NULL,
    name_en     NVARCHAR(100) NOT NULL,
    name_fr     NVARCHAR(100) NOT NULL,
    is_active   BIT           NOT NULL CONSTRAINT df_request_category_is_active DEFAULT 1,
    created_at  DATETIME2(3)  NOT NULL CONSTRAINT df_request_category_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_request_category_group_code UNIQUE (group_id, code),
    CONSTRAINT uq_request_category_group_id UNIQUE (group_id, id)
);

-- Purchase requests -------------------------------------------------------------------------------------------

CREATE SEQUENCE seq_purchase_request            AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_purchase_request_line       AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_purchase_request_transition AS BIGINT START WITH 1 INCREMENT BY 50;

-- Header. Actors are recorded separately (spec §3.2): entered by, requester, beneficiary, requesting, charged and
-- buying company. Charged and buying company are derived by the server; buying is a proposal until purchasing
-- confirms it (S1DR-07, US-15). One currency per request (DR-06). number is NULL while DRAFT (A1).
CREATE TABLE purchase_request (
    id                       BIGINT         NOT NULL CONSTRAINT pk_purchase_request PRIMARY KEY
                                            CONSTRAINT df_purchase_request_id DEFAULT (NEXT VALUE FOR seq_purchase_request),
    group_id                 BIGINT         NOT NULL CONSTRAINT fk_purchase_request_group REFERENCES organization_group (id),
    number                   NVARCHAR(40)   NULL,
    status                   VARCHAR(40)    NOT NULL,
    stage                    VARCHAR(30)    NOT NULL,
    origin                   VARCHAR(20)    NOT NULL CONSTRAINT df_purchase_request_origin DEFAULT 'INTERNAL',
    title                    NVARCHAR(200)  NULL,
    category_id              BIGINT         NULL,
    priority                 VARCHAR(20)    NULL,
    purchase_type            VARCHAR(20)    NULL,
    currency_code            CHAR(3)        NOT NULL CONSTRAINT fk_purchase_request_currency REFERENCES currency (code),
    requesting_company_id    BIGINT         NOT NULL,
    charged_company_id       BIGINT         NOT NULL,
    buying_company_id        BIGINT         NOT NULL,
    buying_company_proposed  BIT            NOT NULL CONSTRAINT df_purchase_request_buying_proposed DEFAULT 1,
    department_id            BIGINT         NULL,
    site_id                  BIGINT         NULL,
    cost_center_id           BIGINT         NULL,
    requester_user_id        BIGINT         NOT NULL CONSTRAINT fk_purchase_request_requester REFERENCES user_account (id),
    entered_by_user_id       BIGINT         NOT NULL CONSTRAINT fk_purchase_request_entered_by REFERENCES user_account (id),
    updated_by_user_id       BIGINT         NOT NULL CONSTRAINT fk_purchase_request_updated_by REFERENCES user_account (id),
    beneficiary              NVARCHAR(200)  NULL,
    beneficiary_site_id      BIGINT         NULL,
    project                  NVARCHAR(100)  NULL,
    justification            NVARCHAR(2000) NULL,
    urgency_justification    NVARCHAR(1000) NULL,
    budget_line_ref          NVARCHAR(60)   NULL,
    equipment_ref            NVARCHAR(100)  NULL,
    maintenance_order_ref    NVARCHAR(60)   NULL,
    required_date            DATE           NULL,
    delivery_location        NVARCHAR(300)  NULL,
    total_amount             DECIMAL(19,4)  NOT NULL CONSTRAINT df_purchase_request_total DEFAULT 0,
    current_owner_user_id    BIGINT         NOT NULL CONSTRAINT fk_purchase_request_owner REFERENCES user_account (id),
    owner_since              DATETIME2(3)   NOT NULL,
    submitted_at             DATETIME2(3)   NULL,
    created_at               DATETIME2(3)   NOT NULL CONSTRAINT df_purchase_request_created_at DEFAULT SYSUTCDATETIME(),
    updated_at               DATETIME2(3)   NOT NULL CONSTRAINT df_purchase_request_updated_at DEFAULT SYSUTCDATETIME(),
    version                  INT            NOT NULL CONSTRAINT df_purchase_request_version DEFAULT 0,
    -- Every company of a request belongs to the request's group.
    CONSTRAINT fk_purchase_request_requesting_company FOREIGN KEY (group_id, requesting_company_id) REFERENCES company (group_id, id),
    CONSTRAINT fk_purchase_request_charged_company FOREIGN KEY (group_id, charged_company_id) REFERENCES company (group_id, id),
    CONSTRAINT fk_purchase_request_buying_company FOREIGN KEY (group_id, buying_company_id) REFERENCES company (group_id, id),
    -- Organisational references belong to the requesting company.
    CONSTRAINT fk_purchase_request_department FOREIGN KEY (requesting_company_id, department_id) REFERENCES department (company_id, id),
    CONSTRAINT fk_purchase_request_site FOREIGN KEY (requesting_company_id, site_id) REFERENCES site (company_id, id),
    CONSTRAINT fk_purchase_request_cost_center FOREIGN KEY (requesting_company_id, cost_center_id) REFERENCES cost_center (company_id, id),
    CONSTRAINT fk_purchase_request_beneficiary_site FOREIGN KEY (requesting_company_id, beneficiary_site_id) REFERENCES site (company_id, id),
    CONSTRAINT fk_purchase_request_category FOREIGN KEY (group_id, category_id) REFERENCES request_category (group_id, id),
    CONSTRAINT ck_purchase_request_status CHECK (status IN (
        'DRAFT', 'CREATED', 'IN_APPROVAL', 'REVISION_REQUESTED', 'REJECTED', 'APPROVED', 'IN_PURCHASING_REVIEW',
        'ASSIGNED_TO_BUYER', 'SOURCING', 'QUOTES_RECEIVED', 'TECHNICAL_VALIDATION', 'COMMERCIAL_VALIDATION',
        'MANAGEMENT_VALIDATION', 'ORDERED', 'CONTRACT_SIGNED', 'ORDER_CONFIRMED', 'IN_PRODUCTION', 'READY', 'SHIPPED',
        'AT_PORT', 'IN_CUSTOMS', 'IN_WAREHOUSE', 'DELIVERED', 'PARTIALLY_RECEIVED', 'NON_CONFORMING', 'ACCEPTED',
        'INVOICED', 'PAYMENT_PENDING', 'PAID', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_purchase_request_stage CHECK (stage IN (
        'REQUEST', 'APPROVAL', 'PURCHASING', 'SOURCING', 'EVALUATION', 'COMMITMENT', 'SHIPMENT', 'RECEIPT',
        'FINANCE', 'CLOSURE')),
    CONSTRAINT ck_purchase_request_origin CHECK (origin IN ('INTERNAL', 'EXTERNAL')),
    CONSTRAINT ck_purchase_request_priority CHECK (priority IS NULL OR priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_purchase_request_purchase_type CHECK (purchase_type IS NULL OR purchase_type IN ('LOCAL', 'INTERNATIONAL')),
    CONSTRAINT ck_purchase_request_total CHECK (total_amount >= 0),
    -- Only drafts lack a number; a submitted request always has one and a submission time.
    CONSTRAINT ck_purchase_request_number CHECK ((status = 'DRAFT' AND number IS NULL AND submitted_at IS NULL)
                                              OR (status <> 'DRAFT' AND number IS NOT NULL AND submitted_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_purchase_request_number ON purchase_request (number) WHERE number IS NOT NULL;
CREATE INDEX ix_purchase_request_requester ON purchase_request (requester_user_id, updated_at);
CREATE INDEX ix_purchase_request_entered_by ON purchase_request (entered_by_user_id, updated_at);
CREATE INDEX ix_purchase_request_department ON purchase_request (requesting_company_id, department_id, status);
CREATE INDEX ix_purchase_request_group_status ON purchase_request (group_id, status);

CREATE TABLE purchase_request_line (
    id                    BIGINT         NOT NULL CONSTRAINT pk_purchase_request_line PRIMARY KEY
                                         CONSTRAINT df_purchase_request_line_id DEFAULT (NEXT VALUE FOR seq_purchase_request_line),
    request_id            BIGINT         NOT NULL CONSTRAINT fk_purchase_request_line_request REFERENCES purchase_request (id) ON DELETE CASCADE,
    line_no               SMALLINT       NOT NULL,
    status                VARCHAR(40)    NOT NULL,
    description           NVARCHAR(500)  NULL,
    item_reference        NVARCHAR(60)   NULL,
    quantity              DECIMAL(19,4)  NULL,
    unit_code             VARCHAR(10)    NULL CONSTRAINT fk_purchase_request_line_unit REFERENCES unit_of_measure (code),
    estimated_unit_price  DECIMAL(19,4)  NULL,
    line_total            DECIMAL(19,4)  NOT NULL CONSTRAINT df_purchase_request_line_total DEFAULT 0,
    required_date         DATE           NULL,
    delivery_location     NVARCHAR(300)  NULL,
    CONSTRAINT uq_purchase_request_line_no UNIQUE (request_id, line_no),
    CONSTRAINT ck_purchase_request_line_no CHECK (line_no >= 1),
    CONSTRAINT ck_purchase_request_line_quantity CHECK (quantity IS NULL OR quantity > 0),
    CONSTRAINT ck_purchase_request_line_price CHECK (estimated_unit_price IS NULL OR estimated_unit_price >= 0),
    CONSTRAINT ck_purchase_request_line_total CHECK (line_total >= 0),
    CONSTRAINT ck_purchase_request_line_status CHECK (status IN (
        'DRAFT', 'CREATED', 'ORDERED', 'ORDER_CONFIRMED', 'IN_PRODUCTION', 'READY', 'SHIPPED', 'AT_PORT', 'IN_CUSTOMS',
        'IN_WAREHOUSE', 'DELIVERED', 'PARTIALLY_RECEIVED', 'NON_CONFORMING', 'ACCEPTED', 'INVOICED',
        'PAYMENT_PENDING', 'PAID', 'CLOSED', 'CANCELLED'))
);

-- Stage timeline (S1DR-09): one row per status change, written in the same transaction as the change and its
-- audit event. Feeds "current stage, owner, since when" now and per-stage delays later (US-10).
CREATE TABLE purchase_request_transition (
    id              BIGINT         NOT NULL CONSTRAINT pk_purchase_request_transition PRIMARY KEY
                                   CONSTRAINT df_purchase_request_transition_id DEFAULT (NEXT VALUE FOR seq_purchase_request_transition),
    request_id      BIGINT         NOT NULL CONSTRAINT fk_purchase_request_transition_request REFERENCES purchase_request (id) ON DELETE CASCADE,
    from_status     VARCHAR(40)    NULL,
    to_status       VARCHAR(40)    NOT NULL,
    stage           VARCHAR(30)    NOT NULL,
    owner_user_id   BIGINT         NULL CONSTRAINT fk_purchase_request_transition_owner REFERENCES user_account (id),
    actor_user_id   BIGINT         NOT NULL CONSTRAINT fk_purchase_request_transition_actor REFERENCES user_account (id),
    occurred_at     DATETIME2(3)   NOT NULL,
    planned_due_at  DATETIME2(3)   NULL,
    comment         NVARCHAR(1000) NULL
);

CREATE INDEX ix_purchase_request_transition_request ON purchase_request_transition (request_id, occurred_at);
