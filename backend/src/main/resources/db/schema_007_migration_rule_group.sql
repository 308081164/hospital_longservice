-- Phase 8: 规则组与变更审计
CREATE TABLE IF NOT EXISTS customer_billing_rule_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    group_code VARCHAR(60) NOT NULL DEFAULT 'default',
    group_name VARCHAR(120) NOT NULL DEFAULT '默认规则组',
    rules_json LONGTEXT NULL COMMENT '规则快照 JSON（与 customer_product_rule 双写过渡）',
    priority INT NOT NULL DEFAULT 100,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cbrg_customer_code (customer_id, group_code),
    INDEX idx_cbrg_customer (customer_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户计费规则组';

CREATE TABLE IF NOT EXISTS billing_rule_change_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    rule_group_id BIGINT NULL,
    product_rule_id BIGINT NULL,
    change_type VARCHAR(30) NOT NULL COMMENT 'CREATE/UPDATE/DELETE/IMPORT/COPY/SIMULATE',
    entity_type VARCHAR(30) NOT NULL DEFAULT 'PRODUCT_RULE',
    before_snapshot JSON NULL,
    after_snapshot JSON NULL,
    operator_name VARCHAR(120) NULL,
    change_summary VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_brcl_customer (customer_id, created_at),
    INDEX idx_brcl_group (rule_group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则变更审计日志';

-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS；重复执行时 entrypoint 使用 mysql --force 忽略 1060
ALTER TABLE customer_product_rule ADD COLUMN rule_group_id BIGINT NULL COMMENT '所属规则组' AFTER customer_id;
