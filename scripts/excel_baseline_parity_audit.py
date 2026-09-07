#!/usr/bin/env python3
"""Excel ↔ baseline/DB 逐院规则覆盖核查（29 家特殊计价医院）。

解析「各医院特殊收费」sheet（合并单元格向下填充），检查每条 Excel 规则是否在
baseline JSON 中有对应活跃规则覆盖（关键词/指定名称）。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
BASELINE_DIR = ROOT / "backend/src/main/resources/billing-rules/baseline"

sys.path.insert(0, str(ROOT / "scripts"))
from import_special_pricing_v17 import build_merged_map, cell, norm, parse_pack_name  # noqa: E402
from strict_hospital_codes import STRICT_BY_CODE, STRICT_KEEP_CODES  # noqa: E402

# Excel 医院全称 → code（补全 v17 映射）
EXTRA_HOSPITAL_TO_CODE: dict[str, str] = {
    "哈尔滨市第五医院": "HRB-WY",
    "平房区人民医院": "PFQ-RM",
    "呼兰中医院": "HULAN-TCM",
    "呼兰区第一人民医院": "HULAN-RM",
    "新发红十字医院": "XINFA-HSZ",
    "黑龙江省远东心脑血管医院": "YUANDONG-XN",
    "祖研-黑龙江省中医医院（三辅院区）": "ZUYAN-SF",
    "奥兰医院": "AOLAN-YY",
    "哈尔滨胸科医院": "HRB-XK-YY",
    "哈尔滨市胸科医院": "HRB-XK-YY",
    "森海医院": "SENHAI-YY",
    "哈尔滨森海医院": "SENHAI-YY",
}


def hospital_code(name: str) -> str | None:
    from import_special_pricing_v17 import HOSPITAL_TO_CODE

    return HOSPITAL_TO_CODE.get(name) or EXTRA_HOSPITAL_TO_CODE.get(name)


def load_baseline(code: str) -> dict:
    path = BASELINE_DIR / f"{code}.json"
    if not path.is_file():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def rule_keywords(rule: dict) -> list[str]:
    kws = rule.get("keywords") or []
    out: list[str] = []
    for kw in kws:
        if not kw:
            continue
        text = str(kw)
        if text.endswith("@contains"):
            out.append(text[:-9])
        else:
            out.append(text)
    return out


def rule_covers_keyword(rule: dict, keyword: str, mode: str) -> bool:
    if rule.get("isActive") is False:
        return False
    name = (rule.get("name") or "").strip()
    raw_kws = rule.get("keywords") or []
    km = (rule.get("keywordMatchMode") or "").lower()
    rule_type = rule.get("ruleType") or ""

    def norm_kw(text: str) -> str:
        return text[:-9] if text.endswith("@contains") else text

    for raw in raw_kws:
        if not raw:
            continue
        text = str(raw)
        base = norm_kw(text)
        if base != keyword and keyword not in base and base not in keyword:
            continue
        if rule_type == "FOLD" or mode == "contains" or text.endswith("@contains") or km == "contains":
            return True
        if mode == "exact" and (base in keyword or keyword in base or base == keyword):
            return True
    if mode == "exact" and (keyword == name or keyword in name):
        return True
    return False


def find_coverage(rules: list[dict], keyword: str | None, mode: str, pack_name: str) -> bool:
    if mode == "generic":
        return True
    if not keyword and mode == "exact":
        keyword = pack_name
    if not keyword:
        return False
    for rule in rules:
        if rule_covers_keyword(rule, keyword, mode):
            return True
    return False


def parse_excel_rows(xlsx: Path) -> list[dict]:
    wb = openpyxl.load_workbook(xlsx, data_only=True)
    ws = wb["各医院特殊收费"]
    merged = build_merged_map(ws)
    state = {
        "hospital_seq": "",
        "hospital": "",
        "item": "",
        "pack_name": "",
        "pack_type": "",
    }
    rows: list[dict] = []
    for r in range(2, ws.max_row + 1):
        if norm(cell(ws, merged, r, 1)):
            state["hospital_seq"] = norm(cell(ws, merged, r, 1))
        if norm(cell(ws, merged, r, 2)):
            state["hospital"] = norm(cell(ws, merged, r, 2))
        if norm(cell(ws, merged, r, 3)):
            state["item"] = norm(cell(ws, merged, r, 3))
        if norm(cell(ws, merged, r, 4)):
            state["pack_name"] = norm(cell(ws, merged, r, 4))
        if norm(cell(ws, merged, r, 5)):
            state["pack_type"] = norm(cell(ws, merged, r, 5))

        inst = norm(cell(ws, merged, r, 6))
        rule = norm(cell(ws, merged, r, 7))
        if not rule and not inst:
            continue
        mode, keyword = parse_pack_name(state["pack_name"])
        # 合并单元格多行包名：逐词检查
        pack_lines = [ln.strip() for ln in (state["pack_name"] or "").splitlines() if ln.strip()]
        keywords: list[tuple[str, str]] = []
        if len(pack_lines) > 1:
            for ln in pack_lines:
                m, k = parse_pack_name(ln)
                if k:
                    keywords.append((m, k))
        elif keyword:
            keywords.append((mode, keyword))
        elif state["pack_name"]:
            keywords.append((mode, state["pack_name"]))
        code = hospital_code(state["hospital"])
        for km, kw in keywords or [(mode, keyword)]:
            rows.append(
                {
                    "row": r,
                    "hospital": state["hospital"],
                    "code": code,
                    "item": state["item"],
                    "pack_name": state["pack_name"],
                    "match_mode": km,
                    "keyword": kw,
                    "instrument_count": inst or None,
                    "rule": rule,
                }
            )
    wb.close()
    return rows


def audit(xlsx: Path) -> dict:
    excel_rows = parse_excel_rows(xlsx)
    by_code: dict[str, list[dict]] = {}
    unmapped: list[str] = []
    for row in excel_rows:
        code = row.get("code")
        if not code:
            unmapped.append(f"row {row['row']}: {row['hospital']}")
            continue
        by_code.setdefault(code, []).append(row)

    results: list[dict] = []
    errors: list[str] = []
    for code in STRICT_KEEP_CODES:
        baseline = load_baseline(code)
        rules = [r for r in (baseline.get("productRules") or []) if r.get("isActive") is not True or r.get("isActive") is not False]
        rules = baseline.get("productRules") or []
        excel_for = by_code.get(code, [])
        missing: list[str] = []
        for er in excel_for:
            kw = er.get("keyword") or er.get("pack_name")
            mode = er.get("match_mode") or "exact"
            if mode == "generic":
                continue
            if not find_coverage(rules, er.get("keyword"), mode, er.get("pack_name") or ""):
                missing.append(
                    f"row{er['row']} [{er.get('item')}] {er.get('pack_name')} | {er.get('rule')[:60]}"
                )
        ok = not missing and code in by_code
        if code not in by_code:
            errors.append(f"{code}: Excel 无对应段落")
        if missing:
            errors.extend([f"{code}: 缺失覆盖 — {m}" for m in missing])
        results.append(
            {
                "code": code,
                "name": STRICT_BY_CODE[code].label,
                "excel_rows": len(excel_for),
                "baseline_rules": len([r for r in rules if r.get("isActive") is not False]),
                "ok": ok and bool(excel_for),
                "missing": missing,
            }
        )

    return {
        "source_excel": str(xlsx),
        "ok": len(errors) == 0 and len(unmapped) == 0,
        "unmapped_hospitals": unmapped,
        "errors": errors,
        "hospitals": results,
        "summary": {
            "total": len(STRICT_KEEP_CODES),
            "pass": sum(1 for r in results if r["ok"]),
            "fail": sum(1 for r in results if not r["ok"]),
            "excel_rows": len(excel_rows),
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--excel", type=Path, required=True)
    parser.add_argument("--out", type=Path, default=ROOT / "测试用例/excel_baseline_parity_report.json")
    args = parser.parse_args()
    if not args.excel.is_file():
        print(f"ERROR: Excel 不存在: {args.excel}", file=sys.stderr)
        return 1
    report = audit(args.excel)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    s = report["summary"]
    print(f"Excel rows: {s['excel_rows']}, hospitals PASS: {s['pass']}/{s['total']}")
    if report["unmapped_hospitals"]:
        print("未映射医院:")
        for u in report["unmapped_hospitals"]:
            print(" ", u)
    if report["errors"]:
        print(f"差异 {len(report['errors'])} 条:")
        for e in report["errors"][:30]:
            print(" ", e)
        if len(report["errors"]) > 30:
            print(f"  ... 另有 {len(report['errors']) - 30} 条，见 {args.out}")
        return 1
    print(f"OK: 全部 {s['total']} 家 Excel 规则在 baseline 中有覆盖")
    print(f"报告: {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
