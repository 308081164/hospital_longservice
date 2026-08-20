#!/usr/bin/env python3
"""Verify billingEnabled parity between manifest and API/MySQL."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from lib.api_client import ApiClient  # noqa: E402
from rules_compare import load_manifest  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify billingEnabled manifest parity")
    parser.add_argument("--api", default="http://127.0.0.1:8853")
    parser.add_argument("--manifest", type=Path, default=None)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    manifest = load_manifest(args.manifest)
    customers = manifest.get("customers") or {}
    expected_by_code = {
        code: bool(entry.get("billingEnabled"))
        for code, entry in customers.items()
    }
    expected_count = int(manifest.get("billing_enabled_count") or sum(expected_by_code.values()))

    client = ApiClient(args.api)
    client.login(force=True)
    api_customers = client.customers()
    api_by_code = {
        str(c.get("code") or "").strip().upper(): c
        for c in api_customers
        if c.get("code")
    }
    actual_by_code = {}
    for code, c in api_by_code.items():
        enabled = c.get("billingEnabled")
        if enabled is None:
            enabled = c.get("billing_enabled")
        actual_by_code[code] = bool(enabled)

    drifts = []
    for code, expected in sorted(expected_by_code.items()):
        actual = actual_by_code.get(code)
        if actual is None:
            drifts.append({"code": code, "expected": expected, "actual": None, "reason": "missing"})
            continue
        if actual != expected:
            drifts.append({"code": code, "expected": expected, "actual": actual})

    actual_count = sum(1 for v in actual_by_code.values() if v)
    report = {
        "ok": not drifts and actual_count == expected_count,
        "manifest_hash": manifest.get("manifest_hash"),
        "expected_count": expected_count,
        "actual_count": actual_count,
        "drift_count": len(drifts),
        "drifts": drifts[:50],
    }
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        print(
            f"billingEnabled parity: {'OK' if report['ok'] else 'FAIL'} "
            f"expected={expected_count} actual={actual_count} drifts={len(drifts)}"
        )
        for row in drifts[:20]:
            print(f"  {row['code']}: expected={row['expected']} actual={row.get('actual')}")
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
