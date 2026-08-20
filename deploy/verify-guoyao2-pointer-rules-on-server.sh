#!/usr/bin/env bash
# 生产校验 GUOYAO-2 电机厂指针 FOLD 规则是否 active，并 spot 计价 13.5
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

echo "==> GUOYAO-2 电机厂指针 productRules（MySQL）"
mysql_q "SELECT r.name, r.is_active, r.rule_type, r.max_instrument_count, r.min_instrument_count
FROM customer_product_rule r
JOIN customer c ON c.id = r.customer_id
WHERE c.code = 'GUOYAO-2' AND r.name LIKE '电机厂指针%'
ORDER BY r.priority ASC, r.id ASC"

ACTIVE_COUNT=$(mysql_q "SELECT COUNT(*) FROM customer_product_rule r
JOIN customer c ON c.id = r.customer_id
WHERE c.code = 'GUOYAO-2' AND r.name IN ('电机厂指针5合1含包材','电机厂指针5合1免包材') AND r.is_active=1")

if [ "${ACTIVE_COUNT}" != "2" ]; then
  echo "错误: 期望 2 条 active 指针 FOLD 规则，实际 active=${ACTIVE_COUNT}" >&2
  echo "建议: bash deploy/reapply-billing-manifest-on-server.sh" >&2
  exit 1
fi

BILLING=$(mysql_q "SELECT billing_enabled FROM customer WHERE code='GUOYAO-2' LIMIT 1")
if [ "${BILLING}" != "1" ]; then
  echo "错误: GUOYAO-2 billing_enabled=${BILLING}（期望 1）" >&2
  exit 1
fi

echo "==> GUOYAO-2 指针规则校验通过（active=${ACTIVE_COUNT}, billing_enabled=${BILLING}）"

if [ -x "${DEPLOY_PATH}/scripts/customer_rules_spot_validation.py" ] || [ -f "${DEPLOY_PATH}/scripts/customer_rules_spot_validation.py" ]; then
  echo "==> spot 计价（指针-10/z7537 期望 13.5）"
  python3 "${DEPLOY_PATH}/scripts/customer_rules_spot_validation.py" \
    --code GUOYAO-2 --id guoyao2_pointer_fold --api http://127.0.0.1:8853 || {
    echo "警告: spot 脚本失败，请确认 backend 已重启且 manifest reconcile 完成" >&2
    exit 1
  }
fi

echo "verify-guoyao2-pointer-rules: OK"
