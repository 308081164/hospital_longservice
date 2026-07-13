#!/usr/bin/env bash
# 确保 backend/src/main/resources/db 下所有增量迁移 SQL 均已列入 migrate_manifest.txt。
# 未来 CI 在 push main 前调用：bash scripts/check-migrate-manifest.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DB_DIR="$ROOT/backend/src/main/resources/db"
MANIFEST="$DB_DIR/migrate_manifest.txt"
cd "$DB_DIR"

missing=0
for f in schema_*migration*.sql; do
  [ -f "$f" ] || continue
  if ! grep -qxF "$f" "$MANIFEST" 2>/dev/null; then
    echo "ERROR: $f 未列入 migrate_manifest.txt，推送到 main 后 Docker 启动将不会执行该迁移。" >&2
    missing=1
  fi
done

if [ "$missing" -ne 0 ]; then
  echo >&2
  echo "请将缺失文件名按依赖顺序追加到: $MANIFEST" >&2
  exit 1
fi

echo "OK: 所有 schema_*migration*.sql 均已列入 migrate_manifest.txt"
