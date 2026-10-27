-- FOLD 折算前先扣除的固定件数（如「钢针-7件 扳手1」里那 1 把扳手）。
-- 被扣除件数按 1 件计入结果，剩余件数再按 fold_ratio 折算。
ALTER TABLE customer_product_rule
    ADD COLUMN fold_offset INT NULL COMMENT 'FOLD 折算前扣除的固定件数' AFTER fold_ratio;
