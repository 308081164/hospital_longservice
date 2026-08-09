-- 客户商品规则：纸塑袋宽度下限（含），用于敷料包固定价等场景
ALTER TABLE customer_product_rule
    ADD COLUMN min_bag_size_inclusive INT NULL COMMENT '纸塑袋宽度下限(含)' AFTER max_bag_size_exclusive;
