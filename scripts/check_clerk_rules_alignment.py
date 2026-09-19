#!/usr/bin/env python3
"""G2：铂康 Excel 映射 vs clerk-rules baseline 覆盖率门禁。"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAPPING = ROOT / "docs/clerk-rules-excel-mapping.json"
INDEX = ROOT / "backend/src/main/resources/clerk-rules/index.json"
BASELINE_DIR = ROOT / "backend/src/main/resources/clerk-rules/baseline"


def main() -> int:
    mapping = json.loads(MAPPING.read_text(encoding="utf-8"))
    index = json.loads(INDEX.read_text(encoding="utf-8"))
    expected = {h["code"] for h in mapping["hospitals"]}
    indexed = {c["code"] for c in index["customers"]}
    missing = sorted(expected - indexed)
    extra = sorted(indexed - expected)
    inactive = []
    for code in expected:
        p = BASELINE_DIR / f"{code}.json"
        if not p.exists():
            continue
        b = json.loads(p.read_text(encoding="utf-8"))
        active = [r for r in b.get("rules", []) if r.get("isActive", True)]
        if not active:
            inactive.append(code)
    ok = not missing and not extra and not inactive
    print(f"expected={len(expected)} indexed={len(indexed)} missing={len(missing)} extra={len(extra)} no_active={len(inactive)}")
    if missing:
        print("MISSING", missing)
    if extra:
        print("EXTRA", extra)
    if inactive:
        print("NO_ACTIVE_RULES", inactive)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
