#!/usr/bin/env bash
# 本地测试库全量重置：清空 MySQL 卷 → 重建栈 → 自动跑 schema/SQL 迁移 + Java 种子
#
# 用法:
#   bash scripts/full-billing-seed-reset.sh           # 仅 billing-seeds + 内置 master/hardcoded
#   bash scripts/full-billing-seed-reset.sh --bokang  # 额外启用铂康 SQL 导入（需 铂康/建表语句/ 下有文件）
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"
USE_BOKANG=0

for arg in "$@"; do
  case "$arg" in
    --bokang) USE_BOKANG=1 ;;
    -h|--help)
      echo "用法: bash scripts/full-billing-seed-reset.sh [--bokang]"
      exit 0
      ;;
    *)
      echo "未知参数: $arg" >&2
      exit 1
      ;;
  esac
done

cd "$ROOT"

if [ ! -f "$ENV_FILE" ]; then
  echo "错误: 未找到 $ENV_FILE，请先配置本地 Docker 环境变量" >&2
  exit 1
fi

BOKANG_DIR="$ROOT/铂康/建表语句"
if [ "$USE_BOKANG" -eq 1 ]; then
  if [ ! -f "$BOKANG_DIR/hospital_reconciliation_job.sql" ]; then
    echo "错误: --bokang 需要 $BOKANG_DIR/hospital_reconciliation_job.sql" >&2
    echo "请将铂康 SQL 转储放入该目录（见 system_docs/bokang-import-report.md）" >&2
    exit 1
  fi
  if grep -q '^IMPORT_BOKANG_DATA=' "$ENV_FILE"; then
    sed -i.bak 's/^IMPORT_BOKANG_DATA=.*/IMPORT_BOKANG_DATA=1/' "$ENV_FILE"
    rm -f "$ENV_FILE.bak"
  else
    echo "IMPORT_BOKANG_DATA=1" >> "$ENV_FILE"
  fi
  echo "已设置 IMPORT_BOKANG_DATA=1"
else
  if grep -q '^IMPORT_BOKANG_DATA=' "$ENV_FILE"; then
    sed -i.bak 's/^IMPORT_BOKANG_DATA=.*/IMPORT_BOKANG_DATA=0/' "$ENV_FILE"
    rm -f "$ENV_FILE.bak"
  fi
  if [ -f "$BOKANG_DIR/hospital_reconciliation_job.sql" ]; then
    echo "检测到铂康 SQL，可追加 --bokang 导入全量医院"
  else
    echo "铂康 SQL 目录为空，本次仅导入 billing-seeds（26 院特色配置）+ 内置 master 数据"
  fi
fi

echo ">>> 停止并删除 MySQL 数据卷（全量重置）..."
docker compose down -v

echo ">>> 重建并启动 mysql + backend + frontend..."
docker compose up -d --build

echo ">>> 等待 MySQL 健康..."
for i in $(seq 1 90); do
  if docker inspect hospital-mysql --format='{{.State.Health.Status}}' 2>/dev/null | grep -qx healthy; then
    echo "MySQL healthy"
    break
  fi
  sleep 2
  if [ "$i" -eq 90 ]; then
    echo "错误: MySQL 健康检查超时" >&2
    exit 1
  fi
done

echo ">>> 等待 backend 健康（种子在 Spring 启动时执行，可能需要 60～90 秒）..."
for i in $(seq 1 60); do
  status=$(docker inspect hospital-backend --format='{{.State.Health.Status}}' 2>/dev/null || echo "unknown")
  if [ "$status" = "healthy" ]; then
    echo "Backend healthy"
    break
  fi
  sleep 3
  if [ "$i" -eq 60 ]; then
    echo "警告: backend 健康检查超时，仍尝试启动 frontend 并验证..." >&2
  fi
done

# compose 可能因 backend 短暂 unhealthy 未启动 frontend，此处补启
docker compose up -d frontend 2>/dev/null || true

echo ">>> 种子执行日志摘要:"
bash "$ROOT/scripts/check-backend-billing-logs.sh"

echo
bash "$ROOT/scripts/verify-billing-seed.sh"

echo
echo "完成。导出供生产使用: bash deploy/export-local-mysql.sh"
