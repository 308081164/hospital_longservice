#!/usr/bin/env python3
"""平房人民缝合针/针盒规则修复验收：simulate API 抽查 + 严格对账入口提示。

用法:
  python3 scripts/tmp_pfq_suture_needle_fix_verify.py
  python3 scripts/tmp_pfq_suture_needle_fix_verify.py --api-base http://127.0.0.1:8000
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from lib.api_client import ApiClient, configure_client  # noqa: E402

CASES = [
    {
        "name": "缝合针-2件应8元（全局通用缝合针）",
        "packName": "缝合针-2件/Z7520",
        "type": "额外包（纸塑袋）",
        "packageMaterial": "高温纸塑袋75*200",
        "instrumentCount": 2,
        "packCount": 1,
        "unitPrice": 8.0,
        "totalPrice": 8.0,
        "expect": 8.0,
        "rule_contains": "通用缝合针",
        "rule_not": "平房人民针盒针",
    },
    {
        "name": "针盒1针58应71.5（院级针盒针5合1）",
        "packName": "针盒1针58/z1026",
        "type": "额外包（纸塑袋）",
        "packageMaterial": "高温纸塑袋15cm",
        "instrumentCount": 59,
        "packCount": 1,
        "unitPrice": 71.5,
        "totalPrice": 71.5,
        "expect": 71.5,
        "rule_contains": "平房人民针盒针",
        "rule_not": None,
    },
]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-base", default="http://127.0.0.1:8000")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin123")
    args = parser.parse_args()

    configure_client(args.api_base, args.username, args.password)
    client = ApiClient()
    cust = client.customer_by_code("PFQ-RM")
    if not cust:
        print("ERROR: PFQ-RM 客户未找到，请确认 API 可用")
        return 1
    cid = cust["id"]
    hospital = cust.get("name") or "哈尔滨市平房区人民医院"

    failed = 0
    for case in CASES:
        row = {
            "hospitalName": hospital,
            "department": "妇病",
            "type": case["type"],
            "packName": case["packName"],
            "packageMaterial": case["packageMaterial"],
            "instrumentCount": case["instrumentCount"],
            "packCount": case["packCount"],
            "unitPrice": case["unitPrice"],
            "totalPrice": case["totalPrice"],
        }
        r = client.simulate_billing(customer_id=cid, hospital_name=hospital, sample_row=row)
        price = float(r.get("expectedUnitPrice") or r.get("ruleUnitPrice") or 0)
        rule = r.get("pricingRule") or r.get("matchedRuleName") or ""
        ok = abs(price - case["expect"]) < 0.02
        if case.get("rule_contains"):
            ok = ok and case["rule_contains"] in rule
        if case.get("rule_not"):
            ok = ok and case["rule_not"] not in rule
        status = "PASS" if ok else "FAIL"
        print(f"{status} {case['name']}: price={price} rule={rule}")
        if not ok:
            failed += 1

    print()
    if failed:
        print(f"失败 {failed}/{len(CASES)} — 若规则仍含「缝合针」关键词，请重启后端触发 baseline sync")
        return 1
    print("全部通过。建议再跑：")
    print("  python3 scripts/special_v8_strict_excel_audit.py --hospital 平房区人民 --month 7 --batch 814")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
