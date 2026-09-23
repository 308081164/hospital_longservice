#!/usr/bin/env python3
"""从 医院内勤规则-20260918.xlsx 生成 clerk-rules v2 baseline。"""
from __future__ import annotations

import hashlib
import json
import re
import shutil
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
XLSX = ROOT / "铂康/内勤要求/医院内勤规则-20260918.xlsx"
CLERK_DIR = ROOT / "backend/src/main/resources/clerk-rules"
BASELINE_DIR = CLERK_DIR / "baseline"
ATTACH_DST = CLERK_DIR / "attachments"
MAPPING_OUT = ROOT / "docs/clerk-rules-excel-mapping.json"
HOSPITAL_LIST_OUT = ROOT / "docs/内勤规则医院清单.md"
SOURCE_VERSION = "医院内勤规则-20260918"

CODE_NAMES = {
    "DAOWAI-RM": "哈尔滨市道外区人民医院",
    "NG-FUCHAN": "哈尔滨市南岗区妇产医院",
    "SANJING-SB": "哈尔滨市三精肾脏病专科医院",
    "TAIPING-RM": "哈尔滨市道外区太平人民医院",
    "HUAXIA-EYE": "哈尔滨华夏眼科医院",
    "ERYY-NG": "黑龙江省第二医院（南岗院区）",
    "ERYY-SB": "黑龙江省第二医院（松北院区）",
    "SHENG-YY-NG": "黑龙江省医院（南岗院区）",
    "SHENG-YY-XF": "黑龙江省医院（香坊院区）",
    "SHKF-YY": "黑龙江省社会康复医院",
    "WUJING-ZD": "武警黑龙江省总队医院",
    "ZY3-DIANLI": "黑龙江中医药大学附属第三医院（电力）",
    "GUOYAO-MAIN": "国药总医院主院区",
    "GUOYAO-2": "国药总医院第二院区",
    "GUOYAO-3": "国药总医院第三院区",
    "HRB-2ND": "哈尔滨市第二医院",
    "HIT-YY": "哈尔滨工业大学医院",
    "HRB-HSZ": "哈尔滨市红十字中心医院",
    "HRB-WY-EM": "哈尔滨市第五医院（二门诊）",
    "HULAN-HSZ": "呼兰区红十字医院",
    "HULAN-RM": "呼兰区第一人民医院",
    "HULAN-TCM": "呼兰中医院",
    "JIUZHOU-FK": "黑龙江九洲妇科医院",
    "RENSHENG": "哈尔滨仁胜医院",
    "VICTORIA": "黑龙江维多利亚妇产医院",
    "XIANGFANG-ZY": "哈尔滨市香坊区中医院",
    "SANFU-SQ": "哈尔滨市三辅社区医院",
    "YUEMEI-FH": "悦美芳华医疗门诊医院",
    "ZUYAN-NG": "祖研-黑龙江省中医医院（南岗院区）",
    "ZUYAN-XA": "祖研-黑龙江省中医医院（香安院区）",
    "ZUYAN-SF": "祖研-黑龙江省中医医院（三辅院区）",
    "HRB-NGJY": "哈尔滨市南岗区人民医院（九院）",
    "HRB-HEU": "哈尔滨工程大学医院",
    "GONGAN-YY": "哈尔滨市公安医院",
    "WUCHANG-RM": "五常市人民医院",
    "DONGDA-GC": "黑龙江东大肛肠医院",
    "ZYY-D2-NG": "黑龙江中医药大学附属第二医院（南岗）",
    "ZYY-D2-HN": "黑龙江中医药大学附属第二医院（哈南分院）",
}

HOSPITAL_TO_CODES: dict[str, list[str]] = {
    "道外区人民医院": ["DAOWAI-RM"],
    "哈尔滨市南岗妇产医院": ["NG-FUCHAN"],
    "三精肾病医院": ["SANJING-SB"],
    "哈尔滨市道外区太平人民医院": ["TAIPING-RM"],
    "太平人民医院": ["TAIPING-RM"],
    "哈尔滨华夏眼科医院": ["HUAXIA-EYE"],
    "黑龙江省第二医院（南岗区）": ["ERYY-NG"],
    "黑龙江省第二医院（松北区）": ["ERYY-SB"],
    "黑龙江省医院（南岗/香坊）": ["SHENG-YY-NG", "SHENG-YY-XF"],
    "黑龙江省社会康复医院": ["SHKF-YY"],
    "武警黑龙江省总队医院": ["WUJING-ZD"],
    "黑龙江省中医药大学附属第三医院（电力）": ["ZY3-DIANLI"],
    "中医药大学附属第三医院（电力）": ["ZY3-DIANLI"],
    "国药总医院（主/二院区）": ["GUOYAO-MAIN", "GUOYAO-2"],
    "国药总医院（三院区）": ["GUOYAO-3"],
    "哈尔滨市第二医院": ["HRB-2ND"],
    "哈尔滨工业大学医院": ["HIT-YY"],
    "哈尔滨市红十字妇产医院": ["HRB-HSZ"],
    "哈尔滨市第五医院（二门诊）": ["HRB-WY-EM"],
    "呼兰区红十字医院": ["HULAN-HSZ"],
    "呼兰区第一人民医院": ["HULAN-RM"],
    "呼兰中医院": ["HULAN-TCM"],
    "黑龙江九洲妇科医院": ["JIUZHOU-FK"],
    "哈尔滨仁胜医院": ["RENSHENG"],
    "黑龙江维多利亚妇产医院": ["VICTORIA"],
    "香坊中医院+三辅社区医院": ["XIANGFANG-ZY", "SANFU-SQ"],
    "悦美芳华医疗门诊医院": ["YUEMEI-FH"],
    "祖研-黑龙江省中医医院（南岗/香安/三辅）": ["ZUYAN-NG", "ZUYAN-XA", "ZUYAN-SF"],
    "哈尔滨市南岗区人民医院（九院）": ["HRB-NGJY"],
    "哈尔滨工程大学医院": ["HRB-HEU"],
    "哈尔滨公安医院": ["GONGAN-YY"],
    "五常市人民医院": ["WUCHANG-RM"],
    "黑龙江东大肛肠医院": ["DONGDA-GC"],
    "中医药大学附属第二医院（南岗）": ["ZYY-D2-NG"],
    "中医药大学附属第二医院（哈南分院）": ["ZYY-D2-HN"],
}

ATTACHMENT_MAP = {
    "DAOWAI-RM": ["attachments/1哈尔滨市道外人民医院价格单.pdf"],
    "NG-FUCHAN": ["attachments/2哈尔滨市南岗区妇产医院价格单.pdf"],
    "SANJING-SB": ["attachments/3哈尔滨市三精肾脏病专科医院价格单.pdf"],
    "TAIPING-RM": ["attachments/4哈尔滨市道外区太平人民医院价格单_000214.pdf"],
    "ZY3-DIANLI": [
        "attachments/5黑龙江中医药大学附属第三医院8月器械把数.xlsx",
        "attachments/6黑龙江中医药大学附属第三医院8月器械把数.xlsx",
    ],
}

TAIPING_PIECE_TIER_RULE = {
    "ruleType": "PIECE_TIER_DISCOUNT",
    "name": "太平导出阶梯折扣",
    "stage": "bill_export",
    "isActive": True,
    "params": {
        "skipWhenAlreadyDiscounted": True,
        "pieceTierDiscounts": [
            {"minPieces": 1, "maxPieces": 1, "rate": 1.0},
            {"minPieces": 2, "maxPieces": 2, "rate": 0.75},
            {"minPieces": 3, "maxPieces": 3, "rate": 0.891, "originalUnitPriceEquals": 16.5},
            {"minPieces": 4, "maxPieces": 999, "rate": 0.75},
        ],
    },
    "sourceText": "2+把75%阶梯（legacy phase-bill-s8-fix）",
    "sourceRef": "seed:phase-bill-s8-fix-20260728",
    "priority": 10,
}


def supplement_discount_rules(code: str, rules: list[dict]) -> list[dict]:
    """Excel 未显式描述、但 legacy seed 已验证的折扣规则补全。"""
    if code == "TAIPING-RM" and not any(r.get("ruleType") == "PIECE_TIER_DISCOUNT" for r in rules):
        rules = [TAIPING_PIECE_TIER_RULE, *rules]
    return rules


LAYOUT_SHEETS = {
    "GUOYAO-MAIN": "国药结款函样式",
    "GUOYAO-2": "国药结款函样式",
    "GUOYAO-3": "国药结款函样式",
    "HULAN-TCM": "呼兰中医院结款函样式",
    "RENSHENG": "仁胜结款函样式",
    "XIANGFANG-ZY": "三辅社区和哈尔滨市香坊区中医院结款函样式",
    "SANFU-SQ": "三辅社区和哈尔滨市香坊区中医院结款函样式",
    "ZUYAN-NG": "祖研-黑龙江省中医医院分科室结算格式",
    "ZUYAN-XA": "祖研-黑龙江省中医医院分科室结算格式",
    "ZUYAN-SF": "祖研-黑龙江省中医医院分科室结算格式",
    "ZY3-DIANLI": "黑龙江中医药大学附属第三医院结款函样式",
}


def _rule(name, rule_type, stage, params, source_text, source_ref, priority=10, **extra):
    return {
        "ruleType": rule_type,
        "name": name,
        "stage": stage,
        "isActive": True,
        "params": params,
        "sourceText": source_text,
        "sourceRef": source_ref,
        "priority": priority,
        **extra,
    }


def _split_types(text: str | None) -> list[str]:
    if not text:
        return []
    parts = re.split(r"[、,，]", str(text))
    return [p.strip() for p in parts if p and p.strip()]


def _parse_unit_price(raw) -> tuple[float | None, str]:
    if raw is None:
        return None, "FIXED"
    s = str(raw).strip()
    if not s or s in ("不读取", "—"):
        return None, "FIXED"
    m = re.match(r"^([\d.]+)\s*/\s*把$", s)
    if m:
        return float(m.group(1)), "PER_PIECE"
    m = re.match(r"^([\d.]+)\s*/\s*包$", s)
    if m:
        return float(m.group(1)), "PER_PACK"
    if s.endswith("%"):
        return None, "FIXED"
    try:
        return float(s.replace(",", "")), "FIXED"
    except ValueError:
        return None, "FIXED"


def _parse_system_price(raw) -> tuple[float | None, str]:
    if raw is None:
        return None, "IGNORE"
    s = str(raw).strip()
    if not s or "不读取" in s:
        return None, "IGNORE"
    try:
        return float(str(raw).replace(",", "")), "VALIDATE_ONLY"
    except (TypeError, ValueError):
        return None, "IGNORE"


def _pack_name_keywords(pack_name) -> list[str] | None:
    if pack_name is None or str(pack_name).strip() == "":
        return None
    s = str(pack_name).strip()
    if s in ("N", "根据名称收费，不需要管件数"):
        return None
    return [s]


REPORT_TYPE_ALIASES = {
    "dept_sterilize_summary": "dept_summary",
    "instrument_count_by_dept": "instrument_audit",
}


def _normalize_report_type(report_type: str) -> str:
    rt = (report_type or "").strip()
    return REPORT_TYPE_ALIASES.get(rt, rt)


def _rule_dedup_key(rule: dict) -> tuple[str, str]:
    return rule.get("ruleType", ""), json.dumps(rule.get("params") or {}, sort_keys=True, ensure_ascii=False)


def dedupe_rules(rules: list[dict]) -> list[dict]:
    merged: list[dict] = []
    index: dict[tuple[str, str], int] = {}
    for rule in rules:
        key = _rule_dedup_key(rule)
        if key in index:
            existing = merged[index[key]]
            ref = rule.get("sourceRef", "")
            if ref and ref not in existing.get("sourceRef", ""):
                existing["sourceRef"] = f"{existing.get('sourceRef', '')}|{ref}".strip("|")
        else:
            index[key] = len(merged)
            merged.append(rule)
    return merged


def parse_bill_row(row: tuple, hospital: str, idx: int) -> list[dict]:
    _, _, pack_type, pack_name, mat, inst, sys_p, unit_p, note = (row + (None,) * 9)[:9]
    rules: list[dict] = []
    ref = f"excel:账单规则#{hospital}#{idx}"

    if unit_p and "系统价格" in str(unit_p) and "折" in str(unit_p):
        return rules

    if pack_type and "标准价格七折" in str(pack_type):
        rules.append(_rule("标准价七折校对", "PRICE_VALIDATE_ONLY", "bill_export",
                           {"rate": 0.7, "validateOnly": True}, str(pack_type), ref))
        return rules
    if pack_type and "标准价格六折" in str(pack_type):
        rules.append(_rule("标准价六折", "DISCOUNT_OVERLAY", "bill_export",
                           {"rate": 0.6}, str(pack_type), ref))
        return rules
    if pack_type and "标准价格七五折" in str(pack_type):
        rules.append(_rule("标准价七五折", "DISCOUNT_OVERLAY", "bill_export",
                           {"rate": 0.75}, str(pack_type), ref))
        return rules

    if hospital == "武警黑龙江省总队医院":
        if mat and unit_p:
            up, mode = _parse_unit_price(unit_p)
            rules.append(_rule(f"零元行{mat}", "ZERO_ROW_PACKAGING_FEE", "bill_export",
                               {"packagingMaterial": str(mat), "unitPrice": up, "unitPriceMode": mode},
                               f"{mat} {unit_p}", ref, priority=10 + idx))
        elif pack_name and "环氧乙烷" in str(pack_name):
            up, mode = _parse_unit_price(unit_p)
            rules.append(_rule("环氧乙烷自行打包", "ZERO_ROW_PACKAGING_FEE", "bill_export",
                               {"packNameKeyword": "环氧乙烷", "unitPrice": up, "unitPriceMode": mode},
                               str(pack_name), ref, priority=10 + idx))
        return rules

    if hospital in ("黑龙江省中医药大学附属第三医院（电力）", "中医药大学附属第三医院（电力）"):
        if note and "器械数量" in str(note):
            rules.append(_rule("保留器械数量列", "EXPORT_LAYOUT", "bill_export",
                               {"keepColumns": ["器械数"]}, str(note), ref))
        return rules

    unit_val, unit_mode = _parse_unit_price(unit_p)
    sys_val, sys_mode = _parse_system_price(sys_p)

    if pack_name and not pack_type and hospital == "哈尔滨市南岗妇产医院":
        kw = _pack_name_keywords(pack_name)
        if kw and kw[0] == "除了以上包名称":
            rules.append(_rule("散包按把", "BILL_EXPORT_PRICE_RULE", "bill_export", {
                "excludePackNameKeywords": ["腹腔镜", "宫腔镜", "阴式包", "引产包", "妇科包", "人流包",
                                            "取环包", "上环包", "药流包", "手术包", "棉球缸", "无菌缸",
                                            "持物钳", "拆线包", "镊子缸", "带架方盘", "治疗盘", "敷料包"],
                "unitPrice": unit_val, "unitPriceMode": unit_mode or "PER_PIECE",
                "instrumentCountExpr": str(inst) if inst else "N",
            }, f"{pack_name} {unit_p}", ref, priority=10 + idx))
            return rules
        rules.append(_rule(kw[0] if kw else "按包名", "PACK_NAME_PRICE", "bill_export", {
            "packNameKeywords": kw,
            "unitPrice": unit_val,
            "unitPriceMode": unit_mode or "FIXED",
            "systemPrice": sys_val,
            "systemPriceMode": sys_mode,
        }, f"{pack_name} {unit_p}", ref, priority=10 + idx))
        return rules

    types = _split_types(pack_type)
    if not types and not pack_name and not mat:
        return rules

    params = {
        "acceptedTypes": types if types else None,
        "packNameKeywords": _pack_name_keywords(pack_name),
        "packagingMaterial": str(mat).strip() if mat else None,
        "instrumentCountRange": str(inst).strip() if inst else None,
        "unitPrice": unit_val,
        "unitPriceMode": unit_mode,
        "systemPrice": sys_val,
        "systemPriceMode": sys_mode,
    }
    if pack_type and "包名称带棉球或者纱布" in str(pack_type):
        params["packNameKeywords"] = ["棉球", "纱布"]
        params["acceptedTypes"] = ["敷料包"]

    rules.append(_rule(
        types[0] if types else (pack_name or "账单价"),
        "BILL_EXPORT_PRICE_RULE",
        "bill_export",
        params,
        " | ".join(str(x) for x in [pack_type, pack_name, mat, inst, sys_p, unit_p, note] if x),
        ref,
        priority=10 + idx,
    ))
    return rules


def parse_settlement_lines(lines: list[str], code: str) -> list[dict]:
    rules: list[dict] = []
    text = "\n".join(lines)
    prio = 100

    if re.search(r"不收物流|无物流费|物流费不收取", text):
        rules.append(_rule("不收物流费", "LOGISTICS_WAIVE", "settlement", {"feePerTrip": 0}, text[:200], f"excel:结款函#{code}", priority=prio))
        prio += 10

    m = re.search(r"物流费\s*(\d+(?:\.\d+)?)\s*元/次", text)
    if m:
        fee = float(m.group(1))
        rules.append(_rule("物流费", "LOGISTICS_FEE", "settlement", {"feePerTrip": fee}, m.group(0), f"excel:结款函#{code}", priority=prio))
        prio += 10

    m = re.search(r"普通物流\s*(\d+(?:\.\d+)?)\s*元/次", text)
    if m:
        rules.append(_rule("普通物流", "LOGISTICS_FEE", "settlement", {"feePerTrip": float(m.group(1))}, m.group(0), f"excel:结款函#{code}", priority=prio))
        prio += 10
    m = re.search(r"加急物流费\s*(\d+(?:\.\d+)?)\s*元/次", text)
    if m:
        rules.append(_rule("加急物流", "LOGISTICS_FEE", "settlement", {"urgentAmount": float(m.group(1))}, m.group(0), f"excel:结款函#{code}", priority=prio))
        prio += 10

    m = re.search(r"低于\s*(\d+)\s*元", text) or re.search(r"低消\s*(\d+)", text)
    if m:
        rules.append(_rule("低消", "SETTLEMENT_MIN_CHARGE", "settlement",
                           {"minCharge": float(m.group(1)), "baseComponents": ["sterilize", "logistics"]},
                           m.group(0), f"excel:结款函#{code}", priority=prio))
        prio += 10

    if "减免四次" in text or "减免4次" in text:
        rules.append(_rule("月减免四次物流", "LOGISTICS_WAIVE", "settlement", {"freeCountPerMonth": 4}, "减免四次物流", f"excel:结款函#{code}", priority=prio))
        prio += 10

    if "物流卡" in text:
        rules.append(_rule("物流卡抵扣", "LOGISTICS_CARD_DEDUCT", "settlement", {"deductFromCard": True}, "物流卡", f"excel:结款函#{code}", priority=prio))
        prio += 10

    if "8折" in text and "账单金额" in text:
        rules.append(_rule("结款八折", "SETTLEMENT_DISCOUNT", "settlement", {"rate": 0.8, "target": "sterilize"}, "8折", f"excel:结款函#{code}", priority=prio))
        prio += 10
    elif "9折" in text and "账单金额" in text:
        rules.append(_rule("结款九折", "SETTLEMENT_DISCOUNT", "settlement", {"rate": 0.9, "target": "sterilize"}, "9折", f"excel:结款函#{code}", priority=prio))
        prio += 10
    elif "75折" in text or "七五折" in text:
        rules.append(_rule("结款七五折", "SETTLEMENT_DISCOUNT", "settlement", {"rate": 0.75, "target": "sterilize"}, "75折", f"excel:结款函#{code}", priority=prio))
        prio += 10

    if "7折" in text and "账单金额" in text:
        rules.append(_rule("结款七折", "DISCOUNT_OVERLAY", "settlement", {"rate": 0.7}, "7折", f"excel:结款函#{code}", priority=prio))
        prio += 10

    if "器械把数" in text and "/5.5" in text:
        campus = "main" if code in ("GUOYAO-MAIN", "GUOYAO-2") else "campus3" if code == "GUOYAO-3" else "main"
        rules.append(_rule("国药结款核算", "GUOYAO_SETTLEMENT_FORMULA", "settlement",
                           {"instrumentPackDivisor": 5.5, "allocationDivisor": 15, "campus": campus},
                           text[:300], f"excel:结款函#{code}", priority=prio))
        prio += 10

    if "手术室（备包）" in text:
        rules.append(_rule("备包单独结款", "SETTLEMENT_PACK_SPLIT", "settlement",
                           {"deptKeyword": "手术室（备包）"}, "备包", f"excel:结款函#{code}", priority=prio))
        prio += 10

    if "分科室灭菌费用汇总表" in text or "三张汇总表" in text:
        rules.append(_rule("分科室汇总附表", "MONTHLY_SUPPLEMENT_REPORT", "both",
                           {"reportType": "dept_summary"}, "各科室价格汇总（含物流）", f"excel:结款函#{code}", priority=prio))
        rules.append(_rule("按包类型价格汇总", "MONTHLY_SUPPLEMENT_REPORT", "both",
                           {"reportType": "price_summary"}, "按包类型统计科室包及金额", f"excel:结款函#{code}", priority=prio + 1))
        rules.append(_rule("按包类型器械量表", "MONTHLY_SUPPLEMENT_REPORT", "both",
                           {"reportType": "instrument_audit"}, "按包类型统计（无金额）", f"excel:结款函#{code}", priority=prio + 2))
        prio += 10

    if "器械把数汇总表" in text:
        rules.append(_rule("器械把数汇总", "MONTHLY_SUPPLEMENT_REPORT", "both",
                           {"reportType": "instrument_audit"}, "把数汇总", f"excel:结款函#{code}", priority=prio))
        prio += 10

    if "消毒灭菌费明细表" in text:
        rules.append(_rule("消毒灭菌费明细表", "MONTHLY_SUPPLEMENT_REPORT", "both",
                           {"reportType": "sterilize_fee_detail", "discountRate": 0.7},
                           "消毒灭菌费明细", f"excel:结款函#{code}", priority=prio))
        prio += 10

    if "7折后" in text or "原价和7折" in text:
        rules.append(_rule("结款原价七折并列", "EXPORT_LAYOUT", "settlement",
                           {"showOriginalAndDiscount": True, "discountRate": 0.7}, "7折并列", f"excel:结款函#{code}", priority=prio))
        prio += 10

    if "合并" in text and ("结款函" in text or "物流费" in text):
        merge_params: dict = {}
        if code == "HRB-WY-EM":
            merge_params = {"mergeWith": "HRB-WY"}
        rules.append(_rule("合并结款", "MERGED_SETTLEMENT", "settlement", merge_params, text[:120], f"excel:结款函#{code}", priority=prio))
        prio += 10

    if "南岗/三辅各50" in text or "物流费按科室" in text:
        if code == "ZUYAN-NG":
            rules.append(_rule("南岗物流", "LOGISTICS_FEE", "settlement", {"feePerTrip": 50}, "南岗50", f"excel:结款函#{code}", priority=prio))
        elif code == "ZUYAN-SF":
            rules.append(_rule("三辅物流", "LOGISTICS_FEE", "settlement", {"feePerTrip": 50}, "三辅50", f"excel:结款函#{code}", priority=prio))
        elif code == "ZUYAN-XA":
            rules.append(_rule("香安物流", "LOGISTICS_FEE", "settlement", {"feePerTrip": 25, "mergeSameDayWith": "ZUYAN-SF"}, "香安25", f"excel:结款函#{code}", priority=prio))
        prio += 10

    if "合并到一个sheet" in text:
        rules.append(_rule("账单合并单sheet", "EXPORT_LAYOUT", "bill_export", {"billLayout": "single_sheet"}, "合并sheet", f"excel:结款函#{code}", priority=prio))
        prio += 10

    return rules


def parse_min_charge_row(row) -> dict | None:
    name, min_std, logistics, discount, note = row[:5]
    if not name:
        return None
    m = re.search(r"(\d+)\s*元", str(min_std))
    if not m:
        return None
    params = {"minCharge": float(m.group(1)), "baseComponents": ["sterilize", "logistics"]}
    rules = [_rule("低消", "SETTLEMENT_MIN_CHARGE", "settlement", params, str(min_std), f"excel:低消#{name}")]
    lm = re.search(r"(\d+(?:\.\d+)?)\s*元/次", str(logistics))
    if lm:
        rules.append(_rule("物流费", "LOGISTICS_FEE", "settlement", {"feePerTrip": float(lm.group(1))}, str(logistics), f"excel:低消物流#{name}"))
    if "减免四次" in str(logistics):
        rules.append(_rule("月减免四次物流", "LOGISTICS_WAIVE", "settlement", {"freeCountPerMonth": 4}, str(logistics), f"excel:低消物流#{name}"))
    if "五折" in str(discount) and "七折" in str(discount):
        rules.append(_rule("高温五折低温七折", "SETTLEMENT_DISCOUNT", "settlement",
                           {"htRate": 0.5, "ltRate": 0.7}, str(discount), f"excel:低消折扣#{name}"))
    return rules


def load_excel_data():
    wb = openpyxl.load_workbook(XLSX, read_only=True, data_only=True)
    bill_by_hospital: dict[str, list] = defaultdict(list)
    ws = wb["账单规则"]
    cur = None
    idx = 0
    for i, row in enumerate(ws.iter_rows(values_only=True)):
        if i < 2:
            continue
        seq, name = row[0], row[1]
        if name:
            cur = str(name).strip()
            idx = 0
        if not cur:
            continue
        if any(row[2:9]):
            idx += 1
            bill_by_hospital[cur].append(tuple(row[:9]))

    settlement: dict[str, list[str]] = defaultdict(list)
    ws2 = wb["结款函规则"]
    cur = None
    for i, row in enumerate(ws2.iter_rows(values_only=True)):
        if i == 0:
            continue
        if row[1]:
            cur = str(row[1]).strip()
        if cur and row[2]:
            settlement[cur].append(str(row[2]).strip())

    min_charge: dict[str, list] = defaultdict(list)
    ws3 = wb["低消保底规则"]
    for row in ws3.iter_rows(min_row=2, values_only=True):
        if row[0]:
            for code in HOSPITAL_TO_CODES.get(str(row[0]).strip(), []):
                min_charge[code].extend(parse_min_charge_row(row) or [])

    wb.close()
    return bill_by_hospital, settlement, min_charge


def build_baselines():
    bill_by_hospital, settlement, min_charge_extra = load_excel_data()
    baselines: dict[str, dict] = {}
    code_hospitals: dict[str, list[str]] = defaultdict(list)
    for hospital, codes in HOSPITAL_TO_CODES.items():
        for code in codes:
            code_hospitals[code].append(hospital)

    for code, hospitals in code_hospitals.items():
        rules: list[dict] = []
        bill_rows: list[tuple] = []
        settle_lines: list[str] = []
        for hospital in hospitals:
            bill_rows.extend(bill_by_hospital.get(hospital, []))
            if settlement.get(hospital):
                settle_lines = settlement.get(hospital, [])
        for i, row in enumerate(bill_rows, start=1):
            rules.extend(parse_bill_row(row, hospitals[0], i))
        rules.extend(parse_settlement_lines(settle_lines, code))
        if code in min_charge_extra:
            rules.extend(min_charge_extra[code])
        if code == "SANFU-SQ":
            rules = [r for r in rules if r["ruleType"] != "MERGED_SETTLEMENT"] + [
                _rule("合并结款", "MERGED_SETTLEMENT", "settlement",
                      {"mergeWith": "XIANGFANG-ZY", "sharedLogisticsFee": 50}, "香坊三辅合并", f"excel:结款函#{code}")
            ]
        for rule in rules:
            if rule.get("ruleType") == "MONTHLY_SUPPLEMENT_REPORT":
                params = rule.setdefault("params", {})
                if "reportType" in params:
                    params["reportType"] = _normalize_report_type(params["reportType"])
        rules = supplement_discount_rules(code, rules)
        rules = dedupe_rules(rules)
        baselines[code] = {
            "customerCode": code,
            "customerName": CODE_NAMES.get(code, code),
            "sourceVersion": SOURCE_VERSION,
            "notes": f"医院内勤规则 v2 · Excel行 {' / '.join(hospitals)}",
            "attachmentRefs": ATTACHMENT_MAP.get(code, []),
            "rules": rules,
        }
    return baselines


def write_outputs(baselines: dict[str, dict]) -> None:
    BASELINE_DIR.mkdir(parents=True, exist_ok=True)
    preserve_codes = {p.stem for p in BASELINE_DIR.glob("*.json")} - set(baselines.keys())
    if BASELINE_DIR.exists():
        for f in BASELINE_DIR.glob("*.json"):
            if f.stem not in baselines:
                continue
            f.unlink()
    for code, baseline in sorted(baselines.items()):
        (BASELINE_DIR / f"{code}.json").write_text(
            json.dumps(baseline, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )

    customers = []
    for code, baseline in sorted(baselines.items()):
        customers.append({
            "code": code,
            "file": f"baseline/{code}.json",
            "ruleCount": len(baseline.get("rules", [])),
        })
    payload = sorted(json.dumps(b, ensure_ascii=False, sort_keys=True) for b in baselines.values())
    baseline_hash = hashlib.sha256("\n".join(payload).encode()).hexdigest()
    index = {
        "baseline_hash": baseline_hash,
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "source": str(XLSX.relative_to(ROOT)),
        "customer_count": len(customers),
        "customers": customers,
    }
    (CLERK_DIR / "index.json").write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    mapping_hospitals = []
    for code, baseline in sorted(baselines.items()):
        types = sorted({r["ruleType"] for r in baseline.get("rules", [])})
        mapping_hospitals.append({
            "code": code,
            "name": baseline.get("customerName"),
            "excelRowName": baseline.get("notes", "").replace("医院内勤规则 v2 · Excel行 ", ""),
            "rules": [{"ruleType": t} for t in types],
        })
    mapping = {
        "generated_at": index["generated_at"],
        "source": str(XLSX.relative_to(ROOT)),
        "hospital_count": len(mapping_hospitals),
        "hospitals": mapping_hospitals,
    }
    MAPPING_OUT.write_text(json.dumps(mapping, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    lines = ["# 内勤规则医院清单（v2）", "", f"来源：`{XLSX.relative_to(ROOT)}`", f"共 **{len(baselines)}** 家 CODE。", ""]
    for code, baseline in sorted(baselines.items()):
        lines.append(f"## {baseline.get('customerName')} (`{code}`)")
        for r in baseline.get("rules", []):
            st = "active" if r.get("isActive", True) else "inactive"
            lines.append(f"- [{st}] **{r['ruleType']}** ({r['stage']}) — {r.get('name', '')}")
        lines.append("")
    HOSPITAL_LIST_OUT.write_text("\n".join(lines), encoding="utf-8")


def main():
    if not XLSX.exists():
        raise SystemExit(f"Missing {XLSX}")
    baselines = build_baselines()
    write_outputs(baselines)
    print(f"Generated {len(baselines)} baselines from {XLSX.name}")


if __name__ == "__main__":
    main()
