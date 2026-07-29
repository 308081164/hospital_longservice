#!/usr/bin/env bash
# Release 前本地检查：wave6 报告 + prod map 新鲜度
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

FAIL=0
WARN=0

latest_local_report=$(ls -t 测试用例/客户反馈账单核对报告-202*.md 2>/dev/null | grep -v prod | head -1 || true)
if [ -z "$latest_local_report" ]; then
  echo "WARN: 未找到本地 wave6 核对报告" >&2
  WARN=$((WARN + 1))
else
  if grep -q "P0 引擎" "$latest_local_report" && grep -A5 "P0 引擎" "$latest_local_report" | grep -qv "_无_"; then
    p0_count=$(grep -c "^\-" "$latest_local_report" 2>/dev/null || echo 0)
    echo "WARN: 本地报告 $latest_local_report 可能含 P0，请确认 wave6 P0=0"
    WARN=$((WARN + 1))
  else
    echo "OK: 本地报告 $latest_local_report"
  fi
fi

PROD_MAP="测试用例/job_baseline_prod.json"
if [ ! -f "$PROD_MAP" ]; then
  echo "FAIL: 缺少 $PROD_MAP" >&2
  FAIL=$((FAIL + 1))
else
  age_days=$(python3 -c "
import json
from datetime import datetime
from pathlib import Path
d = json.loads(Path('$PROD_MAP').read_text(encoding='utf-8'))
u = d.get('updated', '1970-01-01')
print((datetime.now() - datetime.strptime(u, '%Y-%m-%d')).days)
" 2>/dev/null || echo 999)
  if [ "$age_days" -gt 7 ]; then
    echo "WARN: job_baseline_prod.json 已 ${age_days} 天未更新，建议 calibrate"
    WARN=$((WARN + 1))
  else
    echo "OK: prod map updated within 7 days"
  fi
fi

echo ""
echo "提醒：prod 完整 37 院 wave6 对标**仅在本地 Docker**（stable Job）完成。"
echo "生产使用 bash scripts/run_prod_billing_audit.sh（只读门禁）。"
echo "warn=$WARN fail=$FAIL"
exit "$FAIL"
