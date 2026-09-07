#!/usr/bin/env python3
"""从 billing-rules/baseline/*.json 生成 billing-rules-manifest.json（只读产物）。"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINE_DIR = ROOT / "backend/src/main/resources/billing-rules/baseline"
INDEX_PATH = ROOT / "backend/src/main/resources/billing-rules/index.json"
MANIFEST_PATH = ROOT / "backend/src/main/resources/billing-seeds/billing-rules-manifest.json"
TEST_MANIFEST_PATH = ROOT / "backend/src/test/resources/billing-rules-manifest.json"

sys.path.insert(0, str(ROOT / "scripts"))
from strict_hospital_codes import STRICT_KEEP_CODES  # noqa: E402


def canonical_hash(obj: object) -> str:
    text = json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def load_baseline(code: str) -> dict:
    return json.loads((BASELINE_DIR / f"{code}.json").read_text(encoding="utf-8"))


def build_manifest(existing: dict | None = None) -> dict:
    existing = existing or {}
    customers: dict = {}
    enabled = 0
    for code in STRICT_KEEP_CODES:
        baseline = load_baseline(code)
        rules = baseline.get("productRules") or []
        billing_enabled = baseline.get("billingEnabled", True)
        if billing_enabled:
            enabled += 1
        prev = (existing.get("customers") or {}).get(code) or {}
        customers[code] = {
            "code": code,
            "name": baseline.get("customerName") or prev.get("name") or code,
            "status": prev.get("status"),
            "billingPricingMode": baseline.get("billingPricingMode"),
            "standardPricingOverride": prev.get("standardPricingOverride"),
            "billingEnabled": billing_enabled,
            "productRules": rules,
            "rule_count": len(rules),
            "active_rule_count": len(rules),
        }

    manifest_hash = canonical_hash(
        {c: customers[c]["productRules"] for c in STRICT_KEEP_CODES if c in customers}
    )
    return {
        "version": existing.get("version", 1),
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "manifest_hash": manifest_hash,
        "billing_enabled_count": enabled,
        "active_billing_enabled_count": enabled,
        "customers": customers,
    }


def write_manifest(manifest: dict) -> None:
    text = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    MANIFEST_PATH.write_text(text, encoding="utf-8")
    TEST_MANIFEST_PATH.write_text(text, encoding="utf-8")


def stable_manifest_view(manifest: dict) -> dict:
    customers = manifest.get("customers") or {}
    return {
        "manifest_hash": manifest.get("manifest_hash"),
        "billing_enabled_count": manifest.get("billing_enabled_count"),
        "active_billing_enabled_count": manifest.get("active_billing_enabled_count"),
        "customers": {
            code: {
                "billingEnabled": node.get("billingEnabled"),
                "billingPricingMode": node.get("billingPricingMode"),
                "productRules": node.get("productRules"),
            }
            for code, node in customers.items()
        },
    }


def check_manifest() -> int:
    if not MANIFEST_PATH.is_file():
        print(f"ERROR: missing {MANIFEST_PATH}", file=sys.stderr)
        return 1
    existing = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    expected = build_manifest(existing)
    if stable_manifest_view(existing) == stable_manifest_view(expected):
        print("OK: manifest 与 baseline 一致")
        return 0
    # 仅输出摘要 diff
    if existing.get("manifest_hash") != expected.get("manifest_hash"):
        print(
            f"ERROR: manifest_hash 不一致\n"
            f"  file: {existing.get('manifest_hash')}\n"
            f"  want: {expected.get('manifest_hash')}",
            file=sys.stderr,
        )
        return 1
    print("ERROR: manifest 内容与 baseline 不一致（请运行 baseline_to_manifest.py --write）", file=sys.stderr)
    return 1


def main() -> int:
    parser = argparse.ArgumentParser(description="baseline → billing-rules-manifest.json")
    parser.add_argument("--write", action="store_true", help="写入 manifest（prod + test）")
    parser.add_argument("--check", action="store_true", help="校验 manifest 与 baseline 零 diff")
    args = parser.parse_args()

    if args.check:
        return check_manifest()
    if args.write:
        existing = json.loads(MANIFEST_PATH.read_text(encoding="utf-8")) if MANIFEST_PATH.is_file() else {}
        manifest = build_manifest(existing)
        write_manifest(manifest)
        if INDEX_PATH.is_file():
            index_hash = json.loads(INDEX_PATH.read_text(encoding="utf-8")).get("baseline_hash", "")
            print(f"Wrote manifest manifest_hash={manifest['manifest_hash'][:16]}… baseline_hash={index_hash[:16]}…")
        else:
            print(f"Wrote manifest manifest_hash={manifest['manifest_hash'][:16]}…")
        return 0

    parser.print_help()
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
