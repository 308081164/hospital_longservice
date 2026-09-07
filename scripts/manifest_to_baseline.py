#!/usr/bin/env python3
"""一次性：billing-rules-manifest.json → 按院 baseline/{CODE}.json + index.json。"""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "backend/src/main/resources/billing-seeds/billing-rules-manifest.json"
OUT_DIR = ROOT / "backend/src/main/resources/billing-rules"
BASELINE_DIR = OUT_DIR / "baseline"

sys_path = ROOT / "scripts"
import sys

sys.path.insert(0, str(sys_path))
from strict_hospital_codes import STRICT_KEEP_CODES  # noqa: E402


def canonical_hash(obj: object) -> str:
    text = json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=MANIFEST)
    parser.add_argument("--out", type=Path, default=OUT_DIR)
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    customers = manifest.get("customers") or {}
    baseline_dir = args.out / "baseline"
    baseline_dir.mkdir(parents=True, exist_ok=True)

    index_customers = []
    per_file_hashes: list[str] = []
    for code in STRICT_KEEP_CODES:
        node = customers.get(code)
        if not node:
            print(f"WARN: manifest missing {code}")
            continue
        payload = {
            "customerCode": code,
            "customerName": node.get("name") or code,
            "billingEnabled": node.get("billingEnabled", True),
            "billingPricingMode": node.get("billingPricingMode"),
            "productRules": node.get("productRules") or [],
        }
        out_file = baseline_dir / f"{code}.json"
        out_file.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        file_hash = canonical_hash(payload)
        per_file_hashes.append(file_hash)
        index_customers.append(
            {
                "code": code,
                "file": f"baseline/{code}.json",
                "ruleCount": len(payload["productRules"]),
                "fileHash": file_hash,
            }
        )
        print(f"Wrote {out_file.name} ({len(payload['productRules'])} rules)")

    index = {
        "baseline_hash": canonical_hash(per_file_hashes),
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "source": f"billing-rules-manifest@{manifest.get('manifest_hash', 'unknown')}",
        "customer_count": len(index_customers),
        "customers": index_customers,
    }
    (args.out / "index.json").write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"index.json baseline_hash={index['baseline_hash'][:16]}…")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
