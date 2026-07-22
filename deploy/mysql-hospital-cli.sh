#!/usr/bin/env bash
# 与运行中的 hospital-backend 使用同一 MySQL 目标执行命令（避免 SQL 打进 docker 卷、API 读宿主机库）
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
CONTAINER="${MYSQL_CONTAINER:-hospital-mysql}"
BACKEND_CONTAINER="${BACKEND_CONTAINER:-hospital-backend}"

cd "$DEPLOY_PATH"
if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a && source .env && set +a
fi

backend_env() {
  local key="$1"
  docker inspect "$BACKEND_CONTAINER" --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
    | sed -n "s/^${key}=//p" | head -1
}

if docker ps --format '{{.Names}}' | grep -qx "$BACKEND_CONTAINER"; then
  MH="$(backend_env MYSQL_HOST)"
  MP="$(backend_env MYSQL_PORT)"
  MD="$(backend_env MYSQL_DATABASE)"
  MU="$(backend_env MYSQL_USER)"
  MPW="$(backend_env MYSQL_PASSWORD)"
fi

MH="${MH:-${MYSQL_HOST:-127.0.0.1}}"
MP="${MP:-${MYSQL_PORT:-3306}}"
MD="${MD:-${MYSQL_DATABASE:-hospital}}"
MU="${MU:-${MYSQL_USER:-hospital}}"
MPW="${MPW:-${MYSQL_PASSWORD:-}}"

echo "[mysql-cli] backend 连库 target=${MH}:${MP} db=${MD}" >&2

use_docker_exec() {
  case "$MH" in
    127.0.0.1 | localhost | mysql | "") return 0 ;;
    *) return 1 ;;
  esac
}

run_mysql_root() {
  local args=("$@")
  if use_docker_exec; then
    if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
      echo "错误: MySQL 容器 ${CONTAINER} 未运行" >&2
      exit 1
    fi
    docker exec "$CONTAINER" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 "${args[@]}"
  else
    docker run --rm -i --network host mysql:8.0 \
      mysql -h"$MH" -P"$MP" -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 "${args[@]}"
  fi
}

run_mysql_app() {
  local args=("$@")
  if use_docker_exec; then
    docker exec "$CONTAINER" mysql -u"$MU" -p"$MPW" --default-character-set=utf8mb4 "${args[@]}"
  else
    docker run --rm -i --network host mysql:8.0 \
      mysql -h"$MH" -P"$MP" -u"$MU" -p"$MPW" --default-character-set=utf8mb4 "${args[@]}"
  fi
}

if [ "${1:-}" = "--print-target" ]; then
  echo "MYSQL_HOST=${MH}"
  echo "MYSQL_PORT=${MP}"
  echo "MYSQL_DATABASE=${MD}"
  exit 0
fi

if [ "${1:-}" = "--exec-root" ]; then
  shift
  run_mysql_root "$@"
  exit 0
fi

if [ "${1:-}" = "--exec-app" ]; then
  shift
  run_mysql_app "$@"
  exit 0
fi

if [ "${1:-}" = "--import-root" ]; then
  shift
  local_file="${1:?缺少 SQL 文件路径}"
  if use_docker_exec; then
    docker exec -i "$CONTAINER" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 "$MD" \
      < "$local_file"
  else
    docker run --rm -i --network host mysql:8.0 \
      mysql -h"$MH" -P"$MP" -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 "$MD" \
      < "$local_file"
  fi
  exit 0
fi

echo "用法: mysql-hospital-cli.sh --print-target | --import-root FILE | --exec-root [mysql args...]" >&2
exit 1
