-- DEMONSTRATION DATA ONLY - loaded exclusively with the "demo" Spring profile.
-- Approval rules and exchange rates of the fictitious demo group (docs/product/sprint-2.md, "Demo data").
-- Runs after R__demo_organization and R__demo_reference (alphabetical order). Idempotent: rows are inserted only
-- when missing. Approvers are resolved by role at submission time, so no user ids appear here.

DECLARE @group BIGINT = (SELECT id FROM organization_group WHERE code = 'DEMO-GRP');
DECLARE @tn BIGINT = (SELECT id FROM company WHERE code = 'DEMO-TN-PLT');
DECLARE @fr BIGINT = (SELECT id FROM company WHERE code = 'DEMO-FR-PLT');
DECLARE @rule BIGINT;

-- Exchange rates (quote units per 1 base unit), dated 2026-01-01, source labelled as demo.
INSERT INTO exchange_rate (group_id, base_currency, quote_currency, rate, rate_date, source)
SELECT @group, s.base_currency, s.quote_currency, s.rate, '2026-01-01', N'Demo rate (Demo)'
FROM (VALUES ('EUR', 'TND', 3.40000000), ('USD', 'TND', 3.10000000), ('USD', 'EUR', 0.92000000))
     AS s (base_currency, quote_currency, rate)
WHERE NOT EXISTS (SELECT 1 FROM exchange_rate e WHERE e.group_id = @group AND e.base_currency = s.base_currency
                  AND e.quote_currency = s.quote_currency AND e.source = N'Demo rate (Demo)');

-- DEMO-TN-PLT (TND): three bands.
IF NOT EXISTS (SELECT 1 FROM approval_rule WHERE company_id = @tn AND name = N'Sfax up to 5 000 TND (Demo)')
BEGIN
    SET @rule = NEXT VALUE FOR seq_approval_rule;
    INSERT INTO approval_rule (id, group_id, company_id, rule_key, version_no, name, min_amount, max_amount, status, activated_at)
    VALUES (@rule, @group, @tn, @rule, 1, N'Sfax up to 5 000 TND (Demo)', 0, 5000, 'ACTIVE', SYSUTCDATETIME());
    INSERT INTO approval_rule_stage (rule_id, stage_no, name, approver_type, policy, sla_hours, escalation_role)
    VALUES (@rule, 1, N'Department manager', 'DEPARTMENT_MANAGER', 'ANY', 16, 'MANAGEMENT_APPROVER');
END;

IF NOT EXISTS (SELECT 1 FROM approval_rule WHERE company_id = @tn AND name = N'Sfax 5 000 to 50 000 TND (Demo)')
BEGIN
    SET @rule = NEXT VALUE FOR seq_approval_rule;
    INSERT INTO approval_rule (id, group_id, company_id, rule_key, version_no, name, min_amount, max_amount, status, activated_at)
    VALUES (@rule, @group, @tn, @rule, 1, N'Sfax 5 000 to 50 000 TND (Demo)', 5000, 50000, 'ACTIVE', SYSUTCDATETIME());
    INSERT INTO approval_rule_stage (rule_id, stage_no, name, approver_type, policy, sla_hours, escalation_role)
    VALUES (@rule, 1, N'Department manager', 'DEPARTMENT_MANAGER', 'ANY', 16, 'MANAGEMENT_APPROVER'),
           (@rule, 2, N'Budget approval', 'BUDGET_APPROVER', 'ANY', 16, 'MANAGEMENT_APPROVER');
END;

IF NOT EXISTS (SELECT 1 FROM approval_rule WHERE company_id = @tn AND name = N'Sfax from 50 000 TND (Demo)')
BEGIN
    SET @rule = NEXT VALUE FOR seq_approval_rule;
    INSERT INTO approval_rule (id, group_id, company_id, rule_key, version_no, name, min_amount, max_amount, status, activated_at)
    VALUES (@rule, @group, @tn, @rule, 1, N'Sfax from 50 000 TND (Demo)', 50000, NULL, 'ACTIVE', SYSUTCDATETIME());
    INSERT INTO approval_rule_stage (rule_id, stage_no, name, approver_type, policy, sla_hours, escalation_role)
    VALUES (@rule, 1, N'Department manager', 'DEPARTMENT_MANAGER', 'ANY', 16, 'MANAGEMENT_APPROVER'),
           (@rule, 2, N'Budget approval', 'BUDGET_APPROVER', 'ANY', 16, 'MANAGEMENT_APPROVER'),
           (@rule, 3, N'Management approval', 'MANAGEMENT_APPROVER', 'ANY', 16, 'MANAGEMENT_APPROVER');
END;

-- DEMO-FR-PLT (EUR): department manager for any amount.
IF NOT EXISTS (SELECT 1 FROM approval_rule WHERE company_id = @fr AND name = N'Lyon all amounts (Demo)')
BEGIN
    SET @rule = NEXT VALUE FOR seq_approval_rule;
    INSERT INTO approval_rule (id, group_id, company_id, rule_key, version_no, name, min_amount, max_amount, status, activated_at)
    VALUES (@rule, @group, @fr, @rule, 1, N'Lyon all amounts (Demo)', 0, NULL, 'ACTIVE', SYSUTCDATETIME());
    INSERT INTO approval_rule_stage (rule_id, stage_no, name, approver_type, policy, sla_hours, escalation_role)
    VALUES (@rule, 1, N'Department manager', 'DEPARTMENT_MANAGER', 'ANY', 16, 'MANAGEMENT_APPROVER');
END;
