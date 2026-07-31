#!/usr/bin/env bash
# 在生产部署机执行验证（加载 .env；smoke 走 Docker 内 curl，不用宿主机 Python）
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
LEVEL="${1:-smoke}"
shift || true

cd "$DEPLOY_PATH"
if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a && source .env && set +a
fi

export HOST_API_BASE="${HOST_API_BASE:-http://127.0.0.1:8853}"
export DEPLOY_PATH="$DEPLOY_PATH"

case "$LEVEL" in
  smoke)
    # smoke 在容器内 curl :8000；勿传入 API_BASE=8853（宿主机映射端口）
    unset API_BASE
    exec "$(dirname "$0")/prod-smoke-docker.sh" "$@"
    ;;
  deploy-check|full|verify-full)
    echo "错误: 生产机 ${LEVEL} 请在本机用 ./bin/hospital-cli 或 CI parity gate 执行（不在宿主机跑 Python CLI）" >&2
    exit 2
    ;;
  *)
    echo "用法: $0 smoke [--json] [--job-id ID]" >&2
    exit 2
    ;;
esac
