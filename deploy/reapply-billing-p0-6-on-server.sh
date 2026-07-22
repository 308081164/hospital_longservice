#!/usr/bin/env bash
# 在生产服务器上重新应用 P0.6（36 院启用 billing，其余停用）
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
SQL_FILE="${DEPLOY_PATH}/deploy/sql/p0-6-billing-toggle.sql"
CONTAINER="${MYSQL_CONTAINER:-hospital-mysql}"
DB="${MYSQL_DATABASE:-hospital}"

cd "$DEPLOY_PATH"
if [ ! -f .env ]; then
  echo "错误: .env 不存在" >&2
  exit 1
fi
# shellcheck disable=SC1091
set -a && source .env && set +a
DB="${MYSQL_DATABASE:-hospital}"

if [ ! -f "$SQL_FILE" ]; then
  echo "错误: 缺少 $SQL_FILE（请同步 deploy/sql 目录）" >&2
  exit 1
fi

import_p0_6_sql() {
  docker compose -f docker-compose.prod.yml up -d mysql
  for i in $(seq 1 30); do
    if docker exec "$CONTAINER" mysqladmin ping -h 127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; then
      break
    fi
    sleep 2
    [ "$i" -eq 30 ] && { echo "MySQL 未就绪" >&2; exit 1; }
  done

  if [ -x "${DEPLOY_PATH}/deploy/apply-p0-6-billing-sql.sh" ]; then
    bash "${DEPLOY_PATH}/deploy/apply-p0-6-billing-sql.sh"
    return
  fi

  if [ -x "${DEPLOY_PATH}/deploy/mysql-hospital-cli.sh" ]; then
    echo "==> 导入 P0.6 SQL（mysql-hospital-cli）"
    bash "${DEPLOY_PATH}/deploy/mysql-hospital-cli.sh" --import-root "$SQL_FILE"
  else
    echo "==> 导入 P0.6 SQL（fallback: docker exec ${CONTAINER}）"
    docker exec -i "$CONTAINER" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 "$DB" \
      < "$SQL_FILE"
  fi

  ENABLED=""
  if [ -x "${DEPLOY_PATH}/deploy/mysql-hospital-cli.sh" ]; then
    ENABLED=$(bash "${DEPLOY_PATH}/deploy/mysql-hospital-cli.sh" --exec-root -N -e \
      "SELECT COUNT(*) FROM customer WHERE billing_enabled=1" "$DB")
  else
    ENABLED=$(docker exec "$CONTAINER" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -N "$DB" -e \
      "SELECT COUNT(*) FROM customer WHERE billing_enabled=1")
  fi
  echo "billing_enabled=1: ${ENABLED}（期望 36）"
  [ "${ENABLED}" = "36" ] || { echo "P0.6 SQL 后启用数不对" >&2; exit 1; }
}

echo "==> 导入 P0.6 SQL"
import_p0_6_sql

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
if [ -x "${DEPLOY_PATH}/deploy/verify-billing-on-server.sh" ]; then
  bash "${DEPLOY_PATH}/deploy/verify-billing-on-server.sh"
else
  echo "警告: verify-billing-on-server.sh 缺失，跳过校验" >&2
fi
