#!/usr/bin/env python3
"""G4 统一门禁：baseline diff + FIXED_PRICE + keyword gap + 医院清单。"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INDEX_PATH = ROOT / "backend/src/main/resources/billing-rules/index.json"
REPORT_PATH = ROOT / "测试用例/billing_rules_verify_report.json"

sys.path.insert(0, str(ROOT / "scripts"))
from rules_compare import run_rules_compare  # noqa: E402


def load_baseline_manifest() -> dict:
    index = json.loads(INDEX_PATH.read_text(encoding="utf-8"))
    customers = {}
    for entry in index.get("customers") or []:
        code = entry.get("code")
        if not code:
            continue
        rel = entry.get("file", f"baseline/{code}.json")
        path = ROOT / "backend/src/main/resources/billing-rules" / rel.replace("baseline/", "baseline/")
        if not path.is_file():
            path = ROOT / "backend/src/main/resources/billing-rules/baseline" / f"{code}.json"
        node = json.loads(path.read_text(encoding="utf-8"))
        customers[code] = {
            "name": node.get("customerName"),
            "billingEnabled": node.get("billingEnabled", True),
            "billingPricingMode": node.get("billingPricingMode"),
            "productRules": node.get("productRules") or [],
        }
    return {
        "manifest_hash": index.get("baseline_hash"),
        "generated_at": index.get("generated_at"),
        "customers": customers,
    }


def check_fixed_price_convention(baseline: dict) -> list[str]:
    errors = []
    for code, customer in (baseline.get("customers") or {}).items():
        for rule in customer.get("productRules") or []:
            if rule.get("ruleType") != "FIXED_PRICE":
                continue
            if rule.get("isActive") is False:
                continue
            if not rule.get("skipPackaging") or not rule.get("skipDiscount"):
                errors.append(f"{code} FIXED_PRICE「{rule.get('name')}」须 skipPackaging+skipDiscount")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="rules verify（baseline 统一门禁）")
    parser.add_argument("--profile", choices=["local", "prod", "direct"], default="direct")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--customer", help="单院 code")
    parser.add_argument("--fail-on-drift", action="store_true")
    parser.add_argument("--base-url", default="http://127.0.0.1:8853")
    parser.add_argument("--out", type=Path, default=REPORT_PATH)
    args = parser.parse_args()

    baseline = load_baseline_manifest()
    errors: list[str] = []
    warnings: list[str] = []

    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as tmp:
        json.dump(baseline, tmp, ensure_ascii=False)
        tmp_path = Path(tmp.name)

    from lib.api_client import configure_client, get_client  # noqa: E402

    configure_client(api_base=args.base_url, mode="direct")
    client = get_client()
    try:
        compare_report = run_rules_compare(
            client,
            code=args.customer,
            compare_all=args.all or not args.customer,
            manifest_path=tmp_path,
        )
    finally:
        tmp_path.unlink(missing_ok=True)

    for row in compare_report.get("results") or []:
        code = row.get("code")
        if row.get("error"):
            errors.append(f"{code}: {row['error']}")
            continue
        for name in row.get("missing") or []:
            errors.append(f"{code} missing: {name}")
        for name in row.get("changed") or []:
            errors.append(f"{code} changed: {name}")
        for name in row.get("extra") or []:
            errors.append(f"{code} extra: {name}")

    errors.extend(check_fixed_price_convention(baseline))

    proc = subprocess.run(
        [sys.executable, str(ROOT / "scripts/check_strict_hospital_alignment.py")],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        errors.append("医院清单对齐失败")

    g6_proc = subprocess.run(
        [sys.executable, str(ROOT / "scripts/check_accepted_types_runtime.py")],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    if g6_proc.returncode != 0:
        errors.append("G6 acceptedTypes 运行时 type 门控检查失败")
        for line in (g6_proc.stderr or "").splitlines():
            if line.strip().startswith("- "):
                errors.append(line.strip()[2:])

    gap_proc = subprocess.run(
        [sys.executable, str(ROOT / "scripts/keyword_gap_scan.py")],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    gap_lines = [ln for ln in (gap_proc.stdout or "").splitlines() if ln.strip()]
    if gap_lines:
        warnings.append(f"keyword_gap: {len(gap_lines)} 条潜在风险（非阻断）")
        warnings.extend(gap_lines[:10])

    report = {
        "ok": len(errors) == 0,
        "baseline_hash": baseline.get("manifest_hash"),
        "errors": errors,
        "warnings": warnings,
        "compare": compare_report,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(json.dumps({"ok": report["ok"], "errors": len(errors), "warnings": len(warnings)}, ensure_ascii=False))
    for e in errors[:30]:
        print("ERROR:", e)

    if errors and args.fail_on_drift:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
