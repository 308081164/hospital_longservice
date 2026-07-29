#!/usr/bin/env python3
"""Batch June price-reconciliation audit for TODO hospitals.

Phase 1: derive expected price-correction rows (raw vs processed ground truth).
Phase 2: flag rows where neither seeded special rules nor default-pricing heuristics
         can explain the processed target price (likely missing customer requirements).

Reuses parsers from analyze_test_case_excel.py.
"""

from __future__ import annotations

import csv
import json
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from analyze_test_case_excel import (  # noqa: E402
    TOLERANCE,
    DetailRow,
    compare_workbooks,
    discover_hospitals,
    extract_date_range_token,
    extract_month_from_name,
    is_bill_file,
    match_raw_processed,
    nums_close,
    parse_workbook,
    pick_processed_bill,
    price_note,
    to_float,
)

TEST_CASE_DIR = ROOT / "测试用例"
SEED_DIR = ROOT / "backend/src/main/resources/billing-seeds"
TARGET_MONTH = 6
OUTPUT_INDEX = TEST_CASE_DIR / "批量6月期待价格校正索引.md"

# TODO list hospitals (31 campuses) — folder names must match 测试用例/
TODO_HOSPITALS: list[str] = [
    "黑龙江中医药大学附属第一医院",
    "黑龙江省中医药大学附属第三医院（电力）",
    "国药总医院主院区",
    "国药总医院第二院区",
    "国药总医院第三院区",
    "哈尔滨市第二医院",
    "哈尔滨市第五医院",
    "哈尔滨市第五医院（二门诊）",
    "新发红十字医院",
    "黑龙江省医院（南岗院区）",
    "黑龙江省医院（香坊院区）",
    "祖研-黑龙江省中医医院（南岗院区）",
    "祖研-黑龙江省中医医院（三辅院区）",
    "祖研-黑龙江省中医医院（香安院区）",
    "南岗区妇产医院",
    "黑龙江省社会康复医院",
    "道外区人民医院",
    "太平人民医院",
    "三精肾病医院",
    "黑龙江维多利亚妇产医院",
    "黑龙江九洲妇科医院",
    "呼兰区红十字医院",
    "呼兰中医院",
    "黑龙江中医药大学附属第二医院（南岗）",
    "黑龙江中医药大学附属第二医院（哈南分院）",
    "哈尔滨仁胜医院",
    "哈尔滨华夏眼科医院",
    "哈尔滨冰城医疗美容医院",
    "香坊中医院",
    "武警黑龙江省总队医院",
    "悦美芳华医疗门诊医院",
    # 特色账单规则.txt L9-L61 补充
    "黑龙江省第二医院（南岗院区）",
    "黑龙江省第二医院（松北院区）",
    "哈尔滨市呼兰区第一人民医院",
    "哈尔滨市红十字妇产医院",
    "哈尔滨工业大学医院",
    "哈尔滨工程大学医院",
    "哈尔滨长健医院",
]

# Hardcoded engine rules not in billing-seeds (customer_code -> rules)
HARDCODED_RULES: dict[str, list[dict[str, Any]]] = {
    "ERYY-SB": [
        {"ruleType": "FIXED_PRICE", "price": 190.05, "keywords": ["3.6空心钉工具包"]},
        {"ruleType": "FIXED_PRICE", "price": 13.3, "keywords": ["3.6空心钉"]},
        {"ruleType": "FIXED_PRICE", "price": 13.3, "keywords": ["7.3空心钉"]},
        {"ruleType": "FIXED_PRICE", "price": 26.6, "keywords": ["手术衣"], "materials": ["无纺布"]},
        {"ruleType": "FIXED_PRICE", "price": 28.0, "keywords": ["手术衣"], "materials": ["纸塑袋"]},
        {"ruleType": "FIXED_PRICE", "price": 35.0, "keywords": ["钉"]},
        {"ruleType": "FIXED_PRICE", "price": 210.0, "keywords": ["软镜"]},
        {"ruleType": "FIXED_PRICE", "price": 210.0, "keywords": ["泌尿显微镜头"]},
        {"ruleType": "FIXED_PRICE", "price": 53.55, "keywords": ["小腔包"]},
    ],
    "ERYY-NG": [
        {"ruleType": "FIXED_PRICE", "price": 205.45, "keywords": ["3.6空心钉工具包"]},
        {"ruleType": "FIXED_PRICE", "price": 13.3, "keywords": ["3.6空心钉"]},
        {"ruleType": "FIXED_PRICE", "price": 13.3, "keywords": ["7.3空心钉"]},
        {"ruleType": "FIXED_PRICE", "price": 26.6, "keywords": ["手术衣"], "materials": ["无纺布"]},
        {"ruleType": "FIXED_PRICE", "price": 28.0, "keywords": ["手术衣"], "materials": ["纸塑袋"]},
        {"ruleType": "FIXED_PRICE", "price": 140.0, "keywords": ["钉"]},
        {"ruleType": "FIXED_PRICE", "price": 210.0, "keywords": ["软镜"]},
        {"ruleType": "FIXED_PRICE", "price": 210.0, "keywords": ["泌尿显微镜头"]},
        {"ruleType": "FIXED_PRICE", "price": 49.7, "keywords": ["小腔包"]},
    ],
    "HRB-HSZ": [
        {"ruleType": "FIXED_PRICE", "price": 22, "keywords": [], "temperature": "LT", "bagSizeEquals": 1},
        {"ruleType": "FIXED_PRICE", "price": 22, "keywords": ["湿化瓶"], "bagSizeEquals": 2},
        {"ruleType": "FIXED_PRICE", "price": 300, "keywords": ["纤维喉镜", "气管镜", "软管"]},
        {"ruleType": "FIXED_PRICE", "price": 25, "keywords": ["T型管"]},
    ],
    "HULAN-RM": [
        {"ruleType": "FIXED_PRICE", "price": 0, "keywords": []},  # 7折账单明细，无独立产品规则
    ],
    "HRB-HIT": [
        {"ruleType": "FIXED_PRICE", "price": 5.5, "keywords": ["针", "洁牙尖", "成型片"]},
    ],
}

# Test-case folder -> customer code overrides when name differs
FOLDER_CODE_OVERRIDE: dict[str, str] = {
    "黑龙江省第二医院（南岗院区）": "ERYY-NG",
    "黑龙江省第二医院（松北院区）": "ERYY-SB",
    "哈尔滨市呼兰区第一人民医院": "HULAN-RM",
    "哈尔滨市红十字妇产医院": "HRB-HSZ",
    "哈尔滨工业大学医院": "HRB-HIT",
    "哈尔滨工程大学医院": "HRB-HEU",
    "哈尔滨长健医院": "HRB-CJ",
}

# S4 验收固定原始/处理后成对（跨自然月账期）
HOSPITAL_PAIR_OVERRIDE: dict[str, tuple[str, str, str]] = {
    "太平人民医院": (
        "原始表格/太平人民5.13-6.15账单.xlsx",
        "处理后表格/5月__太平人民2026.5.13-2026.6.15账单.xlsx",
        "5.13-6.15跨月验收",
    ),
    "哈尔滨工业大学医院": (
        "原始表格/工业大学6.15-7.14原始账单.xlsx",
        "处理后表格/6月__哈尔滨工业大学医院6.15-7.14月账单.xlsx",
        "6.15-7.14跨月(6月验收)",
    ),
    "黑龙江省医院（南岗院区）": (
        "原始表格/省医院南岗5.21-6.20.xlsx",
        "处理后表格/6月__南岗省医院5.21-6.20账单.xlsx",
        "5.21-6.20(6月验收)",
    ),
    "黑龙江省医院（香坊院区）": (
        "原始表格/省医院香坊5.21-6.20.xlsx",
        "处理后表格/6月__香坊省医院5.21-6.20账单(2).xlsx",
        "5.21-6.20(6月验收)",
    ),
    "国药总医院主院区": (
        "原始表格/汽轮机6月账单.xlsx",
        "处理后表格/6月__国药总医院主院区5.26-6.25账单.xlsx",
        "5.26-6.25(6月验收)",
    ),
    "国药总医院第二院区": (
        "原始表格/电机厂6月账单.xlsx",
        "处理后表格/6月__国药总医院第二院区5.26-6.25账单.xlsx",
        "5.26-6.25(6月验收)",
    ),
    "国药总医院第三院区": (
        "原始表格/锅炉厂6月账单.xlsx",
        "处理后表格/6月__国药总医院第三院区5.26-6.25账单.xlsx",
        "5.26-6.25(6月验收)",
    ),
    "黑龙江省社会康复医院": (
        "原始表格/社会康复6月账单.xlsx",
        "处理后表格/6月__省康复6月账单.xlsx",
        "6月省康复(成康/口腔/中西医)",
    ),
    "哈尔滨工程大学医院": (
        "原始表格/哈尔滨工程大学医院5月账单.xlsx",
        "处理后表格/5月__哈尔滨工程大学医院5月账单.xlsx",
        "5月(5.1-5.31验收)",
    ),
}


@dataclass
class CustomerProfile:
    code: str
    name: str
    pricing_mode: str = "standard"
    aliases: list[str] = field(default_factory=list)
    product_rules: list[dict[str, Any]] = field(default_factory=list)
    discounts: list[dict[str, Any]] = field(default_factory=list)


@dataclass
class ExpectedPriceRow:
    sheet: str
    ship_no: str
    pack_name: str
    pack_count: float | None
    raw_unit: float | None
    proc_unit: float | None
    raw_total: float | None
    proc_total: float | None
    raw_row: int | None
    proc_row: int | None
    note: str = ""
    rule_coverage: str = ""
    matched_rule: str = ""


@dataclass
class HospitalAudit:
    name: str
    status: str
    customer_code: str = ""
    pricing_mode: str = ""
    raw_file: str = ""
    proc_file: str = ""
    expected_count: int = 0
    uncovered_count: int = 0
    expected_rows: list[ExpectedPriceRow] = field(default_factory=list)
    uncovered_rows: list[ExpectedPriceRow] = field(default_factory=list)
    message: str = ""


def load_seed_profiles() -> dict[str, CustomerProfile]:
    by_name: dict[str, CustomerProfile] = {}
    by_code: dict[str, CustomerProfile] = {}

    for path in sorted(SEED_DIR.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for node in data.get("profiles", []):
            code = node.get("code", "")
            name = node.get("name", "")
            incoming_rules = list(node.get("productRules") or [])
            profile = CustomerProfile(
                code=code,
                name=name,
                pricing_mode=node.get("billingPricingMode", "standard"),
                aliases=list(node.get("aliases") or []),
                product_rules=incoming_rules,
                discounts=list(node.get("discounts") or []),
            )
            if code in by_code and incoming_rules:
                merged = {r.get("name"): r for r in by_code[code].product_rules if r.get("name")}
                for r in incoming_rules:
                    n = r.get("name")
                    if n:
                        merged[n] = r
                profile.product_rules = list(merged.values())
            elif code in by_code and not incoming_rules:
                profile.product_rules = list(by_code[code].product_rules)
            by_name[name] = profile
            by_code[code] = profile
            for alias in profile.aliases:
                by_name[alias] = profile

    # Merge hardcoded rules into profiles
    for code, rules in HARDCODED_RULES.items():
        if code in by_code:
            by_code[code].product_rules.extend(rules)
        else:
            by_code[code] = CustomerProfile(code=code, name=code, product_rules=list(rules))

    for code, profile in by_code.items():
        by_name.setdefault(code, profile)

    return by_name


def resolve_profile(hospital_name: str, profiles: dict[str, CustomerProfile]) -> CustomerProfile | None:
    if hospital_name in FOLDER_CODE_OVERRIDE:
        code = FOLDER_CODE_OVERRIDE[hospital_name]
        for p in profiles.values():
            if p.code == code:
                return p
    if hospital_name in profiles:
        return profiles[hospital_name]
    # partial match
    for key, profile in profiles.items():
        if hospital_name in key or key in hospital_name:
            return profile
    return None


def keyword_matches(pack_name: str, keywords: list[str], exclude: list[str] | None = None) -> bool:
    text = pack_name or ""
    if exclude:
        for ex in exclude:
            if ex and ex in text:
                return False
    if not keywords:
        return True
    return any(kw and kw in text for kw in keywords)


def material_matches(row_material: str | None, rule_materials: list[str] | None) -> bool:
    if not rule_materials:
        return True
    mat = row_material or ""
    return any(m in mat for m in rule_materials)


PRICE_RULE_TOLERANCE = 0.05  # 规则价 vs 处理后价（含 17.58≈17.6、30.38≈30.4）


def match_special_rule(
    row: ExpectedPriceRow,
    material: str | None,
    rules: list[dict[str, Any]],
    target_price: float | None,
) -> tuple[bool, str]:
    if target_price is None:
        return False, ""
    keyword_hits: list[tuple[int, str]] = []
    price_hits: list[tuple[int, str, float]] = []
    for rule in rules:
        rtype = rule.get("ruleType", "FIXED_PRICE")
        if rtype in {"FOLD", "EXTRA_FEE"}:
            continue
        keywords = list(rule.get("keywords") or [])
        exclude = list(rule.get("excludeKeywords") or [])
        if not keyword_matches(row.pack_name, keywords, exclude):
            continue
        if not material_matches(material, rule.get("materials")):
            continue
        pri = int(rule.get("priority") or 999)
        name = rule.get("name", rtype)
        keyword_hits.append((pri, name))
        price = rule.get("price")
        if price is not None and nums_close(float(price), target_price, PRICE_RULE_TOLERANCE):
            price_hits.append((pri, name, float(price)))

    if price_hits:
        price_hits.sort(key=lambda x: x[0])
        return True, price_hits[0][1]
    if keyword_hits:
        keyword_hits.sort(key=lambda x: x[0])
        return True, f"{keyword_hits[0][1]}(关键词匹配)"
    return False, ""


def default_pricing_likely_covers(row: ExpectedPriceRow, material: str | None, instrument_count: float | None) -> bool:
    """Heuristic: standard engine repricing patterns."""
    if row.note and "原始按器械数" in row.note:
        return True
    if row.note and "标准价" in row.note:
        return True
    # Raw price looks like instrument-count miscalculation (common ×4.4 / ×8.8 patterns)
    if row.raw_unit and row.proc_unit and instrument_count:
        ratio = row.raw_unit / max(row.proc_unit, 0.01)
        if abs(ratio - instrument_count) < 0.5:
            return True
    # Discount policies: proc is fraction of raw
    if row.raw_unit and row.proc_unit:
        for rate in (0.5, 0.7, 0.9):
            if nums_close(row.proc_unit, row.raw_unit * rate):
                return True
    return False


def classify_coverage(
    row: ExpectedPriceRow,
    material: str | None,
    instrument_count: float | None,
    profile: CustomerProfile | None,
) -> tuple[str, str]:
    rules = profile.product_rules if profile else []
    mode = profile.pricing_mode if profile else "standard"

    ok, rule_name = match_special_rule(row, material, rules, row.proc_unit)
    if ok:
        return "special_rule", rule_name

    if mode == "special_only":
        return "uncovered", "special_only 无匹配特色规则"

    if default_pricing_likely_covers(row, material, instrument_count):
        return "default_heuristic", "标准计费引擎（启发式）"

    # hybrid/standard without match — needs PricingEngine verification
    if mode in {"standard", "hybrid"}:
        return "needs_engine_verify", "待 PricingEngine 验证默认规则"

    return "uncovered", "无特色规则且默认规则未确认"


def pick_june_pair(hospital_dir: Path) -> tuple[Path | None, Path | None, str]:
    raw_dir = hospital_dir / "原始表格"
    proc_dir = hospital_dir / "处理后表格"
    if not raw_dir.is_dir() or not proc_dir.is_dir():
        return None, None, "缺少原始或处理后目录"

    name = hospital_dir.name
    if name in HOSPITAL_PAIR_OVERRIDE:
        rel_raw, rel_proc, label = HOSPITAL_PAIR_OVERRIDE[name]
        raw = hospital_dir / rel_raw
        proc = hospital_dir / rel_proc
        if raw.is_file() and proc.is_file():
            return raw, proc, label

    raw_files = [p for p in raw_dir.iterdir() if p.suffix.lower() in {".xlsx", ".xls"}]
    proc_files = [p for p in proc_dir.iterdir() if p.suffix.lower() in {".xlsx", ".xls"}]

    mapping = match_raw_processed(raw_files, proc_files)
    if TARGET_MONTH in mapping:
        raw, bill, _ = mapping[TARGET_MONTH]
        if bill:
            return raw, bill, "6月"

    # 5.13-6.15 等：按 match_raw_processed 的 5 月成对，避免 6.15 误配 6月__ 其它账期
    if 5 in mapping:
        raw5, bill5, _ = mapping[5]
        if raw5 and bill5 and re.search(r"5\.\d+-6\.", raw5.name):
            return raw5, bill5, "5月跨期(含6月)"

    # Date-range bills ending in June (e.g. 5.9-6.8) — require处理后文件名含同日期段
    june_raw: list[Path] = []
    for raw in raw_files:
        m = extract_month_from_name(raw.name)
        if m == TARGET_MONTH:
            june_raw.append(raw)
        elif re.search(r"[-~]6[\.-]|6[\.-]\d|6月", raw.name):
            june_raw.append(raw)
    for raw in june_raw:
        bill = pick_processed_bill(TARGET_MONTH, proc_files, raw.name)
        if bill:
            raw_range = extract_date_range_token(raw.name)
            if raw_range and raw_range not in bill.name:
                # 处理后账期与原始不一致，跳过
                alt = pick_processed_bill(5, proc_files, raw.name)
                if alt and (not raw_range or raw_range in alt.name):
                    return raw, alt, f"跨月(原始:{raw.name})"
                continue
            return raw, bill, f"6月(原始:{raw.name})"

    return None, None, "无6月可对比材料"


def row_match_key(r: DetailRow) -> tuple[str, str, str]:
    """附一验收口径：发货单号 + 包名 + 包数。"""
    pc = r.pack_count if r.pack_count is not None else 0
    return (str(r.ship_no), r.pack_name, f"{pc:.4g}")


def iter_compare_pairs(
    raw_wb: Any,
    proc_wb: Any,
) -> list[tuple[str, DetailRow, DetailRow]]:
    from analyze_test_case_excel import collect_proc_rows, flatten_raw_rows, should_flatten_compare

    pairs: list[tuple[str, DetailRow, DetailRow]] = []
    if should_flatten_compare(raw_wb, proc_wb):
        raw_rows = flatten_raw_rows(raw_wb)
        proc_rows = collect_proc_rows(proc_wb)
        proc_map = {row_match_key(r): r for r in proc_rows}
        for raw in raw_rows:
            proc = proc_map.get(row_match_key(raw))
            if proc:
                pairs.append(("全部科室(汇总对比)", raw, proc))
    else:
        matched: set[tuple[str, tuple[str, str, str]]] = set()
        for sheet in sorted(set(raw_wb.sheets) | set(proc_wb.sheets)):
            raw_rows = raw_wb.sheets.get(sheet, [])
            proc_rows = proc_wb.sheets.get(sheet, [])
            proc_map = {row_match_key(r): r for r in proc_rows}
            for raw in raw_rows:
                key = row_match_key(raw)
                proc = proc_map.get(key)
                if proc:
                    pairs.append((sheet, raw, proc))
                    matched.add((sheet, key))
        # 跨 sheet 调价：原始行在处理后表迁至其他科室时，同 sheet 对比会漏配对（如市五 1609644）
        global_proc_map = {row_match_key(r): r for r in collect_proc_rows(proc_wb)}
        for sheet, raw_rows in raw_wb.sheets.items():
            for raw in raw_rows:
                key = row_match_key(raw)
                if (sheet, key) in matched:
                    continue
                proc = global_proc_map.get(key)
                if proc:
                    pairs.append((sheet, raw, proc))
                    matched.add((sheet, key))
    return pairs


def extract_expected_price_rows(hospital_dir: Path) -> tuple[list[ExpectedPriceRow], Path | None, Path | None, str]:
    raw_path, proc_path, note = pick_june_pair(hospital_dir)
    if not raw_path or not proc_path:
        return [], raw_path, proc_path, note

    raw_wb = parse_workbook(raw_path)
    proc_wb = parse_workbook(proc_path)

    expected: list[ExpectedPriceRow] = []
    seen: set[tuple[str, str, str, str]] = set()

    for sheet, raw, proc in iter_compare_pairs(raw_wb, proc_wb):
        if nums_close(raw.unit_price, proc.unit_price) and nums_close(raw.total_price, proc.total_price):
            continue
        if not nums_close(raw.pack_count, proc.pack_count):
            continue  # 包数变化不算价格校正

        dedupe = (sheet, str(raw.ship_no), raw.pack_name, f"{raw.pack_count or 0:.4g}")
        if dedupe in seen:
            continue
        seen.add(dedupe)

        note_text = price_note(raw, proc, "单价")
        expected.append(
            ExpectedPriceRow(
                sheet=sheet,
                ship_no=str(raw.ship_no),
                pack_name=raw.pack_name,
                pack_count=raw.pack_count,
                raw_unit=raw.unit_price,
                proc_unit=proc.unit_price,
                raw_total=raw.total_price,
                proc_total=proc.total_price,
                raw_row=raw.excel_row,
                proc_row=proc.excel_row,
                note=note_text,
            )
        )

    return expected, raw_path, proc_path, note


def write_hospital_csv(hospital_dir: Path, rows: list[ExpectedPriceRow]) -> Path:
    out = hospital_dir / "6月期待价格校正清单.csv"
    with out.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow([
            "科室", "原始行", "发货单号", "包名", "包数",
            "原单价", "处理后单价", "原总价", "处理后总价",
            "规则覆盖", "匹配规则", "说明",
        ])
        for r in rows:
            w.writerow([
                r.sheet, r.raw_row or "", r.ship_no, r.pack_name, r.pack_count or "",
                r.raw_unit, r.proc_unit, r.raw_total, r.proc_total,
                r.rule_coverage, r.matched_rule, r.note,
            ])
    return out


def audit_hospital(name: str, profiles: dict[str, CustomerProfile]) -> HospitalAudit:
    hospital_dir = TEST_CASE_DIR / name
    audit = HospitalAudit(name=name, status="pending")

    if not hospital_dir.is_dir():
        audit.status = "skip_no_folder"
        audit.message = "测试用例目录不存在"
        return audit

    raw_files = list((hospital_dir / "原始表格").glob("*")) if (hospital_dir / "原始表格").is_dir() else []
    if not any(p.suffix.lower() in {".xlsx", ".xls"} for p in raw_files):
        audit.status = "skip_no_raw"
        audit.message = "缺少原始表格"
        return audit

    expected, raw_path, proc_path, pair_note = extract_expected_price_rows(hospital_dir)
    if not raw_path or not proc_path:
        audit.status = "skip_no_june"
        audit.message = pair_note
        return audit

    audit.raw_file = raw_path.name
    audit.proc_file = proc_path.name

    profile = resolve_profile(name, profiles)
    if profile:
        audit.customer_code = profile.code
        audit.pricing_mode = profile.pricing_mode
    else:
        audit.customer_code = "UNKNOWN"
        audit.pricing_mode = "standard"

    # Re-parse for material/instrument on expected rows
    raw_wb = parse_workbook(raw_path)
    material_by_key: dict[tuple[str, str, str], tuple[str | None, float | None]] = {}
    for sheet, rows in raw_wb.sheets.items():
        for r in rows:
            material_by_key[(sheet, str(r.ship_no), r.pack_name)] = (r.material, r.instrument_count)
            material_by_key[("全部科室(汇总对比)", str(r.ship_no), r.pack_name)] = (r.material, r.instrument_count)

    for row in expected:
        mat, inst = material_by_key.get((row.sheet, row.ship_no, row.pack_name), (None, None))
        cov, matched = classify_coverage(row, mat, inst, profile)
        row.rule_coverage = cov
        row.matched_rule = matched
        if cov == "uncovered":
            audit.uncovered_rows.append(row)

    audit.expected_rows = expected
    audit.expected_count = len(expected)
    audit.uncovered_count = len(audit.uncovered_rows)

    write_hospital_csv(hospital_dir, expected)

    if audit.uncovered_count > 0:
        audit.status = "blocked_uncovered"
        audit.message = f"{audit.uncovered_count} 条价格变化无规则覆盖，需客户二次核对"
    elif audit.expected_count == 0:
        audit.status = "ready_zero"
        audit.message = "6月无期待价格校正（原始与处理后价格一致）"
    else:
        audit.status = "ready_tune"
        audit.message = f"{audit.expected_count} 条期待校正，可进入规则微调与系统测试"

    return audit


def render_index(audits: list[HospitalAudit]) -> str:
    today = date.today().isoformat()
    lines = [
        "# 批量 6 月期待价格校正 — 索引报告",
        "",
        f"> 生成日期：{today}",
        "> 方法：原始 vs 处理后 ground truth，仅统计单价/总价变化行（附一同款口径）",
        "> 规则覆盖：billing-seeds 特色规则 + 默认计费启发式；`special_only` 无匹配即阻塞",
        "",
        "## 进度总览",
        "",
        "| 序号 | 医院 | 状态 | 期待校正 | 无规则覆盖 | 客户代码 | 计费模式 | 原始/处理后 |",
        "|------|------|------|---------|-----------|---------|---------|------------|",
    ]

    status_label = {
        "ready_tune": "✅ 可继续测试",
        "ready_zero": "✅ 零差异",
        "blocked_uncovered": "🛑 需客户核对",
        "skip_no_raw": "⏭ 缺原始",
        "skip_no_june": "⏭ 缺6月",
        "skip_no_folder": "⏭ 无目录",
    }

    blocked: list[HospitalAudit] = []
    ready: list[HospitalAudit] = []

    for i, a in enumerate(audits, 1):
        label = status_label.get(a.status, a.status)
        files = f"{a.raw_file} / {a.proc_file}" if a.raw_file else "—"
        lines.append(
            f"| {i} | {a.name} | {label} | {a.expected_count} | {a.uncovered_count} | "
            f"{a.customer_code} | {a.pricing_mode} | {files} |"
        )
        if a.status == "blocked_uncovered":
            blocked.append(a)
        elif a.status == "ready_tune":
            ready.append(a)

    lines.extend(["", "## 🛑 需向您二次核对（特色+默认规则均未覆盖）", ""])
    if not blocked:
        lines.append("**无。** 所有有价格变化的医院均能在现有规则体系中找到解释，或属于标准计费范畴。")
    else:
        for a in blocked:
            lines.append(f"### {a.name}（{a.uncovered_count} 条）")
            lines.append("")
            lines.append(f"- 客户代码：`{a.customer_code}`，计费模式：`{a.pricing_mode}`")
            lines.append(f"- 清单：`测试用例/{a.name}/6月期待价格校正清单.csv`")
            lines.append("")
            lines.append("| 科室 | 发货单号 | 包名 | 原单价→处理后 | 说明 |")
            lines.append("|------|---------|------|--------------|------|")
            for r in a.uncovered_rows[:30]:
                lines.append(
                    f"| {r.sheet} | {r.ship_no} | {r.pack_name} | "
                    f"{r.raw_unit}→{r.proc_unit} | {r.matched_rule} |"
                )
            if len(a.uncovered_rows) > 30:
                lines.append(f"| … | | | | 另有 {len(a.uncovered_rows) - 30} 条 |")
            lines.append("")

    lines.extend(["", "## ✅ 可继续规则微调与系统测试", ""])
    zero = [a for a in audits if a.status == "ready_zero"]
    lines.append(f"- **零差异验收**：{len(zero)} 家 — {', '.join(a.name for a in zero) or '无'}")
    lines.append(f"- **有待校正条目**：{len(ready)} 家 — {', '.join(f'{a.name}({a.expected_count})' for a in ready) or '无'}")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    profiles = load_seed_profiles()
    audits = [audit_hospital(name, profiles) for name in TODO_HOSPITALS]
    index_text = render_index(audits)
    OUTPUT_INDEX.write_text(index_text, encoding="utf-8")

    print(index_text)
    print(f"\n索引已写入: {OUTPUT_INDEX}")

    blocked = sum(1 for a in audits if a.status == "blocked_uncovered")
    ready = sum(1 for a in audits if a.status in {"ready_tune", "ready_zero"})
    print(f"\n统计: {len(audits)} 家, 可继续 {ready} 家, 需核对 {blocked} 家")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
