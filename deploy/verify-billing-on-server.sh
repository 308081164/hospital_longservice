#!/usr/bin/env bash
# 校验 billing：MySQL（与 backend 同库）+ HTTP API（UI 数据源）
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
MANIFEST="${DEPLOY_PATH}/backend/src/main/resources/billing-seeds/billing-rules-manifest.json"
MARKER_MANIFEST="${MARKER_MANIFEST:-billing_rules_manifest_hash}"

cd "$DEPLOY_PATH"
# shellcheck disable=SC1091
[ -f .env ] && set -a && source .env && set +a

chmod +x deploy/mysql-hospital-cli.sh deploy/verify-billing-api-on-server.sh 2>/dev/null || true
DB="${MYSQL_DATABASE:-hospital}"

if [ ! -f "$MANIFEST" ]; then
  echo "错误: manifest 不存在: $MANIFEST" >&2
  exit 1
fi

read_manifest_meta() {
  python3 - <<PY
import json
from pathlib import Path
m = json.loads(Path("$MANIFEST").read_text(encoding="utf-8"))
print(m.get("billing_enabled_count", 0))
print(m.get("active_billing_enabled_count", m.get("billing_enabled_count", 0)))
print(m.get("manifest_hash", ""))
PY
}

mapfile -t _meta < <(read_manifest_meta)
EXPECTED_ENABLED="${EXPECTED_BILLING_ENABLED:-${_meta[0]:-0}}"
EXPECTED_ACTIVE_ENABLED="${EXPECTED_ACTIVE_BILLING_ENABLED:-${_meta[1]:-${EXPECTED_ENABLED}}}"
MANIFEST_HASH="${_meta[2]:-}"

mysql_q() {
  bash deploy/mysql-hospital-cli.sh --exec-root -N -e "$1" "$DB"
}

echo "==> backend 连库"
bash deploy/mysql-hospital-cli.sh --print-target

echo "==> billing manifest"
echo "    hash: ${MANIFEST_HASH:0:16}…"
echo "    期望 billingEnabled: ${EXPECTED_ENABLED}（active 启用: ${EXPECTED_ACTIVE_ENABLED}）"

echo ""
echo "==> manifest reconcile marker: ${MARKER_MANIFEST}"
if mysql_q "SELECT setting_value FROM sys_setting WHERE setting_key='${MARKER_MANIFEST}' LIMIT 1" | grep -q .; then
  DB_HASH=$(mysql_q "SELECT setting_value FROM sys_setting WHERE setting_key='${MARKER_MANIFEST}' LIMIT 1")
  echo "    DB hash: ${DB_HASH:0:16}…"
  if [ -n "$MANIFEST_HASH" ] && [ "$DB_HASH" != "$MANIFEST_HASH" ]; then
    echo "警告: manifest hash 与 DB marker 不一致（backend 可能尚未 reconcile）"
  fi
else
  echo "    缺失（backend 启动后将写入）"
fi

echo "==> manifest reconcile 状态 marker"
RECONCILE_STATUS=$(mysql_q "SELECT setting_value FROM sys_setting WHERE setting_key='billing_rules_manifest_reconcile_status' LIMIT 1" || true)
if [ -n "$RECONCILE_STATUS" ]; then
  echo "    status: ${RECONCILE_STATUS:0:120}"
  case "$RECONCILE_STATUS" in
    OK*) : ;;
    *)
      echo "错误: 生产 manifest reconcile 状态异常（规则可能未全量落库）: ${RECONCILE_STATUS:0:160}" >&2
      exit 1
      ;;
  esac
else
  echo "    缺失（旧版 backend 无此 marker，以 hash 对版为准）"
fi

ENABLED=$(mysql_q "SELECT COUNT(*) FROM customer WHERE billing_enabled=1")
ACTIVE_ENABLED=$(mysql_q "SELECT COUNT(*) FROM customer WHERE billing_enabled=1 AND (status IS NULL OR status='' OR status='active')")
DISABLED=$(mysql_q "SELECT COUNT(*) FROM customer WHERE billing_enabled=0")
TOTAL=$(mysql_q "SELECT COUNT(*) FROM customer")

echo ""
echo "==> 客户 billing_enabled 统计（MySQL / backend 同库）"
echo "    全局启用: ${ENABLED}  停用: ${DISABLED}  合计: ${TOTAL}"
echo "    active 且启用: ${ACTIVE_ENABLED}"

FAIL=0
if [ "${ENABLED}" != "${EXPECTED_ENABLED}" ]; then
  echo "错误: 全局启用数 ${ENABLED} != manifest 期望 ${EXPECTED_ENABLED}"
  FAIL=1
fi
if [ "${ACTIVE_ENABLED}" != "${EXPECTED_ACTIVE_ENABLED}" ]; then
  echo "错误: active 启用数 ${ACTIVE_ENABLED} != manifest 期望 ${EXPECTED_ACTIVE_ENABLED}"
  FAIL=1
fi
if [ "$FAIL" -ne 0 ]; then
  echo ""
  echo "==> 启用数不一致明细（前 20 条）"
  mysql_q "SELECT code, billing_enabled, status FROM customer WHERE billing_enabled=1 ORDER BY code LIMIT 20" || true
  exit 1
fi

echo ""
echo "MySQL 校验通过。"
bash deploy/verify-billing-api-on-server.sh
