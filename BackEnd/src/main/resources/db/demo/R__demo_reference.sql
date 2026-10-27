-- DEMONSTRATION DATA ONLY - loaded exclusively with the "demo" Spring profile.
-- Purchase categories for the fictitious demo group. Runs after R__demo_organization (alphabetical order).
-- Idempotent (repeatable migration): rows are inserted only when missing.
-- Demo users are not created here: they need password hashes, so DemoUserSeeder creates them at startup.

DECLARE @group BIGINT = (SELECT id FROM organization_group WHERE code = 'DEMO-GRP');

MERGE request_category AS t
USING (VALUES
    ('DEMO-SPARE',  N'Spare parts (Demo)',             N'Pièces de rechange (Démo)'),
    ('DEMO-MECH',   N'Mechanical equipment (Demo)',    N'Équipements mécaniques (Démo)'),
    ('DEMO-ELEC',   N'Electrical equipment (Demo)',    N'Équipements électriques (Démo)'),
    ('DEMO-CHEM',   N'Chemicals and consumables (Demo)', N'Produits chimiques et consommables (Démo)'),
    ('DEMO-SERV',   N'Services and maintenance (Demo)', N'Services et maintenance (Démo)'),
    ('DEMO-IT',     N'IT hardware (Demo)',             N'Matériel informatique (Démo)')
) AS s (code, name_en, name_fr)
ON t.group_id = @group AND t.code = s.code
WHEN NOT MATCHED THEN
    INSERT (group_id, code, name_en, name_fr) VALUES (@group, s.code, s.name_en, s.name_fr);
