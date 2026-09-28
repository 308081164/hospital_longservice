-- 对账任务：内勤计价规则覆盖（幂等，可重复执行）
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'hospital_reconciliation_job'
      AND COLUMN_NAME = 'pricing_rule_overrides'
);

SET @sql = IF(
    @col_exists = 0,
    'ALTER TABLE hospital_reconciliation_job ADD COLUMN pricing_rule_overrides JSON NULL COMMENT ''内勤规则层覆盖，如 {"disabledCategories":["clerk_price"]}''',
    'SELECT ''pricing_rule_overrides already exists'' AS msg'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
