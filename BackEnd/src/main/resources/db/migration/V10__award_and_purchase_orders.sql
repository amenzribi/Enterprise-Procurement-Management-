-- Sprint 5: supplier award snapshot, purchase orders, milestones, price memory.

CREATE SEQUENCE seq_award AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_award_line AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_purchase_order AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_purchase_order_line AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_po_milestone AS BIGINT START WITH 1 INCREMENT BY 50;

CREATE TABLE award (
    id                   BIGINT          NOT NULL CONSTRAINT pk_award PRIMARY KEY
                                         CONSTRAINT df_award_id DEFAULT (NEXT VALUE FOR seq_award),
    group_id             BIGINT          NOT NULL CONSTRAINT fk_award_group REFERENCES organization_group (id),
    request_id           BIGINT          NOT NULL CONSTRAINT fk_award_request REFERENCES purchase_request (id),
    rfq_id               BIGINT          NOT NULL CONSTRAINT fk_award_rfq REFERENCES rfq (id),
    quotation_id         BIGINT          NOT NULL CONSTRAINT fk_award_quotation REFERENCES quotation (id),
    supplier_id          BIGINT          NOT NULL CONSTRAINT fk_award_supplier REFERENCES supplier (id),
    buying_company_id    BIGINT          NOT NULL CONSTRAINT fk_award_buying REFERENCES company (id),
    requesting_company_id BIGINT         NOT NULL CONSTRAINT fk_award_requesting REFERENCES company (id),
    charged_company_id   BIGINT          NOT NULL CONSTRAINT fk_award_charged REFERENCES company (id),
    currency             CHAR(3)         NOT NULL,
    merchandise_amount   DECIMAL(19,4)   NOT NULL,
    freight_amount       DECIMAL(19,4)   NOT NULL,
    duty_amount          DECIMAL(19,4)   NOT NULL,
    other_amount         DECIMAL(19,4)   NOT NULL,
    landed_amount        DECIMAL(19,4)   NOT NULL,
    reason               NVARCHAR(2000)  NOT NULL,
    awarded_by_user_id   BIGINT          NOT NULL CONSTRAINT fk_award_actor REFERENCES user_account (id),
    awarded_at           DATETIME2(3)    NOT NULL CONSTRAINT df_award_awarded_at DEFAULT SYSUTCDATETIME(),
    version              INT             NOT NULL CONSTRAINT df_award_version DEFAULT 0,
    CONSTRAINT uq_award_request UNIQUE (request_id)
);
CREATE INDEX ix_award_supplier ON award (supplier_id, awarded_at);

CREATE TABLE award_line (
    id                BIGINT          NOT NULL CONSTRAINT pk_award_line PRIMARY KEY
                                      CONSTRAINT df_award_line_id DEFAULT (NEXT VALUE FOR seq_award_line),
    award_id          BIGINT          NOT NULL CONSTRAINT fk_award_line_award REFERENCES award (id),
    rfq_line_id       BIGINT          NOT NULL CONSTRAINT fk_award_line_rfq_line REFERENCES rfq_line (id),
    request_line_id   BIGINT          NOT NULL CONSTRAINT fk_award_line_request_line REFERENCES purchase_request_line (id),
    line_no           SMALLINT        NOT NULL,
    description       NVARCHAR(500)   NOT NULL,
    quantity          DECIMAL(19,4)   NOT NULL,
    unit              NVARCHAR(20)    NOT NULL,
    unit_price        DECIMAL(19,4)   NOT NULL,
    line_amount       DECIMAL(19,4)   NOT NULL,
    CONSTRAINT uq_award_line_no UNIQUE (award_id, line_no)
);

CREATE TABLE purchase_order (
    id                    BIGINT          NOT NULL CONSTRAINT pk_purchase_order PRIMARY KEY
                                          CONSTRAINT df_purchase_order_id DEFAULT (NEXT VALUE FOR seq_purchase_order),
    group_id              BIGINT          NOT NULL CONSTRAINT fk_po_group REFERENCES organization_group (id),
    award_id              BIGINT          NOT NULL CONSTRAINT fk_po_award REFERENCES award (id),
    request_id            BIGINT          NOT NULL CONSTRAINT fk_po_request REFERENCES purchase_request (id),
    number                NVARCHAR(40)    NULL,
    status                VARCHAR(20)     NOT NULL,
    buying_company_id     BIGINT          NOT NULL CONSTRAINT fk_po_buying REFERENCES company (id),
    requesting_company_id BIGINT          NOT NULL CONSTRAINT fk_po_requesting REFERENCES company (id),
    charged_company_id    BIGINT          NOT NULL CONSTRAINT fk_po_charged REFERENCES company (id),
    supplier_id           BIGINT          NOT NULL CONSTRAINT fk_po_supplier REFERENCES supplier (id),
    currency              CHAR(3)         NOT NULL,
    merchandise_amount    DECIMAL(19,4)   NOT NULL,
    freight_amount        DECIMAL(19,4)   NOT NULL,
    duty_amount           DECIMAL(19,4)   NOT NULL,
    other_amount          DECIMAL(19,4)   NOT NULL,
    total_amount          DECIMAL(19,4)   NOT NULL,
    payment_terms         NVARCHAR(200)   NULL,
    incoterm              NVARCHAR(40)    NULL,
    required_date         DATE            NULL,
    delivery_address      NVARCHAR(500)   NULL,
    created_by_user_id    BIGINT          NOT NULL CONSTRAINT fk_po_creator REFERENCES user_account (id),
    created_at            DATETIME2(3)    NOT NULL CONSTRAINT df_po_created_at DEFAULT SYSUTCDATETIME(),
    issued_at             DATETIME2(3)    NULL,
    issued_by_user_id     BIGINT          NULL CONSTRAINT fk_po_issuer REFERENCES user_account (id),
    confirmed_at          DATETIME2(3)    NULL,
    confirmed_by_user_id  BIGINT          NULL CONSTRAINT fk_po_confirmer REFERENCES user_account (id),
    confirmation_note     NVARCHAR(1000)  NULL,
    version               INT             NOT NULL CONSTRAINT df_po_version DEFAULT 0,
    CONSTRAINT ck_po_status CHECK (status IN ('DRAFT', 'ISSUED', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT uq_po_award UNIQUE (award_id),
    CONSTRAINT uq_po_number UNIQUE (buying_company_id, number)
);
CREATE INDEX ix_po_request ON purchase_order (request_id, status);

CREATE TABLE purchase_order_line (
    id              BIGINT          NOT NULL CONSTRAINT pk_po_line PRIMARY KEY
                                    CONSTRAINT df_po_line_id DEFAULT (NEXT VALUE FOR seq_purchase_order_line),
    purchase_order_id BIGINT        NOT NULL CONSTRAINT fk_po_line_po REFERENCES purchase_order (id),
    award_line_id   BIGINT          NOT NULL CONSTRAINT fk_po_line_award_line REFERENCES award_line (id),
    request_line_id BIGINT          NOT NULL CONSTRAINT fk_po_line_request_line REFERENCES purchase_request_line (id),
    line_no         SMALLINT        NOT NULL,
    description     NVARCHAR(500)   NOT NULL,
    quantity        DECIMAL(19,4)   NOT NULL,
    unit            NVARCHAR(20)    NOT NULL,
    unit_price      DECIMAL(19,4)   NOT NULL,
    line_amount     DECIMAL(19,4)   NOT NULL,
    CONSTRAINT uq_po_line_no UNIQUE (purchase_order_id, line_no)
);

CREATE TABLE po_payment_milestone (
    id                BIGINT          NOT NULL CONSTRAINT pk_po_milestone PRIMARY KEY
                                      CONSTRAINT df_po_milestone_id DEFAULT (NEXT VALUE FOR seq_po_milestone),
    purchase_order_id BIGINT          NOT NULL CONSTRAINT fk_po_milestone_po REFERENCES purchase_order (id),
    label             NVARCHAR(200)   NOT NULL,
    due_date          DATE            NULL,
    amount            DECIMAL(19,4)   NOT NULL,
    currency          CHAR(3)         NOT NULL,
    status            VARCHAR(20)     NOT NULL,
    CONSTRAINT ck_po_milestone_status CHECK (status IN ('PLANNED', 'DUE', 'PAID'))
);
CREATE INDEX ix_po_milestone_po ON po_payment_milestone (purchase_order_id);

CREATE TABLE price_memory (
    group_id          BIGINT          NOT NULL CONSTRAINT fk_price_memory_group REFERENCES organization_group (id),
    supplier_id       BIGINT          NOT NULL CONSTRAINT fk_price_memory_supplier REFERENCES supplier (id),
    category_id       BIGINT          NULL     CONSTRAINT fk_price_memory_category REFERENCES request_category (id),
    description_norm  NVARCHAR(200)   NOT NULL,
    unit_price        DECIMAL(19,4)   NOT NULL,
    currency          CHAR(3)         NOT NULL,
    unit              NVARCHAR(20)    NOT NULL,
    award_id          BIGINT          NOT NULL CONSTRAINT fk_price_memory_award REFERENCES award (id),
    awarded_at        DATETIME2(3)    NOT NULL,
    CONSTRAINT pk_price_memory PRIMARY KEY (group_id, supplier_id, description_norm, currency, unit)
);
