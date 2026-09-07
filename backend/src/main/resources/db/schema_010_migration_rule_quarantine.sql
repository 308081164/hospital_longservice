-- 规则隔离存档：manifest reconcile 将非 manifest 规则停用并快照，替代硬删。
-- 幂等：CREATE TABLE IF NOT EXISTS

CREATE TABLE IF NOT EXISTS customer_product_rule_tombstone (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_rule_id BIGINT NOT NULL COMMENT 'customer_product_rule.id',
    customer_id BIGINT NOT NULL,
    customer_code VARCHAR(64) NULL,
    rule_name VARCHAR(200) NOT NULL,
    rule_snapshot JSON NOT NULL COMMENT '隔离时规则完整快照',
    manifest_hash VARCHAR(128) NULL,
    quarantine_reason VARCHAR(500) NOT NULL,
    quarantined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    restored_at DATETIME NULL,
    restored_by VARCHAR(120) NULL,
    INDEX idx_tombstone_customer (customer_id, restored_at),
    INDEX idx_tombstone_rule (product_rule_id),
    INDEX idx_tombstone_active (restored_at, quarantined_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非 manifest 规则隔离存档';
