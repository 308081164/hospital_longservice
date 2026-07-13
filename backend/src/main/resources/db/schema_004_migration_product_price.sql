-- Phase 5: Product public/original price columns
-- Safe to run multiple times (idempotent via information_schema checks in SchemaMigrationRunner;
-- manual run: skip if columns already exist)

ALTER TABLE product
    ADD COLUMN public_price DECIMAL(12,2) NULL COMMENT '公开价格' AFTER pricing_mode;

ALTER TABLE product
    ADD COLUMN original_price DECIMAL(12,2) NULL COMMENT '原价' AFTER public_price;
