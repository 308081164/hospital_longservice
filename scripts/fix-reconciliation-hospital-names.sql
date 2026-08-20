-- 预览：hospital_name 误存为科室名的对账任务
SELECT j.id,
       j.hospital_name,
       j.source_file_name,
       MIN(c.canonical_name) AS resolved_name
FROM hospital_reconciliation_job j
LEFT JOIN customer_alias ca
       ON ca.is_active = 1
      AND (
            REPLACE(REPLACE(j.source_file_name, '.xlsx', ''), '.xls', '') LIKE CONCAT('%', ca.alias, '%')
          )
LEFT JOIN customer c ON c.id = ca.customer_id
WHERE j.hospital_name REGEXP '^(手术室|门诊部|供应室|消毒供应|内镜中心|产房|病区|病房|ICU)'
GROUP BY j.id, j.hospital_name, j.source_file_name
ORDER BY j.id DESC
LIMIT 100;

-- 修复（确认预览结果后执行）
-- UPDATE hospital_reconciliation_job j
-- JOIN (
--   SELECT j2.id AS job_id, MIN(c.canonical_name) AS canonical_name
--   FROM hospital_reconciliation_job j2
--   JOIN customer_alias ca ON ca.is_active = 1
--     AND REPLACE(REPLACE(j2.source_file_name, '.xlsx', ''), '.xls', '') LIKE CONCAT('%', ca.alias, '%')
--   JOIN customer c ON c.id = ca.customer_id
--   WHERE j2.hospital_name REGEXP '^(手术室|门诊部|供应室|消毒供应|内镜中心|产房|病区|病房|ICU)'
--   GROUP BY j2.id
-- ) x ON x.job_id = j.id
-- SET j.hospital_name = x.canonical_name
-- WHERE x.canonical_name IS NOT NULL;
