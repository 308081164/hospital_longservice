#!/usr/bin/env python3
"""从 baseline/{CODE}.json 重新计算 index.json（单院或全量）。"""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "backend/src/main/resources/billing-rules"
BASELINE_DIR = OUT_DIR / "baseline"

import sys

sys.path.insert(0, str(ROOT / "scripts"))
from strict_hospital_codes import STRICT_KEEP_CODES  # noqa: E402


def canonical_hash(obj: object) -> str:
    text = json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--customer", help="仅刷新指定院 index 元数据（仍重算全局 hash）")
    args = parser.parse_args()

    index_path = OUT_DIR / "index.json"
    index = json.loads(index_path.read_text(encoding="utf-8")) if index_path.is_file() else {}

    index_customers = []
    per_file_hashes: list[str] = []
    for code in STRICT_KEEP_CODES:
        path = BASELINE_DIR / f"{code}.json"
        if not path.is_file():
            print(f"WARN: missing {path}")
            continue
        payload = json.loads(path.read_text(encoding="utf-8"))
        file_hash = canonical_hash(payload)
        per_file_hashes.append(file_hash)
        index_customers.append(
            {
                "code": code,
                "file": f"baseline/{code}.json",
                "ruleCount": len(payload.get("productRules") or []),
                "fileHash": file_hash,
            }
        )
        if args.customer and code == args.customer.strip().upper():
            print(f"refreshed {code}: {len(payload.get('productRules') or [])} rules")

    index.update(
        {
            "baseline_hash": canonical_hash(per_file_hashes),
            "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "customer_count": len(index_customers),
            "customers": index_customers,
        }
    )
    index_path.write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"index.json baseline_hash={index['baseline_hash'][:16]}…")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
