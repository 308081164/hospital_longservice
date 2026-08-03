#!/usr/bin/env bash
# CI / 手动：部署后 prod 只读 parity gate（smoke + rules compare + calibrate + coverage warn）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PROD_HOST="${PROD_HOST:-${SSH_HOST:-39.102.213.51}}"
API_BASE="${API_BASE:-http://${PROD_HOST}:8853}"
COVERAGE_MIN="${COVERAGE_MIN:-20}"
HEALTH_WAIT_SEC="${HEALTH_WAIT_SEC:-120}"

_strip() {
  local val="${1:-}"
  val="${val#"${val%%[![:space:]]*}"}"
  val="${val%"${val##*[![:space:]]}"}"
  printf '%s' "$val"
}

_env_val_nonempty() {
  local val
  val="$(_strip "${1:-}")"
  [ -n "$val" ] && printf '%s' "$val"
}

_parse_dotenv_value() {
  local raw="${1#*=}"
  raw="$(_strip "$raw")"
  if [[ "$raw" == \"*\" && "$raw" == *\" ]]; then
    raw="${raw:1:${#raw}-2}"
  elif [[ "$raw" == \'*\' && "$raw" == *\' ]]; then
    raw="${raw:1:${#raw}-2}"
  fi
  printf '%s' "$raw"
}

ensure_prod_credentials() {
  local user pass admin_user admin_pass app_pass
  user="$(_env_val_nonempty "${SMOKE_USER:-}")"
  pass="$(_env_val_nonempty "${SMOKE_PASS:-}")"
  if [ -z "$user" ]; then
    user="$(_env_val_nonempty "${ADMIN_USERNAME:-}")"
  fi
  if [ -z "$pass" ]; then
    pass="$(_env_val_nonempty "${ADMIN_PASSWORD:-}")"
  fi
  if [ -z "$pass" ]; then
    pass="$(_env_val_nonempty "${APP_ADMIN_PASSWORD:-}")"
  fi

  if [ -n "$user" ] && [ -n "$pass" ]; then
    export SMOKE_USER="$user" SMOKE_PASS="$pass"
    export ADMIN_USERNAME="$user" ADMIN_PASSWORD="$pass"
    echo "credentials: from env"
    return 0
  fi

  if [ -z "${SSH_KEY:-}" ] || [ -z "${SSH_HOST:-}" ] || [ -z "${SSH_USER:-}" ]; then
    echo "错误: 无 smoke 凭证且未配置 SSH（SSH_HOST/SSH_USER/SSH_KEY）" >&2
    exit 1
  fi

  local port="${SSH_PORT:-22}"
  local dpath="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
  mkdir -p ~/.ssh
  printf '%s\n' "$SSH_KEY" > ~/.ssh/parity_gate_key
  chmod 600 ~/.ssh/parity_gate_key
  ssh-keyscan -p "$port" -H "$SSH_HOST" >> ~/.ssh/known_hosts 2>/dev/null || true

  local remote_lines line key val
  remote_lines="$(ssh -i ~/.ssh/parity_gate_key -o StrictHostKeyChecking=no -p "$port" \
    "${SSH_USER}@${SSH_HOST}" \
    "grep -E '^(ADMIN_USERNAME|ADMIN_PASSWORD|APP_ADMIN_PASSWORD)=' '${dpath}/.env' 2>/dev/null || true")"

  admin_user=""
  admin_pass=""
  app_pass=""
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    key="${line%%=*}"
    val="$(_parse_dotenv_value "$line")"
    case "$key" in
      ADMIN_USERNAME) admin_user="$val" ;;
      ADMIN_PASSWORD) admin_pass="$val" ;;
      APP_ADMIN_PASSWORD) app_pass="$val" ;;
    esac
  done <<< "$remote_lines"

  user="$(_env_val_nonempty "$admin_user")"
  pass="$(_env_val_nonempty "$admin_pass")"
  if [ -z "$pass" ]; then
    pass="$(_env_val_nonempty "$app_pass")"
  fi
  user="${user:-admin}"

  if [ -z "$pass" ]; then
    echo "错误: SSH 读取 ${dpath}/.env 未找到 ADMIN_PASSWORD 或 APP_ADMIN_PASSWORD" >&2
    exit 1
  fi

  export SMOKE_USER="$user" SMOKE_PASS="$pass"
  export ADMIN_USERNAME="$user" ADMIN_PASSWORD="$pass"
  echo "credentials: from prod .env via SSH"
}

echo "== prod parity gate API=$API_BASE =="

deadline=$((SECONDS + HEALTH_WAIT_SEC))
while [ "$SECONDS" -lt "$deadline" ]; do
  if curl -sf --connect-timeout 4 "${API_BASE}/api/v1/base/health" >/dev/null 2>&1; then
    echo "health OK"
    break
  fi
  sleep 5
done
if ! curl -sf --connect-timeout 4 "${API_BASE}/api/v1/base/health" >/dev/null 2>&1; then
  echo "health 超时" >&2
  exit 1
fi

ensure_prod_credentials

chmod +x bin/hospital-cli 2>/dev/null || true
echo ">> smoke"
if ! ./bin/hospital-cli smoke --mode direct --profile prod --api "$API_BASE" --json > /tmp/parity_smoke.json; then
  cat /tmp/parity_smoke.json 2>/dev/null || true
  echo "smoke 失败" >&2
  exit 1
fi
if grep -q '"ok": false' /tmp/parity_smoke.json 2>/dev/null; then
  cat /tmp/parity_smoke.json
  echo "smoke 未通过" >&2
  exit 1
fi

echo ">> rules compare (--all --fail-on-drift)"
if ! ./bin/hospital-cli rules compare --all --mode direct --profile prod \
  --api "$API_BASE" --fail-on-drift --json; then
  echo "rules parity 失败（manifest vs prod productRules drift）" >&2
  exit 1
fi

echo ">> calibrate (--dry-run，只写 calibration 日志)"
python3 scripts/calibrate_prod_job_map.py --api "$API_BASE" --mode direct --dry-run || true

FOUND=$(python3 -c "
import json
from pathlib import Path
p = Path('测试用例/job_baseline_prod_calibration.json')
d = json.loads(p.read_text(encoding='utf-8'))
print(d['summary']['found'])
" 2>/dev/null || echo 0)
MISSING=$(python3 -c "
import json
from pathlib import Path
p = Path('测试用例/job_baseline_prod_calibration.json')
d = json.loads(p.read_text(encoding='utf-8'))
print(d['summary']['missing'])
" 2>/dev/null || echo 0)

echo "coverage: found=$FOUND missing=$MISSING (min=$COVERAGE_MIN)"

if [ "$FOUND" -lt "$COVERAGE_MIN" ]; then
  echo "::warning::prod Job coverage $FOUND/$COVERAGE_MIN below minimum"
fi
if [ "$MISSING" -gt 0 ]; then
  echo "::warning::prod coverage_gap $MISSING hospitals missing Job (expected, non-blocking)"
fi

echo "parity gate PASS (smoke + rules ok, coverage logged)"
exit 0
