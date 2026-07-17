-- Phase 3: export_template — per-customer / global export template registry
CREATE TABLE IF NOT EXISTS export_template (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
