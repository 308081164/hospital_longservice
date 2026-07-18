#!/usr/bin/env bash
# 生产环境：备份 MySQL → 清空数据卷 → 重建栈 → 等待健康 → 验证种子
#
# 用法（在服务器 DEPLOY_PATH 下）:
#   bash deploy/reset-database-on-server.sh
#
# 环境变量（可选）:
#   DEPLOY_PATH          默认 /mnt/newdisk/app/Hospital
#   SKIP_BACKUP=1          跳过备份（CI 仍应默认备份；仅紧急调试时使用）
#   EXPECTED_MIN_CUSTOMERS 默认 61
#
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
BACKUP_DIR="${BACKUP_DIR:-$DEPLOY_PATH/backups}"
COMPOSE_FILE="docker-compose.prod.yml"
EXPECTED_MIN="${EXPECTED_MIN_CUSTOMERS:-61}"
SKIP_BACKUP="${SKIP_BACKUP:-0}"

cd "$DEPLOY_PATH"

if [ ! -f .env ]; then
  echo "错误: $DEPLOY_PATH/.env 不存在" >&2
  exit 1
fi

# shellcheck disable=SC1091
set -a && source .env && set +a

if [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
  echo "错误: .env 中未设置 MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"

if [ "$SKIP_BACKUP" != "1" ]; then
  BACKUP_FILE="$BACKUP_DIR/hospital-backup-$(date +%Y%m%d-%H%M%S).sql"
  echo ">>> 备份生产库到 $BACKUP_FILE ..."
  if docker ps --format '{{.Names}}' | grep -qx hospital-mysql; then
    docker exec hospital-mysql mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" \
      --single-transaction --routines --triggers --databases "${MYSQL_DATABASE:-hospital}" \
      > "$BACKUP_FILE"
    echo "备份完成: $(wc -c < "$BACKUP_FILE") bytes"
  else
    echo "警告: hospital-mysql 未运行，跳过在线备份（空卷重置前可能无数据）"
  fi
else
  echo ">>> SKIP_BACKUP=1，跳过 mysqldump"
fi

echo
echo ">>> 停止栈并删除所有 Docker 卷（mysql-data、backend-uploads、backend-storage）..."
echo "    ⚠️  此操作不可逆，除 backups/ 目录外所有容器数据将被清空"
docker compose -f "$COMPOSE_FILE" down -v

echo
echo ">>> 拉取镜像并启动全栈..."
docker compose -f "$COMPOSE_FILE" pull
docker compose -f "$COMPOSE_FILE" up -d

echo
echo ">>> 等待 MySQL 健康..."
for i in $(seq 1 90); do
  if docker inspect hospital-mysql --format='{{.State.Health.Status}}' 2>/dev/null | grep -qx healthy; then
    echo "MySQL healthy"
    break
  fi
  sleep 2
  if [ "$i" -eq 90 ]; then
    echo "错误: MySQL 健康检查超时" >&2
    docker logs --tail 80 hospital-mysql 2>&1 || true
    exit 1
  fi
done

echo
echo ">>> 等待 backend 健康（种子在 Spring 启动时执行，约 60～120 秒）..."
for i in $(seq 1 80); do
  if curl -fsS --connect-timeout 4 "http://127.0.0.1:${BACKEND_PUBLISH_PORT:-8853}/api/v1/base/health" >/dev/null 2>&1; then
    echo "Backend health OK"
    break
  fi
  sleep 3
  if [ "$i" -eq 80 ]; then
    echo "错误: backend 健康检查超时" >&2
    docker logs --tail 120 hospital-backend 2>&1 || true
    exit 1
  fi
done

docker compose -f "$COMPOSE_FILE" up -d --no-deps frontend 2>/dev/null || true

echo
echo ">>> 种子执行日志摘要:"
docker logs hospital-backend 2>&1 | grep -E 'Billing seed|Hardcoded|ExtraCustomer|MasterData|billing_seed' | tail -30 || true

echo
echo ">>> 验证数据库..."
MYSQL=(docker exec hospital-mysql mysql -uroot "-p${MYSQL_ROOT_PASSWORD}" -N -B "${MYSQL_DATABASE:-hospital}")

echo "--- sys_setting 种子标记 ---"
"${MYSQL[@]}" -e "
SELECT setting_key, setting_value
FROM sys_setting
WHERE setting_key IN ('billing_seed_profiles_v1', 'hardcoded_rules_migrated_v1')
ORDER BY setting_key;
"

TOTAL=$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM customer;" || echo 0)
BILLING_ON=$("${MYSQL[@]}" -e "SELECT SUM(billing_enabled = 1) FROM customer;" || echo 0)
INACTIVE=$("${MYSQL[@]}" -e "SELECT SUM(status = 'inactive') FROM customer;" || echo 0)

echo "--- 客户统计 ---"
echo "  total=$TOTAL billing_enabled=$BILLING_ON inactive=$INACTIVE (期望 total>=$EXPECTED_MIN, billing_enabled=26, inactive=19)"

BILLING_MARKER=$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM sys_setting WHERE setting_key='billing_seed_profiles_v1' AND setting_value='true';" || echo 0)

FAIL=0
if [ "${BILLING_MARKER:-0}" != "1" ]; then
  echo "错误: billing_seed_profiles_v1 未就绪" >&2
  FAIL=1
fi
if [ "${TOTAL:-0}" -lt "$EXPECTED_MIN" ]; then
  echo "错误: customer 总数 $TOTAL < 期望最少 $EXPECTED_MIN" >&2
  FAIL=1
fi
if [ "${BILLING_ON:-0}" -lt 26 ]; then
  echo "错误: billing_enabled=$BILLING_ON < 26" >&2
  FAIL=1
fi

if [ "$FAIL" -ne 0 ]; then
  exit 1
fi

echo
echo ">>> 验证通过。前端: http://127.0.0.1:${HTTP_PORT:-8854}/  后端: http://127.0.0.1:${BACKEND_PUBLISH_PORT:-8853}/api/v1/base/health"
