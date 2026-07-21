#!/usr/bin/env bash
# 在生产服务器上校验 billing 种子 marker 与启用数量
# 用法：cd /mnt/newdisk/app/Hospital && bash deploy/verify-billing-on-server.sh
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
EXPECTED_ENABLED="${EXPECTED_BILLING_ENABLED:-36}"
MARKER_P06="${MARKER_P06:-billing_seed_batch_p0_6_v1}"

cd "$DEPLOY_PATH"
if [ ! -f .env ]; then
  echo "错误: .env 不存在" >&2
  exit 1
fi
# shellcheck disable=SC1091
set -a && source .env && set +a

mysql_q() {
  docker exec hospital-mysql mysql -uhospital -p"${MYSQL_PASSWORD}" --default-character-set=utf8mb4 \
    -N hospital -e "$1"
}

echo "==> billing 种子 marker（最近 15 条）"
mysql_q "SELECT setting_key FROM sys_setting WHERE setting_key LIKE 'billing_seed%' ORDER BY setting_key DESC LIMIT 15" \
  || { echo "无法查询 sys_setting（MySQL 未就绪？）" >&2; exit 1; }

echo ""
echo "==> P0.6 marker: ${MARKER_P06}"
if mysql_q "SELECT 1 FROM sys_setting WHERE setting_key='${MARKER_P06}' LIMIT 1" | grep -q 1; then
  echo "    存在"
else
  echo "    缺失 — backend 重启后 BillingSeedMigrationRunner 应自动写入"
fi

ENABLED=$(mysql_q "SELECT COUNT(*) FROM customer WHERE billing_enabled=1")
DISABLED=$(mysql_q "SELECT COUNT(*) FROM customer WHERE billing_enabled=0")
TOTAL=$(mysql_q "SELECT COUNT(*) FROM customer")
echo ""
echo "==> 客户 billing_enabled 统计"
echo "    启用: ${ENABLED}  停用: ${DISABLED}  合计: ${TOTAL}"
echo "    期望启用约: ${EXPECTED_ENABLED}"

if [ "${ENABLED}" != "${EXPECTED_ENABLED}" ]; then
  echo ""
  echo "警告: 启用数量与期望 ${EXPECTED_ENABLED} 不符。"
  echo "可能原因："
  echo "  1) P0.6 种子未执行（marker 缺失或 backend 未用最新镜像重启）"
  echo "  2) 种子已标记完成但执行失败（见 backend 日志 grep P0.6）"
  echo "  3) 本地 dump 未导入（CI 不包含 sync-billing-to-prod）"
  echo ""
  echo "修复：bash deploy/reapply-billing-p0-6-on-server.sh"
  exit 1
fi

if ! mysql_q "SELECT 1 FROM sys_setting WHERE setting_key='${MARKER_P06}' LIMIT 1" | grep -q 1; then
  echo "警告: 启用数量正确但 P0.6 marker 缺失，建议重启 backend 或执行 reapply 脚本" >&2
  exit 1
fi

echo ""
echo "校验通过。"
