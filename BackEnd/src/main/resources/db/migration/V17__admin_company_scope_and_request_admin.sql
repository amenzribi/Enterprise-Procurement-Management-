-- ADMIN becomes company-scoped. Expand legacy NULL ADMIN grants to one row per company in the user's group.
-- Logical removal columns for GLOBAL_ADMIN request administration (distinct from archive).

-- 1) Expand group-wide ADMIN grants into explicit per-company grants (preserve reach; no auto-grant for future companies).
INSERT INTO user_role (user_id, role, company_id, department_id)
SELECT ur.user_id, N'ADMIN', c.id, NULL
FROM user_role ur
INNER JOIN user_account ua ON ua.id = ur.user_id
INNER JOIN company home ON home.id = ua.company_id
INNER JOIN company c ON c.group_id = home.group_id
WHERE ur.role = N'ADMIN'
  AND ur.company_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM user_role x
      WHERE x.user_id = ur.user_id AND x.role = N'ADMIN' AND x.company_id = c.id AND x.department_id IS NULL
  );
GO

DELETE FROM user_role WHERE role = N'ADMIN' AND company_id IS NULL;
GO

-- 2) ADMIN may no longer be granted without a company (GLOBAL_ADMIN / GROUP_MANAGEMENT / SUPPLIER_CONTACT remain group-wide).
ALTER TABLE user_role DROP CONSTRAINT ck_user_role_group_wide;
ALTER TABLE user_role ADD CONSTRAINT ck_user_role_group_wide CHECK (
    company_id IS NOT NULL
    OR role IN (N'GROUP_MANAGEMENT', N'AUDITOR', N'GLOBAL_ADMIN', N'SUPPLIER_CONTACT')
);
GO

-- 3) Logical removal (hide from active work; retain history). Distinct from archived_*.
ALTER TABLE purchase_request ADD
    removed_at DATETIME2(3) NULL,
    removed_by_user_id BIGINT NULL
        CONSTRAINT fk_purchase_request_removed_by REFERENCES user_account (id),
    remove_reason NVARCHAR(1000) NULL;
GO

ALTER TABLE purchase_request ADD CONSTRAINT ck_purchase_request_removed CHECK (
    (removed_at IS NULL AND removed_by_user_id IS NULL AND remove_reason IS NULL)
    OR (removed_at IS NOT NULL AND removed_by_user_id IS NOT NULL)
);
GO

CREATE INDEX ix_purchase_request_group_removed ON purchase_request (group_id, removed_at)
    WHERE removed_at IS NOT NULL;
GO
