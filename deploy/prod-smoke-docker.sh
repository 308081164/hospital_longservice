#!/usr/bin/env bash
# 生产 Post-deploy smoke：仅在 hospital-backend 容器内 curl，不使用宿主机 Python/CLI
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
BACKEND_CONTAINER="${BACKEND_CONTAINER:-hospital-backend}"
HOST_API_BASE="${HOST_API_BASE:-http://127.0.0.1:8853}"
CONTAINER_API_BASE="http://127.0.0.1:8000"
SMOKE_JOB_ID="${SMOKE_JOB_ID:-77}"
EXPECT_SHA="${EXPECT_SHA:-}"
JSON_OUT=0
ADMIN_USER="${ADMIN_USERNAME:-admin}"
ADMIN_PASS="${ADMIN_PASSWORD:-${APP_ADMIN_PASSWORD:-admin123}}"

while [ $# -gt 0 ]; do
  case "$1" in
    --json) JSON_OUT=1 ;;
    --api)
      shift
      [ $# -gt 0 ] && HOST_API_BASE="$1"
      ;;
    --job-id) SMOKE_JOB_ID="$2"; shift ;;
    --expect-sha) EXPECT_SHA="$2"; shift ;;
    --mode|--profile)
      shift
      [ $# -gt 0 ] && shift
      ;;
    -h|--help)
      echo "用法: $0 [--json] [--job-id ID] [--expect-sha SHA]" >&2
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
  HOST_API_BASE="${HOST_API_BASE:-http://127.0.0.1:8853}"
fi

# 容器内 Spring 监听 8000；8853 仅为宿主机端口映射，docker exec 内不可达
CONTAINER_API_BASE="http://127.0.0.1:8000"

if ! docker ps --format '{{.Names}}' | grep -qx "$BACKEND_CONTAINER"; then
  echo "错误: 容器 ${BACKEND_CONTAINER} 未运行" >&2
  exit 1
fi

api_curl() {
  docker exec "$BACKEND_CONTAINER" curl -sf "$@"
}

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
for _round in $(seq 1 5); do
  if health_body=$(api_curl --connect-timeout 5 "${CONTAINER_API_BASE}/api/v1/base/health" 2>/dev/null); then
    break
  fi
  [ "$_round" -lt 5 ] && sleep 3
done
if [ -n "$health_body" ]; then
  if json_code_ok "$health_body"; then
    add_step "L0_health" "L0" 1 "OK (docker:${BACKEND_CONTAINER})"
  else
    add_step "L0_health" "L0" 0 "health 非 200"
  fi
else
  add_step "L0_health" "L0" 0 "health 不可达"
fi

version_body=""
if version_body=$(api_curl --connect-timeout 5 "${CONTAINER_API_BASE}/api/v1/base/version" 2>/dev/null); then
  if json_code_ok "$version_body"; then
    add_step "L1_version" "L1" 1 "$(json_field "$version_body" version || echo ok)"
    if [ -n "$EXPECT_SHA" ]; then
      actual_sha=$(json_field "$version_body" gitSha)
      case "$actual_sha" in
        "$EXPECT_SHA"*) add_step "L1_version_sha_parity" "L1" 1 "期望 ${EXPECT_SHA} / 实际 ${actual_sha}" ;;
        *) add_step "L1_version_sha_parity" "L1" 0 "期望 ${EXPECT_SHA} / 实际 ${actual_sha:-未知}" ;;
      esac
    fi
  else
    add_step "L1_version" "L1" 0 "version 非 200"
  fi
else
  add_step "L1_version" "L1" 0 "version 不可达"
fi

token=""
login_body=""
if login_body=$(api_curl --connect-timeout 8 -X POST "${CONTAINER_API_BASE}/api/v1/base/access_token" \
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
  if user_body=$(api_curl --connect-timeout 8 "${CONTAINER_API_BASE}/api/v1/base/userinfo" \
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
  if job_body=$(api_curl --connect-timeout 10 "${CONTAINER_API_BASE}/api/hospital-reconciliations/${SMOKE_JOB_ID}" \
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
  printf '"command":"smoke","profile":"prod","mode":"docker",'
  printf '"api_base":"%s","api_base_host":"%s",' "$CONTAINER_API_BASE" "$HOST_API_BASE"
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
