#!/usr/bin/env python3
"""Build correction-price-audit-fixture.json for CorrectionPriceRedundancyAuditTest."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED_DIR = ROOT / "backend/src/main/resources/billing-seeds/archive/legacy-2026"
BASELINE_DIR = ROOT / "backend/src/main/resources/billing-rules/baseline"
OUT = ROOT / "backend/src/test/resources/correction-price-audit-fixture.json"


def main() -> None:
    hospitals: dict[str, dict] = {}

    for f in BASELINE_DIR.glob("*.json"):
        if "schema" in f.name:
            continue
        data = json.loads(f.read_text(encoding="utf-8"))
        code = data.get("customerCode") or f.stem
        hospitals[code] = dict(data)
        hospitals[code]["code"] = code

    for f in sorted(SEED_DIR.glob("*.json")):
        try:
            data = json.loads(f.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        for upd in list(data.get("profiles", [])) + list(data.get("customerUpdates", [])):
            code = upd.get("code")
            if not code:
                continue
            if code not in hospitals:
                hospitals[code] = {
                    "code": code,
                    "name": upd.get("name", code),
                    "productRules": [],
                }
            h = hospitals[code]
            for key in (
                "name",
                "billingEnabled",
                "billingPricingMode",
                "standardPricingOverride",
                "pathOverride",
            ):
                if upd.get(key) is not None:
                    h[key] = upd[key]
            existing = {r.get("name"): r for r in h.get("productRules", [])}
            for r in upd.get("productRules", []):
                existing[r.get("name")] = r
            h["productRules"] = list(existing.values())

    corrections = []
    out_hospitals = {}
    for code, h in hospitals.items():
        corr_rules = [r for r in h.get("productRules", []) if "校正价" in (r.get("name") or "")]
        if not corr_rules:
            continue
        out_hospitals[code] = h
        for r in corr_rules:
            if not r.get("isActive", True):
                continue
            for kw in r.get("keywords") or [r.get("name")]:
                corrections.append(
                    {
                        "code": code,
                        "hospitalName": h.get("name", code),
                        "ruleName": r.get("name"),
                        "price": r.get("price"),
                        "keyword": kw,
                    }
                )

    OUT.write_text(
        json.dumps(
            {"version": 2, "hospitals": out_hospitals, "corrections": corrections},
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"Wrote {OUT}: {len(out_hospitals)} hospitals, {len(corrections)} keyword checks")


if __name__ == "__main__":
    main()
