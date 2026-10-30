-- DEMONSTRATION DATA ONLY. Ines (CPA buyer) prefers spare-parts requests when affinity is recorded.

DECLARE @group BIGINT = (SELECT id FROM organization_group WHERE code = 'DEMO-GRP');
DECLARE @cpa BIGINT = (SELECT id FROM company WHERE group_id = @group AND code = 'DEMO-CPA');
DECLARE @ines BIGINT = (SELECT id FROM user_account WHERE email = N'buyer@demo.local');
DECLARE @spare BIGINT = (SELECT id FROM request_category WHERE group_id = @group AND code = 'DEMO-SPARE');

IF @cpa IS NULL OR @ines IS NULL OR @spare IS NULL
    RETURN;

IF NOT EXISTS (SELECT 1 FROM buyer_category_affinity WHERE buyer_user_id = @ines AND category_id = @spare AND company_id = @cpa)
    INSERT INTO buyer_category_affinity (buyer_user_id, category_id, company_id)
    VALUES (@ines, @spare, @cpa);
