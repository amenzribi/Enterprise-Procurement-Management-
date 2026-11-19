-- Audit Center (Sprint 2, P1.3): global administrators list the whole trail newest first, filtered by period, actor
-- or action, and page through it with a (occurred_at, id) keyset cursor. The existing indexes cover one entity
-- (ix_audit_event_entity) or one company (ix_audit_event_company); a group-wide, time-ordered list needs this one.
-- Actor filters use the second index.
--
-- Additive. Rollback: drop the two indexes.

CREATE INDEX ix_audit_event_occurred ON audit_event (occurred_at DESC, id DESC);

CREATE INDEX ix_audit_event_actor ON audit_event (actor_user_id, occurred_at DESC, id DESC);
