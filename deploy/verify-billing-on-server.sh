#!/usr/bin/env bash
# 校验 billing：MySQL（与 backend 同库）+ HTTP API（UI 数据源）
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
EXPECTED_ENABLED="${EXPECTED_BILLING_ENABLED:-36}"
MARKER_P06="${MARKER_P06:-billing_seed_batch_p0_6_v1}"

cd "$DEPLOY_PATH"
# shellcheck disable=SC1091
[ -f .env ] && set -a && source .env && set +a

chmod +x deploy/mysql-hospital-cli.sh deploy/verify-billing-api-on-server.sh 2>/dev/null || true
DB="${MYSQL_DATABASE:-hospital}"

mysql_q() {
  bash deploy/mysql-hospital-cli.sh --exec-root -N -e "$1" "$DB"
}

echo "==> backend 连库"
bash deploy/mysql-hospital-cli.sh --print-target

echo "==> billing 种子 marker（最近 15 条）"
mysql_q "SELECT setting_key FROM sys_setting WHERE setting_key LIKE 'billing_seed%' ORDER BY setting_key DESC LIMIT 15"

echo ""
echo "==> P0.6 marker: ${MARKER_P06}"
if mysql_q "SELECT 1 FROM sys_setting WHERE setting_key='${MARKER_P06}' LIMIT 1" | grep -q 1; then
  echo "    存在"
else
  echo "    缺失"
fi

ENABLED=$(mysql_q "SELECT COUNT(*) FROM customer WHERE billing_enabled=1")
DISABLED=$(mysql_q "SELECT COUNT(*) FROM customer WHERE billing_enabled=0")
TOTAL=$(mysql_q "SELECT COUNT(*) FROM customer")
P06_ENABLED=$(mysql_q "SELECT COUNT(*) FROM customer WHERE billing_enabled=1 AND code IN (
  'ZYY-D1','ZY3-DIANLI','GUOYAO-MAIN','GUOYAO-2','GUOYAO-3','HRB-2ND','HRB-WY','HRB-WY-EM',
  'XINFA-HSZ','SHENG-YY-NG','SHENG-YY-XF','ZUYAN-NG','ZUYAN-SF','ZUYAN-XA','NG-FUCHAN','SHKF-YY',
  'DAOWAI-RM','TAIPING-RM','SANJING-SB','VICTORIA','JIUZHOU-FK','HULAN-HSZ','HULAN-TCM',
  'ZYY-D2-NG','ZYY-D2-HN','RENSHENG','HRB-HX-EYE','BINGCHENG-YM','XF-ZYY','WJ-HLJ-ZD','YUEMEI-FH',
  'ERYY-NG','ERYY-SB','HULAN-RM','HRB-HSZ','HRB-HIT'
)")
P06_MISSING=$(mysql_q "SELECT COUNT(*) FROM (
  SELECT 'ZYY-D1' AS code UNION SELECT 'HRB-HIT'
) t WHERE NOT EXISTS (SELECT 1 FROM customer c WHERE c.code=t.code)")

echo ""
echo "==> 客户 billing_enabled 统计（MySQL / backend 同库）"
echo "    全局启用: ${ENABLED}  停用: ${DISABLED}  合计: ${TOTAL}"
echo "    P0.6 名单内且启用: ${P06_ENABLED} / ${EXPECTED_ENABLED}"
echo "    P0.6 名单在库中缺失: ${P06_MISSING} 个 code"

FAIL=0
if [ "${P06_MISSING}" != "0" ]; then
  echo "错误: 生产库缺少 P0.6 客户 code"
  FAIL=1
fi
if [ "${P06_ENABLED}" != "${EXPECTED_ENABLED}" ]; then
  echo "错误: P0.6 名单内启用数 ${P06_ENABLED} != 期望 ${EXPECTED_ENABLED}"
  FAIL=1
fi
if ! mysql_q "SELECT 1 FROM sys_setting WHERE setting_key='${MARKER_P06}' LIMIT 1" | grep -q 1; then
  echo "错误: P0.6 marker 缺失" >&2
  FAIL=1
fi
if [ "$FAIL" -ne 0 ]; then
  exit 1
fi

echo ""
echo "MySQL 校验通过。"
bash deploy/verify-billing-api-on-server.sh
