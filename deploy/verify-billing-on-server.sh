#!/usr/bin/env bash
# 在生产服务器上校验 billing 种子 marker 与启用数量
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
EXPECTED_ENABLED="${EXPECTED_BILLING_ENABLED:-36}"
MARKER_P06="${MARKER_P06:-billing_seed_batch_p0_6_v1}"
CONTAINER="${MYSQL_CONTAINER:-hospital-mysql}"

cd "$DEPLOY_PATH"
if [ ! -f .env ]; then
  echo "错误: .env 不存在" >&2
  exit 1
fi
# shellcheck disable=SC1091
set -a && source .env && set +a

mysql_q() {
  docker exec "$CONTAINER" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 \
    -N hospital -e "$1"
}

echo "==> 容器: ${CONTAINER}"
if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "错误: MySQL 容器 ${CONTAINER} 未运行" >&2
  exit 1
fi

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
  SELECT 'ZYY-D1' AS code UNION SELECT 'ZY3-DIANLI' UNION SELECT 'GUOYAO-MAIN' UNION SELECT 'GUOYAO-2'
  UNION SELECT 'GUOYAO-3' UNION SELECT 'HRB-2ND' UNION SELECT 'HRB-WY' UNION SELECT 'HRB-WY-EM'
  UNION SELECT 'XINFA-HSZ' UNION SELECT 'SHENG-YY-NG' UNION SELECT 'SHENG-YY-XF' UNION SELECT 'ZUYAN-NG'
  UNION SELECT 'ZUYAN-SF' UNION SELECT 'ZUYAN-XA' UNION SELECT 'NG-FUCHAN' UNION SELECT 'SHKF-YY'
  UNION SELECT 'DAOWAI-RM' UNION SELECT 'TAIPING-RM' UNION SELECT 'SANJING-SB' UNION SELECT 'VICTORIA'
  UNION SELECT 'JIUZHOU-FK' UNION SELECT 'HULAN-HSZ' UNION SELECT 'HULAN-TCM' UNION SELECT 'ZYY-D2-NG'
  UNION SELECT 'ZYY-D2-HN' UNION SELECT 'RENSHENG' UNION SELECT 'HRB-HX-EYE' UNION SELECT 'BINGCHENG-YM'
  UNION SELECT 'XF-ZYY' UNION SELECT 'WJ-HLJ-ZD' UNION SELECT 'YUEMEI-FH' UNION SELECT 'ERYY-NG'
  UNION SELECT 'ERYY-SB' UNION SELECT 'HULAN-RM' UNION SELECT 'HRB-HSZ' UNION SELECT 'HRB-HIT'
) t WHERE NOT EXISTS (SELECT 1 FROM customer c WHERE c.code=t.code)")

echo ""
echo "==> 客户 billing_enabled 统计"
echo "    全局启用: ${ENABLED}  停用: ${DISABLED}  合计: ${TOTAL}"
echo "    P0.6 名单内且启用: ${P06_ENABLED} / ${EXPECTED_ENABLED}"
echo "    P0.6 名单在库中缺失: ${P06_MISSING} 个 code"

FAIL=0
if [ "${P06_MISSING}" != "0" ]; then
  echo ""
  echo "错误: 生产库缺少 P0.6 客户 code，需先跑 billing 种子或导入 dump。"
  mysql_q "SELECT t.code FROM (
    SELECT 'ZYY-D1' AS code UNION SELECT 'HRB-HIT'
  ) t WHERE NOT EXISTS (SELECT 1 FROM customer c WHERE c.code=t.code) LIMIT 5" || true
  FAIL=1
fi

if [ "${P06_ENABLED}" != "${EXPECTED_ENABLED}" ]; then
  echo ""
  echo "错误: P0.6 名单内启用数 ${P06_ENABLED} != 期望 ${EXPECTED_ENABLED}"
  echo "未启用的 P0.6 院："
  mysql_q "SELECT code FROM customer WHERE code IN (
    'ZYY-D1','HRB-HIT','TAIPING-RM','HRB-HSZ'
  ) AND billing_enabled=0" || true
  FAIL=1
fi

if [ "${ENABLED}" != "${EXPECTED_ENABLED}" ]; then
  echo "注意: 全局启用数 ${ENABLED} != ${EXPECTED_ENABLED}（若存在非 P0.6 院被误开需排查）"
fi

if ! mysql_q "SELECT 1 FROM sys_setting WHERE setting_key='${MARKER_P06}' LIMIT 1" | grep -q 1; then
  echo "错误: P0.6 marker 缺失" >&2
  FAIL=1
fi

if [ "$FAIL" -ne 0 ]; then
  exit 1
fi

echo ""
echo "校验通过。"
