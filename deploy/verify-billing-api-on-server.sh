#!/usr/bin/env bash
# 通过 backend API 校验客户列表 billing_enabled（与 UI 一致）
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
EXPECTED="${EXPECTED_BILLING_ENABLED:-24}"
ADMIN_USER="${ADMIN_USERNAME:-admin}"

cd "$DEPLOY_PATH"
# shellcheck disable=SC1091
[ -f .env ] && set -a && source .env && set +a
ADMIN_PASS="${ADMIN_PASSWORD:-${APP_ADMIN_PASSWORD:-admin123}}"

chmod +x deploy/mysql-hospital-cli.sh 2>/dev/null || true

for i in $(seq 1 30); do
  curl -sf --connect-timeout 3 http://127.0.0.1:8853/api/v1/base/health >/dev/null && break
  sleep 2
  [ "$i" -eq 30 ] && { echo "backend 8853 不可达" >&2; exit 1; }
done

PY=""
if command -v python3 >/dev/null 2>&1; then
  PY=python3
elif command -v python >/dev/null 2>&1; then
  PY=python
else
  echo "错误: 需要 python3 解析 API JSON" >&2
  exit 1
fi

TOKEN=$(
  curl -sf -X POST http://127.0.0.1:8853/api/v1/base/access_token \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}" \
    | "$PY" -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('access_token',''))"
)

if [ -z "$TOKEN" ]; then
  echo "错误: 无法获取 access_token" >&2
  exit 1
fi

ENABLED=$(
  curl -sf http://127.0.0.1:8853/api/v1/customers -H "Authorization: Bearer ${TOKEN}" \
    | "$PY" -c "
import sys, json
d = json.load(sys.stdin)
rows = d.get('data') or []
on = sum(1 for r in rows if r.get('billing_enabled') or r.get('billingEnabled'))
print(on)
"
)

DB="${MYSQL_DATABASE:-hospital}"
MYSQL_ENABLED=$(bash deploy/mysql-hospital-cli.sh --exec-root -N -e \
  "SELECT COUNT(*) FROM customer WHERE billing_enabled=1" "$DB")

echo "API billing_enabled=1: ${ENABLED} / 期望 ${EXPECTED}"
echo "MySQL billing_enabled=1: ${MYSQL_ENABLED}（backend 连库）"

if [ "${ENABLED}" != "${EXPECTED}" ]; then
  echo "错误: API 与期望不一致（客户管理 UI 读此接口）" >&2
  if [ "${ENABLED}" != "${MYSQL_ENABLED}" ]; then
    echo "错误: API(${ENABLED}) ≠ MySQL(${MYSQL_ENABLED}) — 常见原因：backend MYSQL_HOST 与 P0.6 脚本写入库不一致" >&2
  fi
  exit 1
fi

if [ "${ENABLED}" != "${MYSQL_ENABLED}" ]; then
  echo "错误: API 与 MySQL 计数不一致" >&2
  exit 1
fi

echo "API 校验通过。"
