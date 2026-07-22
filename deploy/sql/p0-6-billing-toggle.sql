-- Idempotent P0.6: enable billing for acceptance-pass hospitals (generated from phase-batch-p0.6.json)
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE customer SET billing_enabled = 1 WHERE code IN ('ZYY-D1', 'ZY3-DIANLI', 'GUOYAO-MAIN', 'GUOYAO-2', 'GUOYAO-3', 'HRB-2ND', 'HRB-WY', 'HRB-WY-EM', 'XINFA-HSZ', 'SHENG-YY-NG', 'SHENG-YY-XF', 'ZUYAN-NG', 'ZUYAN-SF', 'ZUYAN-XA', 'NG-FUCHAN', 'SHKF-YY', 'DAOWAI-RM', 'TAIPING-RM', 'SANJING-SB', 'VICTORIA', 'JIUZHOU-FK', 'HULAN-HSZ', 'HULAN-TCM', 'ZYY-D2-NG', 'ZYY-D2-HN', 'RENSHENG', 'HRB-HX-EYE', 'BINGCHENG-YM', 'XF-ZYY', 'WJ-HLJ-ZD', 'YUEMEI-FH', 'ERYY-NG', 'ERYY-SB', 'HULAN-RM', 'HRB-HSZ', 'HRB-HIT');
UPDATE customer SET billing_enabled = 0 WHERE code NOT IN ('ZYY-D1', 'ZY3-DIANLI', 'GUOYAO-MAIN', 'GUOYAO-2', 'GUOYAO-3', 'HRB-2ND', 'HRB-WY', 'HRB-WY-EM', 'XINFA-HSZ', 'SHENG-YY-NG', 'SHENG-YY-XF', 'ZUYAN-NG', 'ZUYAN-SF', 'ZUYAN-XA', 'NG-FUCHAN', 'SHKF-YY', 'DAOWAI-RM', 'TAIPING-RM', 'SANJING-SB', 'VICTORIA', 'JIUZHOU-FK', 'HULAN-HSZ', 'HULAN-TCM', 'ZYY-D2-NG', 'ZYY-D2-HN', 'RENSHENG', 'HRB-HX-EYE', 'BINGCHENG-YM', 'XF-ZYY', 'WJ-HLJ-ZD', 'YUEMEI-FH', 'ERYY-NG', 'ERYY-SB', 'HULAN-RM', 'HRB-HSZ', 'HRB-HIT');
INSERT INTO sys_setting (setting_key, setting_value, description)
SELECT 'billing_seed_batch_p0_6_v1', 'true', 'P0.6 billing toggle (deploy/reapply script)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_setting WHERE setting_key = 'billing_seed_batch_p0_6_v1');
UPDATE sys_setting SET setting_value = 'true' WHERE setting_key = 'billing_seed_batch_p0_6_v1';
COMMIT;
