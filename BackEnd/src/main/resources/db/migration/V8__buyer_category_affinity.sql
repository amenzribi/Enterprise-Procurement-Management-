-- Sprint 3 closure: optional category affinity for buyer suggestion (S3-AC-03).
-- When no row is recorded for a category, suggestion falls back to lowest open workload.

CREATE TABLE buyer_category_affinity (
    buyer_user_id BIGINT NOT NULL CONSTRAINT fk_buyer_affinity_user REFERENCES user_account (id),
    category_id   BIGINT NOT NULL CONSTRAINT fk_buyer_affinity_category REFERENCES request_category (id),
    company_id    BIGINT NOT NULL CONSTRAINT fk_buyer_affinity_company REFERENCES company (id),
    CONSTRAINT pk_buyer_category_affinity PRIMARY KEY (buyer_user_id, category_id, company_id)
);
CREATE INDEX ix_buyer_affinity_category ON buyer_category_affinity (company_id, category_id);
