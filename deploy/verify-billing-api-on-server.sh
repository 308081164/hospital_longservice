#!/usr/bin/env bash
# 通过 backend API 校验客户列表中的 billing_enabled 数量（与 MySQL 交叉验证）
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
EXPECTED="${EXPECTED_BILLING_ENABLED:-36}"
ADMIN_USER="${ADMIN_USERNAME:-admin}"
ADMIN_PASS="${ADMIN_PASSWORD:-admin123}"

cd "$DEPLOY_PATH"
# shellcheck disable=SC1091
[ -f .env ] && set -a && source .env && set +a
ADMIN_PASS="${ADMIN_PASSWORD:-${APP_ADMIN_PASSWORD:-admin123}}"

for i in $(seq 1 30); do
  curl -sf --connect-timeout 3 http://127.0.0.1:8853/api/v1/base/health >/dev/null && break
  sleep 2
  [ "$i" -eq 30 ] && { echo "backend 8853 不可达" >&2; exit 1; }
done

TOKEN=$(curl -sf -X POST http://127.0.0.1:8853/api/v1/base/access_token \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('access_token',''))")

if [ -z "$TOKEN" ]; then
  echo "错误: 无法获取 access_token（检查 ADMIN_PASSWORD / 默认 admin123）" >&2
  exit 1
fi

ENABLED=$(curl -sf http://127.0.0.1:8853/api/v1/customers \
  -H "Authorization: Bearer ${TOKEN}" \
  | python3 -c "
import sys, json
d = json.load(sys.stdin)
rows = d.get('data') or []
on = sum(1 for r in rows if r.get('billing_enabled') or r.get('billingEnabled'))
print(on)
")

echo "API billing_enabled=1: ${ENABLED} / 期望 ${EXPECTED}"
if [ "${ENABLED}" != "${EXPECTED}" ]; then
  echo "错误: API 与期望不一致（UI 客户管理读此接口）" >&2
  exit 1
fi
echo "API 校验通过。"
