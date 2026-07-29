#!/usr/bin/env bash
# 在生产部署机执行 CLI 验证（加载 .env，直连 8853）
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
LEVEL="${1:-smoke}"
shift || true

cd "$DEPLOY_PATH"
if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a && source .env && set +a
fi

export API_BASE="${API_BASE:-http://127.0.0.1:8853}"
export API_MODE="${API_MODE:-direct}"
export DEPLOY_PATH="$DEPLOY_PATH"

CLI="$DEPLOY_PATH/bin/hospital-cli"
if [ ! -x "$CLI" ]; then
  CLI="$(cd "$(dirname "$0")/.." && pwd)/bin/hospital-cli"
fi
chmod +x "$CLI" 2>/dev/null || true

case "$LEVEL" in
  smoke)
    exec "$CLI" smoke --mode direct --profile prod --api "$API_BASE" "$@"
    ;;
  deploy-check)
    exec "$CLI" deploy-check --mode direct --profile prod --api "$API_BASE" "$@"
    ;;
  full)
    exec "$CLI" verify --mode direct --profile prod --api "$API_BASE" --level basic "$@"
    ;;
  verify-full)
    exec "$CLI" verify --mode direct --profile prod --api "$API_BASE" --level full "$@"
    ;;
  *)
    echo "用法: $0 {smoke|deploy-check|full|verify-full} [--json ...]" >&2
    exit 2
    ;;
esac
