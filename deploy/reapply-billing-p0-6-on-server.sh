#!/usr/bin/env bash
# 在生产服务器上重新应用 P0.6（36 院启用 billing，其余停用）
# 用法：cd /mnt/newdisk/app/Hospital && bash deploy/reapply-billing-p0-6-on-server.sh
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
MARKER="billing_seed_batch_p0_6_v1"
SEED_FILE="${DEPLOY_PATH}/backend/src/main/resources/billing-seeds/phase-batch-p0.6.json"

cd "$DEPLOY_PATH"
if [ ! -f .env ]; then
  echo "错误: .env 不存在" >&2
  exit 1
fi
# shellcheck disable=SC1091
set -a && source .env && set +a

if [ ! -f "$SEED_FILE" ]; then
  echo "错误: 种子文件不存在: $SEED_FILE" >&2
  echo "请先 git pull 或等待 CI 同步 backend 目录" >&2
  exit 1
fi

echo "==> 从 ${SEED_FILE} 生成 SQL"
python3 - "$SEED_FILE" "$MARKER" > /tmp/p0_6_apply.sql <<'PY'
import json, sys
seed_path, marker = sys.argv[1], sys.argv[2]
seed = json.load(open(seed_path, encoding="utf-8"))
codes = seed.get("enableBilling") or []
if not codes:
    raise SystemExit("enableBilling empty")
esc = lambda s: s.replace("\\", "\\\\").replace("'", "''")
in_list = ", ".join(f"'{esc(c)}'" for c in codes)
m = esc(marker)
lines = [
    "SET NAMES utf8mb4;",
    "START TRANSACTION;",
    f"UPDATE customer SET billing_enabled=1 WHERE code IN ({in_list});",
]
if seed.get("disableAllOthers"):
    lines.append(f"UPDATE customer SET billing_enabled=0 WHERE code NOT IN ({in_list});")
lines += [
    f"INSERT INTO sys_setting (setting_key, setting_value, description) "
    f"SELECT '{m}', 'true', 'P0.6 reapply on server' FROM DUAL "
    f"WHERE NOT EXISTS (SELECT 1 FROM sys_setting WHERE setting_key='{m}');",
    f"UPDATE sys_setting SET setting_value='true' WHERE setting_key='{m}';",
    "COMMIT;",
]
print("\n".join(lines))
PY

echo "==> 导入 MySQL"
docker compose -f docker-compose.prod.yml up -d mysql
for i in $(seq 1 30); do
  docker exec hospital-mysql mysqladmin ping -h 127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null && break
  sleep 2
done
docker exec -i hospital-mysql mysql -uhospital -p"${MYSQL_PASSWORD}" --default-character-set=utf8mb4 hospital < /tmp/p0_6_apply.sql
rm -f /tmp/p0_6_apply.sql

echo "==> 重启 backend"
docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate backend

echo "==> 校验"
sleep 15
bash deploy/verify-billing-on-server.sh
