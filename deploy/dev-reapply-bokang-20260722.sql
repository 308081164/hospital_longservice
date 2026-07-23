-- 开发库：强制重新执行 billing_seed_bokang_20260722_v1（HRB-HEU、九院结款9折等）
-- 用法：docker exec -i hospital-mysql mysql -uroot -p... hospital < deploy/dev-reapply-bokang-20260722.sql
-- 然后：docker compose restart backend

DELETE FROM sys_setting WHERE setting_key = 'billing_seed_bokang_20260722_v1';
