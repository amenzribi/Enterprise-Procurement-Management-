-- QA-001: acceptance rules depend on whether a line is goods or a service. Existing lines are goods: they must go
-- through shipment and goods receipt unless someone reclassifies them on purpose.
ALTER TABLE purchase_request_line ADD line_kind VARCHAR(10) NOT NULL
    CONSTRAINT df_purchase_request_line_kind DEFAULT 'GOODS'
    CONSTRAINT ck_purchase_request_line_kind CHECK (line_kind IN ('GOODS', 'SERVICE'));
