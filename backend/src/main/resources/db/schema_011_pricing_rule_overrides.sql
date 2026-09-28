-- 对账任务：内勤计价规则覆盖（禁用指定 clerk 规则后重算）
ALTER TABLE hospital_reconciliation_job
    ADD COLUMN pricing_rule_overrides JSON NULL COMMENT '内勤规则层覆盖，如 {"disabledCategories":["clerk_price"]}';
