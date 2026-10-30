-- Sprint 6: shipments, milestones (local/international), transport document references.

CREATE SEQUENCE seq_shipment AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_shipment_line AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_shipment_milestone AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_shipment_document AS BIGINT START WITH 1 INCREMENT BY 50;

CREATE TABLE shipment (
    id                    BIGINT          NOT NULL CONSTRAINT pk_shipment PRIMARY KEY
                                          CONSTRAINT df_shipment_id DEFAULT (NEXT VALUE FOR seq_shipment),
    group_id              BIGINT          NOT NULL CONSTRAINT fk_shipment_group REFERENCES organization_group (id),
    request_id            BIGINT          NOT NULL CONSTRAINT fk_shipment_request REFERENCES purchase_request (id),
    purchase_order_id     BIGINT          NOT NULL CONSTRAINT fk_shipment_po REFERENCES purchase_order (id),
    number                NVARCHAR(40)    NOT NULL,
    kind                  VARCHAR(20)     NOT NULL,
    status                VARCHAR(20)     NOT NULL,
    buying_company_id     BIGINT          NOT NULL CONSTRAINT fk_shipment_buying REFERENCES company (id),
    carrier_name          NVARCHAR(200)   NULL,
    tracking_reference    NVARCHAR(100)   NULL,
    origin_place          NVARCHAR(200)   NULL,
    destination_place     NVARCHAR(200)   NULL,
    created_by_user_id    BIGINT          NOT NULL CONSTRAINT fk_shipment_creator REFERENCES user_account (id),
    created_at            DATETIME2(3)    NOT NULL CONSTRAINT df_shipment_created DEFAULT SYSUTCDATETIME(),
    version               INT             NOT NULL CONSTRAINT df_shipment_version DEFAULT 0,
    CONSTRAINT ck_shipment_kind CHECK (kind IN ('LOCAL', 'INTERNATIONAL')),
    CONSTRAINT ck_shipment_status CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT uq_shipment_number UNIQUE (buying_company_id, number)
);
CREATE INDEX ix_shipment_request ON shipment (request_id, status);
CREATE INDEX ix_shipment_po ON shipment (purchase_order_id);

CREATE TABLE shipment_line (
    id                    BIGINT          NOT NULL CONSTRAINT pk_shipment_line PRIMARY KEY
                                          CONSTRAINT df_shipment_line_id DEFAULT (NEXT VALUE FOR seq_shipment_line),
    shipment_id           BIGINT          NOT NULL CONSTRAINT fk_shipment_line_shipment REFERENCES shipment (id),
    purchase_order_line_id BIGINT         NOT NULL CONSTRAINT fk_shipment_line_po_line REFERENCES purchase_order_line (id),
    request_line_id       BIGINT          NOT NULL CONSTRAINT fk_shipment_line_request_line REFERENCES purchase_request_line (id),
    line_no               SMALLINT        NOT NULL,
    description           NVARCHAR(500)   NOT NULL,
    quantity              DECIMAL(19,4)   NOT NULL,
    unit                  NVARCHAR(20)    NOT NULL,
    CONSTRAINT uq_shipment_line_no UNIQUE (shipment_id, line_no),
    CONSTRAINT ck_shipment_line_qty CHECK (quantity > 0)
);

CREATE TABLE shipment_milestone (
    id                    BIGINT          NOT NULL CONSTRAINT pk_shipment_milestone PRIMARY KEY
                                          CONSTRAINT df_shipment_milestone_id DEFAULT (NEXT VALUE FOR seq_shipment_milestone),
    shipment_id           BIGINT          NOT NULL CONSTRAINT fk_shipment_milestone_shipment REFERENCES shipment (id),
    code                  VARCHAR(40)     NOT NULL,
    sort_order            SMALLINT        NOT NULL,
    planned_date          DATE            NULL,
    actual_date           DATE            NULL,
    note                  NVARCHAR(1000)  NULL,
    CONSTRAINT uq_shipment_milestone_code UNIQUE (shipment_id, code)
);
CREATE INDEX ix_shipment_milestone_shipment ON shipment_milestone (shipment_id, sort_order);

CREATE TABLE shipment_document (
    id                    BIGINT          NOT NULL CONSTRAINT pk_shipment_document PRIMARY KEY
                                          CONSTRAINT df_shipment_document_id DEFAULT (NEXT VALUE FOR seq_shipment_document),
    shipment_id           BIGINT          NOT NULL CONSTRAINT fk_shipment_document_shipment REFERENCES shipment (id),
    kind                  VARCHAR(40)     NOT NULL,
    reference_number      NVARCHAR(100)   NULL,
    note                  NVARCHAR(1000)  NULL,
    storage_key           VARCHAR(64)     NULL,
    file_name             NVARCHAR(255)   NULL,
    content_type          NVARCHAR(100)   NULL,
    size_bytes            BIGINT          NULL,
    uploaded_by_user_id   BIGINT          NOT NULL CONSTRAINT fk_shipment_document_user REFERENCES user_account (id),
    uploaded_at           DATETIME2(3)    NOT NULL CONSTRAINT df_shipment_document_uploaded DEFAULT SYSUTCDATETIME(),
    CONSTRAINT ck_shipment_document_kind CHECK (kind IN (
        'COMMERCIAL_INVOICE', 'PACKING_LIST', 'CERTIFICATE_OF_ORIGIN', 'BILL_OF_LADING', 'AIR_WAYBILL',
        'CUSTOMS', 'DELIVERY_NOTE', 'CMR', 'OTHER'))
);
CREATE INDEX ix_shipment_document_shipment ON shipment_document (shipment_id);
