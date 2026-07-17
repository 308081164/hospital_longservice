-- Phase 7: L3 — roster, external instruments, department allocation (M10–M12)

CREATE TABLE IF NOT EXISTS roster_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    doctor_name VARCHAR(120) NOT NULL COMMENT '医生姓名',
    department VARCHAR(120) NOT NULL COMMENT '归属科室',
    surgical_room VARCHAR(120) NULL COMMENT '手术室标识（可选）',
    notes VARCHAR(500) NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_roster_customer_doctor (customer_id, doctor_name),
    INDEX idx_roster_customer_dept (customer_id, department, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS external_instrument (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    reconciliation_job_id BIGINT NULL COMMENT 'NULL=价格目录/模板，非空=账期明细',
    category_no VARCHAR(120) NOT NULL COMMENT '包类别号（计价主键）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- allocation_result column added via SchemaMigrationRunner.addColumnIfMissing
