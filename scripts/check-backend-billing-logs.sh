#!/usr/bin/env bash
# 重建 backend 后查看特色账单 / 增量种子相关日志（macOS 默认无 rg，使用 grep -E）
set -euo pipefail

CONTAINER="${BACKEND_CONTAINER:-hospital-backend}"

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "错误: 容器 $CONTAINER 未运行" >&2
  exit 1
fi

PATTERN='Incremental billing seed|Billing seed migration|Failed to load billing seed|NOT marked|Standard pricing seed|ZYY-D1 P0|ERROR|Failed to apply'

echo "=== backend 日志摘要 (${CONTAINER}) ==="
docker logs "$CONTAINER" 2>&1 \
  | grep -iE "$PATTERN" \
  | grep -viE 'Duplicate column name|1060 \(42S21\)' \
  | tail -50 || true

echo
echo "（entrypoint 重复执行 schema 迁移时的 Duplicate column 1060 可忽略，见 docker-entrypoint.sh）"
