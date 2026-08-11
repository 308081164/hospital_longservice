#!/usr/bin/env bash
# 客户反馈规则 20260811 生产逐院 verify-deploy + spot-check
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PROD_HOST="${PROD_HOST:-${SSH_HOST:-39.102.213.51}}"
API_BASE="${API_BASE:-http://${PROD_HOST}:8853}"
OUT_DIR="${OUT_DIR:-测试用例/.prod_verify_20260811}"
PROFILE="${PROFILE:-prod}"
MODE="${MODE:-direct}"

HOSPITALS=(
  GUOYAO-2
  HRB-WY-EM
  BINGCHENG-YM
  HRB-HEU
  NEAU-YY
  HRB-SD-MB
  HRB-HTFH
  FNN-YY
  ZYY-D1
)

mkdir -p "$OUT_DIR"
PASS=0
FAIL=0
RESULTS=()

echo ">> batch customer rules prod verify (api=$API_BASE profile=$PROFILE)"

for code in "${HOSPITALS[@]}"; do
  out="$OUT_DIR/${code}.json"
  echo "=== $code ==="
  if ./bin/hospital-cli rules verify-deploy --code "$code" \
      --profile "$PROFILE" --mode "$MODE" --api "$API_BASE" \
      --spot-check "$code" --fail-on-drift --skip-mysql --json > "$out"; then
    PASS=$((PASS + 1))
    RESULTS+=("\"$code\":true")
    echo "PASS"
  else
    FAIL=$((FAIL + 1))
    RESULTS+=("\"$code\":false")
    echo "FAIL (see $out)"
  fi
done

SUMMARY="$OUT_DIR/summary.json"
{
  printf '{\n  "api_base": "%s",\n  "profile": "%s",\n  "pass": %d,\n  "fail": %d,\n  "hospitals": {\n' \
    "$API_BASE" "$PROFILE" "$PASS" "$FAIL"
  first=1
  for entry in "${RESULTS[@]}"; do
    if [ "$first" -eq 1 ]; then first=0; else printf ',\n'; fi
    printf '    %s' "$entry"
  done
  printf '\n  }\n}\n'
} > "$SUMMARY"

echo ">> done pass=$PASS fail=$FAIL summary=$SUMMARY"
[ "$FAIL" -eq 0 ]
