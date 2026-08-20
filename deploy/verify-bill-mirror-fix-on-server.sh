#!/usr/bin/env bash
# 生产校验「镜像账单」修复：人口别名、呼兰红十字 hybrid + billing_enabled
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

echo "==> HLJ-FY-RK 别名「人口」"
POP_ALIAS=$(mysql_q "SELECT COUNT(*) FROM customer_alias ca
JOIN customer c ON c.id = ca.customer_id
WHERE c.code='HLJ-FY-RK' AND ca.alias='人口' AND ca.is_active=1")
if [ "${POP_ALIAS}" -lt 1 ]; then
  echo "错误: HLJ-FY-RK 缺少别名「人口」(count=${POP_ALIAS})" >&2
  echo "建议: 重启 backend 触发 phase-bill-mirror-fix-20260820.json seed" >&2
  exit 1
fi

echo "==> HULAN-HSZ billing_enabled + hybrid"
HULAN_BILLING=$(mysql_q "SELECT billing_enabled FROM customer WHERE code='HULAN-HSZ' LIMIT 1")
HULAN_MODE=$(mysql_q "SELECT billing_pricing_mode FROM customer WHERE code='HULAN-HSZ' LIMIT 1")
if [ "${HULAN_BILLING}" != "1" ]; then
  echo "错误: HULAN-HSZ billing_enabled=${HULAN_BILLING}（期望 1）" >&2
  exit 1
fi
if [ "${HULAN_MODE}" != "hybrid" ]; then
  echo "错误: HULAN-HSZ billing_pricing_mode=${HULAN_MODE}（期望 hybrid）" >&2
  exit 1
fi

echo "==> seed marker billing_seed_bill_mirror_fix_20260820_v1"
MARKER=$(mysql_q "SELECT COUNT(*) FROM sys_setting WHERE setting_key='billing_seed_bill_mirror_fix_20260820_v1'")
if [ "${MARKER}" -lt 1 ]; then
  echo "警告: seed marker 未写入，请重启 backend 应用 phase-bill-mirror-fix-20260820.json" >&2
  exit 1
fi

echo "verify-bill-mirror-fix: OK (人口别名 + 呼兰 hybrid + seed marker)"
