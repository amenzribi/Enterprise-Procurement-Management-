-- DEMONSTRATION DATA ONLY. Links demo supplier contact account to DEMO-SKF (Sprint 11).
-- Requires DemoUserSeeder to have created contact.skf@demo.local.

DECLARE @skf BIGINT = (SELECT id FROM supplier WHERE code = N'DEMO-SKF');
DECLARE @user BIGINT = (SELECT id FROM user_account WHERE email = N'contact.skf@demo.local');

IF @skf IS NOT NULL AND @user IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM supplier_contact WHERE supplier_id = @skf AND user_id = @user)
    INSERT INTO supplier_contact (supplier_id, user_id, display_label, is_active)
    VALUES (@skf, @user, N'SKF Contact (Demo)', 1);

IF @skf IS NOT NULL AND @user IS NOT NULL
    UPDATE supplier_contact
    SET is_active = 1, revoked_at = NULL, revoked_by_user_id = NULL, display_label = N'SKF Contact (Demo)'
    WHERE supplier_id = @skf AND user_id = @user;
