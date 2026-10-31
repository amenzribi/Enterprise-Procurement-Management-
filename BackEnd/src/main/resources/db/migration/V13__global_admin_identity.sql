-- Sprint 10: GLOBAL_ADMIN identity — credentials version, must-change, password reset tokens, group-wide role.

ALTER TABLE user_account ADD credentials_version INT NOT NULL
    CONSTRAINT df_user_account_credentials_version DEFAULT 1;

ALTER TABLE user_credential ADD must_change_password BIT NOT NULL
    CONSTRAINT df_user_credential_must_change DEFAULT 0;

CREATE SEQUENCE seq_password_reset_token AS BIGINT START WITH 1 INCREMENT BY 50;

CREATE TABLE password_reset_token (
    id           BIGINT        NOT NULL CONSTRAINT pk_password_reset_token PRIMARY KEY
                               CONSTRAINT df_password_reset_token_id DEFAULT (NEXT VALUE FOR seq_password_reset_token),
    user_id      BIGINT        NOT NULL CONSTRAINT fk_password_reset_token_user REFERENCES user_account (id),
    token_hash   VARCHAR(64)   NOT NULL,
    purpose      NVARCHAR(20)  NOT NULL,
    expires_at   DATETIME2(3)  NOT NULL,
    used_at      DATETIME2(3)  NULL,
    created_at   DATETIME2(3)  NOT NULL CONSTRAINT df_password_reset_token_created DEFAULT SYSUTCDATETIME(),
    created_by   BIGINT        NULL CONSTRAINT fk_password_reset_token_actor REFERENCES user_account (id),
    CONSTRAINT ck_password_reset_purpose CHECK (purpose IN ('SETUP', 'RESET')),
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_password_reset_token_user ON password_reset_token (user_id, used_at, expires_at);

-- Allow GLOBAL_ADMIN as a group-wide grant (company_id NULL), same class as ADMIN.
ALTER TABLE user_role DROP CONSTRAINT ck_user_role_group_wide;
ALTER TABLE user_role ADD CONSTRAINT ck_user_role_group_wide CHECK (
    company_id IS NOT NULL
    OR role IN ('GROUP_MANAGEMENT', 'ADMIN', 'AUDITOR', 'GLOBAL_ADMIN')
);

-- One-shot production bootstrap marker (never stores a password).
CREATE TABLE identity_bootstrap (
    id           TINYINT       NOT NULL CONSTRAINT pk_identity_bootstrap PRIMARY KEY,
    completed_at DATETIME2(3)  NOT NULL,
    actor_label  NVARCHAR(200) NOT NULL,
    CONSTRAINT ck_identity_bootstrap_singleton CHECK (id = 1)
);
