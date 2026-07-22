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

echo "==> 确保 MySQL 容器运行: ${CONTAINER}"
docker compose -f docker-compose.prod.yml up -d mysql
for i in $(seq 1 30); do
  if docker exec "$CONTAINER" mysqladmin ping -h 127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; then
    break
  fi
  sleep 2
  if [ "$i" -eq 30 ]; then
    echo "错误: MySQL 未就绪" >&2
    exit 1
  fi
done

echo "==> 导入 P0.6 SQL"
docker cp "$SQL_FILE" "${CONTAINER}:/tmp/p0-6-billing-toggle.sql"
docker exec -i "$CONTAINER" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 hospital \
  < "$SQL_FILE"

echo "==> 导入后统计"
docker exec "$CONTAINER" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 -N hospital -e \
  "SELECT CONCAT('billing_enabled=1: ', COUNT(*)) FROM customer WHERE billing_enabled=1;"

docker exec "$CONTAINER" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 hospital -e \
  "SELECT c.code, c.billing_enabled FROM customer c
   WHERE c.code IN (
     'ZYY-D1','ZY3-DIANLI','GUOYAO-MAIN','GUOYAO-2','GUOYAO-3','HRB-2ND','HRB-WY','HRB-WY-EM',
     'XINFA-HSZ','SHENG-YY-NG','SHENG-YY-XF','ZUYAN-NG','ZUYAN-SF','ZUYAN-XA','NG-FUCHAN','SHKF-YY',
     'DAOWAI-RM','TAIPING-RM','SANJING-SB','VICTORIA','JIUZHOU-FK','HULAN-HSZ','HULAN-TCM',
     'ZYY-D2-NG','ZYY-D2-HN','RENSHENG','HRB-HX-EYE','BINGCHENG-YM','XF-ZYY','WJ-HLJ-ZD','YUEMEI-FH',
     'ERYY-NG','ERYY-SB','HULAN-RM','HRB-HSZ','HRB-HIT'
   )
   ORDER BY c.code;" || true

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
bash deploy/verify-billing-on-server.sh
