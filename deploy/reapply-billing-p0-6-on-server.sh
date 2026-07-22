#!/usr/bin/env bash
# 在生产服务器上重新应用 P0.6（36 院启用 billing，其余停用）
# 不依赖 python3；SQL 见 deploy/sql/p0-6-billing-toggle.sql
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
SQL_FILE="${DEPLOY_PATH}/deploy/sql/p0-6-billing-toggle.sql"
CONTAINER="${MYSQL_CONTAINER:-hospital-mysql}"

cd "$DEPLOY_PATH"
if [ ! -f .env ]; then
  echo "错误: .env 不存在" >&2
  exit 1
fi
# shellcheck disable=SC1091
set -a && source .env && set +a

if [ ! -f "$SQL_FILE" ]; then
  echo "错误: 缺少 $SQL_FILE（请同步 deploy/sql 目录）" >&2
  exit 1
fi

echo "==> 导入 P0.6 SQL"
bash deploy/apply-p0-6-billing-sql.sh

echo "==> 重启 backend"
docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate backend

echo "==> 等待 backend"
for i in $(seq 1 40); do
  if curl -sf --connect-timeout 3 http://127.0.0.1:8853/api/v1/base/health >/dev/null 2>&1; then
    echo "backend 健康"
    break
  fi
  sleep 3
done

echo "==> 校验"
sleep 5
bash deploy/verify-billing-on-server.sh
