-- Sprint 7: goods receipts, manual final invoice, closure score (no AI / no algorithmic three-way match).

CREATE SEQUENCE seq_goods_receipt AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_goods_receipt_line AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_receipt_evidence AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_supplier_invoice AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_service_acceptance AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_supplier_score AS BIGINT START WITH 1 INCREMENT BY 50;

CREATE TABLE goods_receipt (
    id                    BIGINT          NOT NULL CONSTRAINT pk_goods_receipt PRIMARY KEY
                                          CONSTRAINT df_goods_receipt_id DEFAULT (NEXT VALUE FOR seq_goods_receipt),
    group_id              BIGINT          NOT NULL CONSTRAINT fk_gr_group REFERENCES organization_group (id),
    request_id            BIGINT          NOT NULL CONSTRAINT fk_gr_request REFERENCES purchase_request (id),
    purchase_order_id     BIGINT          NOT NULL CONSTRAINT fk_gr_po REFERENCES purchase_order (id),
    shipment_id           BIGINT          NULL CONSTRAINT fk_gr_shipment REFERENCES shipment (id),
    number                NVARCHAR(40)    NOT NULL,
    status                VARCHAR(20)     NOT NULL,
    received_at           DATETIME2(3)    NOT NULL,
    received_by_user_id   BIGINT          NOT NULL CONSTRAINT fk_gr_receiver REFERENCES user_account (id),
    confirmed_at          DATETIME2(3)    NULL,
    confirmed_by_user_id  BIGINT          NULL CONSTRAINT fk_gr_confirmer REFERENCES user_account (id),
    note                  NVARCHAR(2000)  NULL,
    created_at            DATETIME2(3)    NOT NULL CONSTRAINT df_gr_created DEFAULT SYSUTCDATETIME(),
    version               INT             NOT NULL CONSTRAINT df_gr_version DEFAULT 0,
    CONSTRAINT ck_gr_status CHECK (status IN ('RECORDED', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT uq_gr_number UNIQUE (purchase_order_id, number)
);
CREATE INDEX ix_gr_request ON goods_receipt (request_id, status);

CREATE TABLE goods_receipt_line (
    id                    BIGINT          NOT NULL CONSTRAINT pk_goods_receipt_line PRIMARY KEY
                                          CONSTRAINT df_grl_id DEFAULT (NEXT VALUE FOR seq_goods_receipt_line),
    goods_receipt_id      BIGINT          NOT NULL CONSTRAINT fk_grl_receipt REFERENCES goods_receipt (id),
    purchase_order_line_id BIGINT         NOT NULL CONSTRAINT fk_grl_po_line REFERENCES purchase_order_line (id),
    shipment_line_id      BIGINT          NULL CONSTRAINT fk_grl_ship_line REFERENCES shipment_line (id),
    request_line_id       BIGINT          NOT NULL CONSTRAINT fk_grl_request_line REFERENCES purchase_request_line (id),
    line_no               SMALLINT        NOT NULL,
    description           NVARCHAR(500)   NOT NULL,
    quantity_received     DECIMAL(19,4)   NOT NULL,
    quantity_accepted     DECIMAL(19,4)   NOT NULL,
    quantity_rejected     DECIMAL(19,4)   NOT NULL,
    quantity_damaged      DECIMAL(19,4)   NOT NULL,
    condition_code        VARCHAR(20)     NOT NULL,
    note                  NVARCHAR(1000)  NULL,
    CONSTRAINT uq_grl_no UNIQUE (goods_receipt_id, line_no),
    CONSTRAINT ck_grl_qty CHECK (
        quantity_received > 0
        AND quantity_accepted >= 0 AND quantity_rejected >= 0 AND quantity_damaged >= 0
        AND quantity_accepted + quantity_rejected <= quantity_received
        AND quantity_damaged <= quantity_received),
    CONSTRAINT ck_grl_condition CHECK (condition_code IN ('OK', 'DAMAGED', 'MISSING', 'NON_CONFORMING'))
);

CREATE TABLE receipt_evidence (
    id                    BIGINT          NOT NULL CONSTRAINT pk_receipt_evidence PRIMARY KEY
                                          CONSTRAINT df_re_id DEFAULT (NEXT VALUE FOR seq_receipt_evidence),
    goods_receipt_id      BIGINT          NOT NULL CONSTRAINT fk_re_receipt REFERENCES goods_receipt (id),
    kind                  VARCHAR(40)     NOT NULL,
    reference_number      NVARCHAR(100)   NULL,
    note                  NVARCHAR(1000)  NULL,
    storage_key           VARCHAR(64)     NULL,
    file_name             NVARCHAR(255)   NULL,
    uploaded_by_user_id   BIGINT          NOT NULL CONSTRAINT fk_re_user REFERENCES user_account (id),
    uploaded_at           DATETIME2(3)    NOT NULL CONSTRAINT df_re_uploaded DEFAULT SYSUTCDATETIME(),
    CONSTRAINT ck_re_kind CHECK (kind IN ('PHOTO', 'DAMAGE_REPORT', 'DELIVERY_NOTE', 'OTHER'))
);
CREATE INDEX ix_re_receipt ON receipt_evidence (goods_receipt_id);

CREATE TABLE service_acceptance (
    id                    BIGINT          NOT NULL CONSTRAINT pk_service_acceptance PRIMARY KEY
                                          CONSTRAINT df_sa_id DEFAULT (NEXT VALUE FOR seq_service_acceptance),
    request_id            BIGINT          NOT NULL CONSTRAINT fk_sa_request REFERENCES purchase_request (id),
    accepted_by_user_id   BIGINT          NOT NULL CONSTRAINT fk_sa_user REFERENCES user_account (id),
    accepted_at           DATETIME2(3)    NOT NULL,
    performance_ok        BIT             NOT NULL CONSTRAINT df_sa_ok DEFAULT 1,
    comment               NVARCHAR(2000)  NOT NULL,
    CONSTRAINT uq_sa_request UNIQUE (request_id)
);

CREATE TABLE supplier_invoice (
    id                    BIGINT          NOT NULL CONSTRAINT pk_supplier_invoice PRIMARY KEY
                                          CONSTRAINT df_si_id DEFAULT (NEXT VALUE FOR seq_supplier_invoice),
    request_id            BIGINT          NOT NULL CONSTRAINT fk_si_request REFERENCES purchase_request (id),
    purchase_order_id     BIGINT          NOT NULL CONSTRAINT fk_si_po REFERENCES purchase_order (id),
    invoice_number        NVARCHAR(80)    NOT NULL,
    amount                DECIMAL(19,4)   NOT NULL,
    currency              CHAR(3)         NOT NULL,
    document_reference    NVARCHAR(200)   NULL,
    match_attested        BIT             NOT NULL CONSTRAINT df_si_match DEFAULT 0,
    payment_status        VARCHAR(20)     NOT NULL,
    recorded_by_user_id   BIGINT          NOT NULL CONSTRAINT fk_si_user REFERENCES user_account (id),
    recorded_at           DATETIME2(3)    NOT NULL CONSTRAINT df_si_recorded DEFAULT SYSUTCDATETIME(),
    version               INT             NOT NULL CONSTRAINT df_si_version DEFAULT 0,
    CONSTRAINT ck_si_amount CHECK (amount > 0),
    CONSTRAINT ck_si_payment CHECK (payment_status IN ('PENDING', 'PAID')),
    CONSTRAINT uq_si_request UNIQUE (request_id)
);

CREATE TABLE supplier_score (
    id                    BIGINT          NOT NULL CONSTRAINT pk_supplier_score PRIMARY KEY
                                          CONSTRAINT df_ss_id DEFAULT (NEXT VALUE FOR seq_supplier_score),
    group_id              BIGINT          NOT NULL CONSTRAINT fk_ss_group REFERENCES organization_group (id),
    request_id            BIGINT          NOT NULL CONSTRAINT fk_ss_request REFERENCES purchase_request (id),
    supplier_id           BIGINT          NOT NULL CONSTRAINT fk_ss_supplier REFERENCES supplier (id),
    rating                TINYINT         NOT NULL,
    comment               NVARCHAR(1000)  NULL,
    scored_by_user_id     BIGINT          NOT NULL CONSTRAINT fk_ss_user REFERENCES user_account (id),
    scored_at             DATETIME2(3)    NOT NULL CONSTRAINT df_ss_scored DEFAULT SYSUTCDATETIME(),
    CONSTRAINT ck_ss_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uq_ss_request UNIQUE (request_id)
);
