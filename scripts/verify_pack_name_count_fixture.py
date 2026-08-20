#!/usr/bin/env python3
"""对照 pack-name-count-customer-review.json 验证 Python 解析器（与 Java 逻辑镜像）。"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from scan_pack_name_field_consistency import extract_total  # noqa: E402

FIXTURE = ROOT / "backend/src/test/resources/pack-name-count-customer-review.json"


def main() -> int:
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    failed = 0
    for case in data["cases"]:
        per = extract_total(case["packName"])
        exp_per = case["expectedPerPack"]
        if per != exp_per:
            print(f"FAIL {case['id']} {case['packName']}: got per={per} want={exp_per}")
            failed += 1
            continue
        if per is not None:
            total = per * case["packCount"]
            if total != case["expectedTotal"]:
                print(f"FAIL {case['id']} total: got {total} want {case['expectedTotal']}")
                failed += 1
    behaviors = {}
    for case in data["cases"]:
        behaviors[case["behavior"]] = behaviors.get(case["behavior"], 0) + 1
    print(f"cases={len(data['cases'])} behaviors={behaviors}")
    if failed:
        print(f"FAILED {failed} parser assertions")
        return 1
    print("OK: fixture aligned with scan_pack_name_field_consistency (Java mirror)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
