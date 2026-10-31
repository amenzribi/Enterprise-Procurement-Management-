-- Sprint 12: internal assistant conversation history (ADR-012). No model credentials stored.

CREATE SEQUENCE seq_assistant_thread AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE seq_assistant_turn AS BIGINT START WITH 1 INCREMENT BY 50;

CREATE TABLE assistant_thread (
    id              BIGINT         NOT NULL CONSTRAINT pk_assistant_thread PRIMARY KEY
                                   CONSTRAINT df_assistant_thread_id DEFAULT (NEXT VALUE FOR seq_assistant_thread),
    user_id         BIGINT         NOT NULL CONSTRAINT fk_assistant_thread_user REFERENCES user_account (id),
    company_id      BIGINT         NOT NULL CONSTRAINT fk_assistant_thread_company REFERENCES company (id),
    title           NVARCHAR(200)  NULL,
    created_at      DATETIME2(3)   NOT NULL CONSTRAINT df_assistant_thread_created DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2(3)   NOT NULL CONSTRAINT df_assistant_thread_updated DEFAULT SYSUTCDATETIME()
);
CREATE INDEX ix_assistant_thread_user ON assistant_thread (user_id, updated_at);

CREATE TABLE assistant_turn (
    id              BIGINT         NOT NULL CONSTRAINT pk_assistant_turn PRIMARY KEY
                                   CONSTRAINT df_assistant_turn_id DEFAULT (NEXT VALUE FOR seq_assistant_turn),
    thread_id       BIGINT         NOT NULL CONSTRAINT fk_assistant_turn_thread REFERENCES assistant_thread (id),
    role            VARCHAR(16)    NOT NULL,
    body_text       NVARCHAR(MAX)  NOT NULL,
    mode            VARCHAR(16)    NOT NULL,
    sources_json    NVARCHAR(MAX)  NULL,
    draft_json      NVARCHAR(MAX)  NULL,
    duration_ms     INT            NULL,
    created_at      DATETIME2(3)   NOT NULL CONSTRAINT df_assistant_turn_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT ck_assistant_turn_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_assistant_turn_mode CHECK (mode IN ('DISABLED', 'DEMO', 'LIVE', 'UNAVAILABLE'))
);
CREATE INDEX ix_assistant_turn_thread ON assistant_turn (thread_id, id);
