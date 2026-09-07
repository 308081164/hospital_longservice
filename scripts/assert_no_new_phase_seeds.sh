#!/usr/bin/env bash
# 禁止新增 phase 增量种子；manifest 必须由 baseline 生成
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

violations=0

# phase-*.json 只允许在 archive/ 下
while IFS= read -r f; do
  case "$f" in
    */archive/*) ;;
    *)
      echo "ERROR: phase 种子须在 archive/ 下: $f" >&2
      violations=$((violations + 1))
      ;;
  esac
done < <(find backend/src/main/resources/billing-seeds -name 'phase-*.json' -type f 2>/dev/null || true)

# BillingSeedMigrationRunner 不得注册新 IncrementalSeed
count="$(grep -c 'new IncrementalSeed' backend/src/main/java/com/hospital/backend/config/BillingSeedMigrationRunner.java 2>/dev/null || true)"
if [ "${count:-0}" -gt 0 ]; then
  echo "ERROR: BillingSeedMigrationRunner 含 ${count} 个 IncrementalSeed 注册（须为 0）" >&2
  violations=$((violations + 1))
fi

if [ "$violations" -gt 0 ]; then
  exit 1
fi
echo "OK: 无新增 phase 种子违规"
