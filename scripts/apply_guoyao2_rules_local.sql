-- Apply GUOYAO-2 customer rules seed (local dev)
SET NAMES utf8mb4;

UPDATE customer_product_rule
SET is_active = 0
WHERE customer_id = (SELECT id FROM customer WHERE code = 'GUOYAO-2')
  AND name = '校正价8.0';

INSERT INTO customer_product_rule (
  customer_id, rule_type, match_mode, name, priority, keywords, exclude_keywords,
  price, threshold, fold_ratio, skip_packaging, skip_discount, is_active
)
SELECT c.id, 'FIXED_PRICE', 'first', '电机厂缝合针8元', 10,
       JSON_ARRAY('缝合针'), NULL, 8.0, NULL, NULL, 1, 1, 1
FROM customer c
WHERE c.code = 'GUOYAO-2'
  AND NOT EXISTS (
    SELECT 1 FROM customer_product_rule r
    WHERE r.customer_id = c.id AND r.name = '电机厂缝合针8元'
  );

INSERT INTO customer_product_rule (
  customer_id, rule_type, match_mode, name, priority, keywords, exclude_keywords,
  price, threshold, fold_ratio, skip_packaging, skip_discount, is_active
)
SELECT c.id, 'PRICE_PER_INSTRUMENT', 'first', '电机厂双按件5.5', 12,
       JSON_ARRAY('双'), JSON_ARRAY('(双)', '/(双)', '双极', '双极线'),
       5.5, NULL, NULL, 0, 1, 1
FROM customer c
WHERE c.code = 'GUOYAO-2'
  AND NOT EXISTS (
    SELECT 1 FROM customer_product_rule r
    WHERE r.customer_id = c.id AND r.name = '电机厂双按件5.5'
  );

INSERT INTO customer_product_rule (
  customer_id, rule_type, match_mode, name, priority, keywords, exclude_keywords,
  price, threshold, fold_ratio, skip_packaging, skip_discount, is_active
)
SELECT c.id, 'FOLD', 'first', '电机厂指针5合1', 14,
       JSON_ARRAY('指针'), NULL, NULL, 5, 5, 0, 0, 1
FROM customer c
WHERE c.code = 'GUOYAO-2'
  AND NOT EXISTS (
    SELECT 1 FROM customer_product_rule r
    WHERE r.customer_id = c.id AND r.name = '电机厂指针5合1'
  );

INSERT INTO sys_setting (setting_key, setting_value, description)
SELECT 'billing_seed_guoyao_2_customer_rules_20260809_v1', 'true', '电机厂客户特色规则 20260809'
WHERE NOT EXISTS (
  SELECT 1 FROM sys_setting WHERE setting_key = 'billing_seed_guoyao_2_customer_rules_20260809_v1'
);

SELECT id, name, rule_type, price, is_active, keywords
FROM customer_product_rule
WHERE customer_id = (SELECT id FROM customer WHERE code = 'GUOYAO-2')
ORDER BY priority, id;
