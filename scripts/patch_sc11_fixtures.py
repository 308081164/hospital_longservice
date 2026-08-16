#!/usr/bin/env python3
"""Patch sc11-fixtures.json per billing-evidence audit plan."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "backend/src/test/resources/pricing-engine/sc11-fixtures.json"

CONFIRMED_IDS = {
    "t01_bingcheng_huanzuan_5pc",
    "t04b_neau_root_11",
    "t04b_neau_root_6",
    "t06_zuyan_pai_15",
    "t06_zuyan_pai_25",
    "t08_jiuzhou_fangpan",
    "t08_shkf_billing_off",
    "billing_hybrid_unchanged",
    "billing_disabled_jiuzhou",
}

# DB / mode fixtures stay runnable but not SC11-type confirmed
DB_VALID_IDS = {"billing_hybrid_unchanged", "billing_disabled_jiuzhou"}


def patch_fixture(f: dict) -> dict:
    fid = f["id"]
    if fid in CONFIRMED_IDS:
        f["valid"] = True
        f["skipParameterized"] = False
        f.pop("invalidReason", None)
        if fid not in DB_VALID_IDS and f.get("billingEvidence") != "none":
            f["billingEvidence"] = "confirmed"
    else:
        f["valid"] = False
        f["skipParameterized"] = True
        f["billingEvidence"] = "none"
        f["invalidReason"] = f.get("invalidReason") or "无账单实践或 fixture 设置与 manifest/账单不一致"

    return f


def apply_content_fixes(fixtures: list[dict]) -> None:
    by_id = {f["id"]: f for f in fixtures}

    # T01 — 7月冰城环钻 CSV（仅保留与 7 月账单一致的用例）
    t01_5 = by_id["t01_bingcheng_huanzuan_5pc"]
    t01_5["billingRef"] = "测试用例/哈尔滨冰城医疗美容医院/7月期待价格校正清单.csv#1624134"
    t01_5["row"]["instrumentCount"] = 10
    t01_5["row"]["packCount"] = 2
    t01_5["row"]["unitPrice"] = 27.5
    t01_5["row"]["totalPrice"] = 55.0
    t01_5["expect"] = {
        "status": "warning",
        "expectedUnitPrice": 33.5,
        "pricingRuleContains": "环钻",
        "pricingRuleNotContains": "27.5",
    }

    t01_1 = by_id["t01_bingcheng_huanzuan_1pc"]
    t01_1["valid"] = False
    t01_1["skipParameterized"] = True
    t01_1["billingEvidence"] = "none"
    t01_1["invalidReason"] = "7月账单无 1 件环钻独立行，与 CSV 无法对齐"

    # T04b — NEAU 根管锉 5.6（待匹配文档 + manifest）
    for fid, inst, before, after in [
        ("t04b_neau_root_11", 11, 126.5, 16.8),
        ("t04b_neau_root_6", 6, 66.0, 11.2),
    ]:
        fx = by_id[fid]
        fx["hospitalName"] = "NEAU-YY"
        fx["billingRef"] = "测试用例/待匹配/松电东北农大_口腔科针类5合1核对说明.md"
        fx["row"]["department"] = "口腔科"
        fx["row"]["packName"] = f"根管锉-{inst}/Z7520"
        fx["row"]["instrumentCount"] = inst
        fx["row"]["unitPrice"] = before
        fx["row"]["totalPrice"] = before
        fx["expect"] = {
            "status": "warning",
            "expectedUnitPrice": after,
        }

    # T06 — 祖研 6月 CSV：处理后单价已与账单一致（hybrid 保留已校正价）
    z15 = by_id["t06_zuyan_pai_15"]
    z15["billingRef"] = "测试用例/祖研-黑龙江省中医医院（南岗院区）/6月期待价格校正清单.csv#1609061"
    z15["row"] = {
        "department": "美容科",
        "type": "额外包(纸塑袋)",
        "packName": "排针-15/Z1026",
        "packageMaterial": "高温纸塑袋75*370",
        "instrumentCount": 15,
        "packCount": 1,
        "unitPrice": 13.5,
        "totalPrice": 13.5,
    }
    z15["expect"] = {
        "status": "unchanged",
        "expectedUnitPrice": 13.5,
    }

    z25 = by_id["t06_zuyan_pai_25"]
    z25["billingRef"] = "测试用例/祖研-黑龙江省中医医院（南岗院区）/6月期待价格校正清单.csv#1609796"
    z25["row"] = {
        "department": "美容科",
        "type": "额外包(纸塑袋)",
        "packName": "排针-23/Z1026",
        "packageMaterial": "高温纸塑袋75*370",
        "instrumentCount": 23,
        "packCount": 1,
        "unitPrice": 16.5,
        "totalPrice": 16.5,
    }
    z25["expect"] = {
        "status": "unchanged",
        "expectedUnitPrice": 16.5,
    }

    # T08 — 九州 billing 关闭（confirmed）
    by_id["t08_jiuzhou_fangpan"]["billingRef"] = "manifest#JIUZHOU-FK#billingEnabled=false"
    by_id["t08_jiuzhou_fangpan"]["expect"] = {
        "status": "unchanged",
        "pricingRuleContains": "特色账单已关闭",
    }

    # T08 — 社会康复 billing 关闭（原 t08_shkf_16_5 改为 billing_off 场景）
    shkf = by_id["t08_shkf_billing_off"]
    shkf["billingRef"] = "manifest#SHKF-YY#billingEnabled=false"
    shkf["expect"] = {
        "status": "unchanged",
        "pricingRuleContains": "特色账单已关闭",
    }

    shkf44 = by_id.get("t08_shkf_44")
    if shkf44:
        shkf44["valid"] = False
        shkf44["skipParameterized"] = True
        shkf44["billingEvidence"] = "none"
        shkf44["invalidReason"] = "SHKF billingEnabled=false，无 warning 账单实践"


def main() -> None:
    doc = json.loads(FIXTURES.read_text(encoding="utf-8"))
    fixtures = doc["fixtures"]

    for f in fixtures:
        if f["id"] == "t08_shkf_16_5":
            f["id"] = "t08_shkf_billing_off"
    by_id = {f["id"]: f for f in fixtures}

    apply_content_fixes(fixtures)
    doc["fixtures"] = [patch_fixture(f) for f in fixtures]

    FIXTURES.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Patched {len(doc['fixtures'])} fixtures")


if __name__ == "__main__":
    main()
