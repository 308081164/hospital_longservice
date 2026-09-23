#!/usr/bin/env python3
"""一次性补齐内勤规则缺口 baseline（不覆盖 ZYY-D1 等 EXTRA 客户）。"""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINE_DIR = ROOT / "backend/src/main/resources/clerk-rules/baseline"

REPORT_TYPE_ALIASES = {
    "dept_sterilize_summary": "dept_summary",
    "instrument_count_by_dept": "instrument_audit",
}


def norm_params(params: dict) -> str:
    return json.dumps(params or {}, sort_keys=True, ensure_ascii=False)


def dedupe_rules(rules: list[dict]) -> list[dict]:
    merged: list[dict] = []
    index: dict[tuple[str, str], int] = {}
    for rule in rules:
        key = (rule.get("ruleType", ""), norm_params(rule.get("params")))
        if key in index:
            existing = merged[index[key]]
            ref = rule.get("sourceRef", "")
            if ref and ref not in existing.get("sourceRef", ""):
                existing["sourceRef"] = f"{existing.get('sourceRef', '')}|{ref}".strip("|")
        else:
            index[key] = len(merged)
            merged.append(rule)
    return merged


def migrate_report_types(rules: list[dict]) -> None:
    for rule in rules:
        if rule.get("ruleType") != "MONTHLY_SUPPLEMENT_REPORT":
            continue
        params = rule.setdefault("params", {})
        rt = params.get("reportType", "")
        if rt in REPORT_TYPE_ALIASES:
            params["reportType"] = REPORT_TYPE_ALIASES[rt]


def three_summary_rules(code: str, priority: int = 100) -> list[dict]:
    base_ref = f"excel:结款函#{code}"
    return [
        {
            "ruleType": "MONTHLY_SUPPLEMENT_REPORT",
            "name": "分科室汇总附表",
            "stage": "both",
            "isActive": True,
            "params": {"reportType": "dept_summary"},
            "sourceText": "各科室价格汇总（含物流）",
            "sourceRef": base_ref,
            "priority": priority,
        },
        {
            "ruleType": "MONTHLY_SUPPLEMENT_REPORT",
            "name": "按包类型价格汇总",
            "stage": "both",
            "isActive": True,
            "params": {"reportType": "price_summary"},
            "sourceText": "按包类型统计科室包及金额",
            "sourceRef": base_ref,
            "priority": priority + 1,
        },
        {
            "ruleType": "MONTHLY_SUPPLEMENT_REPORT",
            "name": "按包类型器械量表",
            "stage": "both",
            "isActive": True,
            "params": {"reportType": "instrument_audit"},
            "sourceText": "按包类型统计（无金额）",
            "sourceRef": base_ref,
            "priority": priority + 2,
        },
    ]


def build_taiping_bill_rules() -> list[dict]:
    """从 Excel 账单规则块生成 BILL_EXPORT_PRICE_RULE（跳过系统价格75折行，由 PIECE_TIER 覆盖）。"""
    rows = [
        ("额外包(纸塑袋)、额外包（无纺布）、器械包（ZSD）、器械包、单包装包", None, None, None, 16.5, 8.91, 11),
        ("敷料包", None, None, None, 25, 18.6, 12),
        ("敷料包", None, None, None, 20, 15, 13),
        ("敷料包", None, None, None, 15, 11.3, 14),
        ("敷料包（无纺布）", None, None, None, 35, 26.3, 15),
        ("敷料包（无纺布）", None, None, None, 30, 22.5, 16),
        ("敷料包（无纺布）", None, None, None, 25, 18.8, 17),
        ("敷料包（包名称带棉球或者纱布）", None, None, None, 2.5, 1.9, 18),
        ("敷料包（包名称带棉球或者纱布）", None, None, None, 2, 1.5, 19),
        ("敷料包（包名称带棉球或者纱布）", None, None, None, 1.5, 1.1, 20),
        ("敷料包（纸塑袋）", None, None, None, 4, 3, 21),
        ("敷料包（纸塑袋）", None, None, None, 2.5, 1.9, 22),
        ("额外包(纸塑袋)", None, "25cm", None, 16, 9.03, 23),
        ("额外包(纸塑袋)", None, "20cm", None, 13, 6.83, 24),
        ("额外包(纸塑袋)", None, "15cm", None, 11, 5.33, 25),
        ("额外包(纸塑袋)", None, "10cm", None, 8, 3.03, 26),
        ("器械包（低温等离子）、器械包（ETO)、单包装包(老肯等温）、单包装包（EO）、额外包（低温等离子）、额外包（ETO)", None, None, "10≤N<20", None, 225, 27),
        ("器械包（低温等离子）、器械包（ETO)、单包装包(老肯等温）、单包装包（EO）、额外包（低温等离子）、额外包（ETO)", None, None, "5≤N<10", None, 123.8, 28),
        ("器械包（低温等离子）、器械包（ETO)、单包装包(老肯等温）、单包装包（EO）、额外包（低温等离子）、额外包（ETO)", None, None, "1≤N<5", None, 66, 29),
        ("单包装包(老肯等温）、单包装包（EO）、额外包（低温等离子）、额外包（ETO)", None, "30cm", None, None, 26.3, 30),
        ("单包装包(老肯等温）、单包装包（EO）、额外包（低温等离子）、额外包（ETO)", None, "25cm", None, None, 22.5, 31),
        ("单包装包(老肯等温）、单包装包（EO）、额外包（低温等离子）、额外包（ETO)", None, "20cm", None, None, 21, 32),
        ("单包装包(老肯等温）、单包装包（EO）、额外包（低温等离子）、额外包（ETO)", None, "15cm", None, None, 18.8, 33),
        ("单包装包(老肯等温）、单包装包（EO）、额外包（低温等离子）、额外包（ETO)", None, "10cm", None, None, 16.5, 34),
    ]
    rules = []
    for pack_type, pack_name, mat, inst, sys_p, unit_p, prio in rows:
        types = [t.strip() for t in re.split(r"[、,，]", str(pack_type)) if t.strip()]
        params = {
            "acceptedTypes": types,
            "packNameKeywords": None,
            "packagingMaterial": str(mat).strip() if mat else None,
            "instrumentCountRange": str(inst).strip() if inst else None,
            "unitPrice": float(unit_p) if unit_p is not None else None,
            "unitPriceMode": "FIXED",
            "systemPrice": float(sys_p) if sys_p is not None else None,
            "systemPriceMode": "VALIDATE_ONLY" if sys_p else "IGNORE",
        }
        if pack_type and "包名称带棉球或者纱布" in str(pack_type):
            params["packNameKeywords"] = ["棉球", "纱布"]
            params["acceptedTypes"] = ["敷料包"]
        rules.append({
            "ruleType": "BILL_EXPORT_PRICE_RULE",
            "name": types[0] if types else "账单价",
            "stage": "bill_export",
            "isActive": True,
            "params": params,
            "sourceText": f"{pack_type} | {mat or ''} | {inst or ''} | {sys_p} | {unit_p}",
            "sourceRef": f"excel:账单规则#哈尔滨市道外区太平人民医院#{prio - 10}",
            "priority": prio,
        })
    return rules


def load_baseline(code: str) -> dict:
    path = BASELINE_DIR / f"{code}.json"
    return json.loads(path.read_text(encoding="utf-8"))


def save_baseline(code: str, data: dict) -> None:
    path = BASELINE_DIR / f"{code}.json"
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def patch_file(code: str, mutator) -> None:
    data = load_baseline(code)
    mutator(data)
    data["rules"] = dedupe_rules(data.get("rules", []))
    save_baseline(code, data)
    print(f"patched {code}")


def patch_taiping(data: dict) -> None:
    preserved = [r for r in data["rules"] if r["ruleType"] in ("PIECE_TIER_DISCOUNT", "LOGISTICS_WAIVE")]
    data["rules"] = preserved + build_taiping_bill_rules()
    data["notes"] = "医院内勤规则 v2 · Excel行 太平人民医院 · 含账单价表与 legacy 导出阶梯折扣"


def patch_fuyier(data: dict) -> None:
    others = [r for r in data["rules"] if r.get("ruleType") != "MONTHLY_SUPPLEMENT_REPORT"]
    code = data["customerCode"]
    data["rules"] = others + three_summary_rules(code)


def patch_zy3(data: dict) -> None:
    migrate_report_types(data["rules"])
    data["attachmentRefs"] = ["attachments/5黑龙江中医药大学附属第三医院8月器械把数.xlsx"]
    has_sterilize = any(
        r.get("ruleType") == "MONTHLY_SUPPLEMENT_REPORT"
        and r.get("params", {}).get("reportType") == "sterilize_fee_detail"
        for r in data["rules"]
    )
    if not has_sterilize:
        data["rules"].append({
            "ruleType": "MONTHLY_SUPPLEMENT_REPORT",
            "name": "消毒灭菌费明细表",
            "stage": "both",
            "isActive": True,
            "params": {"reportType": "sterilize_fee_detail", "discountRate": 0.7},
            "sourceText": "按消毒方式/包装材料统计包数把数与七折单价金额",
            "sourceRef": "excel:结款函#ZY3-DIANLI",
            "priority": 105,
        })


def patch_hrb_wy_em(data: dict) -> None:
    for rule in data["rules"]:
        if rule.get("ruleType") == "MERGED_SETTLEMENT":
            rule["params"] = {"mergeWith": "HRB-WY"}


def patch_zuyan(data: dict) -> None:
    migrate_report_types(data["rules"])
    code = data["customerCode"]
    has_logistics_report = any(
        r.get("ruleType") == "MONTHLY_SUPPLEMENT_REPORT"
        and r.get("params", {}).get("reportType") == "logistics_allocation"
        for r in data["rules"]
    )
    if not has_logistics_report:
        data["rules"].append({
            "ruleType": "MONTHLY_SUPPLEMENT_REPORT",
            "name": "物流费按科室分摊",
            "stage": "both",
            "isActive": True,
            "params": {"reportType": "logistics_allocation", "allocateBy": "sterilize_fee_ratio"},
            "sourceText": "物流费按科室消毒费比例分摊",
            "sourceRef": f"excel:结款函#{code}",
            "priority": 120,
        })
    for rule in data["rules"]:
        if rule.get("ruleType") == "LOGISTICS_FEE":
            rule.setdefault("params", {})["allocateBy"] = "sterilize_fee_ratio"


def main() -> None:
    patch_file("TAIPING-RM", patch_taiping)
    for code in ("ZYY-D2-NG", "ZYY-D2-HN"):
        patch_file(code, patch_fuyier)
    patch_file("ZY3-DIANLI", patch_zy3)
    patch_file("HRB-WY-EM", patch_hrb_wy_em)
    for code in ("ZUYAN-NG", "ZUYAN-XA", "ZUYAN-SF"):
        patch_file(code, patch_zuyan)
    for code in ("HRB-2ND", "HRB-HSZ"):
        patch_file(code, lambda d: migrate_report_types(d["rules"]))
    for code in ("HULAN-TCM", "VICTORIA", "JIUZHOU-FK", "HULAN-HSZ", "YUEMEI-FH"):
        patch_file(code, lambda d: None)
    print("done")


if __name__ == "__main__":
    main()
