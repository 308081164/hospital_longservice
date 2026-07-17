#!/usr/bin/env python3
"""Generate hospital-billing-golden-rows.json skeleton (>=20 hospitals x >=5 rows)."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/test/resources/hospital-billing-golden-rows.json"

HT_ROW = {
    "type": "额外包(纸塑袋)",
    "packName": "普通器械-4/Z7526",
    "packageMaterial": "高温纸塑袋20cm",
    "instrumentCount": 4,
    "packCount": 1,
    "unitPrice": 22,
    "totalPrice": 22,
}
HT_EXPECT = {"expectedUnitPrice": 22.0, "correctedTotalPrice": 22.0, "status": "unchanged"}

HOSPITALS = [
    ("dongbei-nongda", "东北农业大学医院", [
        ("per-instrument-jieya", "洁牙机尖按件固定价", {
            "type": "额外包(纸塑袋)", "packName": "洁牙机尖-4/Z7526",
            "packageMaterial": "高温纸塑袋75*200", "instrumentCount": 4,
            "packCount": 1, "unitPrice": 22, "totalPrice": 22,
        }, HT_EXPECT),
        ("ht-4-standard", "高温4件标准价", HT_ROW, HT_EXPECT),
        ("ht-1-min", "高温1件低消", {
            **HT_ROW, "packName": "普通器械-1/Z7526", "instrumentCount": 1,
            "unitPrice": 16.5, "totalPrice": 16.5,
        }, {"expectedUnitPrice": 16.5, "correctedTotalPrice": 16.5, "status": "unchanged"}),
        ("ht-3-mid-bag", "高温15cm三件", {
            **HT_ROW, "packageMaterial": "高温纸塑袋15cm", "instrumentCount": 3,
            "unitPrice": 16.5, "totalPrice": 16.5,
        }, {"expectedUnitPrice": 16.5, "correctedTotalPrice": 16.5, "status": "unchanged"}),
        ("ht-2-small-bag", "高温10cm两件", {
            **HT_ROW, "packageMaterial": "高温纸塑袋10cm", "instrumentCount": 2,
            "unitPrice": 16.5, "totalPrice": 16.5,
        }, {"expectedUnitPrice": 16.5, "correctedTotalPrice": 16.5, "status": "unchanged"}),
    ]),
    ("hangtian-fenghua", "哈尔滨航天风华医院", [
        ("per-instrument-spoon", "挖勺按件计价warning", {
            "type": "额外包(纸塑袋)", "packName": "挖勺-2/z7530",
            "packageMaterial": "高温纸塑袋75*300", "instrumentCount": 8,
            "packCount": 4, "unitPrice": 13.5, "totalPrice": 54,
        }, {"expectedUnitPrice": 11.0, "correctedTotalPrice": 44.0, "status": "warning"}),
    ] + [
        (f"ht-{i}", f"高温标准样例{i}", HT_ROW, HT_EXPECT) for i in range(1, 5)
    ]),
    ("songdian", "哈尔滨道外区松电慢性病专科门诊部", [
        ("fold-jikuozhen", "机扩针FOLD折算", {
            "type": "额外包(纸塑袋)", "packName": "机扩针-20/Z7520",
            "packageMaterial": "高温纸塑袋75*200", "instrumentCount": 20,
            "packCount": 1, "unitPrice": 22, "totalPrice": 22,
        }, {**HT_EXPECT, "notesContains": ["松电机扩针"]}),
    ] + [
        (f"ht-{i}", f"高温标准样例{i}", HT_ROW, HT_EXPECT) for i in range(1, 5)
    ]),
    ("hl-zgh", "黑龙江总工会医院", [
        ("extra-fee-lens", "镜头低温阶梯+加收", {
            "type": "单包装包(老肯低温)", "packName": "30°镜头，镜鞘-2（带转换帽）/Z2060",
            "packageMaterial": "低温纸塑袋200*600", "instrumentCount": 4,
            "packCount": 2, "unitPrice": 52, "totalPrice": 104,
        }, {**HT_EXPECT, "expectedUnitPrice": 52.0, "correctedTotalPrice": 104.0,
            "notesContains": ["镜头"]}),
    ] + [
        (f"ht-{i}", f"高温标准样例{i}", HT_ROW, HT_EXPECT) for i in range(1, 5)
    ]),
    ("er-ng", "黑龙江省第二医院（南岗区）", [
        ("fixed-xiaqiang", "小腔包固定价", {
            **HT_ROW, "packName": "小腔包/Z7526", "unitPrice": 49.7, "totalPrice": 49.7,
        }, {"expectedUnitPrice": 49.7, "correctedTotalPrice": 49.7, "status": "unchanged",
            "notesContains": ["小腔包"]}),
        ("fixed-ruan-jing", "软镜固定价", {
            **HT_ROW, "packName": "软镜/Z7526", "unitPrice": 210, "totalPrice": 210,
        }, {"expectedUnitPrice": 210.0, "correctedTotalPrice": 210.0, "status": "unchanged"}),
        ("fixed-ding", "钉固定价", {
            **HT_ROW, "packName": "xx钉/Z7526", "unitPrice": 140, "totalPrice": 140,
        }, {"expectedUnitPrice": 140.0, "correctedTotalPrice": 140.0, "status": "unchanged"}),
        ("fixed-hollow-skip", "空心钉不命中钉规则", {
            **HT_ROW, "packName": "3.6空心钉-2", "unitPrice": 13.3, "totalPrice": 13.3,
        }, {"expectedUnitPrice": 13.3, "correctedTotalPrice": 13.3, "status": "unchanged",
            "notesContains": ["3.6空心钉"]}),
        ("ht-standard", "高温标准", HT_ROW, HT_EXPECT),
    ]),
    ("er-sb", "黑龙江省第二医院（松北区）", [
        ("fixed-xiaqiang", "小腔包固定价", {
            **HT_ROW, "packName": "小腔包/Z7526", "unitPrice": 53.55, "totalPrice": 53.55,
        }, {"expectedUnitPrice": 53.55, "correctedTotalPrice": 53.55, "status": "unchanged"}),
        ("fixed-ding", "钉固定价", {
            **HT_ROW, "packName": "xx钉/Z7526", "unitPrice": 35, "totalPrice": 35,
        }, {"expectedUnitPrice": 35.0, "correctedTotalPrice": 35.0, "status": "unchanged"}),
    ] + [
        (f"ht-{i}", f"高温标准样例{i}", HT_ROW, HT_EXPECT) for i in range(1, 4)
    ]),
]

# Pad to 20 hospitals with standard 5-row blocks
STANDARD_BLOCK = [
    (f"ht-4-standard", "高温4件标准价", HT_ROW, HT_EXPECT),
    (f"ht-1-min", "高温1件低消", {
        **HT_ROW, "packName": "普通器械-1/Z7526", "instrumentCount": 1,
        "unitPrice": 16.5, "totalPrice": 16.5,
    }, {"expectedUnitPrice": 16.5, "correctedTotalPrice": 16.5, "status": "unchanged"}),
    (f"ht-3-mid", "高温15cm三件", {
        **HT_ROW, "packageMaterial": "高温纸塑袋15cm", "instrumentCount": 3,
        "unitPrice": 16.5, "totalPrice": 16.5,
    }, {"expectedUnitPrice": 16.5, "correctedTotalPrice": 16.5, "status": "unchanged"}),
    (f"ht-2-small", "高温10cm两件", {
        **HT_ROW, "packageMaterial": "高温纸塑袋10cm", "instrumentCount": 2,
        "unitPrice": 16.5, "totalPrice": 16.5,
    }, {"expectedUnitPrice": 16.5, "correctedTotalPrice": 16.5, "status": "unchanged"}),
    (f"ht-8-large", "高温20cm八件", {
        **HT_ROW, "instrumentCount": 8, "unitPrice": 44, "totalPrice": 44,
    }, {"expectedUnitPrice": 44.0, "correctedTotalPrice": 44.0, "status": "unchanged"}),
]

EXTRA_HOSPITALS = [
    "呼兰区第一人民医院", "显著医生集团中西医结合门诊", "哈尔滨美涵美医疗美容有限公司",
    "黑龙江省海员总医院（松北）", "黑龙江省中医药大学附属第四医院", "哈尔滨市道里区妇幼保健院",
    "黑龙江省妇幼保健院（人口）", "哈尔滨市道外区人民医院", "黑龙江维多利亚妇产医院",
    "哈尔滨市红十字妇产医院", "黑龙江中医药大学附属第三医院", "哈尔滨冰城医疗美容医院",
    "五常市人民医院", "予美医疗整形医院",
]

for idx, name in enumerate(EXTRA_HOSPITALS, start=1):
    slug = f"std-{idx:02d}"
    rows = [(f"{slug}-{rid}", desc, {**inp, "hospitalName": name}, exp)
            for rid, desc, inp, exp in STANDARD_BLOCK]
    HOSPITALS.append((slug, name, rows))

# Phase 0 overlay cases (省二院多报价 / excludeKeywords)
OVERLAY_CASES = [
    {
        "id": "exclude-keywords-hollow-nail-skipped",
        "description": "excludeKeywords：空心钉不匹配「xx钉」固定价规则",
        "rulesOverlay": {"fixedPrices": [{
            "name": "xx钉", "price": 200.0, "skipPackaging": True,
            "hospitals": ["省二院"], "keywords": ["钉"], "excludeKeywords": ["空心钉"],
        }]},
        "input": {"hospitalName": "省二院", "type": "额外包(纸塑袋)", "packName": "3.6空心钉-2",
                  "packageMaterial": "高温纸塑袋75*200", "instrumentCount": 2, "packCount": 1,
                  "unitPrice": 19, "totalPrice": 19},
        "expected": {"status": "warning", "notesNotContains": ["xx钉"]},
    },
    {
        "id": "any-price-lower-tier",
        "description": "matchMode=any_price：账单价 71 命中较低候选价",
        "rulesOverlay": {"fixedPrices": [{
            "ruleId": 100, "name": "小腔包", "price": 71.0, "matchMode": "any_price",
            "skipPackaging": True, "hospitals": ["省二院"], "keywords": ["小腔包"],
            "acceptedPrices": [71.0, 76.5],
        }]},
        "input": {"hospitalName": "省二院", "type": "额外包(纸塑袋)", "packName": "小腔包A",
                  "packageMaterial": "高温纸塑袋75*200", "instrumentCount": 1, "packCount": 1,
                  "unitPrice": 71, "totalPrice": 71},
        "expected": {"status": "unchanged", "matchedRuleId": 100, "matchedPriceOption": 71.0,
                     "notesContains": ["多报价命中"],
                     "billingNotes": {"type": "any_price_match", "matchedRuleId": 100,
                                      "matchedPrice": 71.0, "candidatePrices": [71.0, 76.5]}},
    },
    {
        "id": "path-override-daowai",
        "description": "pathOverride：道外人民无低温强制高温单价",
        "rulesOverlay": {"billingProfile": {
            "pathOverride": {"disableLowTemp": True, "forceHighTempUnitPrice": 3.0}}},
        "input": {"hospitalName": "道外人民", "type": "单包装包(老肯低温)",
                  "packName": "普通器械-4/Z7526", "packageMaterial": "低温纸塑袋200*600",
                  "instrumentCount": 4, "packCount": 1, "unitPrice": 12, "totalPrice": 12},
        "expected": {"expectedUnitPrice": 12.0, "correctedTotalPrice": 12.0, "status": "unchanged",
                     "notesContains": ["路径覆盖"]},
    },
    {
        "id": "special-only-unmatched",
        "description": "special_only 未命中特色规则",
        "rulesOverlay": {"billingProfile": {"pricingMode": "special_only"}},
        "input": {"hospitalName": "某院", "type": "额外包(纸塑袋)", "packName": "普通器械-4/Z7526",
                  "packageMaterial": "高温纸塑袋20cm", "instrumentCount": 4, "packCount": 1,
                  "unitPrice": 22, "totalPrice": 22},
        "expected": {"status": "unchanged", "notesContains": ["仅特色规则"]},
    },
]

cases = []
for slug, hospital, rows in HOSPITALS:
    for rid, desc, inp, exp in rows:
        case = {
            "id": f"{slug}-{rid}",
            "description": f"{hospital}：{desc}",
            "input": {**inp, "hospitalName": hospital},
            "expected": exp,
        }
        cases.append(case)

cases.extend(OVERLAY_CASES)

doc = {
    "version": "phase0-v2",
    "description": "Phase 0 golden rows — >=20 hospitals x >=5 rows regression skeleton",
    "hospitalCount": len(HOSPITALS),
    "caseCount": len(cases),
    "cases": cases,
}

OUT.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Wrote {len(cases)} cases for {len(HOSPITALS)} hospitals -> {OUT}")
