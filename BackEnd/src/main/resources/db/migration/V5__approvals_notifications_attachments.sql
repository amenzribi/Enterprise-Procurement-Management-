-- Sprint 2: Arabic, approval rules and circuits, exchange rates, notifications, attachments.
-- See docs/product/sprint-2.md (design review outcomes S2DR-01..17, decisions on QA questions).

-- Languages: Arabic joins English and French (S2-AC-01, S2DR-13) --------------------------------------------------

ALTER TABLE company DROP CONSTRAINT ck_company_language;
ALTER TABLE company ADD CONSTRAINT ck_company_language CHECK (default_language IN ('en', 'fr', 'ar'));
ALTER TABLE user_account DROP CONSTRAINT ck_user_account_language;
ALTER TABLE user_account ADD CONSTRAINT ck_user_account_language CHECK (preferred_language IN ('en', 'fr', 'ar'));

-- Working week of a company, used for approval SLAs in the company's time zone (S2DR-10).
ALTER TABLE company ADD
    work_days   VARCHAR(30) NOT NULL CONSTRAINT df_company_work_days DEFAULT 'MON,TUE,WED,THU,FRI',
    work_start  TIME(0)     NOT NULL CONSTRAINT df_company_work_start DEFAULT '08:00',
    work_end    TIME(0)     NOT NULL CONSTRAINT df_company_work_end DEFAULT '17:00';
GO
ALTER TABLE company ADD CONSTRAINT ck_company_work_hours CHECK (work_end > work_start);

-- Requests: AWARDED status, approval basis, participants ----------------------------------------------------------

ALTER TABLE purchase_request DROP CONSTRAINT ck_purchase_request_status;
ALTER TABLE purchase_request ADD CONSTRAINT ck_purchase_request_status CHECK (status IN (
    'DRAFT', 'CREATED', 'IN_APPROVAL', 'REVISION_REQUESTED', 'REJECTED', 'APPROVED', 'IN_PURCHASING_REVIEW',
    'ASSIGNED_TO_BUYER', 'SOURCING', 'QUOTES_RECEIVED', 'TECHNICAL_VALIDATION', 'COMMERCIAL_VALIDATION',
    'MANAGEMENT_VALIDATION', 'AWARDED', 'ORDERED', 'CONTRACT_SIGNED', 'ORDER_CONFIRMED', 'IN_PRODUCTION', 'READY',
    'SHIPPED', 'AT_PORT', 'IN_CUSTOMS', 'IN_WAREHOUSE', 'DELIVERED', 'PARTIALLY_RECEIVED', 'NON_CONFORMING',
    'ACCEPTED', 'INVOICED', 'PAYMENT_PENDING', 'PAID', 'CLOSED', 'CANCELLED'));

-- Amount used to choose the approval rule, in the requesting company's currency, with the rate that produced it
-- (S2-AC-03). Rewritten at each submission round.
ALTER TABLE purchase_request ADD
    approval_amount     DECIMAL(19,4) NULL,
    approval_currency   CHAR(3)       NULL CONSTRAINT fk_purchase_request_approval_currency REFERENCES currency (code),
    approval_rate       DECIMAL(19,8) NULL,
    approval_rate_date  DATE          NULL,
    approval_rule_id    BIGINT        NULL,
    round_no            INT           NOT NULL CONSTRAINT df_purchase_request_round_no DEFAULT 0;
GO

-- Users who take part in a request beyond its authors (approvers, delegates). Request visibility includes them
-- (S2-AC-13): an approver sees a request while they have or had a task on it.
CREATE TABLE purchase_request_participant (
    request_id  BIGINT       NOT NULL CONSTRAINT fk_purchase_request_participant_request REFERENCES purchase_request (id) ON DELETE CASCADE,
    user_id     BIGINT       NOT NULL CONSTRAINT fk_purchase_request_participant_user REFERENCES user_account (id),
    reason      VARCHAR(30)  NOT NULL,
    added_at    DATETIME2(3) NOT NULL CONSTRAINT df_purchase_request_participant_added DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_purchase_request_participant PRIMARY KEY (request_id, user_id)
);
CREATE INDEX ix_purchase_request_participant_user ON purchase_request_participant (user_id, request_id);

-- Exchange rates (S2-AC-03, S2DR-06) --------------------------------------------------------------------------------

CREATE SEQUENCE seq_exchange_rate AS BIGINT START WITH 1 INCREMENT BY 1;

-- rate = units of quote currency per 1 unit of base currency. Rows are immutable: a correction is a new row; the
-- latest row (by rate_date, then created_at) on or before the date applies.
CREATE TABLE exchange_rate (
    id              BIGINT         NOT NULL CONSTRAINT pk_exchange_rate PRIMARY KEY
                                   CONSTRAINT df_exchange_rate_id DEFAULT (NEXT VALUE FOR seq_exchange_rate),
    group_id        BIGINT         NOT NULL CONSTRAINT fk_exchange_rate_group REFERENCES organization_group (id),
    base_currency   CHAR(3)        NOT NULL CONSTRAINT fk_exchange_rate_base REFERENCES currency (code),
    quote_currency  CHAR(3)        NOT NULL CONSTRAINT fk_exchange_rate_quote REFERENCES currency (code),
    rate            DECIMAL(19,8)  NOT NULL,
    rate_date       DATE           NOT NULL,
    source          NVARCHAR(60)   NOT NULL,
    created_by      BIGINT         NULL CONSTRAINT fk_exchange_rate_created_by REFERENCES user_account (id),
    created_at      DATETIME2(3)   NOT NULL CONSTRAINT df_exchange_rate_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT ck_exchange_rate_positive CHECK (rate > 0),
    CONSTRAINT ck_exchange_rate_pair CHECK (base_currency <> quote_currency)
);
CREATE INDEX ix_exchange_rate_lookup ON exchange_rate (group_id, base_currency, quote_currency, rate_date, created_at);

-- Approval rules (S2-AC-02, S2DR-05) ---------------------------------------------------------------------------------

CREATE SEQUENCE seq_approval_rule       AS BIGINT START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_approval_rule_stage AS BIGINT START WITH 1 INCREMENT BY 1;

-- One row per rule version. rule_key groups the versions of one rule; at most one version per key is ACTIVE.
-- Band is [min_amount, max_amount) in the company's currency; max_amount NULL = no upper bound.
CREATE TABLE approval_rule (
    id             BIGINT         NOT NULL CONSTRAINT pk_approval_rule PRIMARY KEY
                                  CONSTRAINT df_approval_rule_id DEFAULT (NEXT VALUE FOR seq_approval_rule),
    group_id       BIGINT         NOT NULL CONSTRAINT fk_approval_rule_group REFERENCES organization_group (id),
    company_id     BIGINT         NOT NULL,
    rule_key       BIGINT         NOT NULL,
    version_no     INT            NOT NULL,
    name           NVARCHAR(150)  NOT NULL,
    category_id    BIGINT         NULL,
    purchase_type  VARCHAR(20)    NULL,
    min_amount     DECIMAL(19,4)  NOT NULL CONSTRAINT df_approval_rule_min DEFAULT 0,
    max_amount     DECIMAL(19,4)  NULL,
    status         VARCHAR(10)    NOT NULL,
    created_by     BIGINT         NULL CONSTRAINT fk_approval_rule_created_by REFERENCES user_account (id),
    created_at     DATETIME2(3)   NOT NULL CONSTRAINT df_approval_rule_created_at DEFAULT SYSUTCDATETIME(),
    activated_at   DATETIME2(3)   NULL,
    CONSTRAINT fk_approval_rule_company FOREIGN KEY (group_id, company_id) REFERENCES company (group_id, id),
    CONSTRAINT fk_approval_rule_category FOREIGN KEY (group_id, category_id) REFERENCES request_category (group_id, id),
    CONSTRAINT uq_approval_rule_version UNIQUE (rule_key, version_no),
    CONSTRAINT ck_approval_rule_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_approval_rule_purchase_type CHECK (purchase_type IS NULL OR purchase_type IN ('LOCAL', 'INTERNATIONAL')),
    CONSTRAINT ck_approval_rule_band CHECK (min_amount >= 0 AND (max_amount IS NULL OR max_amount > min_amount))
);
CREATE UNIQUE INDEX uq_approval_rule_active_key ON approval_rule (rule_key) WHERE status = 'ACTIVE';
CREATE INDEX ix_approval_rule_company ON approval_rule (company_id, status);

CREATE TABLE approval_rule_stage (
    id                BIGINT         NOT NULL CONSTRAINT pk_approval_rule_stage PRIMARY KEY
                                     CONSTRAINT df_approval_rule_stage_id DEFAULT (NEXT VALUE FOR seq_approval_rule_stage),
    rule_id           BIGINT         NOT NULL CONSTRAINT fk_approval_rule_stage_rule REFERENCES approval_rule (id) ON DELETE CASCADE,
    stage_no          SMALLINT       NOT NULL,
    name              NVARCHAR(100)  NOT NULL,
    approver_type     VARCHAR(30)    NOT NULL,
    approver_user_id  BIGINT         NULL CONSTRAINT fk_approval_rule_stage_user REFERENCES user_account (id),
    policy            VARCHAR(3)     NOT NULL,
    sla_hours         SMALLINT       NOT NULL,
    escalation_role   VARCHAR(40)    NULL,
    min_amount        DECIMAL(19,4)  NULL,
    CONSTRAINT uq_approval_rule_stage_no UNIQUE (rule_id, stage_no),
    CONSTRAINT ck_approval_rule_stage_no CHECK (stage_no >= 1),
    CONSTRAINT ck_approval_rule_stage_type CHECK (approver_type IN ('DEPARTMENT_MANAGER', 'BUDGET_APPROVER',
        'MANAGEMENT_APPROVER', 'NAMED_USER')),
    CONSTRAINT ck_approval_rule_stage_named CHECK ((approver_type = 'NAMED_USER' AND approver_user_id IS NOT NULL)
        OR (approver_type <> 'NAMED_USER' AND approver_user_id IS NULL)),
    CONSTRAINT ck_approval_rule_stage_policy CHECK (policy IN ('ANY', 'ALL')),
    CONSTRAINT ck_approval_rule_stage_sla CHECK (sla_hours BETWEEN 1 AND 720),
    CONSTRAINT ck_approval_rule_stage_escalation CHECK (escalation_role IS NULL OR escalation_role IN (
        'DEPARTMENT_MANAGER', 'BUDGET_APPROVER', 'MANAGEMENT_APPROVER'))
);

-- Approval circuits (S2-AC-04/05) ------------------------------------------------------------------------------------

CREATE SEQUENCE seq_approval_round      AS BIGINT START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_approval_task       AS BIGINT START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_approval_task_event AS BIGINT START WITH 1 INCREMENT BY 1;

-- One round per submission. Records the rule version, the rate and the converted amount used (S2DR-04).
CREATE TABLE approval_round (
    id                BIGINT         NOT NULL CONSTRAINT pk_approval_round PRIMARY KEY
                                     CONSTRAINT df_approval_round_id DEFAULT (NEXT VALUE FOR seq_approval_round),
    request_id        BIGINT         NOT NULL CONSTRAINT fk_approval_round_request REFERENCES purchase_request (id) ON DELETE CASCADE,
    company_id        BIGINT         NOT NULL CONSTRAINT fk_approval_round_company REFERENCES company (id),
    round_no          INT            NOT NULL,
    rule_id           BIGINT         NOT NULL CONSTRAINT fk_approval_round_rule REFERENCES approval_rule (id),
    request_amount    DECIMAL(19,4)  NOT NULL,
    request_currency  CHAR(3)        NOT NULL,
    company_amount    DECIMAL(19,4)  NOT NULL,
    company_currency  CHAR(3)        NOT NULL,
    rate              DECIMAL(19,8)  NOT NULL,
    rate_date         DATE           NULL,
    status            VARCHAR(10)    NOT NULL,
    current_stage_no  SMALLINT       NOT NULL,
    started_at        DATETIME2(3)   NOT NULL,
    completed_at      DATETIME2(3)   NULL,
    version           INT            NOT NULL CONSTRAINT df_approval_round_version DEFAULT 0,
    CONSTRAINT uq_approval_round_no UNIQUE (request_id, round_no),
    CONSTRAINT ck_approval_round_status CHECK (status IN ('ACTIVE', 'APPROVED', 'REJECTED', 'RETURNED', 'CANCELLED'))
);

-- One task per approver and stage. assignee = who must act now; original = the approver the rule designated when
-- the task was routed to a delegate (absence or explicit delegation).
CREATE TABLE approval_task (
    id                 BIGINT         NOT NULL CONSTRAINT pk_approval_task PRIMARY KEY
                                      CONSTRAINT df_approval_task_id DEFAULT (NEXT VALUE FOR seq_approval_task),
    round_id           BIGINT         NOT NULL CONSTRAINT fk_approval_task_round REFERENCES approval_round (id),
    request_id         BIGINT         NOT NULL CONSTRAINT fk_approval_task_request REFERENCES purchase_request (id),
    company_id         BIGINT         NOT NULL CONSTRAINT fk_approval_task_company REFERENCES company (id),
    stage_no           SMALLINT       NOT NULL,
    stage_name         NVARCHAR(100)  NOT NULL,
    approver_type      VARCHAR(30)    NOT NULL,
    policy             VARCHAR(3)     NOT NULL,
    assignee_user_id   BIGINT         NOT NULL CONSTRAINT fk_approval_task_assignee REFERENCES user_account (id),
    original_user_id   BIGINT         NULL CONSTRAINT fk_approval_task_original REFERENCES user_account (id),
    routing_note       VARCHAR(30)    NULL,
    status             VARCHAR(12)    NOT NULL,
    sla_minutes        INT            NOT NULL,
    escalation_role    VARCHAR(40)    NULL,
    due_at             DATETIME2(3)   NOT NULL,
    escalation_at      DATETIME2(3)   NOT NULL,
    paused_at          DATETIME2(3)   NULL,
    question_open      BIT            NOT NULL CONSTRAINT df_approval_task_question DEFAULT 0,
    decided_at         DATETIME2(3)   NULL,
    decided_by         BIGINT         NULL CONSTRAINT fk_approval_task_decided_by REFERENCES user_account (id),
    decision_comment   NVARCHAR(2000) NULL,
    created_at         DATETIME2(3)   NOT NULL,
    version            INT            NOT NULL CONSTRAINT df_approval_task_version DEFAULT 0,
    CONSTRAINT ck_approval_task_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'RETURNED', 'DELEGATED',
        'CANCELLED', 'SUPERSEDED')),
    CONSTRAINT ck_approval_task_policy CHECK (policy IN ('ANY', 'ALL'))
);
CREATE INDEX ix_approval_task_assignee ON approval_task (assignee_user_id, status, created_at);
CREATE INDEX ix_approval_task_round ON approval_task (round_id, stage_no, status);
CREATE INDEX ix_approval_task_request ON approval_task (request_id);
CREATE INDEX ix_approval_task_due ON approval_task (status, due_at);

-- Decision history of a request (tracker): every action with actor, on behalf of whom, text and statuses.
CREATE TABLE approval_task_event (
    id                     BIGINT         NOT NULL CONSTRAINT pk_approval_task_event PRIMARY KEY
                                          CONSTRAINT df_approval_task_event_id DEFAULT (NEXT VALUE FOR seq_approval_task_event),
    request_id             BIGINT         NOT NULL CONSTRAINT fk_approval_task_event_request REFERENCES purchase_request (id),
    round_id               BIGINT         NOT NULL CONSTRAINT fk_approval_task_event_round REFERENCES approval_round (id),
    task_id                BIGINT         NULL CONSTRAINT fk_approval_task_event_task REFERENCES approval_task (id),
    kind                   VARCHAR(30)    NOT NULL,
    actor_user_id          BIGINT         NULL CONSTRAINT fk_approval_task_event_actor REFERENCES user_account (id),
    on_behalf_of_user_id   BIGINT         NULL CONSTRAINT fk_approval_task_event_behalf REFERENCES user_account (id),
    target_user_id         BIGINT         NULL CONSTRAINT fk_approval_task_event_target REFERENCES user_account (id),
    text                   NVARCHAR(2000) NULL,
    from_status            VARCHAR(40)    NULL,
    to_status              VARCHAR(40)    NULL,
    channel                VARCHAR(10)    NOT NULL CONSTRAINT df_approval_task_event_channel DEFAULT 'APP',
    occurred_at            DATETIME2(3)   NOT NULL
);
CREATE INDEX ix_approval_task_event_request ON approval_task_event (request_id, occurred_at, id);

-- Reminders and escalations are sent once per task and kind (S2DR-10).
CREATE TABLE approval_task_reminder (
    task_id  BIGINT       NOT NULL CONSTRAINT fk_approval_task_reminder_task REFERENCES approval_task (id),
    kind     VARCHAR(12)  NOT NULL,
    sent_at  DATETIME2(3) NOT NULL,
    CONSTRAINT pk_approval_task_reminder PRIMARY KEY (task_id, kind),
    CONSTRAINT ck_approval_task_reminder_kind CHECK (kind IN ('REMINDER', 'ESCALATION'))
);

CREATE SEQUENCE seq_approval_link_token AS BIGINT START WITH 1 INCREMENT BY 1;

-- One-tap e-mail approval (S2-AC-07, S2DR-07). Only the SHA-256 of the token is stored.
CREATE TABLE approval_link_token (
    id          BIGINT       NOT NULL CONSTRAINT pk_approval_link_token PRIMARY KEY
                             CONSTRAINT df_approval_link_token_id DEFAULT (NEXT VALUE FOR seq_approval_link_token),
    token_hash  CHAR(64)     NOT NULL CONSTRAINT uq_approval_link_token_hash UNIQUE,
    task_id     BIGINT       NOT NULL CONSTRAINT fk_approval_link_token_task REFERENCES approval_task (id),
    user_id     BIGINT       NOT NULL CONSTRAINT fk_approval_link_token_user REFERENCES user_account (id),
    expires_at  DATETIME2(3) NOT NULL,
    used_at     DATETIME2(3) NULL,
    created_at  DATETIME2(3) NOT NULL
);

CREATE SEQUENCE seq_user_absence AS BIGINT START WITH 1 INCREMENT BY 1;

-- Absence delegation (S2-AC-06). Period is [starts_at, ends_at).
CREATE TABLE user_absence (
    id                BIGINT         NOT NULL CONSTRAINT pk_user_absence PRIMARY KEY
                                     CONSTRAINT df_user_absence_id DEFAULT (NEXT VALUE FOR seq_user_absence),
    user_id           BIGINT         NOT NULL CONSTRAINT fk_user_absence_user REFERENCES user_account (id),
    delegate_user_id  BIGINT         NOT NULL CONSTRAINT fk_user_absence_delegate REFERENCES user_account (id),
    starts_at         DATETIME2(3)   NOT NULL,
    ends_at           DATETIME2(3)   NOT NULL,
    reason            NVARCHAR(300)  NULL,
    admin_override    BIT            NOT NULL CONSTRAINT df_user_absence_override DEFAULT 0,
    created_by        BIGINT         NOT NULL CONSTRAINT fk_user_absence_created_by REFERENCES user_account (id),
    created_at        DATETIME2(3)   NOT NULL,
    cancelled_at      DATETIME2(3)   NULL,
    CONSTRAINT ck_user_absence_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_user_absence_not_self CHECK (delegate_user_id <> user_id)
);
CREATE INDEX ix_user_absence_user ON user_absence (user_id, starts_at, ends_at);

-- Notifications (S2-AC-10) -----------------------------------------------------------------------------------------

CREATE SEQUENCE seq_notification_outbox AS BIGINT START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_in_app_notification AS BIGINT START WITH 1 INCREMENT BY 1;

-- Transactional outbox: written in the business transaction, sent by a scheduler (at least once; the stable
-- Message-ID lets mail clients drop a rare duplicate).
CREATE TABLE notification_outbox (
    id                 BIGINT         NOT NULL CONSTRAINT pk_notification_outbox PRIMARY KEY
                                      CONSTRAINT df_notification_outbox_id DEFAULT (NEXT VALUE FOR seq_notification_outbox),
    kind               VARCHAR(40)    NOT NULL,
    recipient_user_id  BIGINT         NOT NULL CONSTRAINT fk_notification_outbox_user REFERENCES user_account (id),
    recipient_email    NVARCHAR(320)  NOT NULL,
    language           VARCHAR(5)     NOT NULL,
    subject            NVARCHAR(300)  NOT NULL,
    body_html          NVARCHAR(MAX)  NOT NULL,
    body_text          NVARCHAR(MAX)  NOT NULL,
    message_id         VARCHAR(200)   NOT NULL CONSTRAINT uq_notification_outbox_message UNIQUE,
    status             VARCHAR(10)    NOT NULL,
    attempts           INT            NOT NULL CONSTRAINT df_notification_outbox_attempts DEFAULT 0,
    next_attempt_at    DATETIME2(3)   NOT NULL,
    last_error         NVARCHAR(500)  NULL,
    created_at         DATETIME2(3)   NOT NULL,
    sent_at            DATETIME2(3)   NULL,
    CONSTRAINT ck_notification_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);
CREATE INDEX ix_notification_outbox_due ON notification_outbox (status, next_attempt_at);

CREATE TABLE in_app_notification (
    id          BIGINT         NOT NULL CONSTRAINT pk_in_app_notification PRIMARY KEY
                               CONSTRAINT df_in_app_notification_id DEFAULT (NEXT VALUE FOR seq_in_app_notification),
    user_id     BIGINT         NOT NULL CONSTRAINT fk_in_app_notification_user REFERENCES user_account (id),
    kind        VARCHAR(40)    NOT NULL,
    request_id  BIGINT         NULL,
    title       NVARCHAR(300)  NOT NULL,
    body        NVARCHAR(1000) NULL,
    link        NVARCHAR(300)  NULL,
    created_at  DATETIME2(3)   NOT NULL,
    read_at     DATETIME2(3)   NULL
);
CREATE INDEX ix_in_app_notification_user ON in_app_notification (user_id, read_at, created_at);

-- Idempotency of state-changing actions (S2-AC-13): replays of the same key return the first result.
CREATE TABLE idempotency_record (
    actor_key         VARCHAR(80)    NOT NULL,
    idempotency_key   VARCHAR(100)   NOT NULL,
    fingerprint       CHAR(64)       NOT NULL,
    http_status       SMALLINT       NOT NULL,
    response_json     NVARCHAR(MAX)  NULL,
    created_at        DATETIME2(3)   NOT NULL,
    CONSTRAINT pk_idempotency_record PRIMARY KEY (actor_key, idempotency_key)
);

-- Attachments (S2-AC-12, S2DR-11) ------------------------------------------------------------------------------------

CREATE SEQUENCE seq_attachment AS BIGINT START WITH 1 INCREMENT BY 1;

CREATE TABLE attachment (
    id            BIGINT         NOT NULL CONSTRAINT pk_attachment PRIMARY KEY
                                 CONSTRAINT df_attachment_id DEFAULT (NEXT VALUE FOR seq_attachment),
    request_id    BIGINT         NOT NULL CONSTRAINT fk_attachment_request REFERENCES purchase_request (id) ON DELETE CASCADE,
    line_id       BIGINT         NULL,
    company_id    BIGINT         NOT NULL CONSTRAINT fk_attachment_company REFERENCES company (id),
    file_name     NVARCHAR(255)  NOT NULL,
    content_type  VARCHAR(100)   NOT NULL,
    size_bytes    BIGINT         NOT NULL,
    sha256        CHAR(64)       NOT NULL,
    storage_key   VARCHAR(100)   NOT NULL CONSTRAINT uq_attachment_storage_key UNIQUE,
    version_no    INT            NOT NULL,
    scan_status   VARCHAR(10)    NOT NULL,
    uploaded_by   BIGINT         NOT NULL CONSTRAINT fk_attachment_uploaded_by REFERENCES user_account (id),
    uploaded_at   DATETIME2(3)   NOT NULL,
    deleted_by    BIGINT         NULL CONSTRAINT fk_attachment_deleted_by REFERENCES user_account (id),
    deleted_at    DATETIME2(3)   NULL,
    CONSTRAINT ck_attachment_size CHECK (size_bytes > 0),
    CONSTRAINT ck_attachment_scan CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED'))
);
CREATE INDEX ix_attachment_request ON attachment (request_id, deleted_at);
