-- Phase 1-2: Product category & structured product matching tables
-- Safe to run multiple times (IF NOT EXISTS)

CREATE TABLE IF NOT EXISTS product_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE COMMENT 'SMALL_ITEM|DRESSING|HT_PAPER|LT_PAPER|...',
    name VARCHAR(120) NOT NULL,
    parent_id BIGINT NULL,
    pricing_path VARCHAR(40) NOT NULL COMMENT 'standard|dressing_cotton|dressing_nonwoven|fixed|legacy_per_piece',
    sort_order INT DEFAULT 0,
    is_active TINYINT(1) DEFAULT 1,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product_category_parent (parent_id),
    INDEX idx_product_category_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    sku_code VARCHAR(64) NULL,
    name VARCHAR(200) NOT NULL,
    pricing_mode VARCHAR(40) NULL COMMENT 'override pricing path from category',
    public_price DECIMAL(12,2) NULL COMMENT '公开价格',
    original_price DECIMAL(12,2) NULL COMMENT '原价',
    priority INT NOT NULL DEFAULT 100,
    is_active TINYINT(1) DEFAULT 1,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES product_category(id),
    INDEX idx_product_category (category_id),
    INDEX idx_product_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product_match_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    match_type VARCHAR(20) NOT NULL COMMENT 'EXACT_NAME|CONTAINS|REGEX|COMPOSITE',
    target_field VARCHAR(40) NULL COMMENT 'pack_name|type|package_material|category_no|instrument_count',
    pattern_value VARCHAR(500) NULL,
    match_fields JSON NULL COMMENT 'fields to check for simple match types',
    conditions_json JSON NULL COMMENT '[{field, operator, value}] for COMPOSITE',
    priority INT NOT NULL DEFAULT 100,
    is_active TINYINT(1) DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    INDEX idx_match_rule_product (product_id, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product_alias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    alias VARCHAR(200) NOT NULL,
    match_type VARCHAR(20) NOT NULL DEFAULT 'CONTAINS' COMMENT 'EXACT|CONTAINS',
    priority INT NOT NULL DEFAULT 100,
    is_active TINYINT(1) DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    INDEX idx_alias_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
