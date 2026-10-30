-- Sprint 4: RFQs, quotations, secure supplier quote links, evaluation statuses already in ck from V7.

CREATE SEQUENCE seq_rfq AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_rfq_supplier AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_rfq_line AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_quotation AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_quotation_line AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_quote_link_token AS BIGINT START WITH 1 INCREMENT BY 50;

CREATE TABLE rfq (
    id                   BIGINT         NOT NULL CONSTRAINT pk_rfq PRIMARY KEY
                                        CONSTRAINT df_rfq_id DEFAULT (NEXT VALUE FOR seq_rfq),
    group_id             BIGINT         NOT NULL CONSTRAINT fk_rfq_group REFERENCES organization_group (id),
    request_id           BIGINT         NOT NULL CONSTRAINT fk_rfq_request REFERENCES purchase_request (id),
    buying_company_id    BIGINT         NOT NULL CONSTRAINT fk_rfq_buying REFERENCES company (id),
    kind                 VARCHAR(20)    NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    title                NVARCHAR(200)  NOT NULL,
    comparison_currency  CHAR(3)        NOT NULL,
    created_by_user_id   BIGINT         NOT NULL CONSTRAINT fk_rfq_creator REFERENCES user_account (id),
    created_at           DATETIME2(3)   NOT NULL CONSTRAINT df_rfq_created_at DEFAULT SYSUTCDATETIME(),
    closed_at            DATETIME2(3)   NULL,
    version              INT            NOT NULL CONSTRAINT df_rfq_version DEFAULT 0,
    CONSTRAINT ck_rfq_kind CHECK (kind IN ('QUICK', 'STANDARD')),
    CONSTRAINT ck_rfq_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT uq_rfq_request UNIQUE (request_id)
);
CREATE INDEX ix_rfq_buying ON rfq (buying_company_id, status);

CREATE TABLE rfq_line (
    id              BIGINT          NOT NULL CONSTRAINT pk_rfq_line PRIMARY KEY
                                    CONSTRAINT df_rfq_line_id DEFAULT (NEXT VALUE FOR seq_rfq_line),
    rfq_id          BIGINT          NOT NULL CONSTRAINT fk_rfq_line_rfq REFERENCES rfq (id),
    request_line_id BIGINT          NOT NULL CONSTRAINT fk_rfq_line_request_line REFERENCES purchase_request_line (id),
    line_no         SMALLINT        NOT NULL,
    description     NVARCHAR(500)   NOT NULL,
    quantity        DECIMAL(19,4)   NOT NULL,
    unit            NVARCHAR(20)    NOT NULL,
    CONSTRAINT uq_rfq_line_no UNIQUE (rfq_id, line_no)
);

CREATE TABLE rfq_supplier (
    id              BIGINT         NOT NULL CONSTRAINT pk_rfq_supplier PRIMARY KEY
                                   CONSTRAINT df_rfq_supplier_id DEFAULT (NEXT VALUE FOR seq_rfq_supplier),
    rfq_id          BIGINT         NOT NULL CONSTRAINT fk_rfq_supplier_rfq REFERENCES rfq (id),
    supplier_id     BIGINT         NOT NULL CONSTRAINT fk_rfq_supplier_supplier REFERENCES supplier (id),
    invited_at      DATETIME2(3)   NOT NULL CONSTRAINT df_rfq_supplier_invited DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_rfq_supplier UNIQUE (rfq_id, supplier_id)
);

CREATE TABLE quotation (
    id                   BIGINT          NOT NULL CONSTRAINT pk_quotation PRIMARY KEY
                                         CONSTRAINT df_quotation_id DEFAULT (NEXT VALUE FOR seq_quotation),
    rfq_id               BIGINT          NOT NULL CONSTRAINT fk_quotation_rfq REFERENCES rfq (id),
    rfq_supplier_id      BIGINT          NOT NULL CONSTRAINT fk_quotation_rfq_supplier REFERENCES rfq_supplier (id),
    supplier_id          BIGINT          NOT NULL CONSTRAINT fk_quotation_supplier REFERENCES supplier (id),
    status               VARCHAR(20)     NOT NULL,
    currency             CHAR(3)         NOT NULL,
    valid_until          DATE            NULL,
    lead_time_days       INT             NULL,
    payment_terms        NVARCHAR(200)   NULL,
    incoterm             NVARCHAR(40)    NULL,
    freight_amount       DECIMAL(19,4)   NOT NULL CONSTRAINT df_quotation_freight DEFAULT 0,
    duty_amount          DECIMAL(19,4)   NOT NULL CONSTRAINT df_quotation_duty DEFAULT 0,
    other_amount         DECIMAL(19,4)   NOT NULL CONSTRAINT df_quotation_other DEFAULT 0,
    merchandise_amount   DECIMAL(19,4)   NOT NULL CONSTRAINT df_quotation_merch DEFAULT 0,
    notes                NVARCHAR(2000)  NULL,
    source               VARCHAR(20)     NOT NULL,
    submitted_at         DATETIME2(3)    NOT NULL,
    submitted_by_user_id BIGINT          NULL CONSTRAINT fk_quotation_actor REFERENCES user_account (id),
    version              INT             NOT NULL CONSTRAINT df_quotation_version DEFAULT 0,
    CONSTRAINT ck_quotation_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'WITHDRAWN')),
    CONSTRAINT ck_quotation_source CHECK (source IN ('BUYER', 'SUPPLIER_LINK')),
    CONSTRAINT uq_quotation_rfq_supplier UNIQUE (rfq_supplier_id)
);
CREATE INDEX ix_quotation_rfq ON quotation (rfq_id, status);

CREATE TABLE quotation_line (
    id              BIGINT          NOT NULL CONSTRAINT pk_quotation_line PRIMARY KEY
                                    CONSTRAINT df_quotation_line_id DEFAULT (NEXT VALUE FOR seq_quotation_line),
    quotation_id    BIGINT          NOT NULL CONSTRAINT fk_quotation_line_quotation REFERENCES quotation (id),
    rfq_line_id     BIGINT          NOT NULL CONSTRAINT fk_quotation_line_rfq_line REFERENCES rfq_line (id),
    quantity        DECIMAL(19,4)   NOT NULL,
    unit_price      DECIMAL(19,4)   NULL,
    line_amount     DECIMAL(19,4)   NULL,
    CONSTRAINT uq_quotation_line UNIQUE (quotation_id, rfq_line_id)
);

CREATE TABLE quote_link_token (
    id               BIGINT        NOT NULL CONSTRAINT pk_quote_link_token PRIMARY KEY
                                   CONSTRAINT df_quote_link_token_id DEFAULT (NEXT VALUE FOR seq_quote_link_token),
    token_hash       VARBINARY(32) NOT NULL,
    rfq_supplier_id  BIGINT        NOT NULL CONSTRAINT fk_quote_link_rfq_supplier REFERENCES rfq_supplier (id),
    expires_at       DATETIME2(3)  NOT NULL,
    used_at          DATETIME2(3)  NULL,
    created_at       DATETIME2(3)  NOT NULL CONSTRAINT df_quote_link_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_quote_link_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_quote_link_supplier ON quote_link_token (rfq_supplier_id);
