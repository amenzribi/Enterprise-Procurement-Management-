-- DEMONSTRATION DATA ONLY. Shared and company-scoped suppliers for the demo group.

DECLARE @group BIGINT = (SELECT id FROM organization_group WHERE code = 'DEMO-GRP');
DECLARE @tn BIGINT = (SELECT id FROM company WHERE group_id = @group AND code = 'DEMO-TN-PLT');

IF @group IS NULL
    RETURN;

IF NOT EXISTS (SELECT 1 FROM supplier WHERE group_id = @group AND code = 'DEMO-SKF')
    INSERT INTO supplier (group_id, owning_company_id, code, legal_name, country_code, visibility, tax_id, city)
    VALUES (@group, NULL, N'DEMO-SKF', N'SKF Bearings International (Demo)', 'DE', 'SHARED', N'DEMO-DE-SKF', N'Gothenburg');

IF @tn IS NOT NULL AND NOT EXISTS (SELECT 1 FROM supplier WHERE group_id = @group AND code = 'DEMO-SFAX-SEALS')
    INSERT INTO supplier (group_id, owning_company_id, code, legal_name, country_code, visibility, tax_id, city)
    VALUES (@group, @tn, N'DEMO-SFAX-SEALS', N'Sfax Industrial Seals (Demo)', 'TN', 'COMPANY', N'DEMO-TN-SEALS', N'Sfax');
