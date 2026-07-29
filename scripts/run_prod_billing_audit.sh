#!/usr/bin/env bash
# 生产只读账单核对：S1/S4/S8 + 报告。禁止 import / S4 重导。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export PROD_BILLING_AUDIT=1
export API_BASE="${API_BASE:-http://39.102.213.51:8853}"
export API_MODE="${API_MODE:-direct}"

JOB_MAP="${JOB_MAP:-测试用例/job_baseline_prod.json}"
EXPORT_DIR="${EXPORT_DIR:-测试用例/.s8_exports_prod}"
REPORT_SUFFIX=prod
SKIP_CALIBRATE=0
SKIP_DEPLOY=0
HEALTH_WAIT_SEC="${HEALTH_WAIT_SEC:-600}"

usage() {
  sed -n '2,22p' "$0"
}

for arg in "$@"; do
  case "$arg" in
    --skip-calibrate) SKIP_CALIBRATE=1 ;;
    --skip-deploy) SKIP_DEPLOY=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $arg" >&2; usage; exit 2 ;;
  esac
done

if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a && source .env && set +a
fi

FORBIDDEN_PATTERNS=(
  "batch_june_system_test"
  "hospital-cli s4"
  "allow-import"
  "import_bill"
)

check_forbidden() {
  local cmd="$*"
  for pat in "${FORBIDDEN_PATTERNS[@]}"; do
    if [[ "$cmd" == *"$pat"* ]]; then
      echo "拒绝执行写库命令（命中 $pat）: $cmd" >&2
      exit 2
    fi
  done
}

wait_for_health() {
  local deadline=$((SECONDS + HEALTH_WAIT_SEC))
  echo ">> 等待 API 健康（最多 ${HEALTH_WAIT_SEC}s）: $API_BASE"
  while [ "$SECONDS" -lt "$deadline" ]; do
    if curl -sf --connect-timeout 4 "${API_BASE}/api/v1/base/health" >/dev/null 2>&1; then
      echo ">> health OK"
      return 0
    fi
    sleep 5
  done
  echo "health 超时" >&2
  return 1
}

run_preflight() {
  wait_for_health
  echo ">> 预检 smoke"
  if ! ./bin/hospital-cli smoke --mode direct --profile prod --api "$API_BASE" --json > /tmp/prod_smoke.json 2>&1; then
    cat /tmp/prod_smoke.json
    echo "smoke 失败，中止" >&2
    exit 1
  fi
  if grep -q '"ok": false' /tmp/prod_smoke.json 2>/dev/null; then
    cat /tmp/prod_smoke.json
    echo "smoke 未通过，中止" >&2
    exit 1
  fi

  ENABLED=$(./bin/hospital-cli deploy-check --mode direct --profile prod --api "$API_BASE" --skip-mysql --json 2>/dev/null \
    | python3 -c "import json,sys; d=json.load(sys.stdin); print(next((s['data'].get('enabled',0) for s in d.get('steps',[]) if s.get('name')=='L8_billing_enabled_api'), 47))" 2>/dev/null || echo 47)
  ./bin/hospital-cli deploy-check --mode direct --profile prod --api "$API_BASE" --expected "$ENABLED" --skip-mysql --json > /tmp/prod_deploy.json 2>&1 || true

  python3 - <<'PY'
import json
from pathlib import Path
from datetime import datetime
smoke = json.loads(Path("/tmp/prod_smoke.json").read_text())
deploy = json.loads(Path("/tmp/prod_deploy.json").read_text()) if Path("/tmp/prod_deploy.json").is_file() else {}
out = {
    "smoke_ok": smoke.get("ok", False),
    "deploy_check_ok": deploy.get("ok", False),
    "deploy_check_detail": next((s.get("detail") for s in deploy.get("steps", []) if not s.get("ok")), "ok"),
    "api_base": __import__("os").environ.get("API_BASE"),
    "checked_at": datetime.now().isoformat(timespec="seconds"),
}
Path("测试用例/.billing_audit_preflight.prod.json").write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(out, ensure_ascii=False, indent=2))
PY
}

check_forbidden "$0" "$@"

echo "== 生产账单核对（只读）API=$API_BASE =="

if [ "$SKIP_DEPLOY" -eq 0 ]; then
  run_preflight
else
  wait_for_health
fi

if [ "$SKIP_CALIBRATE" -eq 0 ]; then
  echo ">> 校准 prod Job map"
  python3 scripts/calibrate_prod_job_map.py --api "$API_BASE" --mode direct || true
fi

echo ">> S4 只读审计"
check_forbidden "s4_stable_job_audit"
python3 scripts/s4_stable_job_audit.py \
  --job-map "$JOB_MAP" \
  --output 测试用例/s4_prod_job_audit.json \
  --mode direct --api "$API_BASE" || true

echo ">> S8 bill"
check_forbidden "batch_s8_export_compare"
python3 scripts/batch_s8_export_compare.py \
  --job-map "$JOB_MAP" \
  --mode direct --api-base "$API_BASE" \
  --report-suffix "$REPORT_SUFFIX" \
  --export-dir "$EXPORT_DIR" \
  --no-todo-update \
  --export-sleep 2 || true

echo ">> S8 settlement"
python3 scripts/batch_s8_settlement_compare.py \
  --job-map "$JOB_MAP" \
  --mode direct --api-base "$API_BASE" \
  --report-suffix "$REPORT_SUFFIX" \
  --export-dir "$EXPORT_DIR" \
  --no-todo-update \
  --export-sleep 2 || true

echo ">> 汇总报告"
python3 scripts/billing_reconciliation_report.py --profile prod
REPORT_EXIT=$?

echo "完成。报告: 测试用例/客户反馈账单核对报告-prod-*.md"
exit "$REPORT_EXIT"
