package com.hospital.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SchemaMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        migratePricingRuleTable();
        migratePricingRuleRevisionTable();
        migrateReconciliationJobTable();
        migrateReconciliationRowTable();
        migrateReconciliationJobUrgentColumns();
        migrateExportLogTable();
        migrateProductMasterTables();
        migrateProductVariantTable();
        migrateProductMatchRuleVariantColumn();
        migrateCustomerProductRuleVariantColumn();
        migrateProductPriceColumns();
        migrateCustomerMasterTables();
        migrateBillingProfileColumns();
        migrateCustomerBillingPolicyTable();
        migrateCustomerProductRuleTemperatureColumn();
        migrateExportTemplateTable();
        migrateRuleGroupTables();
        migratePhase7L3Tables();
        migrateLogisticsTables();
        migrateDeptPhysicianTables();
        migrateSysSettingTable();
        seedInstrumentPackCategory();
    }

    private void migratePhase7L3Tables() {
        createTableIfMissing("roster_entry", """
                CREATE TABLE roster_entry (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    doctor_name VARCHAR(120) NOT NULL COMMENT '医生姓名',
                    department VARCHAR(120) NOT NULL COMMENT '归属科室',
                    surgical_room VARCHAR(120) NULL COMMENT '手术室标识',
                    notes VARCHAR(500) NULL,
                    is_active TINYINT(1) NOT NULL DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_roster_customer_doctor (customer_id, doctor_name),
                    INDEX idx_roster_customer_dept (customer_id, department, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        createTableIfMissing("external_instrument", """
                CREATE TABLE external_instrument (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    reconciliation_job_id BIGINT NULL COMMENT 'NULL=目录价，非空=账期明细',
                    category_no VARCHAR(120) NOT NULL COMMENT '包类别号',
                    pack_name VARCHAR(300) NOT NULL,
                    department VARCHAR(120) NULL,
                    package_material VARCHAR(120) NULL,
                    patient_name VARCHAR(120) NULL,
                    usage_date DATE NULL,
                    pack_count INT NOT NULL DEFAULT 1,
                    instrument_count INT NOT NULL DEFAULT 0,
                    unit_price DECIMAL(12,2) NOT NULL,
                    total_amount DECIMAL(12,2) NULL,
                    notes TEXT NULL,
                    is_active TINYINT(1) NOT NULL DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_ext_inst_customer (customer_id, is_active),
                    INDEX idx_ext_inst_job (reconciliation_job_id),
                    INDEX idx_ext_inst_category (customer_id, category_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        addColumnIfMissing("hospital_reconciliation_job", "allocation_result",
                "allocation_result JSON NULL COMMENT '科室借调/费用调整分配结果'");
    }

    private void migrateLogisticsTables() {
        createTableIfMissing("logistics_import", """
                CREATE TABLE logistics_import (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    job_id BIGINT NULL,
                    billing_month VARCHAR(7) NULL,
                    trip_date DATE NOT NULL,
                    route VARCHAR(200) NULL,
                    trip_count INT NOT NULL DEFAULT 1,
                    fee_amount DECIMAL(12,2) NULL,
                    notes VARCHAR(500) NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_li_customer_month (customer_id, billing_month),
                    INDEX idx_li_job (job_id),
                    INDEX idx_li_trip_date (trip_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        createTableIfMissing("logistics_card", """
                CREATE TABLE logistics_card (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    name VARCHAR(120) NOT NULL DEFAULT '默认物流卡',
                    balance DECIMAL(12,2) NOT NULL DEFAULT 0,
                    initial_balance DECIMAL(12,2) NOT NULL DEFAULT 0,
                    is_active TINYINT(1) NOT NULL DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_lc_customer (customer_id, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        createTableIfMissing("logistics_card_transaction", """
                CREATE TABLE logistics_card_transaction (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    card_id BIGINT NOT NULL,
                    transaction_type VARCHAR(20) NOT NULL,
                    amount DECIMAL(12,2) NOT NULL,
                    balance_after DECIMAL(12,2) NOT NULL,
                    job_id BIGINT NULL,
                    remark VARCHAR(500) NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_lct_card (card_id),
                    INDEX idx_lct_job (job_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        createTableIfMissing("customer_group", """
                CREATE TABLE customer_group (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(120) NOT NULL,
                    group_type VARCHAR(40) NOT NULL DEFAULT 'settlement_merge',
                    config JSON NULL,
                    is_active TINYINT(1) NOT NULL DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_cg_type (group_type, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        createTableIfMissing("customer_group_member", """
                CREATE TABLE customer_group_member (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    group_id BIGINT NOT NULL,
                    customer_id BIGINT NOT NULL,
                    share_ratio DECIMAL(8,4) NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE INDEX idx_cgm_group_customer (group_id, customer_id),
                    INDEX idx_cgm_customer (customer_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void migrateDeptPhysicianTables() {
        createTableIfMissing("department_entry", """
                CREATE TABLE department_entry (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    department_name VARCHAR(120) NOT NULL COMMENT '科室名称',
                    code VARCHAR(60) NULL COMMENT '科室编码',
                    notes VARCHAR(500) NULL,
                    usage_count INT NOT NULL DEFAULT 0 COMMENT '使用统计占位',
                    is_active TINYINT(1) NOT NULL DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_dept_customer_name (customer_id, department_name),
                    INDEX idx_dept_customer_active (customer_id, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        createTableIfMissing("physician_entry", """
                CREATE TABLE physician_entry (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    physician_name VARCHAR(120) NOT NULL COMMENT '医生姓名',
                    department_entry_id BIGINT NULL COMMENT '关联科室主数据',
                    department_name VARCHAR(120) NULL COMMENT '科室名称冗余',
                    code VARCHAR(60) NULL COMMENT '医生编码',
                    notes VARCHAR(500) NULL,
                    usage_count INT NOT NULL DEFAULT 0 COMMENT '使用统计占位',
                    is_active TINYINT(1) NOT NULL DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_physician_customer_name (customer_id, physician_name),
                    INDEX idx_physician_customer_dept (customer_id, department_entry_id, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void migrateRuleGroupTables() {
        createTableIfMissing("customer_billing_rule_group", """
                CREATE TABLE customer_billing_rule_group (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    group_code VARCHAR(60) NOT NULL DEFAULT 'default',
                    group_name VARCHAR(120) NOT NULL DEFAULT '默认规则组',
                    rules_json LONGTEXT NULL COMMENT '规则快照 JSON',
                    priority INT NOT NULL DEFAULT 100,
                    is_active TINYINT(1) NOT NULL DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_cbrg_customer_code (customer_id, group_code),
                    INDEX idx_cbrg_customer (customer_id, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        createTableIfMissing("billing_rule_change_log", """
                CREATE TABLE billing_rule_change_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    rule_group_id BIGINT NULL,
                    product_rule_id BIGINT NULL,
                    change_type VARCHAR(30) NOT NULL,
                    entity_type VARCHAR(30) NOT NULL DEFAULT 'PRODUCT_RULE',
                    before_snapshot JSON NULL,
                    after_snapshot JSON NULL,
                    operator_name VARCHAR(120) NULL,
                    change_summary VARCHAR(500) NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_brcl_customer (customer_id, created_at),
                    INDEX idx_brcl_group (rule_group_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        addColumnIfMissing("customer_product_rule", "rule_group_id",
                "rule_group_id BIGINT NULL COMMENT '所属规则组' AFTER customer_id");
    }

    private void migrateBillingProfileColumns() {
        addColumnIfMissing("customer", "billing_enabled",
                "billing_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '特色账单开关' AFTER default_rule_id");
        addColumnIfMissing("customer_product_rule", "exclude_keywords",
                "exclude_keywords JSON NULL COMMENT '排除关键词' AFTER keywords");
        addColumnIfMissing("customer_product_rule", "accepted_prices",
                "accepted_prices JSON NULL COMMENT '多报价候选' AFTER price");
        addColumnIfMissing("customer_product_rule", "match_mode",
                "match_mode VARCHAR(20) NOT NULL DEFAULT 'first' AFTER rule_type");
        addColumnIfMissing("customer_product_rule", "original_unit_price",
                "original_unit_price DECIMAL(12,4) NULL COMMENT '原价匹配条件' AFTER price");
        addColumnIfMissing("customer_product_rule", "conditions_json",
                "conditions_json JSON NULL COMMENT '扩展条件如科室' AFTER materials");
        addColumnIfMissing("hospital_reconciliation_row", "matched_rule_id",
                "matched_rule_id BIGINT NULL COMMENT '命中特色规则ID'");
        addColumnIfMissing("hospital_reconciliation_row", "matched_price_option",
                "matched_price_option DECIMAL(12,2) NULL COMMENT '多报价命中价格'");
        addColumnIfMissing("hospital_reconciliation_row", "billing_notes",
                "billing_notes JSON NULL COMMENT '计费说明JSON'");
        addColumnIfMissing("customer", "billing_pricing_mode",
                "billing_pricing_mode VARCHAR(20) NOT NULL DEFAULT 'standard' COMMENT 'standard/special_only/hybrid' AFTER billing_enabled");
        addColumnIfMissing("customer", "path_override",
                "path_override JSON NULL COMMENT '路径覆盖 disableLowTemp/forceHighTempUnitPrice' AFTER billing_pricing_mode");
        addColumnIfMissing("customer", "export_name_mapping",
                "export_name_mapping JSON NULL COMMENT '导出名称替换 FR-M1-09' AFTER path_override");
        seedBillingEnabledForExistingCustomers();
    }

    private void migrateCustomerBillingPolicyTable() {
        createTableIfMissing("customer_billing_policy", """
                CREATE TABLE customer_billing_policy (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    policy_type VARCHAR(30) NOT NULL,
                    name VARCHAR(120) NULL,
                    scope JSON NULL COMMENT '作用域，如 { "temperature": "HT|LT|ANY" }',
                    params JSON NOT NULL COMMENT '策略参数，如 { "rate": 0.7 }',
                    priority INT NOT NULL DEFAULT 100,
                    is_active TINYINT(1) DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_cbp_customer (customer_id, policy_type, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        migrateDiscountsToBillingPolicies();
    }

    private void migrateDiscountsToBillingPolicies() {
        if (!tableExists("customer_billing_policy") || !tableExists("customer_discount")) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO customer_billing_policy (customer_id, policy_type, name, scope, params, priority, is_active, created_at, updated_at)
                SELECT d.customer_id, 'DISCOUNT', d.name,
                       '{"temperature":"ANY"}',
                       JSON_OBJECT('rate', d.discount_rate, 'skipWhenFixedPrice', IF(d.skip_when_fixed_price = 1, TRUE, FALSE)),
                       d.priority, d.is_active, d.created_at, d.updated_at
                FROM customer_discount d
                WHERE NOT EXISTS (
                    SELECT 1 FROM customer_billing_policy p
                    WHERE p.customer_id = d.customer_id AND p.policy_type = 'DISCOUNT'
                )
                """);
    }

    private void migrateCustomerProductRuleTemperatureColumn() {
        addColumnIfMissing("customer_product_rule", "temperature",
                "temperature VARCHAR(10) NULL COMMENT 'HT/LT/ANY 温度条件' AFTER materials");
    }

    private void migrateExportTemplateTable() {
        createTableIfMissing("export_template", """
                CREATE TABLE export_template (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_id BIGINT NULL COMMENT 'NULL=global default',
                    template_type VARCHAR(40) NOT NULL COMMENT 'bill|settlement|dept_summary|price_summary|instrument_audit|daily',
                    name VARCHAR(120) NOT NULL,
                    storage_path VARCHAR(500) NOT NULL DEFAULT '',
                    column_mapping JSON NULL,
                    sheet_config JSON NULL,
                    is_active TINYINT(1) NOT NULL DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_export_template_customer (customer_id, template_type, is_active),
                    INDEX idx_export_template_type (template_type, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        seedDefaultExportTemplates();
        seedBatchExportTemplates();
    }

    private void seedBatchExportTemplates() {
        if (!tableExists("export_template")) {
            return;
        }
        seedTemplateIfMissing("bill", "呼兰一院账单骨架", """
                {"keepColumns":["发货日期","单号","类型","包类别号","包名","包数","单价","总价"]}
                """, """
                {"strategyKey":"standard_bill","customerCode":"HULAN_FIRST"}
                """);
        seedTemplateIfMissing("bill", "冰城医美账单骨架", """
                {"removeColumns":["备注","差额"]}
                """, """
                {"strategyKey":"standard_bill","customerCode":"BINGCHENG_YIMEI"}
                """);
        seedTemplateIfMissing("settlement", "工程大学结款函独立折扣", """
                {"settlementDiscountRows":true}
                """, """
                {"strategyKey":"standard_settlement","customerCode":"GONGCHENG_UNIV"}
                """);
        seedTemplateIfMissing("settlement", "九院结款函独立折扣", """
                {"settlementDiscountRows":true}
                """, """
                {"strategyKey":"standard_settlement","customerCode":"JIUYUAN"}
                """);
        seedTemplateIfMissing("settlement", "东大肛肠结款函独立折扣", """
                {"settlementDiscountRows":true}
                """, """
                {"strategyKey":"standard_settlement","customerCode":"DONGDA"}
                """);
        seedTemplateIfMissing("settlement", "先锋路结款函独立折扣", """
                {"settlementDiscountRows":true}
                """, """
                {"strategyKey":"standard_settlement","customerCode":"XIANFENGLU"}
                """);
        seedTemplateIfMissing("bill", "国药汽轮机账单", """
                {"keepColumns":["发货日期","单号","类型","包类别号","包名","器械数","包数","单价","总价"]}
                """, """
                {"strategyKey":"guoyao_bill","customerCode":"GUOYAO_MAIN"}
                """);
        seedTemplateIfMissing("daily", "远东日结导出骨架", """
                {"columns":["日期","包数","把数","灭菌费","物流费","合计"]}
                """, """
                {"strategyKey":"daily_split","customerCode":"YUANDONG-XN"}
                """);
        seedTemplateIfMissing("dept_summary", "市五院分科室汇总骨架", """
                {"columns":["科室","类型","行数","包数","把数","毛额","调整额","净额"]}
                """, """
                {"strategyKey":"standard_dept_summary","customerCode":"HRB-WY"}
                """);
        seedTemplateIfMissing("instrument_audit", "中医三院把数表骨架", """
                {"columns":["科室","包名","把数","包数"]}
                """, """
                {"strategyKey":"instrument_audit","customerCode":"ZY3-DIANLI"}
                """);
    }

    private void seedTemplateIfMissing(String templateType, String name, String columnMapping, String sheetConfig) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM export_template WHERE customer_id IS NULL AND template_type = ? AND name = ?",
                Integer.class,
                templateType,
                name);
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO export_template (customer_id, template_type, name, storage_path, column_mapping, sheet_config, is_active) "
                        + "VALUES (NULL, ?, ?, '', ?, ?, 1)",
                templateType,
                name,
                columnMapping.trim(),
                sheetConfig.trim());
    }

    private void seedDefaultExportTemplates() {
        if (!tableExists("export_template")) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM export_template", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO export_template (customer_id, template_type, name, storage_path, column_mapping, sheet_config, is_active)
                VALUES (NULL, 'bill', '系统默认账单', '',
                        '{}',
                        '{"strategyKey":"standard_bill"}', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO export_template (customer_id, template_type, name, storage_path, column_mapping, sheet_config, is_active)
                VALUES (NULL, 'settlement', '系统默认结款函', '',
                        '{}',
                        '{"strategyKey":"standard_settlement"}', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO export_template (customer_id, template_type, name, storage_path, column_mapping, sheet_config, is_active)
                VALUES (NULL, 'bill', '省二南岗账单骨架', '',
                        '{"keepColumns":["发货日期","单号","类型","包类别号","包名","器械数","包数","单价","总价"]}',
                        '{"strategyKey":"sheng_er_bill","customerCode":"SHENG_ER_NANGANG"}', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO export_template (customer_id, template_type, name, storage_path, column_mapping, sheet_config, is_active)
                VALUES (NULL, 'bill', '道外人民账单骨架', '',
                        '{"removeColumns":["器械数","备注","差额"]}',
                        '{"strategyKey":"daowai_bill","customerCode":"DAOWAI"}', 1)
                """);
        log.info("Seeded default export_template rows");
    }

    private void seedBillingEnabledForExistingCustomers() {
        if (!tableExists("customer") || !columnExists("customer", "billing_enabled")) {
            return;
        }
        if (!tableExists("customer_product_rule") || !tableExists("sys_setting")) {
            return;
        }
        Integer marker = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_setting WHERE setting_key='billing_auto_enable_product_rules_v1'",
                Integer.class);
        if (marker != null && marker > 0) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE customer c SET billing_enabled = 1 "
                        + "WHERE billing_enabled = 0 AND EXISTS ("
                        + "SELECT 1 FROM customer_product_rule r WHERE r.customer_id = c.id)");
        jdbcTemplate.update(
                "INSERT INTO sys_setting (setting_key, setting_value, description) "
                        + "VALUES ('billing_auto_enable_product_rules_v1', 'true', "
                        + "'一次性：有 product_rule 的客户默认开启 billing')");
    }

    private void migrateSysSettingTable() {
        createTableIfMissing("sys_setting", """
                CREATE TABLE sys_setting (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    setting_key VARCHAR(120) NOT NULL UNIQUE,
                    setting_value JSON NOT NULL,
                    description VARCHAR(500) NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void migrateCustomerMasterTables() {
        createTableIfMissing("customer", """
                CREATE TABLE customer (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(64) NOT NULL UNIQUE,
                    canonical_name VARCHAR(200) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'active',
                    cap_mode VARCHAR(20) NULL,
                    charge_double_bag_when_capped TINYINT(1) DEFAULT 0,
                    default_rule_id BIGINT NULL,
                    billing_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '特色账单开关',
                    notes TEXT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_customer_name (canonical_name),
                    INDEX idx_customer_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        createTableIfMissing("customer_alias", """
                CREATE TABLE customer_alias (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    alias VARCHAR(200) NOT NULL,
                    match_type VARCHAR(20) NOT NULL DEFAULT 'contains',
                    source VARCHAR(20) NOT NULL DEFAULT 'manual',
                    priority INT NOT NULL DEFAULT 100,
                    is_active TINYINT(1) DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_customer_alias (alias),
                    INDEX idx_alias_customer (customer_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        createTableIfMissing("customer_discount", """
                CREATE TABLE customer_discount (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    name VARCHAR(120) NOT NULL,
                    discount_rate DECIMAL(6,4) NOT NULL,
                    apply_stage VARCHAR(30) NOT NULL DEFAULT 'after_base',
                    skip_when_fixed_price TINYINT(1) DEFAULT 1,
                    category_filter JSON NULL,
                    product_keyword_filter JSON NULL,
                    effective_from DATE NULL,
                    effective_to DATE NULL,
                    priority INT NOT NULL DEFAULT 100,
                    is_active TINYINT(1) DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_discount_customer (customer_id, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        createTableIfMissing("customer_product_rule", """
                CREATE TABLE customer_product_rule (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    rule_type VARCHAR(40) NOT NULL,
                    match_mode VARCHAR(20) NOT NULL DEFAULT 'first',
                    name VARCHAR(200) NOT NULL,
                    priority INT NOT NULL DEFAULT 100,
                    product_id BIGINT NULL,
                    keywords JSON NULL,
                    exclude_keywords JSON NULL,
                    materials JSON NULL,
                    temperature VARCHAR(10) NULL COMMENT 'HT/LT/ANY 温度条件',
                    bag_size_equals INT NULL,
                    max_bag_size_exclusive INT NULL,
                    min_instrument_count INT NULL,
                    max_instrument_count INT NULL,
                    price DECIMAL(12,4) NULL,
                    accepted_prices JSON NULL,
                    fee DECIMAL(12,4) NULL,
                    multiplier DECIMAL(8,4) NULL,
                    threshold INT NULL,
                    fold_ratio DECIMAL(8,4) NULL,
                    skip_packaging TINYINT(1) DEFAULT 0,
                    skip_discount TINYINT(1) DEFAULT 0,
                    is_active TINYINT(1) DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_cpr_customer_priority (customer_id, priority, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void migrateProductMasterTables() {
        createTableIfMissing("product_category", """
                CREATE TABLE product_category (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(64) NOT NULL UNIQUE,
                    name VARCHAR(120) NOT NULL,
                    parent_id BIGINT NULL,
                    pricing_path VARCHAR(40) NOT NULL,
                    sort_order INT DEFAULT 0,
                    is_active TINYINT(1) DEFAULT 1,
                    deleted_at DATETIME NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_product_category_parent (parent_id),
                    INDEX idx_product_category_active (is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        createTableIfMissing("product", """
                CREATE TABLE product (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    category_id BIGINT NOT NULL,
                    sku_code VARCHAR(64) NULL,
                    name VARCHAR(200) NOT NULL,
                    pricing_mode VARCHAR(40) NULL,
                    public_price DECIMAL(12,2) NULL,
                    original_price DECIMAL(12,2) NULL,
                    priority INT NOT NULL DEFAULT 100,
                    is_active TINYINT(1) DEFAULT 1,
                    deleted_at DATETIME NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_product_category (category_id),
                    INDEX idx_product_active (is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        createTableIfMissing("product_match_rule", """
                CREATE TABLE product_match_rule (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    product_id BIGINT NOT NULL,
                    match_type VARCHAR(20) NOT NULL,
                    target_field VARCHAR(40) NULL,
                    pattern_value VARCHAR(500) NULL,
                    match_fields JSON NULL,
                    conditions_json JSON NULL,
                    priority INT NOT NULL DEFAULT 100,
                    is_active TINYINT(1) DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_match_rule_product (product_id, priority)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        createTableIfMissing("product_alias", """
                CREATE TABLE product_alias (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    product_id BIGINT NOT NULL,
                    alias VARCHAR(200) NOT NULL,
                    match_type VARCHAR(20) NOT NULL DEFAULT 'CONTAINS',
                    priority INT NOT NULL DEFAULT 100,
                    is_active TINYINT(1) DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_alias_product (product_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void migrateProductPriceColumns() {
        addColumnIfMissing("product", "public_price", "public_price DECIMAL(12,2) NULL COMMENT '公开价格'");
        addColumnIfMissing("product", "original_price", "original_price DECIMAL(12,2) NULL COMMENT '原价'");
        addColumnIfMissing("product", "family_code", "family_code VARCHAR(64) NULL COMMENT '产品族编码'");
        addColumnIfMissing("product", "variant_count", "variant_count INT NOT NULL DEFAULT 0 COMMENT '变体数量'");
    }

    private void migrateProductVariantTable() {
        createTableIfMissing("product_variant", """
                CREATE TABLE product_variant (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    product_id BIGINT NOT NULL,
                    sku_code VARCHAR(64) NOT NULL,
                    spec_fingerprint VARCHAR(64) NOT NULL,
                    pack_name VARCHAR(300) NOT NULL,
                    type VARCHAR(120) NULL,
                    package_material VARCHAR(120) NULL,
                    category_no VARCHAR(120) NULL,
                    family_name_parsed VARCHAR(200) NULL,
                    spec_suffix VARCHAR(200) NULL,
                    instrument_count_hint INT NULL,
                    order_no_pattern VARCHAR(32) NULL,
                    bag_material_class VARCHAR(40) NULL,
                    bag_temp_class VARCHAR(10) NULL,
                    bag_width_mm INT NULL,
                    bag_height_mm INT NULL,
                    bag_size_label VARCHAR(32) NULL,
                    display_name VARCHAR(400) NOT NULL,
                    public_price DECIMAL(12,2) NULL,
                    original_price DECIMAL(12,2) NULL,
                    price_sample_count INT NOT NULL DEFAULT 0,
                    occurrence_count INT NOT NULL DEFAULT 0,
                    is_active TINYINT(1) NOT NULL DEFAULT 1,
                    deleted_at DATETIME NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_variant_sku (sku_code),
                    UNIQUE KEY uk_variant_fingerprint (spec_fingerprint),
                    INDEX idx_variant_product (product_id, is_active),
                    INDEX idx_variant_pack_name (pack_name(100))
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void migrateProductMatchRuleVariantColumn() {
        addColumnIfMissing("product_match_rule", "variant_id",
                "variant_id BIGINT NULL COMMENT '变体级规则' AFTER product_id");
    }

    private void migrateCustomerProductRuleVariantColumn() {
        addColumnIfMissing("customer_product_rule", "variant_id",
                "variant_id BIGINT NULL COMMENT '规格级规则' AFTER product_id");
    }

    private void migratePricingRuleRevisionTable() {
        createTableIfMissing("hospital_pricing_rule_revision", """
                CREATE TABLE hospital_pricing_rule_revision (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    rule_id BIGINT NOT NULL,
                    version VARCHAR(50) NOT NULL,
                    rules_json LONGTEXT NOT NULL,
                    created_by VARCHAR(120) NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_revision_rule (rule_id, created_at DESC)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void seedInstrumentPackCategory() {
        if (!tableExists("product_category")) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_category WHERE code = 'INSTRUMENT_PACK'",
                Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO product_category (code, name, pricing_path, sort_order, is_active, created_at, updated_at) "
                        + "VALUES ('INSTRUMENT_PACK', '器械包', 'standard', 25, 1, NOW(), NOW())");
        log.info("Seeded INSTRUMENT_PACK category");
    }

    private void createTableIfMissing(String tableName, String ddl) {
        if (tableExists(tableName)) {
            return;
        }
        jdbcTemplate.execute(ddl);
        log.info("Created missing table {}", tableName);
    }

    private void migratePricingRuleTable() {
        addColumnIfMissing("hospital_pricing_rule", "name", "name VARCHAR(120) NULL");
        addColumnIfMissing("hospital_pricing_rule", "version", "version VARCHAR(50) NULL");
        addColumnIfMissing("hospital_pricing_rule", "hospital_name", "hospital_name VARCHAR(120) NULL");
        addColumnIfMissing("hospital_pricing_rule", "plan_name", "plan_name VARCHAR(120) NULL");
        addColumnIfMissing("hospital_pricing_rule", "description", "description TEXT NULL");
        addColumnIfMissing("hospital_pricing_rule", "is_active", "is_active TINYINT(1) DEFAULT 0");
        addColumnIfMissing("hospital_pricing_rule", "rules_json", "rules_json LONGTEXT NULL");
        addColumnIfMissing("hospital_pricing_rule", "created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        addColumnIfMissing("hospital_pricing_rule", "updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
    }

    private void migrateReconciliationJobTable() {
        addColumnIfMissing("hospital_reconciliation_job", "hospital_name", "hospital_name VARCHAR(200) NULL");
        addColumnIfMissing("hospital_reconciliation_job", "source_file_name", "source_file_name VARCHAR(255) NULL");
        addColumnIfMissing("hospital_reconciliation_job", "source_file_path", "source_file_path VARCHAR(500) NULL");
        addColumnIfMissing("hospital_reconciliation_job", "source_file_size", "source_file_size BIGINT NULL");
        addColumnIfMissing("hospital_reconciliation_job", "rule_id", "rule_id BIGINT NULL");
        addColumnIfMissing("hospital_reconciliation_job", "rule_name", "rule_name VARCHAR(120) NULL");
        addColumnIfMissing("hospital_reconciliation_job", "plan_name", "plan_name VARCHAR(120) NULL");
        addColumnIfMissing("hospital_reconciliation_job", "rule_version", "rule_version VARCHAR(50) NULL");
        addColumnIfMissing("hospital_reconciliation_job", "version_no", "version_no INT DEFAULT 1");
        addColumnIfMissing("hospital_reconciliation_job", "total_rows", "total_rows INT DEFAULT 0");
        addColumnIfMissing("hospital_reconciliation_job", "corrected_rows", "corrected_rows INT DEFAULT 0");
        addColumnIfMissing("hospital_reconciliation_job", "unchanged_rows", "unchanged_rows INT DEFAULT 0");
        addColumnIfMissing("hospital_reconciliation_job", "warning_rows", "warning_rows INT DEFAULT 0");
        addColumnIfMissing("hospital_reconciliation_job", "skipped_rows", "skipped_rows INT DEFAULT 0");
        addColumnIfMissing("hospital_reconciliation_job", "total_difference", "total_difference DECIMAL(12,2) DEFAULT 0");
        addColumnIfMissing("hospital_reconciliation_job", "review_status", "review_status VARCHAR(20) DEFAULT 'pending'");
        addColumnIfMissing("hospital_reconciliation_job", "review_comment", "review_comment TEXT NULL");
        addColumnIfMissing("hospital_reconciliation_job", "operator_name", "operator_name VARCHAR(120) NULL");
        addColumnIfMissing("hospital_reconciliation_job", "reviewer_name", "reviewer_name VARCHAR(120) NULL");
        addColumnIfMissing("hospital_reconciliation_job", "rows_json", "rows_json LONGTEXT NULL");
        addColumnIfMissing("hospital_reconciliation_job", "source_date_range", "source_date_range VARCHAR(500) NULL");
        addColumnIfMissing("hospital_reconciliation_job", "sheet_names", "sheet_names TEXT NULL");
        addColumnIfMissing("hospital_reconciliation_job", "sheet_row_counts", "sheet_row_counts TEXT NULL");
        addColumnIfMissing("hospital_reconciliation_job", "sheet_warning_counts", "sheet_warning_counts TEXT NULL");
        addColumnIfMissing("hospital_reconciliation_job", "logistics_trip_count", "logistics_trip_count INT NULL");
        addColumnIfMissing("hospital_reconciliation_job", "logistics_fee", "logistics_fee DECIMAL(12,2) NULL");
        addColumnIfMissing("hospital_reconciliation_job", "logistics_breakdown", "logistics_breakdown JSON NULL");
        addColumnIfMissing("hospital_reconciliation_job", "original_total_price", "original_total_price DECIMAL(12,2) DEFAULT 0");
        addColumnIfMissing("hospital_reconciliation_job", "corrected_total_price", "corrected_total_price DECIMAL(12,2) DEFAULT 0");
        addColumnIfMissing("hospital_reconciliation_job", "settlement_adjustment", "settlement_adjustment DECIMAL(12,2) NULL COMMENT '月度结算调整额'");
        addColumnIfMissing("hospital_reconciliation_job", "monthly_breakdown", "monthly_breakdown JSON NULL COMMENT '月度结算明细'");
        addColumnIfMissing("hospital_reconciliation_job", "created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
        addColumnIfMissing("hospital_reconciliation_job", "updated_at", "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
    }

    private void migrateReconciliationRowTable() {
        addColumnIfMissing("hospital_reconciliation_row", "job_id", "job_id BIGINT NULL");
        addColumnIfMissing("hospital_reconciliation_row", "sheet_name", "sheet_name VARCHAR(120) NULL");
        addColumnIfMissing("hospital_reconciliation_row", "row_number", "`row_number` INT NULL");
        addColumnIfMissing("hospital_reconciliation_row", "delivery_date", "delivery_date VARCHAR(50) NULL");
        addColumnIfMissing("hospital_reconciliation_row", "order_no", "order_no VARCHAR(100) NULL");
        addColumnIfMissing("hospital_reconciliation_row", "type", "`type` VARCHAR(120) NULL");
        addColumnIfMissing("hospital_reconciliation_row", "category_no", "category_no VARCHAR(120) NULL");
        addColumnIfMissing("hospital_reconciliation_row", "pack_name", "pack_name VARCHAR(300) NULL");
        addColumnIfMissing("hospital_reconciliation_row", "package_material", "package_material VARCHAR(120) NULL");
        addColumnIfMissing("hospital_reconciliation_row", "pack_count", "pack_count INT DEFAULT 0");
        addColumnIfMissing("hospital_reconciliation_row", "instrument_count", "instrument_count INT DEFAULT 0");
        addColumnIfMissing("hospital_reconciliation_row", "unit_price", "unit_price DECIMAL(12,2) NULL");
        addColumnIfMissing("hospital_reconciliation_row", "total_price", "total_price DECIMAL(12,2) NULL");
        addColumnIfMissing("hospital_reconciliation_row", "expected_unit_price", "expected_unit_price DECIMAL(12,2) NULL");
        addColumnIfMissing("hospital_reconciliation_row", "corrected_total_price", "corrected_total_price DECIMAL(12,2) NULL");
        addColumnIfMissing("hospital_reconciliation_row", "difference", "difference DECIMAL(12,2) NULL");
        addColumnIfMissing("hospital_reconciliation_row", "status", "`status` VARCHAR(20) NULL");
        addColumnIfMissing("hospital_reconciliation_row", "pricing_rule", "pricing_rule VARCHAR(200) NULL");
        addColumnIfMissing("hospital_reconciliation_row", "notes_json", "notes_json LONGTEXT NULL");
        addColumnIfMissing("hospital_reconciliation_row", "matched_product_id",
                "matched_product_id BIGINT NULL COMMENT '匹配产品族ID'");
        addColumnIfMissing("hospital_reconciliation_row", "matched_variant_id",
                "matched_variant_id BIGINT NULL COMMENT '匹配变体ID'");
        addColumnIfMissing("hospital_reconciliation_row", "pricing_path",
                "pricing_path VARCHAR(40) NULL COMMENT '产品计价路径'");
        addColumnIfMissing("hospital_reconciliation_row", "is_urgent",
                "is_urgent TINYINT(1) NOT NULL DEFAULT 0 COMMENT '加急标记'");
        addColumnIfMissing("hospital_reconciliation_row", "created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
    }

    private void migrateReconciliationJobUrgentColumns() {
        addColumnIfMissing("hospital_reconciliation_job", "urgent_breakdown",
                "urgent_breakdown JSON NULL COMMENT '加急费明细'");
        addColumnIfMissing("hospital_reconciliation_job", "deduction_breakdown",
                "deduction_breakdown JSON NULL COMMENT '设备抵扣明细'");
    }

    private void migrateExportLogTable() {
        addColumnIfMissing("hospital_reconciliation_export_log", "job_id", "job_id BIGINT NULL");
        addColumnIfMissing("hospital_reconciliation_export_log", "export_type", "export_type VARCHAR(50) NULL");
        addColumnIfMissing("hospital_reconciliation_export_log", "file_name", "file_name VARCHAR(255) NULL");
        addColumnIfMissing("hospital_reconciliation_export_log", "file_path", "file_path VARCHAR(500) NULL");
        addColumnIfMissing("hospital_reconciliation_export_log", "operator_name", "operator_name VARCHAR(120) NULL");
        addColumnIfMissing("hospital_reconciliation_export_log", "created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
    }

    private void addColumnIfMissing(String tableName, String columnName, String columnDefinition) {
        if (!tableExists(tableName) || columnExists(tableName, columnName)) {
            return;
        }
        String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnDefinition;
        jdbcTemplate.execute(sql);
        log.info("Added missing column {}.{}", tableName, columnName);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }
}
