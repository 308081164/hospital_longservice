-- Phase 4: System settings key-value store
-- Safe to run multiple times (IF NOT EXISTS)

CREATE TABLE IF NOT EXISTS sys_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(120) NOT NULL UNIQUE COMMENT '如 company.name, dressing.cotton.20cm',
    setting_value JSON NOT NULL COMMENT 'JSON 值',
    description VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
