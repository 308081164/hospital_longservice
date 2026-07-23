-- 开发库重跑 S3 种子 phase-s3-pdf-align-20260722（backend 重启后 BillingSeedMigrationRunner 幂等导入）
DELETE FROM sys_setting WHERE setting_key = 'billing_seed_s3_pdf_20260722_v1';
