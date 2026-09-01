-- Phase 1: Customer master data tables
-- Safe to run multiple times (IF NOT EXISTS)

CREATE TABLE IF NOT EXISTS customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE COMMENT '稳定业务编码，如 HRB-WY',
    canonical_name VARCHAR(200) NOT NULL COMMENT '规范名称',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    cap_mode VARCHAR(20) NULL COMMENT 'none|standard',
    charge_double_bag_when_capped TINYINT(1) DEFAULT 0,
    default_rule_id BIGINT NULL COMMENT '默认绑定的 hospital_pricing_rule.id',
    notes TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_customer_name (canonical_name),
    INDEX idx_customer_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customer_alias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    alias VARCHAR(200) NOT NULL,
    match_type VARCHAR(20) NOT NULL DEFAULT 'contains' COMMENT 'exact|contains|regex',
    source VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT 'engine|bokang_job|manual',
    priority INT NOT NULL DEFAULT 100,
    is_active TINYINT(1) DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE,
    UNIQUE KEY uk_customer_alias (alias),
    INDEX idx_alias_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customer_discount (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    discount_rate DECIMAL(6,4) NOT NULL COMMENT '如 0.7000',
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
    FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE,
    INDEX idx_discount_customer (customer_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customer_product_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    rule_type VARCHAR(40) NOT NULL COMMENT 'FIXED_PRICE|PRICE_PER_INSTRUMENT|ADD_FEE|MULTIPLY|FOLD|EXTRA_FEE',
    name VARCHAR(200) NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    product_id BIGINT NULL,
    keywords JSON NULL,
    materials JSON NULL,
    bag_size_equals INT NULL,
    max_bag_size_exclusive INT NULL,
    min_instrument_count INT NULL,
    max_instrument_count INT NULL,
    price DECIMAL(12,4) NULL,
    fee DECIMAL(12,4) NULL,
    multiplier DECIMAL(8,4) NULL,
    threshold INT NULL,
    fold_ratio DECIMAL(8,4) NULL,
    keyword_match_mode VARCHAR(20) NOT NULL DEFAULT 'exact_token' COMMENT 'exact_token|contains',
    skip_packaging TINYINT(1) DEFAULT 0,
    skip_discount TINYINT(1) DEFAULT 0,
    is_active TINYINT(1) DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE,
    INDEX idx_cpr_customer_priority (customer_id, priority, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
