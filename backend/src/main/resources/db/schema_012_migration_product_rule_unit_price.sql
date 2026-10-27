-- FOLD 折算规则按折算后件数计价所需的单价（unitPrice）。
-- 历史缺口：PricingEngine 的 FOLD 分支支持 unitPrice，但此前没有列可落库，
-- baseline 里写 unitPrice 会在同步进 customer_product_rule 时静默丢失价格（有规则无价格）。
ALTER TABLE customer_product_rule
    ADD COLUMN unit_price DECIMAL(12,4) NULL COMMENT 'FOLD 折算后单价（unitPrice）' AFTER price;
