-- D-10: purchasing confirms each service line before an order can be awarded, so a requester's classification alone
-- never decides that goods skip receipt.
ALTER TABLE purchase_request_line ADD
    service_confirmed_by_user_id BIGINT NULL CONSTRAINT fk_purchase_request_line_service_confirmer REFERENCES user_account (id),
    service_confirmed_at DATETIME2(3) NULL;
