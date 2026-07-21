#!/usr/bin/env bash
# 本地测试库 → 生产 MySQL 同步（导出 dump + SCP + 导入 + 重启 backend）
#
# 用法：
#   export SSH_HOST=39.102.213.51 SSH_USER=root DEPLOY_PATH=/mnt/newdisk/app/Hospital
#   bash deploy/sync-billing-to-prod.sh
#
# 可选：SSH_KEY=~/.ssh/id_rsa  SSH_PORT=22
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SSH_HOST="${SSH_HOST:?请设置 SSH_HOST}"
SSH_USER="${SSH_USER:-root}"
SSH_PORT="${SSH_PORT:-22}"
DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
SSH_KEY="${SSH_KEY:-}"
SSH_OPTS=(-o StrictHostKeyChecking=no -p "$SSH_PORT")
if [ -n "$SSH_KEY" ]; then
  SSH_OPTS+=(-i "$SSH_KEY")
fi

echo "==> 1/5 导出本地 MySQL"
bash deploy/export-local-mysql.sh
DUMP_FILE=$(ls -t deploy/hospital-migration-*.sql | head -1)
DUMP_NAME=$(basename "$DUMP_FILE")
echo "    dump: $DUMP_FILE ($(du -h "$DUMP_FILE" | cut -f1))"

echo "==> 2/5 备份生产库"
ssh "${SSH_OPTS[@]}" "${SSH_USER}@${SSH_HOST}" bash -s <<EOF
set -euo pipefail
cd "$DEPLOY_PATH"
mkdir -p backups
if [ ! -f .env ]; then echo "错误: .env 不存在" >&2; exit 1; fi
set -a && source .env && set +a
docker compose -f docker-compose.prod.yml up -d mysql
for i in \$(seq 1 30); do
  docker exec hospital-mysql mysqladmin ping -h 127.0.0.1 -uroot -p"\${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null && break
  sleep 2
done
BACKUP="backups/hospital-backup-\$(date +%Y%m%d-%H%M%S).sql"
docker exec hospital-mysql mysqldump -uroot -p"\${MYSQL_ROOT_PASSWORD}" \
  --single-transaction --routines --triggers --databases hospital > "\$BACKUP"
echo "    备份: \$BACKUP"
EOF

echo "==> 3/5 上传 dump"
scp "${SSH_OPTS[@]}" "$DUMP_FILE" "${SSH_USER}@${SSH_HOST}:${DEPLOY_PATH}/${DUMP_NAME}"
scp "${SSH_OPTS[@]}" deploy/import-on-server.sh "${SSH_USER}@${SSH_HOST}:${DEPLOY_PATH}/deploy/"

echo "==> 4/5 导入生产库"
ssh "${SSH_OPTS[@]}" "${SSH_USER}@${SSH_HOST}" \
  "cd '$DEPLOY_PATH' && chmod +x deploy/import-on-server.sh && bash deploy/import-on-server.sh '$DUMP_NAME'"

echo "==> 5/5 重启 backend + 健康检查"
ssh "${SSH_OPTS[@]}" "${SSH_USER}@${SSH_HOST}" bash -s <<EOF
set -euo pipefail
cd "$DEPLOY_PATH"
set -a && source .env && set +a
docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate backend
for i in \$(seq 1 60); do
  if curl -sf --connect-timeout 3 http://127.0.0.1:8853/api/v1/base/health >/dev/null; then
    echo "    backend 健康检查通过"
    exit 0
  fi
  sleep 5
done
echo "警告: backend 健康检查超时，请手动查看 docker logs hospital-backend" >&2
exit 1
EOF

echo "完成。UI: http://${SSH_HOST}:8854"
