-- Sprint admin: soft-archive for purchase requests (GLOBAL_ADMIN request administration).
-- Archive removes a request from default active lists while preserving history and the business number.
-- Hard delete remains DRAFT-only via application rules; cancelled/committed numbers are never reused.

ALTER TABLE purchase_request ADD
    archived_at DATETIME2(3) NULL,
    archived_by_user_id BIGINT NULL
        CONSTRAINT fk_purchase_request_archived_by REFERENCES user_account (id),
    archive_reason NVARCHAR(1000) NULL;
GO

ALTER TABLE purchase_request ADD CONSTRAINT ck_purchase_request_archive CHECK (
    (archived_at IS NULL AND archived_by_user_id IS NULL AND archive_reason IS NULL)
    OR (archived_at IS NOT NULL AND archived_by_user_id IS NOT NULL)
);
GO

CREATE INDEX ix_purchase_request_group_archived ON purchase_request (group_id, archived_at)
    WHERE archived_at IS NOT NULL;
GO
