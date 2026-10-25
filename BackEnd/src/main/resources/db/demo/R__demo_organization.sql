-- DEMONSTRATION DATA ONLY - loaded exclusively with the "demo" Spring profile.
-- Fictitious group used for demos and manual testing. Every name carries the "(Demo)" label and every
-- code is prefixed "DEMO" so it can never be mistaken for real customer data.
-- Idempotent (repeatable migration): rows are inserted only when missing.

IF NOT EXISTS (SELECT 1 FROM organization_group WHERE code = 'DEMO-GRP')
    INSERT INTO organization_group (code, name, consolidation_currency)
    VALUES ('DEMO-GRP', N'Atlas Industrial Group (Demo)', 'EUR');

DECLARE @group BIGINT = (SELECT id FROM organization_group WHERE code = 'DEMO-GRP');

IF NOT EXISTS (SELECT 1 FROM company WHERE group_id = @group AND code = 'DEMO-HOLD')
    INSERT INTO company (group_id, parent_company_id, code, legal_name, company_type, country_code, currency_code,
                         default_language, time_zone, tax_id, erp_company_code, purchasing_mode)
    VALUES (@group, NULL, 'DEMO-HOLD', N'Atlas Holding SA (Demo)', 'PARENT', 'TN', 'TND',
            'fr', 'Africa/Tunis', N'DEMO-0000001A', 'DEMO1000', 'DIRECT');

DECLARE @holding BIGINT = (SELECT id FROM company WHERE group_id = @group AND code = 'DEMO-HOLD');

IF NOT EXISTS (SELECT 1 FROM company WHERE group_id = @group AND code = 'DEMO-CPA')
    INSERT INTO company (group_id, parent_company_id, code, legal_name, company_type, country_code, currency_code,
                         default_language, time_zone, tax_id, erp_company_code, purchasing_mode)
    VALUES (@group, @holding, 'DEMO-CPA', N'Atlas Sourcing & Trading (Demo)', 'PURCHASING_CENTER', 'TN', 'TND',
            'fr', 'Africa/Tunis', N'DEMO-0000002B', 'DEMO1100', 'DIRECT');

IF NOT EXISTS (SELECT 1 FROM company WHERE group_id = @group AND code = 'DEMO-TN-PLT')
    INSERT INTO company (group_id, parent_company_id, code, legal_name, company_type, country_code, currency_code,
                         default_language, time_zone, tax_id, erp_company_code, purchasing_mode)
    VALUES (@group, @holding, 'DEMO-TN-PLT', N'Atlas Mechanical Sfax (Demo)', 'SUBSIDIARY', 'TN', 'TND',
            'fr', 'Africa/Tunis', N'DEMO-0000003C', 'DEMO2100', 'MIXED');

IF NOT EXISTS (SELECT 1 FROM company WHERE group_id = @group AND code = 'DEMO-FR-PLT')
    INSERT INTO company (group_id, parent_company_id, code, legal_name, company_type, country_code, currency_code,
                         default_language, time_zone, tax_id, erp_company_code, purchasing_mode)
    VALUES (@group, @holding, 'DEMO-FR-PLT', N'Atlas Composites Lyon SAS (Demo)', 'SUBSIDIARY', 'FR', 'EUR',
            'fr', 'Europe/Paris', N'DEMO-FR00000004', 'DEMO3100', 'CENTRALIZED');

DECLARE @cpa BIGINT = (SELECT id FROM company WHERE group_id = @group AND code = 'DEMO-CPA');
DECLARE @tn  BIGINT = (SELECT id FROM company WHERE group_id = @group AND code = 'DEMO-TN-PLT');
DECLARE @fr  BIGINT = (SELECT id FROM company WHERE group_id = @group AND code = 'DEMO-FR-PLT');

MERGE site AS t
USING (VALUES
    (@holding, 'DEMO-HQ',   N'Head office Tunis (Demo)',       'HEAD_OFFICE', 'TN', N'Tunis'),
    (@cpa,     'DEMO-RADES', N'Central warehouse Radès (Demo)', 'WAREHOUSE',   'TN', N'Radès'),
    (@tn,      'DEMO-SFAX', N'Sfax plant (Demo)',              'PLANT',       'TN', N'Sfax'),
    (@fr,      'DEMO-LYON', N'Lyon plant (Demo)',              'PLANT',       'FR', N'Lyon')
) AS s (company_id, code, name, site_type, country_code, city)
ON t.company_id = s.company_id AND t.code = s.code
WHEN NOT MATCHED THEN
    INSERT (company_id, code, name, site_type, country_code, city)
    VALUES (s.company_id, s.code, s.name, s.site_type, s.country_code, s.city);

MERGE department AS t
USING (VALUES
    (@tn,  'DEMO-SFAX', 'DEMO-MAINT', N'Maintenance (Demo)'),
    (@tn,  'DEMO-SFAX', 'DEMO-PROD',  N'Production (Demo)'),
    (@fr,  'DEMO-LYON', 'DEMO-MAINT', N'Maintenance (Demo)'),
    (@cpa, 'DEMO-RADES', 'DEMO-PURCH', N'Group purchasing (Demo)'),
    (@holding, 'DEMO-HQ', 'DEMO-FIN', N'Finance (Demo)')
) AS s (company_id, site_code, code, name)
ON t.company_id = s.company_id AND t.code = s.code
WHEN NOT MATCHED THEN
    INSERT (company_id, site_id, code, name)
    VALUES (s.company_id, (SELECT id FROM site WHERE company_id = s.company_id AND code = s.site_code), s.code, s.name);

MERGE cost_center AS t
USING (VALUES
    (@tn,  'DEMO-CC-2100', N'Sfax plant maintenance (Demo)', 'DEMO-K2100'),
    (@fr,  'DEMO-CC-3100', N'Lyon plant maintenance (Demo)', 'DEMO-K3100'),
    (@cpa, 'DEMO-CC-1100', N'Group purchasing overheads (Demo)', 'DEMO-K1100')
) AS s (company_id, code, name, erp_code)
ON t.company_id = s.company_id AND t.code = s.code
WHEN NOT MATCHED THEN
    INSERT (company_id, code, name, erp_code) VALUES (s.company_id, s.code, s.name, s.erp_code);
