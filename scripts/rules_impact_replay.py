#!/usr/bin/env python3
"""规则变更影响回放：对指定客户的真实包名语料批量调用 simulate API。"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from lib.api_client import ApiClient, configure_client, get_client  # noqa: E402

TEST_CASE = ROOT / "测试用例"


def collect_pack_names(customer_code: str, limit: int) -> list[str]:
    """从测试用例目录收集包名（简化：按医院目录名模糊匹配）。"""
    names: list[str] = []
    if not TEST_CASE.is_dir():
        return names
    for hospital_dir in TEST_CASE.iterdir():
        if not hospital_dir.is_dir():
            continue
        for xlsx in hospital_dir.rglob("*.xlsx"):
            if xlsx.name.startswith("~$"):
                continue
            try:
                import openpyxl  # type: ignore

                wb = openpyxl.load_workbook(xlsx, read_only=True, data_only=True)
                for ws in wb.worksheets:
                    headers = [str(c.value or "").strip() for c in next(ws.iter_rows(max_row=1))]
                    col = next((i for i, h in enumerate(headers) if "包名" in h), None)
                    if col is None:
                        continue
                    for row in ws.iter_rows(min_row=2, values_only=True):
                        if col >= len(row):
                            continue
                        val = str(row[col] or "").strip()
                        if val and val not in names:
                            names.append(val)
                        if len(names) >= limit:
                            wb.close()
                            return names
                wb.close()
            except Exception:
                continue
            if len(names) >= limit:
                break
    return names[:limit]


def main() -> int:
    parser = argparse.ArgumentParser(description="规则影响回放（simulate 批量）")
    parser.add_argument("--base-url", default="http://127.0.0.1:8853")
    parser.add_argument("--customer-id", type=int, required=True)
    parser.add_argument("--hospital-name", required=True)
    parser.add_argument("--code", help="customer code（仅用于语料目录提示）")
    parser.add_argument("--limit", type=int, default=50)
    parser.add_argument("--out", type=Path, default=ROOT / "测试用例" / "rules_impact_replay_report.json")
    args = parser.parse_args()
    configure_client(args.base_url)
    client = get_client()
    pack_names = collect_pack_names(args.code or "", args.limit)
    results = []
    for pack_name in pack_names:
        payload = {
            "customerId": args.customer_id,
            "hospitalName": args.hospital_name,
            "sampleRow": {
                "packName": pack_name,
                "type": "额外包(纸塑袋)",
                "packageMaterial": "高温纸塑袋150*260",
                "instrumentCount": 1,
                "unitPrice": 10,
                "totalPrice": 10,
                "packCount": 1,
            },
        }
        try:
            resp = client.post("/api/v1/billing-rules/simulate", json=payload)
            data = resp.get("data") or resp
            results.append(
                {
                    "packName": pack_name,
                    "expectedUnitPrice": data.get("expected_unit_price") or data.get("expectedUnitPrice"),
                    "pricingRule": data.get("pricing_rule") or data.get("pricingRule"),
                    "matchedRuleId": data.get("matched_rule_id") or data.get("matchedRuleId"),
                }
            )
        except Exception as exc:
            results.append({"packName": pack_name, "error": str(exc)})
    report = {"customerId": args.customer_id, "hospitalName": args.hospital_name, "results": results}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {args.out} ({len(results)} rows)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
