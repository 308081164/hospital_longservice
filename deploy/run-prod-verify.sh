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

CLI_BIN=""
CLI_PY=""
for candidate in \
  "$DEPLOY_PATH/bin/hospital-cli" \
  "$(cd "$(dirname "$0")/.." && pwd)/bin/hospital-cli" \
  "$DEPLOY_PATH/hospital-cli"; do
  if [ -x "$candidate" ]; then
    CLI_BIN="$candidate"
    chmod +x "$CLI_BIN" 2>/dev/null || true
    break
  fi
done
if [ -z "$CLI_BIN" ] && [ -f "$DEPLOY_PATH/scripts/hospital_cli.py" ]; then
  CLI_BIN="python3"
  CLI_PY="$DEPLOY_PATH/scripts/hospital_cli.py"
fi
if [ -z "$CLI_BIN" ]; then
  echo "未找到 hospital-cli（期望 $DEPLOY_PATH/bin/hospital-cli）" >&2
  exit 127
fi

run_cli() {
  if [ -n "$CLI_PY" ]; then
    exec "$CLI_BIN" "$CLI_PY" "$@"
  fi
  exec "$CLI_BIN" "$@"
}

case "$LEVEL" in
  smoke)
    run_cli smoke --mode direct --profile prod --api "$API_BASE" "$@"
    ;;
  deploy-check)
    run_cli deploy-check --mode direct --profile prod --api "$API_BASE" "$@"
    ;;
  full)
    run_cli verify --mode direct --profile prod --api "$API_BASE" --level basic "$@"
    ;;
  verify-full)
    run_cli verify --mode direct --profile prod --api "$API_BASE" --level full "$@"
    ;;
  *)
    echo "用法: $0 {smoke|deploy-check|full|verify-full} [--json ...]" >&2
    exit 2
    ;;
esac
