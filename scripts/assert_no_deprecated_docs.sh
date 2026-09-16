#!/usr/bin/env bash
# 防止已废止的《账单规则全览手册》及生成器被恢复到主路径。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
errors=0

if [[ -f "$ROOT/docs/账单规则全览手册.md" ]]; then
  echo "FATAL: docs/账单规则全览手册.md 已废止，请使用 docs/archive/deprecated-20260815/ 归档副本。" >&2
  errors=$((errors + 1))
fi

if [[ -f "$ROOT/scripts/generate_billing_rules_handbook.py" ]]; then
  echo "FATAL: scripts/generate_billing_rules_handbook.py 已废止，请使用 billing_rules_catalog.py。" >&2
  errors=$((errors + 1))
fi

if [[ "$errors" -gt 0 ]]; then
  exit 1
fi

echo "assert_no_deprecated_docs: OK"
