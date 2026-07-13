-- ============================================================
-- Schema for Hospital Backend
-- All table definitions for MyBatis-based data access
-- ============================================================

-- 1. System user table
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    is_active TINYINT(1) DEFAULT 1,
    is_superuser TINYINT(1) DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. System role table
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. System menu table
CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    menu_type VARCHAR(50),
    icon VARCHAR(100),
    path VARCHAR(200) NOT NULL,
    menu_order INT,
    parent_id BIGINT DEFAULT 0,
    is_hidden TINYINT(1) DEFAULT 0,
    component VARCHAR(200),
    keepalive TINYINT(1) DEFAULT 1,
    redirect VARCHAR(200),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. User-Role join table
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Role-Menu join table
CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id),
    FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    FOREIGN KEY (menu_id) REFERENCES sys_menu(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Hospital pricing rule table
CREATE TABLE IF NOT EXISTS hospital_pricing_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    version VARCHAR(50) NOT NULL,
    hospital_name VARCHAR(120),
    plan_name VARCHAR(120),
    description TEXT,
    is_active TINYINT(1) DEFAULT 0,
    rules_json LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. Hospital reconciliation job table
CREATE TABLE IF NOT EXISTS hospital_reconciliation_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_name VARCHAR(200) NOT NULL,
    source_file_name VARCHAR(255) NOT NULL,
    source_file_path VARCHAR(500) NOT NULL,
    source_file_size BIGINT,
    rule_id BIGINT,
    rule_name VARCHAR(120),
    plan_name VARCHAR(120),
    rule_version VARCHAR(50),
    INDEX idx_recon_job_rule_id (rule_id),
    version_no INT DEFAULT 1,
    total_rows INT DEFAULT 0,
    corrected_rows INT DEFAULT 0,
    unchanged_rows INT DEFAULT 0,
    warning_rows INT DEFAULT 0,
    skipped_rows INT DEFAULT 0,
    total_difference DECIMAL(12,2) DEFAULT 0,
    review_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    review_comment TEXT,
    operator_name VARCHAR(120) NOT NULL,
    reviewer_name VARCHAR(120),
    rows_json LONGTEXT,
    source_date_range VARCHAR(500),
    sheet_names TEXT,
    sheet_row_counts TEXT,
    sheet_warning_counts TEXT,
    logistics_trip_count INT,
    logistics_fee DECIMAL(12,2),
    logistics_breakdown JSON,
    original_total_price DECIMAL(12,2) DEFAULT 0,
    corrected_total_price DECIMAL(12,2) DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. Hospital reconciliation row detail table
CREATE TABLE IF NOT EXISTS hospital_reconciliation_row (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    sheet_name VARCHAR(120) NOT NULL,
    `row_number` INT NOT NULL,
    delivery_date VARCHAR(50),
    order_no VARCHAR(100),
    `type` VARCHAR(120),
    category_no VARCHAR(120),
    pack_name VARCHAR(300),
    package_material VARCHAR(120),
    pack_count INT DEFAULT 0,
    instrument_count INT DEFAULT 0,
    unit_price DECIMAL(12,2),
    total_price DECIMAL(12,2),
    expected_unit_price DECIMAL(12,2),
    corrected_total_price DECIMAL(12,2),
    difference DECIMAL(12,2),
    `status` VARCHAR(20) NOT NULL,
    pricing_rule VARCHAR(200),
    notes_json LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_recon_row_job_id (job_id),
    INDEX idx_recon_row_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. Product category table
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

-- 11. Product master table
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

-- 12. Product structured match rules
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

-- 13. Product alias / synonym entries
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

-- 14. Hospital reconciliation export log table
CREATE TABLE IF NOT EXISTS hospital_reconciliation_export_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    export_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    operator_name VARCHAR(120) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_export_log_job_id (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. System settings key-value store
CREATE TABLE IF NOT EXISTS sys_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(120) NOT NULL UNIQUE,
    setting_value JSON NOT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
