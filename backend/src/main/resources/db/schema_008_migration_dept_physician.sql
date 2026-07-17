-- Phase 8: department & physician master data (all hospitals)

CREATE TABLE IF NOT EXISTS department_entry (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS physician_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    physician_name VARCHAR(120) NOT NULL COMMENT '医生姓名',
    department_entry_id BIGINT NULL COMMENT '关联科室主数据',
    department_name VARCHAR(120) NULL COMMENT '科室名称（冗余便于展示）',
    code VARCHAR(60) NULL COMMENT '医生编码',
    notes VARCHAR(500) NULL,
    usage_count INT NOT NULL DEFAULT 0 COMMENT '使用统计占位',
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_physician_customer_name (customer_id, physician_name),
    INDEX idx_physician_customer_dept (customer_id, department_entry_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
