#!/usr/bin/env bash
# 对账任务 pricing_rule_overrides 列（写入 backend 实际连接的 MySQL）
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
SQL_FILE="${DEPLOY_PATH}/deploy/sql/schema_011_pricing_rule_overrides.sql"
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

echo "==> 导入 schema_011 SQL: $SQL_FILE"
bash deploy/mysql-hospital-cli.sh --import-root "$SQL_FILE"

DB="${MYSQL_DATABASE:-hospital}"
COL=$(bash deploy/mysql-hospital-cli.sh --exec-root -N -e \
  "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='${DB}' AND TABLE_NAME='hospital_reconciliation_job' AND COLUMN_NAME='pricing_rule_overrides'" \
  information_schema)

echo "pricing_rule_overrides 列: ${COL}（期望 1）"
[ "${COL}" = "1" ] || { echo "schema_011 后列不存在" >&2; exit 1; }
