#!/usr/bin/env bash
# 从本地 Docker MySQL 导出 hospital 数据库（macOS/Linux）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"
CONTAINER="${MYSQL_CONTAINER:-hospital-mysql}"

if [ ! -f "$ENV_FILE" ]; then
  echo "错误: 未找到 $ENV_FILE" >&2
  exit 1
fi

# shellcheck disable=SC1090
set -a && source "$ENV_FILE" && set +a

if [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
  echo "错误: .env 中缺少 MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

OUT_FILE="$ROOT/deploy/hospital-migration-$(date +%Y%m%d).sql"
REMOTE_DUMP="/tmp/hospital-migration.sql"

echo "从 $CONTAINER 导出到 $OUT_FILE ..."

docker exec "$CONTAINER" mysqldump \
  -u root "-p${MYSQL_ROOT_PASSWORD}" \
  --single-transaction \
  --routines \
  --triggers \
  --databases hospital \
  --result-file="$REMOTE_DUMP"

docker cp "${CONTAINER}:${REMOTE_DUMP}" "$OUT_FILE"
docker exec "$CONTAINER" rm -f "$REMOTE_DUMP"

SIZE=$(wc -c < "$OUT_FILE" | tr -d ' ')
echo "完成。大小: $(( SIZE / 1024 / 1024 )) MB"
echo "上传到生产: /mnt/newdisk/app/Hospital/$(basename "$OUT_FILE")"
echo "生产导入: bash deploy/import-on-server.sh $(basename "$OUT_FILE")"
