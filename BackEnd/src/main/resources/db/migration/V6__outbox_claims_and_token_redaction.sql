-- Sprint 2 review fixes (docs/reviews/sprint-2-code-review.md).

-- S2CR-06: rows are claimed (SENDING) and committed before the SMTP call; a claim older than a few minutes is taken
-- over again after a crash.
ALTER TABLE notification_outbox ADD claimed_at DATETIME2(3) NULL;
ALTER TABLE notification_outbox DROP CONSTRAINT ck_notification_outbox_status;
ALTER TABLE notification_outbox ADD CONSTRAINT ck_notification_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED'));
GO

-- S2CR-01: one-tap tokens are no longer stored in the outbox (the link is created at send time). Rows written
-- before this migration may contain usable tokens: redact them and invalidate every unused token.
UPDATE notification_outbox SET body_html = N'[redacted]', body_text = N'[redacted]'
WHERE body_html LIKE N'%/a#%' OR body_text LIKE N'%/a#%';
UPDATE approval_link_token SET used_at = SYSUTCDATETIME() WHERE used_at IS NULL;
