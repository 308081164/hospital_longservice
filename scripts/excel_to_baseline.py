#!/usr/bin/env python3
"""Excel → 按院 baseline JSON（策略 B）。完整语义解析待与 Excel 对账报告对齐后扩展。"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "backend/src/main/resources/billing-rules/baseline"

sys.path.insert(0, str(ROOT / "scripts"))
from strict_hospital_codes import STRICT_BY_CODE  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="Excel → baseline/{CODE}.json（占位：需人工核对）")
    parser.add_argument("--excel", type=Path, required=True)
    parser.add_argument("--customer", required=True, help="STRICT_KEEP_CODES 中的 code")
    parser.add_argument("--out", type=Path, default=OUT_DIR)
    args = parser.parse_args()
    code = args.customer.strip().upper()
    if code not in STRICT_BY_CODE:
        print(f"ERROR: {code} 不在 29 家清单", file=sys.stderr)
        return 1
    if not args.excel.is_file():
        print(f"ERROR: Excel 不存在: {args.excel}", file=sys.stderr)
        return 1

    hospital = STRICT_BY_CODE[code]
    payload = {
        "customerCode": code,
        "customerName": hospital.label,
        "billingEnabled": True,
        "productRules": [],
        "_comment": f"由 excel_to_baseline 从 {args.excel.name} 生成；请补全 productRules 后运行 rules verify",
        "_source_excel": str(args.excel),
    }
    args.out.mkdir(parents=True, exist_ok=True)
    out = args.out / f"{code}.json"
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote skeleton {out} — 请根据 Excel 手工/脚本补全 productRules")
    print("下一步: python3 scripts/manifest_to_baseline.py  # 刷新 index.json")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
