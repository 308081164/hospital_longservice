#!/usr/bin/env bash
# 将生产环境回退到指定留档版本。
#
# ⚠️  安全约束：仅可在运维负责人发出明确回退指令后手动执行。
#     CI / 自动化流水线不得调用本脚本。
#
# 用法:
#   bash deploy/rollback-prod-to-archive.sh              # 回退到 latest 留档
#   bash deploy/rollback-prod-to-archive.sh 20260908-164500
#   CONFIRM_ROLLBACK=YES bash deploy/rollback-prod-to-archive.sh 20260908-164500
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
ARCHIVE_ROOT="${DEPLOY_PATH}/releases/archive"
STAMP="${1:-}"

if [ -z "${STAMP}" ]; then
  if [ -f "${ARCHIVE_ROOT}/latest-stamp.txt" ]; then
    STAMP="$(cat "${ARCHIVE_ROOT}/latest-stamp.txt")"
  else
    echo "错误: 请指定留档时间戳，或确保 ${ARCHIVE_ROOT}/latest-stamp.txt 存在" >&2
    exit 1
  fi
fi

ARCHIVE_DIR="${ARCHIVE_ROOT}/${STAMP}"
if [ ! -d "${ARCHIVE_DIR}" ] || [ ! -f "${ARCHIVE_DIR}/images.env" ]; then
  echo "错误: 留档目录不存在或缺少 images.env: ${ARCHIVE_DIR}" >&2
  exit 1
fi

if [ "${CONFIRM_ROLLBACK:-}" != "YES" ]; then
  echo "=========================================="
  echo "  生产回退确认"
  echo "  留档: ${STAMP}"
  echo "  目录: ${ARCHIVE_DIR}"
  echo ""
  cat "${ARCHIVE_DIR}/README.txt" 2>/dev/null || true
  echo ""
  echo "  本操作将 force-recreate backend/frontend 为留档镜像。"
  echo "  须负责人明确授权后再执行:"
  echo "    CONFIRM_ROLLBACK=YES bash deploy/rollback-prod-to-archive.sh ${STAMP}"
  echo "=========================================="
  exit 2
fi

cd "${DEPLOY_PATH}"
set -a
# shellcheck disable=SC1091
source "${ARCHIVE_DIR}/images.env"
set +a

if [ ! -f .env ]; then
  echo "错误: ${DEPLOY_PATH}/.env 不存在" >&2
  exit 1
fi

echo "==> 回退到留档 ${STAMP}"
echo "    IMAGE_BACKEND=${IMAGE_BACKEND}"
echo "    IMAGE_FRONTEND=${IMAGE_FRONTEND}"

export IMAGE_BACKEND
export IMAGE_FRONTEND

docker compose -f docker-compose.prod.yml up -d mysql
for i in $(seq 1 30); do
  if docker compose -f docker-compose.prod.yml ps mysql | grep -q '(healthy)'; then
    break
  fi
  sleep 2
done

docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate backend

echo "等待 backend 健康 …"
for i in $(seq 1 60); do
  if curl -sS --connect-timeout 4 -o /dev/null "http://127.0.0.1:8853/api/v1/base/health"; then
    echo "backend 健康"
    break
  fi
  sleep 5
done

docker compose -f docker-compose.prod.yml stop frontend 2>/dev/null || true
docker compose -f docker-compose.prod.yml rm -f frontend 2>/dev/null || true
docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate frontend

echo "==> 回退完成。请执行: bash deploy/run-prod-verify.sh smoke"
curl -sS "http://127.0.0.1:8853/api/v1/base/version" || true
