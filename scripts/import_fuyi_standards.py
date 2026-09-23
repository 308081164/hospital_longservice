#!/usr/bin/env python3
"""从 docs/source/附一收费标准.xlsx 生成 ZYY-D1 billing/clerk baseline（独立维护，不走医院内勤规则 Excel）。"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
XLSX = ROOT / "docs/source/附一收费标准.xlsx"
BILLING_BASELINE = ROOT / "backend/src/main/resources/billing-rules/baseline/ZYY-D1.json"
CLERK_BASELINE = ROOT / "backend/src/main/resources/clerk-rules/baseline/ZYY-D1.json"
CLERK_INDEX = ROOT / "backend/src/main/resources/clerk-rules/index.json"
SUPPLEMENT = ROOT / "backend/src/main/resources/fuyi-rules/supplement.json"
SOURCE_VERSION = "docs/source/附一收费标准.xlsx"
CUSTOMER_CODE = "ZYY-D1"
CUSTOMER_NAME = "黑龙江中医药大学附属第一医院"


def _num(v) -> float | None:
    if v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v)
    s = str(v).strip()
    if not s:
        return None
    try:
        return float(s)
    except ValueError:
        return None


def _rule(
    name: str,
    rule_type: str,
    *,
    price: float | None = None,
    priority: int = 100,
    keywords: list[str] | None = None,
    exclude_keywords: list[str] | None = None,
    threshold: int | None = None,
    fold_ratio: int | None = None,
    bag_size_equals: int | None = None,
    source_ref: str = "",
) -> dict:
    r: dict = {
        "ruleType": rule_type,
        "name": name,
        "priority": priority,
        "skipPackaging": True,
        "skipDiscount": True,
        "isActive": False,
        "matchMode": "first",
    }
    if price is not None:
        r["price"] = price
    if keywords:
        r["keywords"] = keywords
    if exclude_keywords:
        r["excludeKeywords"] = exclude_keywords
    if threshold is not None:
        r["threshold"] = threshold
    if fold_ratio is not None:
        r["foldRatio"] = fold_ratio
    if bag_size_equals is not None:
        r["bagSizeEquals"] = bag_size_equals
    if source_ref:
        r["sourceRef"] = source_ref
    return r


def build_standard_pricing_override(ws2) -> dict:
    """Sheet2 高温/低温/敷料 → hybrid standardPricingOverride（capMode=fuyi）。"""
    return {
        "highTemperature": {
            "paperPlastic": {
                "capMode": "fuyi",
                "perPackagePrice": 4.4,
                "minCharge": 13.2,
                "bagSizes": [
                    {"size": 25, "price": 12.79, "keywords": ["25cm", "25", "特大"]},
                    {"size": 20, "price": 10.39, "keywords": ["20cm", "20", "大"]},
                    {"size": 15, "price": 8.79, "keywords": ["15cm", "15", "中"]},
                    {"size": 10, "price": 6.39, "keywords": ["10cm", "10", "小"]},
                ],
            },
            "nonWoven": {
                "minCharge": 13.2,
                "flatPerPackagePrice": 4.4,
                "flatRateThreshold": 3,
            },
        },
        "lowTemperature": {
            "paperPlastic": {
                "bagSizes": [
                    {"size": 30, "price": 27.97, "keywords": ["30cm", "30", "低温灭菌 30cm"]},
                    {"size": 25, "price": 23.98, "keywords": ["25cm", "25", "低温灭菌 25cm"]},
                    {"size": 20, "price": 22.38, "keywords": ["20cm", "20", "低温灭菌 20cm"]},
                    {"size": 15, "price": 19.98, "keywords": ["15cm", "15", "低温灭菌 15cm"]},
                    {"size": 10, "price": 17.58, "keywords": ["10cm", "10", "低温灭菌 10cm"]},
                ],
                "tierPrices": [
                    {"count": 20, "price": 239.76},
                    {"count": 10, "price": 131.87},
                    {"count": 5, "price": 70.33},
                ],
                "remainderPerPiecePrice": 17.58,
            },
            "nonWoven": {
                "tierPrices": [
                    {"count": 20, "price": 239.76},
                    {"count": 10, "price": 131.87},
                    {"count": 5, "price": 70.33},
                ],
                "remainderPerPiecePrice": 17.58,
                "minSingleCharge": 17.58,
            },
        },
        "dressingPack": {
            "nonWoven": {
                "below90": 19.98,
                "equals90": 23.98,
                "range12to15": 27.97,
            }
        },
    }


def parse_excel_product_rules(ws2) -> list[dict]:
    rules: list[dict] = []

    # Sheet2 · 特殊收费
    rules.append(
        _rule(
            "30°腹腔镜组合价",
            "FIXED_PRICE",
            price=30.38,
            priority=5,
            keywords=["30°腹腔镜", "30度腹腔镜"],
            source_ref="excel:Sheet2#特殊收费",
        )
    )
    rules.append(
        _rule(
            "换药包120布组合价",
            "FIXED_PRICE",
            price=21.99,
            priority=10,
            keywords=["换药包(120布)", "换药包120"],
            source_ref="excel:Sheet2#特殊收费",
        )
    )
    rules.append(
        _rule(
            "橄榄头5件算1件",
            "FOLD",
            priority=15,
            keywords=["橄榄头"],
            threshold=5,
            fold_ratio=5,
            source_ref="excel:Sheet2#耳鼻喉",
        )
    )
    rules.append(
        _rule(
            "冲洗头5件算1件",
            "FOLD",
            priority=16,
            keywords=["冲洗头"],
            threshold=5,
            fold_ratio=5,
            source_ref="excel:Sheet2#耳鼻喉",
        )
    )

    # Sheet2 · 低温套固定价
    rules.extend(
        [
            _rule(
                "低温套5件",
                "FIXED_PRICE",
                price=70.33,
                priority=30,
                keywords=["低温灭菌（套）5件", "低温灭菌(套)5件"],
                source_ref="excel:Sheet2#低温套",
            ),
            _rule(
                "低温套10件",
                "FIXED_PRICE",
                price=131.87,
                priority=31,
                keywords=["低温灭菌（套）10件", "低温灭菌 (套)10件"],
                source_ref="excel:Sheet2#低温套",
            ),
            _rule(
                "低温套20件",
                "FIXED_PRICE",
                price=239.76,
                priority=32,
                keywords=["低温灭菌（套）20件", "低温灭菌 (套)20件"],
                source_ref="excel:Sheet2#低温套",
            ),
        ]
    )

    # Sheet2 · 敷料包（行 47-51）
    dressing_rows = [
        ("敷料20x20", 3.2, ["20cm*20cm", "20×20"]),
        ("敷料大15x10", 2.0, ["长15cm*宽10cm", "大 （20cm*20cm*15cm）", "20cm*20cm*15cm"]),
        ("敷料大30x30x50", 27.97, ["敷料大（30cm*30cm*50cm）"]),
        ("敷料小20x20x15", 19.98, ["敷料小（20cm*20cm*15cm）"]),
        ("敷料中20x20x30", 23.98, ["敷料中（20cm*20cm*30cm）"]),
    ]
    for i, (name, price, kws) in enumerate(dressing_rows, start=70):
        rules.append(
            _rule(
                name,
                "FIXED_PRICE",
                price=price,
                priority=i,
                keywords=kws,
                source_ref="excel:Sheet2#敷料包",
            )
        )

    return rules


def load_supplement_rules() -> list[dict]:
    if not SUPPLEMENT.is_file():
        return []
    data = json.loads(SUPPLEMENT.read_text(encoding="utf-8"))
    return list(data.get("productRules") or [])


def merge_product_rules(excel_rules: list[dict], supplement_rules: list[dict]) -> list[dict]:
    by_name: dict[str, dict] = {}
    for r in supplement_rules:
        by_name[r["name"]] = r
    for r in excel_rules:
        by_name[r["name"]] = r
    merged = sorted(by_name.values(), key=lambda x: (x.get("priority", 100), x["name"]))
    return merged


def build_billing_baseline(wb) -> dict:
    ws2 = wb["Sheet2"]
    excel_rules = parse_excel_product_rules(ws2)
    product_rules = merge_product_rules(excel_rules, load_supplement_rules())
    return {
        "customerCode": CUSTOMER_CODE,
        "customerName": CUSTOMER_NAME,
        "billingEnabled": True,
        "billingPricingMode": "hybrid",
        "sourceVersion": SOURCE_VERSION,
        "notes": "中医附一独立价表 · 2026-09-23 电话确认：对账试用改为标准计价×0.8×0.99（逐步四舍五入）；原 productRules 封存保留。",
        "billingPolicies": [
            {
                "policyType": "DISCOUNT",
                "name": "附一对账八折",
                "priority": 10,
                "scope": {"temperature": "ANY"},
                "params": {
                    "rate": 0.8,
                    "applyStage": "bill_detail",
                    "skipWhenFixedPrice": False,
                },
                "sourceRef": "phone:2026-09-23",
            },
            {
                "policyType": "DISCOUNT",
                "name": "附一对账九九折",
                "priority": 20,
                "scope": {"temperature": "ANY"},
                "params": {
                    "rate": 0.99,
                    "applyStage": "bill_detail",
                    "skipWhenFixedPrice": False,
                },
                "sourceRef": "phone:2026-09-23",
            },
        ],
        "archivedProductRulesNote": "2026-09-23 封存：类型特色规则暂停，对账改走标准价叠折试用",
        "standardPricingOverride": build_standard_pricing_override(ws2),
        "productRules": product_rules,
    }


def _clerk_rule(
    name: str,
    rule_type: str,
    stage: str,
    params: dict,
    source_text: str,
    priority: int,
    *,
    is_active: bool = True,
) -> dict:
    return {
        "ruleType": rule_type,
        "name": name,
        "stage": stage,
        "isActive": is_active,
        "params": params,
        "sourceText": source_text,
        "sourceRef": f"excel:{SOURCE_VERSION}" if is_active else "sealed:phone-20260923",
        "priority": priority,
    }


def build_clerk_baseline(wb) -> dict:
    """内勤/导出规则：与 附一收费标准.xlsx 及院方确认包一致，独立维护。"""
    rules = [
        _clerk_rule(
            "附一11列分科室账单",
            "EXPORT_LAYOUT",
            "bill_export",
            {
                "billLayout": "dept_split",
                "billColumnLayout": "fuyi_extended_11col",
                "d8DisplaySource": "hospitalName",
            },
            "Sheet1#47-50：包数后插包装材料/把数/单价（把）；分科室多 Sheet",
            100,
        ),
        _clerk_rule(
            "物流2.5元每公里18公里",
            "LOGISTICS_FEE",
            "settlement",
            {"feePerTrip": 45.0},
            "2.5元/公里×18公里=45元/趟",
            110,
        ),
        _clerk_rule(
            "加急125%无减免",
            "URGENT",
            "settlement",
            {
                "baseMultiplier": 1.25,
                "adjustedMultiplier": 1.25,
                "urgentLogisticsDiscountRate": 1.0,
            },
            "通用加急灭菌费125%，无医院专属减免",
            120,
        ),
        _clerk_rule(
            "分科室汇总附表",
            "MONTHLY_SUPPLEMENT_REPORT",
            "both",
            {"reportType": "dept_summary"},
            "分科室汇总表",
            130,
        ),
        _clerk_rule(
            "物流分摊附表",
            "MONTHLY_SUPPLEMENT_REPORT",
            "both",
            {"reportType": "logistics_allocation"},
            "物流分摊表",
            140,
        ),
        _clerk_rule(
            "手术一区洗涤费",
            "SETTLEMENT_EXTRA",
            "settlement",
            {
                "itemName": "手术一区洗涤费用",
                "amountByMonth": {"2026-05": 5275.4, "2026-06": 3987.1},
                "note": "5月起写入结款函；明细表单独维护，按月录入 amountByMonth",
            },
            "本院独有洗涤费独立收费行",
            150,
        ),
        _clerk_rule(
            "包装材料名标准化",
            "EXPORT_LAYOUT",
            "bill_export",
            {
                "materialAliases": [
                    {"from": "高温纸塑袋", "toPrefix": "纸塑袋"},
                    {"from": "低温纸塑袋", "toPrefix": "低温灭菌"},
                ]
            },
            "Sheet1#52：导出时纸塑袋名称替换为标准名",
            160,
        ),
        _clerk_rule(
            "整院八折（封存）",
            "DISCOUNT_OVERLAY",
            "bill_export",
            {
                "rate": 0.8,
                "validateOnly": True,
                "_sealedReason": "2026-09-23 电话确认：旧整院折扣/export 调价封存，改 billing baseline 对账叠折",
            },
            "整院折扣/export 阶段调价（已停用）",
            170,
            is_active=False,
        ),
        _clerk_rule(
            "标准价叠折校对（封存）",
            "PRICE_VALIDATE_ONLY",
            "bill_export",
            {
                "rate": 0.792,
                "validateOnly": True,
                "_sealedReason": "2026-09-23 旧复合调价方案封存；现行 0.8×0.99 分步四舍五入见 billingPolicies",
            },
            "旧版类型特色单价/export 校对（已停用）",
            180,
            is_active=False,
        ),
    ]
    return {
        "customerCode": CUSTOMER_CODE,
        "customerName": CUSTOMER_NAME,
        "sourceVersion": SOURCE_VERSION,
        "notes": "中医附一内勤规则 · 2026-09-23 电话确认：调价/整院折扣类 clerk 规则已封存；对账叠折改由 billing-rules/baseline/ZYY-D1.json billingPolicies。",
        "attachmentRefs": [],
        "rules": rules,
    }


def canonical_hash(obj: object) -> str:
    text = json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def update_clerk_index() -> None:
    baseline_dir = CLERK_BASELINE.parent
    customers = []
    payloads = []
    for path in sorted(baseline_dir.glob("*.json")):
        code = path.stem
        payload = json.loads(path.read_text(encoding="utf-8"))
        payloads.append(json.dumps(payload, ensure_ascii=False, sort_keys=True))
        customers.append(
            {
                "code": code,
                "file": f"baseline/{path.name}",
                "ruleCount": len(payload.get("rules") or []),
            }
        )
    customers.sort(key=lambda x: x["code"])
    index = {
        "baseline_hash": hashlib.sha256("\n".join(sorted(payloads)).encode()).hexdigest(),
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "source": f"{SOURCE_VERSION} + 铂康/内勤要求/医院内勤规则-20260918.xlsx",
        "customer_count": len(customers),
        "customers": customers,
    }
    CLERK_INDEX.write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_baselines(billing: dict, clerk: dict, dry_run: bool) -> None:
    if dry_run:
        print(json.dumps({"billing": billing, "clerk": clerk}, ensure_ascii=False, indent=2)[:4000])
        return
    BILLING_BASELINE.parent.mkdir(parents=True, exist_ok=True)
    CLERK_BASELINE.parent.mkdir(parents=True, exist_ok=True)
    BILLING_BASELINE.write_text(json.dumps(billing, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    CLERK_BASELINE.write_text(json.dumps(clerk, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    update_clerk_index()
    print(f"Wrote {BILLING_BASELINE.relative_to(ROOT)} ({len(billing['productRules'])} productRules)")
    print(f"Wrote {CLERK_BASELINE.relative_to(ROOT)} ({len(clerk['rules'])} clerk rules)")
    print(f"Updated {CLERK_INDEX.relative_to(ROOT)} (customer_count={json.loads(CLERK_INDEX.read_text())['customer_count']})")


def main() -> int:
    parser = argparse.ArgumentParser(description="附一收费标准 → ZYY-D1 billing/clerk baseline")
    parser.add_argument("--xlsx", type=Path, default=XLSX, help="权威 Excel 路径")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if not args.xlsx.is_file():
        print(f"ERROR: missing {args.xlsx}", flush=True)
        return 1
    wb = openpyxl.load_workbook(args.xlsx, data_only=True)
    billing = build_billing_baseline(wb)
    clerk = build_clerk_baseline(wb)
    wb.close()
    write_baselines(billing, clerk, args.dry_run)
    if not args.dry_run:
        print("Next: python3 scripts/refresh_baseline_index.py && python3 scripts/baseline_to_manifest.py --write")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
