-- Reference data (real ISO 3166-1 / ISO 4217 codes; minor_units = ISO 4217 decimal places, used for rounding
-- document totals HALF_UP - DR-16) for the initial target markets.
-- Further countries and currencies are added through administration or later migrations.

INSERT INTO country (code, name_en, name_fr) VALUES
    ('TN', N'Tunisia', N'Tunisie'),
    ('FR', N'France', N'France'),
    ('DZ', N'Algeria', N'Algérie'),
    ('MA', N'Morocco', N'Maroc'),
    ('LY', N'Libya', N'Libye'),
    ('IT', N'Italy', N'Italie'),
    ('DE', N'Germany', N'Allemagne'),
    ('ES', N'Spain', N'Espagne'),
    ('GB', N'United Kingdom', N'Royaume-Uni'),
    ('US', N'United States', N'États-Unis'),
    ('CN', N'China', N'Chine'),
    ('TR', N'Türkiye', N'Turquie'),
    ('AE', N'United Arab Emirates', N'Émirats arabes unis');

INSERT INTO currency (code, name_en, name_fr, minor_units) VALUES
    ('TND', N'Tunisian dinar', N'Dinar tunisien', 3),
    ('EUR', N'Euro', N'Euro', 2),
    ('USD', N'US dollar', N'Dollar américain', 2),
    ('GBP', N'Pound sterling', N'Livre sterling', 2),
    ('DZD', N'Algerian dinar', N'Dinar algérien', 2),
    ('MAD', N'Moroccan dirham', N'Dirham marocain', 2),
    ('LYD', N'Libyan dinar', N'Dinar libyen', 3),
    ('CNY', N'Chinese yuan', N'Yuan chinois', 2),
    ('TRY', N'Turkish lira', N'Livre turque', 2),
    ('AED', N'UAE dirham', N'Dirham des Émirats', 2),
    ('JPY', N'Japanese yen', N'Yen japonais', 0);
