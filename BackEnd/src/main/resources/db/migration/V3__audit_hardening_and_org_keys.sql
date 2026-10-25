-- Sprint 0 hardening after QA/review (QA-D-01 / CR-13, CR-17).
--
-- Audit trail: the append-only trigger on audit_event does not fire for TRUNCATE, and audit_chain_head was freely
-- writable, so the whole trail could be erased without trace. This migration:
--   * makes audit_chain_head reference its last event (last_event_id -> audit_event). SQL Server refuses TRUNCATE
--     on a table referenced by a foreign key, so audit_event can no longer be truncated;
--   * forbids deleting a chain head and any update that does not strictly advance last_seq (no rewind, no
--     re-keying), and requires new heads to start empty.
-- Remaining limit (accepted under DR-04): a principal that can ALTER the schema (db_owner) can still drop these
-- constraints and triggers. Separate runtime/migration principals (Sprint 1) and external anchoring of chain heads
-- (Sprint 9) close that gap.

ALTER TABLE audit_chain_head ADD last_event_id BIGINT NULL;
GO

UPDATE head
SET last_event_id = event.id
FROM audit_chain_head AS head
JOIN audit_event AS event ON event.chain_key = head.chain_key AND event.chain_seq = head.last_seq
WHERE head.last_seq > 0;

ALTER TABLE audit_chain_head ADD
    CONSTRAINT fk_audit_chain_head_last_event FOREIGN KEY (last_event_id) REFERENCES audit_event (id),
    CONSTRAINT ck_audit_chain_head_last_event CHECK ((last_seq = 0 AND last_event_id IS NULL)
                                                 OR (last_seq > 0 AND last_event_id IS NOT NULL));
GO

CREATE TRIGGER trg_audit_chain_head_no_delete ON audit_chain_head
INSTEAD OF DELETE
AS
BEGIN
    THROW 51001, 'audit_chain_head rows cannot be deleted.', 1;
END;
GO

CREATE TRIGGER trg_audit_chain_head_forward_only ON audit_chain_head
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
    BEGIN
        IF UPDATE(chain_key)
            THROW 51002, 'audit_chain_head.chain_key cannot be changed.', 1;
        IF EXISTS (SELECT 1 FROM inserted AS i JOIN deleted AS d ON d.chain_key = i.chain_key
                   WHERE i.last_seq <= d.last_seq)
            THROW 51003, 'audit_chain_head.last_seq must strictly increase.', 1;
    END
    ELSE IF EXISTS (SELECT 1 FROM inserted WHERE last_seq <> 0)
        THROW 51004, 'A new audit chain head must start at last_seq = 0.', 1;
END;
GO

-- CR-17: same-company references to cost centres (as for sites and departments), and a parent company must
-- belong to the same group.
ALTER TABLE cost_center ADD CONSTRAINT uq_cost_center_company_id UNIQUE (company_id, id);

ALTER TABLE company ADD CONSTRAINT uq_company_group_id UNIQUE (group_id, id);

ALTER TABLE company ADD CONSTRAINT fk_company_parent_same_group
    FOREIGN KEY (group_id, parent_company_id) REFERENCES company (group_id, id);
GO
