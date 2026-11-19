-- Account e-mail correction by a global administrator (Sprint 2, ADR-016 step 5; owner approval 2026-11-18).
--
-- A change never takes effect on request: it waits for a second global administrator when the account holds a
-- sensitive or administrator role, then for proof of control of the new mailbox (a single-use, expiring link sent
-- there). Only then is user_account.email updated, outstanding set-up and reset links expired and sessions ended.
-- The token is stored as a SHA-256 hash, created when the e-mail is sent (never in the outbox).
--
-- Additive. Rollback: drop the table and its sequence.

CREATE SEQUENCE seq_email_change_request AS BIGINT START WITH 1 INCREMENT BY 1;

CREATE TABLE email_change_request (
    id                    BIGINT        NOT NULL CONSTRAINT pk_email_change_request PRIMARY KEY
                                        CONSTRAINT df_email_change_request_id DEFAULT (NEXT VALUE FOR seq_email_change_request),
    user_id               BIGINT        NOT NULL CONSTRAINT fk_email_change_user REFERENCES user_account (id),
    old_email             NVARCHAR(320) NOT NULL,
    new_email             NVARCHAR(320) NOT NULL,
    reason                NVARCHAR(500) NOT NULL,
    status                VARCHAR(30)   NOT NULL,
    requested_by_user_id  BIGINT        NOT NULL CONSTRAINT fk_email_change_requested_by REFERENCES user_account (id),
    requested_at          DATETIME2(3)  NOT NULL,
    approved_by_user_id   BIGINT        NULL CONSTRAINT fk_email_change_approved_by REFERENCES user_account (id),
    approved_at           DATETIME2(3)  NULL,
    token_hash            VARCHAR(64)   NULL,
    token_expires_at      DATETIME2(3)  NULL,
    closed_at             DATETIME2(3)  NULL,
    closed_by_user_id     BIGINT        NULL CONSTRAINT fk_email_change_closed_by REFERENCES user_account (id),
    CONSTRAINT ck_email_change_status CHECK (status IN ('PENDING_APPROVAL', 'PENDING_VERIFICATION', 'CONFIRMED',
                                                        'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_email_change_differs CHECK (new_email <> old_email)
);

-- One open change per account.
CREATE UNIQUE INDEX uq_email_change_open ON email_change_request (user_id)
    WHERE status IN ('PENDING_APPROVAL', 'PENDING_VERIFICATION');

CREATE UNIQUE INDEX uq_email_change_token ON email_change_request (token_hash) WHERE token_hash IS NOT NULL;
