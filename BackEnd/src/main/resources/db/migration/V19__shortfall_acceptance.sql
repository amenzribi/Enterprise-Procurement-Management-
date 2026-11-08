-- A purchasing manager accepts delivered goods with a shortfall or rejected quantity, with a recorded reason.
-- Without it a request with any rejected unit could never reach ACCEPTED (stabilization review D-01).
CREATE SEQUENCE seq_shortfall_acceptance AS BIGINT START WITH 1 INCREMENT BY 50;

CREATE TABLE shortfall_acceptance (
    id                   BIGINT         NOT NULL CONSTRAINT pk_shortfall_acceptance PRIMARY KEY
                                        CONSTRAINT df_shortfall_acceptance_id DEFAULT (NEXT VALUE FOR seq_shortfall_acceptance),
    request_id           BIGINT         NOT NULL CONSTRAINT fk_shortfall_request REFERENCES purchase_request (id),
    accepted_by_user_id  BIGINT         NOT NULL CONSTRAINT fk_shortfall_user REFERENCES user_account (id),
    accepted_at          DATETIME2(3)   NOT NULL,
    reason               NVARCHAR(2000) NOT NULL,
    CONSTRAINT uq_shortfall_request UNIQUE (request_id)
);
