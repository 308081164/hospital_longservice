-- Phase 2-4: product_variant、规则版本、对账行匹配字段
-- 幂等：CREATE IF NOT EXISTS；ALTER 重复执行时 entrypoint 使用 mysql --force 可忽略已存在列错误

CREATE TABLE IF NOT EXISTS product_variant (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hospital_pricing_rule_revision (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    version VARCHAR(50) NOT NULL,
    rules_json LONGTEXT NOT NULL,
    created_by VARCHAR(120) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_revision_rule (rule_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE product ADD COLUMN family_code VARCHAR(64) NULL COMMENT '产品族编码' AFTER original_price;
ALTER TABLE product ADD COLUMN variant_count INT NOT NULL DEFAULT 0 COMMENT '变体数量' AFTER family_code;

ALTER TABLE product_match_rule ADD COLUMN variant_id BIGINT NULL COMMENT '变体级规则' AFTER product_id;
ALTER TABLE customer_product_rule ADD COLUMN variant_id BIGINT NULL COMMENT '规格级规则' AFTER product_id;

ALTER TABLE hospital_reconciliation_job ADD COLUMN rule_version VARCHAR(50) NULL;

ALTER TABLE hospital_reconciliation_row ADD COLUMN matched_product_id BIGINT NULL COMMENT '匹配产品族ID';
ALTER TABLE hospital_reconciliation_row ADD COLUMN matched_variant_id BIGINT NULL COMMENT '匹配变体ID';
ALTER TABLE hospital_reconciliation_row ADD COLUMN pricing_path VARCHAR(40) NULL COMMENT '产品计价路径';
