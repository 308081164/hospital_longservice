-- Phase 5: logistics import, logistics card, customer group (settlement/logistics merge)

CREATE TABLE IF NOT EXISTS logistics_import (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    job_id BIGINT NULL COMMENT '关联对账任务',
    billing_month VARCHAR(7) NULL COMMENT 'YYYY-MM',
    trip_date DATE NOT NULL,
    route VARCHAR(200) NULL,
    trip_count INT NOT NULL DEFAULT 1,
    fee_amount DECIMAL(12,2) NULL COMMENT '可选固定金额，覆盖单价×次数',
    notes VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_li_customer_month (customer_id, billing_month),
    INDEX idx_li_job (job_id),
    INDEX idx_li_trip_date (trip_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS logistics_card (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL DEFAULT '默认物流卡',
    balance DECIMAL(12,2) NOT NULL DEFAULT 0,
    initial_balance DECIMAL(12,2) NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_lc_customer (customer_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS logistics_card_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL COMMENT 'RECHARGE|DEDUCT|ADJUST',
    amount DECIMAL(12,2) NOT NULL,
    balance_after DECIMAL(12,2) NOT NULL,
    job_id BIGINT NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_lct_card (card_id),
    INDEX idx_lct_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customer_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    group_type VARCHAR(40) NOT NULL DEFAULT 'settlement_merge' COMMENT 'settlement_merge|logistics_merge',
    config JSON NULL COMMENT '合并策略，如 splitMode=equal',
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cg_type (group_type, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customer_group_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    share_ratio DECIMAL(8,4) NULL COMMENT '自定义分摊比例，NULL=均分',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_cgm_group_customer (group_id, customer_id),
    INDEX idx_cgm_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
