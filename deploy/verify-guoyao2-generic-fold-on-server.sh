#!/usr/bin/env bash
# 生产校验 GUOYAO-2 克氏针通用 FOLD（模板 foldRules）spot 计价 16.5
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
DB="${MYSQL_DATABASE:-hospital}"

cd "$DEPLOY_PATH"
# shellcheck disable=SC1091
[ -f .env ] && set -a && source .env && set +a

chmod +x deploy/mysql-hospital-cli.sh 2>/dev/null || true

mysql_q() {
  bash deploy/mysql-hospital-cli.sh --exec-root -N -e "$1" "$DB"
}

echo "==> 标准模板 foldRules 含「通用小件5合1」（MySQL hospital_pricing_rule）"
FOLD_COUNT=$(mysql_q "SELECT COUNT(*) FROM hospital_pricing_rule r
WHERE r.is_active=1
  AND r.rules_json LIKE '%通用小件5合1含包材%'
  AND r.rules_json LIKE '%通用小件5合1免包材%'")

if [ "${FOLD_COUNT}" -lt 1 ]; then
  echo "错误: 标准模板 rules_json 未含通用小件5合1 FOLD（count=${FOLD_COUNT}）" >&2
  echo "建议: 重启 backend 触发 BillingSeedMigrationRunner，或手动合并 phase-global-generic-fold-20260820.json" >&2
  exit 1
fi

BILLING=$(mysql_q "SELECT billing_enabled FROM customer WHERE code='GUOYAO-2' LIMIT 1")
if [ "${BILLING}" != "1" ]; then
  echo "错误: GUOYAO-2 billing_enabled=${BILLING}（期望 1）" >&2
  exit 1
fi

echo "==> GUOYAO-2 通用克氏针 FOLD 模板校验通过（foldRules present, billing_enabled=${BILLING}）"

if [ -x "${DEPLOY_PATH}/scripts/customer_rules_spot_validation.py" ] || [ -f "${DEPLOY_PATH}/scripts/customer_rules_spot_validation.py" ]; then
  echo "==> spot 计价（克氏针-12/Z7530 期望 16.5 + 通用小件5合1免包材）"
  python3 "${DEPLOY_PATH}/scripts/customer_rules_spot_validation.py" \
    --code GUOYAO-2 --id guoyao2_kirschner_fold_12 --api http://127.0.0.1:8853 || {
    echo "警告: spot 脚本失败，请确认 backend 已重启且模板 foldRules 已写入" >&2
    exit 1
  }
fi

echo "verify-guoyao2-generic-fold: OK"
