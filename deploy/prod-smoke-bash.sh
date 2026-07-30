#!/usr/bin/env bash
# 生产机最小 smoke（不依赖 Python 3.9+）：health / version / login / job 读取
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
API_BASE="${API_BASE:-http://127.0.0.1:8853}"
SMOKE_JOB_ID="${SMOKE_JOB_ID:-77}"
JSON_OUT=0
ADMIN_USER="${ADMIN_USERNAME:-admin}"
ADMIN_PASS="${ADMIN_PASSWORD:-${APP_ADMIN_PASSWORD:-admin123}}"

while [ $# -gt 0 ]; do
  case "$1" in
    --json) JSON_OUT=1 ;;
    --api) API_BASE="$2"; shift ;;
    --job-id) SMOKE_JOB_ID="$2"; shift ;;
    --mode|--profile)
      shift
      [ $# -gt 0 ] && shift
      ;;
    -h|--help)
      echo "用法: $0 [--json] [--api URL] [--job-id ID]" >&2
      exit 0
      ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
  shift
done

cd "$DEPLOY_PATH"
if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a && source .env && set +a
  ADMIN_USER="${ADMIN_USERNAME:-$ADMIN_USER}"
  ADMIN_PASS="${ADMIN_PASSWORD:-${APP_ADMIN_PASSWORD:-$ADMIN_PASS}}"
fi

steps=()
ok=1
started_at=$(date +%s)

add_step() {
  local name="$1" level="$2" step_ok="$3" detail="$4"
  steps+=("$name|$level|$step_ok|$detail")
  if [ "$step_ok" != "1" ]; then
    ok=0
  fi
}

json_field() {
  local body="$1" key="$2"
  printf '%s' "$body" | tr -d '\n' | sed -n "s/.*\"${key}\":\"\\([^\"]*\\)\".*/\\1/p" | head -1
}

json_code_ok() {
  local body="$1"
  printf '%s' "$body" | tr -d '\n' | grep -q '"code":200'
}

health_body=""
if health_body=$(curl -sf --connect-timeout 5 "${API_BASE}/api/v1/base/health" 2>/dev/null); then
  if json_code_ok "$health_body"; then
    add_step "L0_health" "L0" 1 "OK"
  else
    add_step "L0_health" "L0" 0 "health 非 200"
  fi
else
  add_step "L0_health" "L0" 0 "health 不可达"
fi

version_body=""
if version_body=$(curl -sf --connect-timeout 5 "${API_BASE}/api/v1/base/version" 2>/dev/null); then
  if json_code_ok "$version_body"; then
    add_step "L1_version" "L1" 1 "$(json_field "$version_body" version || echo ok)"
  else
    add_step "L1_version" "L1" 0 "version 非 200"
  fi
else
  add_step "L1_version" "L1" 0 "version 不可达"
fi

token=""
login_body=""
if login_body=$(curl -sf --connect-timeout 8 -X POST "${API_BASE}/api/v1/base/access_token" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}" 2>/dev/null); then
  token=$(json_field "$login_body" access_token)
  if [ -n "$token" ]; then
    add_step "L2_login" "L2" 1 "access_token ok"
  else
    add_step "L2_login" "L2" 0 "无 access_token"
  fi
else
  add_step "L2_login" "L2" 0 "login 请求失败"
fi

if [ -n "$token" ]; then
  user_body=""
  if user_body=$(curl -sf --connect-timeout 8 "${API_BASE}/api/v1/base/userinfo" \
    -H "Authorization: Bearer ${token}" 2>/dev/null); then
    if json_code_ok "$user_body"; then
      add_step "L3_userinfo" "L3" 1 "$(json_field "$user_body" username || echo ok)"
    else
      add_step "L3_userinfo" "L3" 0 "userinfo 非 200"
    fi
  else
    add_step "L3_userinfo" "L3" 0 "userinfo 不可达"
  fi

  job_body=""
  if job_body=$(curl -sf --connect-timeout 10 "${API_BASE}/api/hospital-reconciliations/${SMOKE_JOB_ID}" \
    -H "Authorization: Bearer ${token}" 2>/dev/null); then
    if json_code_ok "$job_body"; then
      hospital=$(json_field "$job_body" hospitalName)
      [ -z "$hospital" ] && hospital=$(json_field "$job_body" hospital_name)
      add_step "L4_job_get" "L4" 1 "Job #${SMOKE_JOB_ID} ${hospital}"
    else
      add_step "L4_job_get" "L4" 0 "Job #${SMOKE_JOB_ID} 非 200"
    fi
  else
    add_step "L4_job_get" "L4" 0 "Job #${SMOKE_JOB_ID} 不可达"
  fi
fi

finished_at=$(date +%s)
duration=$((finished_at - started_at))

if [ "$JSON_OUT" -eq 1 ]; then
  printf '{'
  printf '"command":"smoke","profile":"prod","mode":"direct","api_base":"%s",' "$API_BASE"
  printf '"ok":%s,"duration_sec":%s,"steps":[' "$([ "$ok" -eq 1 ] && echo true || echo false)" "$duration"
  first=1
  for step in "${steps[@]}"; do
    IFS='|' read -r name level step_ok detail <<< "$step"
    [ "$first" -eq 1 ] || printf ','
    first=0
    esc_detail=${detail//\"/\\\"}
    printf '{"name":"%s","level":"%s","ok":%s,"detail":"%s"}' \
      "$name" "$level" "$([ "$step_ok" = "1" ] && echo true || echo false)" "$esc_detail"
  done
  printf ']}\n'
else
  for step in "${steps[@]}"; do
    IFS='|' read -r name level step_ok detail <<< "$step"
    mark=$([ "$step_ok" = "1" ] && echo OK || echo FAIL)
    printf '%s %s [%s] %s\n' "$mark" "$name" "$level" "$detail"
  done
  printf '结果: %s (%ss)\n' "$([ "$ok" -eq 1 ] && echo PASS || echo FAIL)" "$duration"
fi

exit "$([ "$ok" -eq 1 ] && echo 0 || echo 1)"
