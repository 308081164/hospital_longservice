#!/usr/bin/env bash
# 在生产服务器上重启 backend，由 BillingRulesManifestReconciler 同步 billingEnabled/status
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"

cd "$DEPLOY_PATH"
if [ ! -f .env ]; then
  echo "错误: .env 不存在" >&2
  exit 1
fi
# shellcheck disable=SC1091
set -a && source .env && set +a

MANIFEST="${DEPLOY_PATH}/backend/src/main/resources/billing-seeds/billing-rules-manifest.json"
if [ ! -f "$MANIFEST" ]; then
  echo "错误: 缺少 manifest $MANIFEST（请同步 backend 目录）" >&2
  exit 1
fi

read_manifest_counts() {
  python3 - <<PY
import json
from pathlib import Path
m = json.loads(Path("$MANIFEST").read_text(encoding="utf-8"))
print(m.get("billing_enabled_count", 0))
print(m.get("active_billing_enabled_count", m.get("billing_enabled_count", 0)))
PY
}

mapfile -t _counts < <(read_manifest_counts)
EXPECTED_ENABLED="${_counts[0]:-0}"
EXPECTED_ACTIVE_ENABLED="${_counts[1]:-${EXPECTED_ENABLED}}"
export EXPECTED_BILLING_ENABLED="$EXPECTED_ENABLED"
export EXPECTED_ACTIVE_BILLING_ENABLED="$EXPECTED_ACTIVE_ENABLED"

echo "==> manifest 期望 billingEnabled=${EXPECTED_ENABLED} active=${EXPECTED_ACTIVE_ENABLED}"

echo "==> 重启 backend（触发 manifest reconcile）"
docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate backend

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

echo "==> 校验"
sleep 5
if [ -x "${DEPLOY_PATH}/deploy/verify-billing-on-server.sh" ]; then
  bash "${DEPLOY_PATH}/deploy/verify-billing-on-server.sh"
else
  echo "警告: verify-billing-on-server.sh 缺失，跳过校验" >&2
fi
