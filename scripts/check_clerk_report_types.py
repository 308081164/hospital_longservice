#!/usr/bin/env python3
"""门禁：clerk baseline 中 MONTHLY_SUPPLEMENT_REPORT.reportType 须为合法 ExportType。"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINE_DIR = ROOT / "backend/src/main/resources/clerk-rules/baseline"

ALLOWED = {
    "bill",
    "settlement",
    "dept_summary",
    "price_summary",
    "instrument_audit",
    "logistics_allocation",
    "grand_total",
    "daily",
    "sterilize_fee_detail",
}

ALIASES = {
    "dept_sterilize_summary": "dept_summary",
    "instrument_count_by_dept": "instrument_audit",
}


def main() -> int:
    errors: list[str] = []
    for path in sorted(BASELINE_DIR.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        code = data.get("customerCode", path.stem)
        for rule in data.get("rules", []):
            if rule.get("ruleType") != "MONTHLY_SUPPLEMENT_REPORT":
                continue
            if not rule.get("isActive", True):
                continue
            rt = rule.get("params", {}).get("reportType", "")
            if rt in ALIASES:
                errors.append(f"{code}: illegal alias reportType={rt} (use {ALIASES[rt]})")
            elif rt not in ALLOWED:
                errors.append(f"{code}: unknown reportType={rt}")
    if errors:
        print("FATAL: clerk reportType gate failed:", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        return 1
    print(f"OK: {len(list(BASELINE_DIR.glob('*.json')))} baselines, reportType gate pass")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
