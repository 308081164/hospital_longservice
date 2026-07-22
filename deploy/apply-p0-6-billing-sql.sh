#!/usr/bin/env bash
# 导入 P0.6 billing 开关（写入 backend 实际连接的 MySQL）
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
SQL_FILE="${DEPLOY_PATH}/deploy/sql/p0-6-billing-toggle.sql"
CONTAINER="${MYSQL_CONTAINER:-hospital-mysql}"

cd "$DEPLOY_PATH"
# shellcheck disable=SC1091
[ -f .env ] && set -a && source .env && set +a

chmod +x deploy/mysql-hospital-cli.sh 2>/dev/null || true
bash deploy/mysql-hospital-cli.sh --print-target

if [ ! -f "$SQL_FILE" ]; then
  echo "错误: 缺少 $SQL_FILE" >&2
  exit 1
fi

docker compose -f docker-compose.prod.yml up -d mysql
for i in $(seq 1 30); do
  docker exec "$CONTAINER" mysqladmin ping -h 127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null && break
  sleep 2
  [ "$i" -eq 30 ] && { echo "MySQL 未就绪" >&2; exit 1; }
done

echo "==> 导入 P0.6 SQL: $SQL_FILE"
bash deploy/mysql-hospital-cli.sh --import-root "$SQL_FILE"

DB="${MYSQL_DATABASE:-hospital}"
ENABLED=$(bash deploy/mysql-hospital-cli.sh --exec-root -N -e \
  "SELECT COUNT(*) FROM customer WHERE billing_enabled=1" "$DB")

echo "billing_enabled=1: ${ENABLED}（期望 36）"
[ "${ENABLED}" = "36" ] || { echo "P0.6 SQL 后启用数不对" >&2; exit 1; }
