#!/usr/bin/env bash
# CI / 手动：部署后 prod 只读 parity gate（smoke + rules compare + calibrate + coverage warn）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PROD_HOST="${PROD_HOST:-${SSH_HOST:-39.102.213.51}}"
API_BASE="${API_BASE:-http://${PROD_HOST}:8853}"
COVERAGE_MIN="${COVERAGE_MIN:-20}"
HEALTH_WAIT_SEC="${HEALTH_WAIT_SEC:-120}"

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
