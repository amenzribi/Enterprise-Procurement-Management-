-- Sprint 11: contextual buyer–supplier messaging and supplier contact identity (ADR-011).

ALTER TABLE user_role DROP CONSTRAINT ck_user_role_group_wide;
ALTER TABLE user_role ADD CONSTRAINT ck_user_role_group_wide CHECK (
    company_id IS NOT NULL
    OR role IN ('GROUP_MANAGEMENT', 'ADMIN', 'AUDITOR', 'GLOBAL_ADMIN', 'SUPPLIER_CONTACT')
);

CREATE SEQUENCE seq_supplier_contact AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_conversation AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_conversation_message AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_conversation_participant AS BIGINT START WITH 1 INCREMENT BY 50;

CREATE TABLE supplier_contact (
    id               BIGINT         NOT NULL CONSTRAINT pk_supplier_contact PRIMARY KEY
                                    CONSTRAINT df_supplier_contact_id DEFAULT (NEXT VALUE FOR seq_supplier_contact),
    supplier_id      BIGINT         NOT NULL CONSTRAINT fk_supplier_contact_supplier REFERENCES supplier (id),
    user_id          BIGINT         NOT NULL CONSTRAINT fk_supplier_contact_user REFERENCES user_account (id),
    display_label    NVARCHAR(200)  NOT NULL,
    invited_by_user_id BIGINT       NULL CONSTRAINT fk_supplier_contact_inviter REFERENCES user_account (id),
    invited_at       DATETIME2(3)   NOT NULL CONSTRAINT df_supplier_contact_invited DEFAULT SYSUTCDATETIME(),
    revoked_at       DATETIME2(3)   NULL,
    revoked_by_user_id BIGINT       NULL CONSTRAINT fk_supplier_contact_revoker REFERENCES user_account (id),
    is_active        BIT            NOT NULL CONSTRAINT df_supplier_contact_active DEFAULT 1,
    CONSTRAINT uq_supplier_contact_user UNIQUE (supplier_id, user_id)
);
CREATE INDEX ix_supplier_contact_user ON supplier_contact (user_id, is_active);

CREATE TABLE conversation (
    id                   BIGINT         NOT NULL CONSTRAINT pk_conversation PRIMARY KEY
                                        CONSTRAINT df_conversation_id DEFAULT (NEXT VALUE FOR seq_conversation),
    group_id             BIGINT         NOT NULL CONSTRAINT fk_conversation_group REFERENCES organization_group (id),
    buying_company_id    BIGINT         NOT NULL CONSTRAINT fk_conversation_buying REFERENCES company (id),
    supplier_id          BIGINT         NOT NULL CONSTRAINT fk_conversation_supplier REFERENCES supplier (id),
    context_type         VARCHAR(10)    NOT NULL,
    context_id           BIGINT         NOT NULL,
    request_id           BIGINT         NOT NULL,
    status               VARCHAR(10)    NOT NULL,
    created_by_user_id   BIGINT         NOT NULL CONSTRAINT fk_conversation_creator REFERENCES user_account (id),
    created_at           DATETIME2(3)   NOT NULL CONSTRAINT df_conversation_created DEFAULT SYSUTCDATETIME(),
    closed_at            DATETIME2(3)   NULL,
    version              INT            NOT NULL CONSTRAINT df_conversation_version DEFAULT 0,
    CONSTRAINT ck_conversation_context CHECK (context_type IN ('RFQ', 'PO')),
    CONSTRAINT ck_conversation_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT uq_conversation_context_supplier UNIQUE (context_type, context_id, supplier_id)
);
CREATE INDEX ix_conversation_buying ON conversation (buying_company_id, status);
CREATE INDEX ix_conversation_supplier ON conversation (supplier_id, status);
CREATE INDEX ix_conversation_request ON conversation (request_id);

CREATE TABLE conversation_participant (
    id                   BIGINT         NOT NULL CONSTRAINT pk_conversation_participant PRIMARY KEY
                                        CONSTRAINT df_conversation_participant_id DEFAULT (NEXT VALUE FOR seq_conversation_participant),
    conversation_id      BIGINT         NOT NULL CONSTRAINT fk_conversation_participant_conv REFERENCES conversation (id),
    user_id              BIGINT         NOT NULL CONSTRAINT fk_conversation_participant_user REFERENCES user_account (id),
    participant_kind     VARCHAR(20)    NOT NULL,
    last_read_message_id BIGINT         NULL,
    joined_at            DATETIME2(3)   NOT NULL CONSTRAINT df_conversation_participant_joined DEFAULT SYSUTCDATETIME(),
    CONSTRAINT ck_conversation_participant_kind CHECK (participant_kind IN ('INTERNAL', 'SUPPLIER')),
    CONSTRAINT uq_conversation_participant UNIQUE (conversation_id, user_id)
);
CREATE INDEX ix_conversation_participant_user ON conversation_participant (user_id);

CREATE TABLE conversation_message (
    id                   BIGINT         NOT NULL CONSTRAINT pk_conversation_message PRIMARY KEY
                                        CONSTRAINT df_conversation_message_id DEFAULT (NEXT VALUE FOR seq_conversation_message),
    conversation_id      BIGINT         NOT NULL CONSTRAINT fk_conversation_message_conv REFERENCES conversation (id),
    audience             VARCHAR(10)    NOT NULL,
    body_text            NVARCHAR(4000) NOT NULL,
    sender_user_id       BIGINT         NOT NULL CONSTRAINT fk_conversation_message_sender REFERENCES user_account (id),
    client_message_id    VARCHAR(100)   NULL,
    created_at           DATETIME2(3)   NOT NULL CONSTRAINT df_conversation_message_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT ck_conversation_message_audience CHECK (audience IN ('INTERNAL', 'EXTERNAL')),
    CONSTRAINT ck_conversation_message_body CHECK (LEN(body_text) > 0)
);
CREATE UNIQUE INDEX uq_conversation_message_client
    ON conversation_message (conversation_id, client_message_id)
    WHERE client_message_id IS NOT NULL;
CREATE INDEX ix_conversation_message_conv ON conversation_message (conversation_id, id);
