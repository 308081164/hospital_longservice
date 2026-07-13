#!/usr/bin/env bash
# 在服务器上导入本地导出的 MySQL 数据
# 用法：bash deploy/import-on-server.sh [dump文件路径]
# 默认：/mnt/newdisk/app/Hospital/hospital-migration-*.sql（取最新一个）
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
cd "$DEPLOY_PATH"

if [ ! -f .env ]; then
  echo "错误: $DEPLOY_PATH/.env 不存在" >&2
  exit 1
fi

# shellcheck disable=SC1091
set -a && source .env && set +a

DUMP_FILE="${1:-}"
if [ -z "$DUMP_FILE" ]; then
  DUMP_FILE=$(ls -t hospital-migration-*.sql 2>/dev/null | head -1 || true)
fi

if [ -z "$DUMP_FILE" ] || [ ! -f "$DUMP_FILE" ]; then
  echo "错误: 未找到 dump 文件。请先上传 hospital-migration-YYYYMMDD.sql 到 $DEPLOY_PATH" >&2
  exit 1
fi

echo "使用 dump 文件: $DUMP_FILE"

# 确保 MySQL 容器已启动
docker compose -f docker-compose.prod.yml up -d mysql
echo "等待 MySQL 就绪..."
for i in $(seq 1 60); do
  if docker exec hospital-mysql mysqladmin ping -h 127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; then
    echo "MySQL 已就绪"
    break
  fi
  sleep 2
  if [ "$i" -eq 60 ]; then
    echo "错误: MySQL 启动超时" >&2
    exit 1
  fi
done

echo "导入数据（可能需要几分钟）..."
docker exec -i hospital-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" < "$DUMP_FILE"

echo "导入完成。可启动 backend："
echo "  docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate backend"
echo "验证："
echo "  curl http://127.0.0.1:8853/api/v1/base/health"
