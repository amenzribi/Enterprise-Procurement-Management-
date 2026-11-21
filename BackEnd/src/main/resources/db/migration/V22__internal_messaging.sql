-- Internal employee messaging (docs/architecture/internal-messaging.md): one-to-one conversations between employees
-- of one company, and one discussion per purchase request. New tables only; the supplier conversation tables of V14
-- (conversation, conversation_participant, conversation_message) are not touched, so the INTERNAL/EXTERNAL boundary of
-- supplier messaging stays as it is. Existing users and requests need no backfill: a conversation exists only once
-- someone starts it.
--
-- Rollback before any message exists: drop the three tables and three sequences. After messages exist, dropping
-- them deletes employee correspondence; retention is undecided, so that needs the owner's decision.

CREATE SEQUENCE seq_internal_conversation AS BIGINT START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_internal_participant AS BIGINT START WITH 1 INCREMENT BY 1;
-- Messages are ordered by id: a single sequence gives a strictly increasing, unique tie-breaker after created_at.
CREATE SEQUENCE seq_internal_message AS BIGINT START WITH 1 INCREMENT BY 1;

CREATE TABLE internal_conversation (
    id                    BIGINT        NOT NULL CONSTRAINT pk_internal_conversation PRIMARY KEY
                                        CONSTRAINT df_internal_conversation_id DEFAULT (NEXT VALUE FOR seq_internal_conversation),
    -- DIRECT: the company of both employees. REQUEST: the requesting company of the request.
    company_id            BIGINT        NOT NULL CONSTRAINT fk_internal_conversation_company REFERENCES company (id),
    kind                  VARCHAR(10)   NOT NULL,
    request_id            BIGINT        NULL CONSTRAINT fk_internal_conversation_request REFERENCES purchase_request (id),
    -- DIRECT only: the two users in ascending id order, so a pair maps to exactly one conversation.
    user_low_id           BIGINT        NULL CONSTRAINT fk_internal_conversation_low REFERENCES user_account (id),
    user_high_id          BIGINT        NULL CONSTRAINT fk_internal_conversation_high REFERENCES user_account (id),
    created_by_user_id    BIGINT        NOT NULL CONSTRAINT fk_internal_conversation_creator REFERENCES user_account (id),
    created_at            DATETIME2(3)  NOT NULL CONSTRAINT df_internal_conversation_created DEFAULT SYSUTCDATETIME(),
    last_message_id       BIGINT        NULL,
    last_message_at       DATETIME2(3)  NULL,
    CONSTRAINT ck_internal_conversation_kind CHECK (kind IN ('DIRECT', 'REQUEST')),
    CONSTRAINT ck_internal_conversation_shape CHECK (
        (kind = 'DIRECT' AND request_id IS NULL AND user_low_id IS NOT NULL AND user_high_id IS NOT NULL
            AND user_low_id < user_high_id)
        OR (kind = 'REQUEST' AND request_id IS NOT NULL AND user_low_id IS NULL AND user_high_id IS NULL))
);
-- Concurrent "open conversation" calls race on these indexes: the loser reads the winner's row.
CREATE UNIQUE INDEX uq_internal_conversation_pair ON internal_conversation (company_id, user_low_id, user_high_id)
    WHERE kind = 'DIRECT';
CREATE UNIQUE INDEX uq_internal_conversation_request ON internal_conversation (request_id) WHERE kind = 'REQUEST';
CREATE INDEX ix_internal_conversation_high ON internal_conversation (user_high_id) WHERE kind = 'DIRECT';
CREATE INDEX ix_internal_conversation_creator ON internal_conversation (created_by_user_id);

CREATE TABLE internal_participant (
    id                    BIGINT        NOT NULL CONSTRAINT pk_internal_participant PRIMARY KEY
                                        CONSTRAINT df_internal_participant_id DEFAULT (NEXT VALUE FOR seq_internal_participant),
    conversation_id       BIGINT        NOT NULL CONSTRAINT fk_internal_participant_conversation REFERENCES internal_conversation (id),
    user_id               BIGINT        NOT NULL CONSTRAINT fk_internal_participant_user REFERENCES user_account (id),
    added_by_user_id      BIGINT        NULL CONSTRAINT fk_internal_participant_added_by REFERENCES user_account (id),
    joined_at             DATETIME2(3)  NOT NULL CONSTRAINT df_internal_participant_joined DEFAULT SYSUTCDATETIME(),
    -- Reserved for a later "leave" action; this release never sets it, and every query requires it to be NULL.
    removed_at            DATETIME2(3)  NULL,
    -- Highest message id this participant has displayed. Only ever moves forward.
    last_read_message_id  BIGINT        NULL,
    CONSTRAINT uq_internal_participant UNIQUE (conversation_id, user_id)
);
CREATE INDEX ix_internal_participant_user ON internal_participant (user_id, conversation_id) WHERE removed_at IS NULL;
CREATE INDEX ix_internal_participant_added_by ON internal_participant (added_by_user_id);

CREATE TABLE internal_message (
    id                    BIGINT         NOT NULL CONSTRAINT pk_internal_message PRIMARY KEY
                                         CONSTRAINT df_internal_message_id DEFAULT (NEXT VALUE FOR seq_internal_message),
    conversation_id       BIGINT         NOT NULL CONSTRAINT fk_internal_message_conversation REFERENCES internal_conversation (id),
    -- TEXT: written by a person. SYSTEM: a participant change, written by the application (body is a code).
    kind                  VARCHAR(10)    NOT NULL,
    sender_user_id        BIGINT         NOT NULL CONSTRAINT fk_internal_message_sender REFERENCES user_account (id),
    body_text             NVARCHAR(4000) NOT NULL,
    -- A request card. It grants nothing: access is checked again whenever the card is shown or opened.
    linked_request_id     BIGINT         NULL CONSTRAINT fk_internal_message_request REFERENCES purchase_request (id),
    -- Chosen by the browser; a retry with the same value returns the stored message instead of a second one.
    client_message_id     VARCHAR(64)    NOT NULL,
    created_at            DATETIME2(3)   NOT NULL CONSTRAINT df_internal_message_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT ck_internal_message_kind CHECK (kind IN ('TEXT', 'SYSTEM')),
    CONSTRAINT ck_internal_message_body CHECK (LEN(body_text) > 0)
);
CREATE UNIQUE INDEX uq_internal_message_client ON internal_message (conversation_id, sender_user_id, client_message_id);
CREATE INDEX ix_internal_message_conversation ON internal_message (conversation_id, id);
CREATE INDEX ix_internal_message_sender ON internal_message (sender_user_id);
CREATE INDEX ix_internal_message_request ON internal_message (linked_request_id) WHERE linked_request_id IS NOT NULL;
