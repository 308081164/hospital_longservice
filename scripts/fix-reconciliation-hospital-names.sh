#!/usr/bin/env bash
# 将 hospital_reconciliation_job 中误存为科室名的 hospital_name 纠正为客户规范名。
# 用法：在部署机或本地 MySQL 客户端执行（先备份数据库）。
#
# 逻辑：
# 1. 找出 hospital_name 疑似科室名的 job
# 2. 从 source_file_name 提取医院关键词
# 3. 用 customer_alias 匹配 canonical_name 并更新
#
# 示例：hospital_name=门诊部, source_file_name=东大肛肠3月账单.xlsx → 黑龙江东大肛肠

set -euo pipefail

MYSQL_CMD="${MYSQL_CMD:-docker exec -i hospital-mysql mysql -u root -p\"${MYSQL_ROOT_PASSWORD}\" hospital}"

cat <<'SQL'
-- 预览待修复记录
SELECT j.id, j.hospital_name, j.source_file_name, c.canonical_name AS resolved_name
FROM hospital_reconciliation_job j
LEFT JOIN customer_alias ca ON ca.is_active = 1
  AND (
    REPLACE(j.source_file_name, '.xlsx', '') LIKE CONCAT('%', ca.alias, '%')
    OR REPLACE(j.source_file_name, '.xls', '') LIKE CONCAT('%', ca.alias, '%')
  )
LEFT JOIN customer c ON c.id = ca.customer_id
WHERE j.hospital_name REGEXP '^(手术室|门诊部|供应室|消毒供应|内镜中心|产房|病区|病房|ICU)'
ORDER BY j.id DESC
LIMIT 100;

-- 执行修复（确认预览结果后再取消注释）
-- UPDATE hospital_reconciliation_job j
-- JOIN (
--   SELECT j2.id AS job_id, MIN(c.canonical_name) AS canonical_name
--   FROM hospital_reconciliation_job j2
--   JOIN customer_alias ca ON ca.is_active = 1
--     AND (
--       REPLACE(j2.source_file_name, '.xlsx', '') LIKE CONCAT('%', ca.alias, '%')
--       OR REPLACE(j2.source_file_name, '.xls', '') LIKE CONCAT('%', ca.alias, '%')
--     )
--   JOIN customer c ON c.id = ca.customer_id
--   WHERE j2.hospital_name REGEXP '^(手术室|门诊部|供应室|消毒供应|内镜中心|产房|病区|病房|ICU)'
--   GROUP BY j2.id
-- ) x ON x.job_id = j.id
-- SET j.hospital_name = x.canonical_name
-- WHERE x.canonical_name IS NOT NULL;
SQL

echo "Preview SQL above. Uncomment UPDATE block in scripts/fix-reconciliation-hospital-names.sql after review."
