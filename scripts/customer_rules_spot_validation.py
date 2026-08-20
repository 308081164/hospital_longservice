#!/usr/bin/env python3
"""One-off spot validation for customer rules compare checklist (2026-08-09)."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from lib.api_client import ApiClient  # noqa: E402

OUTPUT = ROOT / "测试用例" / "customer_rules_spot_validation_20260811.json"

CASES: list[dict[str, Any]] = [
    {
        "id": "global_keshizhen_fold",
        "code": "ZYY-D1",
        "hospital": "黑龙江中医药大学附属第一医院",
        "name": "克氏针8件5合1",
        "sample": {
            "department": "骨科",
            "packName": "克氏针-8/Z7520",
            "type": "额外包(纸塑袋)",
            "packageMaterial": "高温纸塑袋250*200",
            "instrumentCount": 8,
            "packCount": 1,
            "unitPrice": 22,
            "totalPrice": 22,
        },
        "expectedUnitPrice": 16.5,
    },
    {
        "id": "global_juanmianzi_fold",
        "code": "ZYY-D1",
        "hospital": "黑龙江中医药大学附属第一医院",
        "name": "卷棉子10件5合1",
        "sample": {
            "department": "手术室",
            "packName": "卷棉子-10/Z7520",
            "type": "额外包(纸塑袋)",
            "packageMaterial": "高温纸塑袋250*200",
            "instrumentCount": 10,
            "packCount": 1,
            "unitPrice": 22,
            "totalPrice": 22,
        },
        "expectedUnitPrice": 16.5,
    },
    {
        "id": "global_lt_double_35",
        "code": "ZYY-D1",
        "hospital": "黑龙江中医药大学附属第一医院",
        "name": "低温双层1件35元",
        "sample": {
            "department": "手术室",
            "packName": "双极钳-1/z2060",
            "type": "额外包(低温等离子)",
            "packageMaterial": "低温纸塑袋200*300",
            "instrumentCount": 1,
            "packCount": 1,
            "unitPrice": 35,
            "totalPrice": 35,
        },
        "expectedUnitPrice": 35.0,
    },
    {
        "id": "hrb2nd_lt_double",
        "code": "HRB-2ND",
        "hospital": "哈尔滨市第二医院",
        "name": "市二院低温双层1件",
        "sample": {
            "department": "手术室",
            "packName": "双极钳-1/z2060",
            "type": "额外包(低温等离子)",
            "packageMaterial": "低温纸塑袋200*300",
            "instrumentCount": 1,
            "packCount": 1,
            "unitPrice": 35,
            "totalPrice": 35,
        },
        "expectedUnitPrice": None,
        "note": "记录实际价，核对市二院是否排除35元最低",
    },
    {
        "id": "global_suture_dressing",
        "code": "ZYY-D1",
        "hospital": "黑龙江中医药大学附属第一医院",
        "name": "缝合针敷料纸塑25cm",
        "sample": {
            "department": "外科",
            "packName": "缝合针敷料包",
            "type": "敷料包(纸塑袋)",
            "packageMaterial": "高温纸塑袋250*200",
            "instrumentCount": 5,
            "packCount": 1,
            "unitPrice": 4,
            "totalPrice": 4,
        },
        "expectedUnitPrice": 4.0,
    },
    {
        "id": "guoyao2_suture_8",
        "code": "GUOYAO-2",
        "hospital": "国药总医院第二院区",
        "name": "电机厂缝合针8元",
        "sample": {
            "department": "手术室",
            "packName": "缝合针-6/Z7520",
            "type": "额外包(纸塑袋)",
            "packageMaterial": "高温纸塑袋75*200",
            "instrumentCount": 2,
            "packCount": 1,
            "unitPrice": 8,
            "totalPrice": 16,
        },
        "expectedUnitPrice": 8.0,
    },
    {
        "id": "heu_kongjin_4",
        "code": "HRB-HEU",
        "hospital": "哈尔滨工程大学医院",
        "name": "孔巾20cm4元",
        "sample": {
            "department": "五官科",
            "packName": "孔巾/Z2032",
            "type": "敷料包(纸塑袋)",
            "packageMaterial": "高温纸塑袋200*320",
            "instrumentCount": 1,
            "packCount": 1,
            "unitPrice": 4,
            "totalPrice": 4,
        },
        "expectedUnitPrice": 4.0,
    },
    {
        "id": "guoyao2_pointer_fold",
        "code": "GUOYAO-2",
        "hospital": "国药总医院第二院区",
        "name": "指针10件5合1含包材",
        "sample": {
            "department": "手术室",
            "packName": "指针-10/z7537",
            "type": "额外包(纸塑袋)",
            "packageMaterial": "高温纸塑袋75*370",
            "instrumentCount": 10,
            "packCount": 1,
            "unitPrice": 13.5,
            "totalPrice": 13.5,
        },
        "expectedUnitPrice": 13.5,
        "note": "客户表：2折算件×5.5+包材(10cm)=13.5；补规则后一致",
    },
    {
        "id": "guoyao2_kirschner_fold_12",
        "code": "GUOYAO-2",
        "hospital": "国药总医院第二院区",
        "name": "克氏针12件5合1免包材",
        "sample": {
            "department": "手术室",
            "packName": "克氏针-12/Z7530",
            "type": "额外包(纸塑袋)",
            "packageMaterial": "高温纸塑袋75*300",
            "instrumentCount": 12,
            "packCount": 1,
            "unitPrice": 16.5,
            "totalPrice": 16.5,
        },
        "expectedUnitPrice": 16.5,
        "expectedPricingRuleContains": "通用小件5合1免包材",
        "note": "Excel通用：ceil(12/5)×5.5=16.5；非12×5.5=66",
    },
    {
        "id": "guoyao2_double_per_piece",
        "code": "GUOYAO-2",
        "hospital": "国药总医院第二院区",
        "name": "双1件纸塑袋含外层袋",
        "sample": {
            "department": "手术室",
            "packName": "双-1/z2060",
            "type": "额外包(纸塑袋)",
            "packageMaterial": "高温纸塑袋75*200",
            "instrumentCount": 1,
            "packCount": 1,
            "unitPrice": 8.0,
            "totalPrice": 8.0,
        },
        "expectedUnitPrice": 8.0,
    },
    {
        "id": "bcym_huanzuan_per_piece",
        "code": "BINGCHENG-YM",
        "hospital": "哈尔滨冰城医疗美容医院",
        "name": "环钻包2件按件",
        "sample": {
            "department": "手术室",
            "packName": "环钻包",
            "type": "器械包(ZSD)",
            "packageMaterial": "高温灭菌无纺布60*60",
            "instrumentCount": 2,
            "packCount": 1,
            "unitPrice": 14.0,
            "totalPrice": 14.0,
        },
        "expectedUnitPrice": 14.0,
    },
    {
        "id": "wyem_quxuedai_w50",
        "code": "HRB-WY-EM",
        "hospital": "哈尔滨市第五医院（二门诊）",
        "name": "驱血带W50",
        "sample": {
            "department": "手术室",
            "packName": "驱血带/W5050",
            "type": "敷料包(无纺布包)",
            "packageMaterial": "无纺布W50*50",
            "instrumentCount": 1,
            "packCount": 1,
            "unitPrice": 25.0,
            "totalPrice": 25.0,
        },
        "expectedUnitPrice": 25.0,
    },
]


def _price_close(actual: Any, expected: float, *, tol: float = 0.05) -> bool:
    try:
        return math.isclose(float(actual), expected, abs_tol=tol)
    except (TypeError, ValueError):
        return False


def run(*, code: str | None = None, case_id: str | None = None, api_base: str | None = None) -> dict[str, Any]:
    client = ApiClient(api_base=api_base) if api_base else ApiClient(mode="docker")
    client.login(force=True)
    results: list[dict[str, Any]] = []
    cases = CASES
    if code:
        cases = [c for c in cases if c.get("code") == code.strip().upper()]
    if case_id:
        cases = [c for c in cases if c.get("id") == case_id.strip()]
    if not cases:
        return {"passed": 0, "failed": 1, "observed": 0, "results": [{"ok": False, "error": "no cases matched filter"}]}
    for case in cases:
        customer = client.customer_by_code(case["code"])
        if customer is None:
            results.append({**case, "ok": False, "error": "customer not found"})
            continue
        customer_id = int(customer.get("id") or customer.get("customerId") or 0)
        rule_id = customer.get("defaultRuleId") or customer.get("default_rule_id") or 1
        sample = dict(case["sample"])
        sample.setdefault("sheetName", sample.get("department"))
        try:
            sim = client.simulate_billing(
                customer_id=customer_id,
                hospital_name=case["hospital"],
                sample_row=sample,
                rule_id=int(rule_id),
            )
            actual = sim.get("expectedUnitPrice") or sim.get("expected_unit_price")
            expected = case.get("expectedUnitPrice")
            pricing_rule = sim.get("pricingRule") or sim.get("pricing_rule")
            ok: bool | None
            if expected is None:
                ok = None
            else:
                ok = _price_close(actual, float(expected))
            rule_contains = case.get("expectedPricingRuleContains")
            if ok and rule_contains:
                ok = rule_contains in str(pricing_rule or "")
            results.append(
                {
                    "id": case["id"],
                    "name": case["name"],
                    "code": case["code"],
                    "expectedUnitPrice": expected,
                    "actualUnitPrice": actual,
                    "pricingRule": pricing_rule,
                    "status": sim.get("status"),
                    "ok": ok,
                    "note": case.get("note"),
                }
            )
        except Exception as exc:
            results.append(
                {
                    "id": case["id"],
                    "name": case["name"],
                    "code": case["code"],
                    "ok": False,
                    "error": str(exc),
                    "expectedUnitPrice": case.get("expectedUnitPrice"),
                }
            )
    report = {
        "passed": sum(1 for r in results if r.get("ok") is True),
        "failed": sum(1 for r in results if r.get("ok") is False),
        "observed": sum(1 for r in results if r.get("ok") is None),
        "results": results,
    }
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Customer rules spot validation via simulate API")
    parser.add_argument("--code", help="Filter by customer code")
    parser.add_argument("--id", help="Filter by case id")
    parser.add_argument("--api", help="API base URL (default: docker/local)")
    args = parser.parse_args()
    report = run(code=args.code, case_id=args.id, api_base=args.api)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if report.get("failed", 0) > 0:
        raise SystemExit(1)
