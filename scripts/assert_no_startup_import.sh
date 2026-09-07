#!/usr/bin/env bash
# 策略 A：仅允许 BillingRulesBaselineSyncRunner 在启动时全量 import baseline
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

violations=0

if grep -E 'reconcile-enabled:.*\$\{BILLING_SEED_RECONCILE_ENABLED:true\}' backend/src/main/resources/application.yml 2>/dev/null; then
  echo "ERROR: application.yml reconcile-enabled 不得默认为 true" >&2
  violations=$((violations + 1))
fi

if grep -E 'incremental-enabled:.*\$\{BILLING_SEED_INCREMENTAL_ENABLED:true\}' backend/src/main/resources/application.yml 2>/dev/null; then
  echo "ERROR: application.yml incremental-enabled 不得默认为 true" >&2
  violations=$((violations + 1))
fi

if grep -rn 'importAllBaselines' backend/src/main/java --include '*.java' \
  | grep -v 'BillingRulesBaselineSyncRunner' \
  | grep -v 'BaselineRuleSyncService' \
  | grep -v 'BillingRuleController' \
  | grep -v 'BaselineRuleSyncServiceImpl' >/dev/null 2>&1; then
  echo "ERROR: 发现非授权路径调用 importAllBaselines" >&2
  grep -rn 'importAllBaselines' backend/src/main/java --include '*.java' \
    | grep -v 'BillingRulesBaselineSyncRunner' \
    | grep -v 'BaselineRuleSyncService' \
    | grep -v 'BillingRuleController' \
    | grep -v 'BaselineRuleSyncServiceImpl' >&2 || true
  violations=$((violations + 1))
fi

if [ "$violations" -gt 0 ]; then
  exit 1
fi
echo "OK: 无启动自动 import 违规"
