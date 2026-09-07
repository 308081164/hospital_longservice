#!/usr/bin/env bash
# 在生产服务器上重启 backend，由 BillingRulesBaselineSyncRunner 按 baseline_hash 同步规则
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"

cd "$DEPLOY_PATH"
if [ ! -f .env ]; then
  echo "错误: .env 不存在" >&2
  exit 1
fi
# CI 部署传入的镜像引用（不可变 SHA 标签）优先于 .env 中的 :latest
CI_IMAGE_BACKEND="${IMAGE_BACKEND:-}"
CI_IMAGE_FRONTEND="${IMAGE_FRONTEND:-}"
# shellcheck disable=SC1091
set -a && source .env && set +a
[ -n "$CI_IMAGE_BACKEND" ] && export IMAGE_BACKEND="$CI_IMAGE_BACKEND"
[ -n "$CI_IMAGE_FRONTEND" ] && export IMAGE_FRONTEND="$CI_IMAGE_FRONTEND"

INDEX="${DEPLOY_PATH}/backend/src/main/resources/billing-rules/index.json"
if [ ! -f "$INDEX" ]; then
  echo "错误: 缺少 baseline index $INDEX（请同步 backend 目录）" >&2
  exit 1
fi

EXPECTED_BASELINE_HASH="$(python3 -c "import json;print(json.load(open('$INDEX'))['baseline_hash'])")"
echo "==> 期望 baseline_hash=${EXPECTED_BASELINE_HASH:0:16}…"

# 幂等：backend 已在目标镜像上运行则跳过重启
TARGET_IMAGE="${IMAGE_BACKEND:-}"
CURRENT_IMAGE="$(docker inspect hospital-backend --format '{{.Config.Image}}' 2>/dev/null || true)"
if [ -n "$TARGET_IMAGE" ] && [ "$CURRENT_IMAGE" = "$TARGET_IMAGE" ] \
  && curl -sf --connect-timeout 3 http://127.0.0.1:8853/api/v1/base/health >/dev/null 2>&1; then
  echo "==> backend 已在目标镜像运行（${TARGET_IMAGE}），跳过 force-recreate"
else
  echo "==> 重启 backend（触发 baseline sync）当前=${CURRENT_IMAGE:-未知} 目标=${TARGET_IMAGE:-compose 默认}"
  docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate backend
fi

echo "==> 等待 backend"
backend_healthy=0
for i in $(seq 1 40); do
  if curl -sf --connect-timeout 3 http://127.0.0.1:8853/api/v1/base/health >/dev/null 2>&1; then
    echo "backend 健康（第 ${i} 轮）"
    backend_healthy=1
    break
  fi
  sleep 3
done
if [ "$backend_healthy" -ne 1 ]; then
  echo "错误: backend 在 120s 内未通过 health 检查" >&2
  docker logs --tail 80 hospital-backend 2>&1 || true
  exit 1
fi

echo "==> 校验 baseline hash 与 verify 状态"
sleep 3
VER_BODY="$(curl -sS --connect-timeout 5 http://127.0.0.1:8853/api/v1/base/version)"
echo "$VER_BODY"
ACTUAL_BASELINE="$(printf '%s' "$VER_BODY" | python3 -c 'import sys,json; print((json.load(sys.stdin).get("data") or {}).get("rulesBaselineHash") or "")')"
VERIFY_OK="$(printf '%s' "$VER_BODY" | python3 -c 'import sys,json; d=json.load(sys.stdin).get("data") or {}; print(d.get("rulesVerifyStatus", True))')"

if [ -z "$ACTUAL_BASELINE" ]; then
  echo "错误: 生产未返回 rulesBaselineHash" >&2
  exit 1
fi
if [ "$ACTUAL_BASELINE" != "$EXPECTED_BASELINE_HASH" ]; then
  echo "错误: rulesBaselineHash 不一致（prod=${ACTUAL_BASELINE:0:16}… expected=${EXPECTED_BASELINE_HASH:0:16}…）" >&2
  exit 1
fi
if [ "$VERIFY_OK" = "False" ] || [ "$VERIFY_OK" = "false" ]; then
  echo "错误: rulesVerifyStatus 未通过（DB 与 baseline 漂移）" >&2
  exit 1
fi
echo "OK: baseline hash 一致且 verify 通过"

if [ -x "${DEPLOY_PATH}/deploy/verify-billing-on-server.sh" ]; then
  bash "${DEPLOY_PATH}/deploy/verify-billing-on-server.sh"
fi
