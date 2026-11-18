-- Who created an account (Sprint 1 remediation, review findings R-1 and R-2).
--
-- A company administrator must not be able to create an account and then, alone, give it approval authority or
-- name it delegate of an approver. The application compares the creator and the creation date of an account with
-- the administrator who acts; the creation date already exists (created_at), the creator did not.
--
-- NULL for every account that exists today and for accounts not created through user administration (demo seed,
-- first administrator, supplier contacts): for those the rule does not apply.
--
-- Additive. Rollback: drop the index, the constraint and the column.

ALTER TABLE user_account ADD created_by_user_id BIGINT NULL
    CONSTRAINT fk_user_account_created_by REFERENCES user_account (id);

CREATE INDEX ix_user_account_created_by ON user_account (created_by_user_id);
