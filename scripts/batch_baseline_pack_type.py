#!/usr/bin/env python3
"""按特殊收费 Excel「包类型」列为 baseline 规则补 acceptedTypes。"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
BASELINE_DIR = ROOT / "backend/src/main/resources/billing-rules/baseline"
MANIFEST_PATH = ROOT / "backend/src/main/resources/billing-seeds/billing-rules-manifest.json"

sys.path.insert(0, str(ROOT / "scripts"))
from strict_hospital_codes import STRICT_BY_CODE, STRICT_KEEP_CODES  # noqa: E402

EXCEL_HOSPITAL_TO_CODE: dict[str, str] = {
    "冰城医美": "BINGCHENG-YM",
    "电机厂医院": "GUOYAO-2",
    "方南南医院": "FNN-YY",
    "东北农业大学": "NEAU-YY",
    "松电慢病": "HRB-SD-MB",
    "航天风华": "HRB-HTFH",
    "市五院（二门诊）": "HRB-WY-EM",
    "九州医院": "JIUZHOU-FK",
    "博尚医院": "BOSHANG-YY",
    "黑龙江省海员总医院（松北）": "HAIYUAN-SB",
    "黑龙江省妇幼保健院（人口）": "HLJ-FY-RK",
    "祖研-黑龙江省中医医院（南岗院区）": "ZUYAN-NG",
    "黑龙江省社会康复医院": "SHKF-YY",
    "哈尔滨市道里区妇幼保健院": "DL-FUCHAN",
    "春语医疗美容医院": "CHUNYU-YL",
    "黑龙江总工会医院": "HL-ZGH",
    "哈尔滨基准生物有限公司": "JZSW-BIO",
    "索菲医疗美容门诊": "SUOFEI-YL",
    "省监狱管理局医院": "HLJ-JYGLJ-YY",
    "呼兰中医院": "HULAN-TCM",
    "平房区人民医院": "PFQ-RM",
    "哈尔滨市第五医院": "HRB-WY",
    "祖研-黑龙江省中医医院（三辅院区）": "ZUYAN-SF",
    "新发红十字医院": "XINFA-HSZ",
    "呼兰区第一人民医院": "HULAN-RM",
    "黑龙江省远东心脑血管医院": "YUANDONG-XN",
    "奥兰医院": "AOLAN-YY",
    "哈尔滨市胸科医院": "HRB-XK-YY",
    "哈尔滨森海医院": "SENHAI-YY",
}

QUOTE_RE = re.compile(r"[“\"']([^”\"']+)[”\"']")


def extract_pack_keyword(pack_name: str) -> str:
    text = (pack_name or "").strip()
    m = QUOTE_RE.search(text)
    if m:
        return m.group(1).strip()
    if "包名称带" in text:
        return text.replace("包名称带", "").strip("“”\"' ")
    return text


def parse_excel_rows(excel_path: Path) -> dict[str, list[dict[str, str]]]:
    wb = openpyxl.load_workbook(excel_path, read_only=True, data_only=True)
    ws = wb["各医院特殊收费"]
    by_code: dict[str, list[dict[str, str]]] = {}
    cur_hospital = None
    for row in ws.iter_rows(values_only=True):
        if row[1]:
            cur_hospital = str(row[1]).strip()
        pack_name = row[3]
        if not pack_name or not cur_hospital or cur_hospital == "医院名称":
            continue
        code = EXCEL_HOSPITAL_TO_CODE.get(cur_hospital)
        if not code:
            continue
        pack_type = str(row[4]).strip() if row[4] else ""
        if not pack_type or pack_type == "包类型":
            continue
        by_code.setdefault(code, []).append(
            {
                "packName": str(pack_name).strip(),
                "packKeyword": extract_pack_keyword(str(pack_name)),
                "packType": pack_type,
                "ruleText": str(row[6]).strip() if row[6] else "",
            }
        )
    return by_code


def has_export_apply(rule: dict) -> bool:
    cj = rule.get("conditionsJson") or ""
    return "exportApply" in cj


def should_skip_rule(rule: dict) -> bool:
    if rule.get("isActive") is False:
        return True
    name = str(rule.get("name") or "")
    if name.startswith("校正价"):
        return True
    if has_export_apply(rule):
        return True
    keywords = rule.get("keywords") or []
    return not keywords


def match_pack_types(rule: dict, excel_rows: list[dict[str, str]]) -> list[str]:
    keywords = [str(k).strip() for k in (rule.get("keywords") or []) if str(k).strip()]
    if not keywords:
        return []
    matched_types: set[str] = set()
    for row in excel_rows:
        pack_name = row["packName"]
        pack_keyword = row["packKeyword"]
        for kw in keywords:
            if kw == pack_name or kw == pack_keyword or kw in pack_name or kw in pack_keyword:
                matched_types.add(row["packType"])
                break
            if pack_keyword and (pack_keyword in kw or pack_name in kw):
                matched_types.add(row["packType"])
                break
    return sorted(matched_types)


def apply_to_baseline(code: str, excel_rows: list[dict[str, str]], dry_run: bool) -> list[str]:
    path = BASELINE_DIR / f"{code}.json"
    if not path.is_file():
        return [f"SKIP {code}: baseline 不存在"]
    data = json.loads(path.read_text(encoding="utf-8"))
    changes: list[str] = []
    for rule in data.get("productRules") or []:
        if should_skip_rule(rule):
            continue
        pack_types = match_pack_types(rule, excel_rows)
        if not pack_types:
            continue
        existing = rule.get("acceptedTypes") or []
        if existing == pack_types:
            continue
        rule["acceptedTypes"] = pack_types
        changes.append(f"{code}「{rule.get('name')}」→ {pack_types}")
    if changes and not dry_run:
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return changes


def sync_manifest(dry_run: bool) -> None:
    if dry_run or not MANIFEST_PATH.is_file():
        return
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    customers = manifest.get("customers") or {}
    for code in STRICT_KEEP_CODES:
        baseline_path = BASELINE_DIR / f"{code}.json"
        if not baseline_path.is_file():
            continue
        baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
        if code not in customers:
            continue
        customers[code]["productRules"] = baseline.get("productRules") or []
    MANIFEST_PATH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--excel", type=Path, required=True)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--sync-manifest", action="store_true")
    args = parser.parse_args()
    if not args.excel.is_file():
        print(f"ERROR: Excel 不存在: {args.excel}", file=sys.stderr)
        return 1

    excel_by_code = parse_excel_rows(args.excel)
    all_changes: list[str] = []
    for code in STRICT_KEEP_CODES:
        rows = excel_by_code.get(code, [])
        if not rows:
            continue
        all_changes.extend(apply_to_baseline(code, rows, args.dry_run))

    print(f"{'DRY-RUN ' if args.dry_run else ''}更新 {len(all_changes)} 条规则 acceptedTypes")
    for line in all_changes:
        print(" -", line)

    if args.sync_manifest:
        sync_manifest(args.dry_run)
        print("manifest 已同步" if not args.dry_run else "DRY-RUN: 将同步 manifest")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
