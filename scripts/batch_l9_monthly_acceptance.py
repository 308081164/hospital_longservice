#!/usr/bin/env python3
"""L9-L61 五院：逐月期待清单 + 开启 billing 后系统对账验收。"""

from __future__ import annotations

import csv
import json
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE_DIR = ROOT / "测试用例"
BACKEND = "hospital-backend"
API = "http://127.0.0.1:8000"
RULE_ID = 1
OPERATOR = "l9-monthly-audit"

sys.path.insert(0, str(ROOT / "scripts"))

from analyze_test_case_excel import match_raw_processed, parse_workbook  # noqa: E402
from batch_june_price_reconciliation import (  # noqa: E402
    ExpectedPriceRow,
    classify_coverage,
    iter_compare_pairs,
    load_seed_profiles,
    nums_close,
    pick_june_pair,
    price_note,
    resolve_profile,
)
from batch_june_system_test import (  # noqa: E402
    expected_key,
    fetch_warnings,
    get_token,
    import_bill,
    warn_key,
)
from batch_month_backscan import extract_for_month, pick_month_pair  # noqa: E402

L9_HOSPITALS = [
    "黑龙江省第二医院（南岗院区）",
    "黑龙江省第二医院（松北院区）",
    "哈尔滨市呼兰区第一人民医院",
    "哈尔滨市红十字妇产医院",
    "哈尔滨(工程)大学医院",
]

OUTPUT_INDEX = TEST_CASE_DIR / "批量L9逐月特色验收结果.md"
SCAN_MONTHS = [4, 5, 6]


@dataclass
class MonthResult:
    hospital: str
    month: int
    raw_file: str = ""
    expected_all: int = 0
    expected_special: int = 0
    job_id: int | None = None
    system_warnings: int = 0
    matched: int = 0
    missed: int = 0
    extra: int = 0
    status: str = "pending"
    message: str = ""


def period_tokens(name: str) -> tuple[str, ...]:
    return tuple(re.findall(r"\d+\.\d+", name))


def periods_aligned(raw_path: Path, proc_path: Path) -> bool:
    raw_t = period_tokens(raw_path.name)
    proc_t = period_tokens(proc_path.name)
    if raw_t and proc_t:
        return raw_t == proc_t
    return True


def pick_raw_for_month(hospital_dir: Path, month: int) -> tuple[Path | None, str]:
    if month == 6:
        raw, _, note = pick_june_pair(hospital_dir)
        if raw:
            return raw, note
    raw, _, note = pick_month_pair(hospital_dir, month)
    if raw:
        return raw, note
    return None, note


def build_month_expected(
    hospital_dir: Path,
    month: int,
    profile,
) -> tuple[list[ExpectedPriceRow], list[ExpectedPriceRow], Path | None]:
    if month == 6:
        raw_path, proc_path, _ = pick_june_pair(hospital_dir)
        if not raw_path or not proc_path:
            return [], [], None
    else:
        raw_path, proc_path, _ = pick_month_pair(hospital_dir, month)
        if not raw_path or not proc_path:
            rows, _ = extract_for_month(hospital_dir, month)
            return rows, [], None

    raw_wb = parse_workbook(raw_path)
    proc_wb = parse_workbook(proc_path)
    all_rows: list[ExpectedPriceRow] = []
    special_rows: list[ExpectedPriceRow] = []
    seen: set[tuple[str, str, str, str]] = set()

    material_by_key: dict[tuple[str, str, str], tuple] = {}
    for sheet, rows in raw_wb.sheets.items():
        for r in rows:
            material_by_key[(sheet, str(r.ship_no), r.pack_name)] = (r.material, r.instrument_count)
            material_by_key[("全部科室(汇总对比)", str(r.ship_no), r.pack_name)] = (
                r.material,
                r.instrument_count,
            )

    for sheet, raw, proc in iter_compare_pairs(raw_wb, proc_wb):
        if nums_close(raw.unit_price, proc.unit_price) and nums_close(raw.total_price, proc.total_price):
            continue
        if not nums_close(raw.pack_count, proc.pack_count):
            continue
        dedupe = (sheet, str(raw.ship_no), raw.pack_name, f"{raw.pack_count or 0:.4g}")
        if dedupe in seen:
            continue
        seen.add(dedupe)
        row = ExpectedPriceRow(
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
            note=price_note(raw, proc, "单价"),
        )
        mat, inst = material_by_key.get((sheet, row.ship_no, row.pack_name), (None, None))
        cov, matched = classify_coverage(row, mat, inst, profile)
        row.rule_coverage = cov
        row.matched_rule = matched
        all_rows.append(row)
        if cov == "special_rule":
            special_rows.append(row)

    return all_rows, special_rows, raw_path


def build_zero_diff_keys(hospital_dir: Path, month: int) -> set[tuple[str, str, str]]:
    if month == 6:
        raw_path, proc_path, _ = pick_june_pair(hospital_dir)
    else:
        raw_path, proc_path, _ = pick_month_pair(hospital_dir, month)
    if not raw_path or not proc_path:
        return set()
    raw_wb = parse_workbook(raw_path)
    proc_wb = parse_workbook(proc_path)
    keys: set[tuple[str, str, str]] = set()
    for _, raw, proc in iter_compare_pairs(raw_wb, proc_wb):
        if nums_close(raw.unit_price, proc.unit_price) and nums_close(raw.total_price, proc.total_price):
            keys.add(expected_key(ExpectedPriceRow(
                sheet="", ship_no=str(raw.ship_no), pack_name=raw.pack_name, pack_count=raw.pack_count,
                raw_unit=raw.unit_price, proc_unit=proc.unit_price,
                raw_total=raw.total_price, proc_total=proc.total_price,
                raw_row=raw.excel_row, proc_row=proc.excel_row,
            )))
    return keys


def write_expected_csv(path: Path, rows: list[ExpectedPriceRow]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as f:
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


def compare_month(
    token: str,
    hospital: str,
    month: int,
    expected: list[ExpectedPriceRow],
    raw_path: Path,
    zero_diff_keys: set[tuple[str, str, str]] | None = None,
) -> MonthResult:
    res = MonthResult(hospital=hospital, month=month, expected_all=len(expected), raw_file=raw_path.name)
    res.expected_special = len(expected)
    exp_keys = {expected_key(r) for r in expected}

    try:
        job = import_bill(token, hospital, raw_path)
        res.job_id = job.get("id")
        time.sleep(0.4)
        warnings = fetch_warnings(token, res.job_id)
        res.system_warnings = len(warnings)
        sys_keys = {
            warn_key("", str(w.get("orderNo") or ""), w.get("packName") or "", w.get("packCount"))
            for w in warnings
        }
        res.matched = sum(1 for r in expected if expected_key(r) in sys_keys)
        missed = [expected_key(r) for r in expected if expected_key(r) not in sys_keys]
        res.missed = len(missed)
        extra_keys = sys_keys - exp_keys
        if zero_diff_keys:
            extra_keys -= zero_diff_keys
        res.extra = len(extra_keys)

        if res.expected_special == 0:
            res.status = "pass_zero" if res.system_warnings == 0 else "fail_extra"
            res.message = "零期待" + ("" if res.system_warnings == 0 else f"，系统 {res.system_warnings} 条 warning")
        elif res.missed == 0 and res.extra == 0:
            res.status = "pass"
            res.message = f"完全一致 {res.expected_special} 条"
        else:
            res.status = "fail"
            res.message = f"命中 {res.matched}/{res.expected_special}，漏检 {res.missed}，多报 {res.extra}"

        tsv = TEST_CASE_DIR / hospital / f"{month}月系统warning.tsv"
        with tsv.open("w", encoding="utf-8") as f:
            f.write("sheet\trow\torderNo\tpackName\tpackCount\tunitPrice\truleUnit\tstatus\tpricingRule\n")
            for w in warnings:
                f.write("\t".join([
                    str(w.get("sheetName") or ""),
                    str(w.get("rowNumber") or ""),
                    str(w.get("orderNo") or ""),
                    str(w.get("packName") or ""),
                    str(w.get("packCount") or ""),
                    str(w.get("unitPrice") or ""),
                    str(w.get("expectedUnitPrice") or ""),
                    str(w.get("status") or ""),
                    str(w.get("pricingRule") or ""),
                ]) + "\n")
    except Exception as exc:
        res.status = "error"
        res.message = str(exc)
    return res


def available_months(hospital_dir: Path) -> list[int]:
    raw_dir = hospital_dir / "原始表格"
    proc_dir = hospital_dir / "处理后表格"
    if not raw_dir.is_dir() or not proc_dir.is_dir():
        return []
    mapping = match_raw_processed(
        list(raw_dir.glob("*.xlsx")) + list(raw_dir.glob("*.xls")),
        list(proc_dir.glob("*.xlsx")) + list(proc_dir.glob("*.xls")),
    )
    months = sorted(mapping.keys())
    if pick_june_pair(hospital_dir)[0]:
        if 6 not in months:
            months.append(6)
        months = sorted(set(months))
    return [m for m in SCAN_MONTHS if m in months or m == 6]


def render_index(results: list[MonthResult]) -> str:
    lines = [
        "# 批量 L9-L61 逐月特色规则验收",
        "",
        f"> 生成日期：{date.today().isoformat()}",
        "> billing：**已开启**（P0.5 special_only · P0.5.2 收窄）",
        "> 期待口径：**特色规则覆盖行**（`规则覆盖=special_rule`）；零 diff 月为 pass_zero",
        "> 多报统计：**已排除** ground truth 原始=处理后同价行（边界误报）",
        "",
        "| 医院 | 月份 | 原始文件 | 特色期待 | 系统warning | 命中 | 漏检 | 多报 | 状态 |",
        "|------|------|----------|---------|------------|------|------|------|------|",
    ]
    for r in results:
        lines.append(
            f"| {r.hospital} | {r.month}月 | {r.raw_file or '—'} | {r.expected_special} | "
            f"{r.system_warnings} | {r.matched} | {r.missed} | {r.extra} | {r.status} |"
        )
    skips = [r for r in results if r.status == "skip"]
    fails = [r for r in results if r.status in {"fail", "fail_extra", "error"}]
    lines.append("")
    if skips:
        lines.append("## 跳过（材料账期不匹配）")
        lines.append("")
        for r in skips:
            lines.append(f"- **{r.hospital} {r.month}月**：{r.message}")
        lines.append("")
    if fails:
        lines.append("## 待规则微调")
        lines.append("")
        for r in fails:
            lines.append(f"- **{r.hospital} {r.month}月**：{r.message}（Job #{r.job_id or '—'}）")
    elif not skips or all(r.status in {"pass", "pass_zero", "skip"} for r in results):
        lines.append("## 结论")
        lines.append("")
        passed = sum(1 for r in results if r.status in {"pass", "pass_zero"})
        lines.append(f"**{passed}/{len(results)} 月份验收通过**（pass / pass_zero）。")
    return "\n".join(lines)


def main() -> int:
    profiles = load_seed_profiles()
    token = get_token()
    all_results: list[MonthResult] = []

    for hospital in L9_HOSPITALS:
        hospital_dir = TEST_CASE_DIR / hospital
        profile = resolve_profile(hospital, profiles)
        months = available_months(hospital_dir)
        print(f"\n=== {hospital} 月份: {months} ===", flush=True)

        for month in months:
            all_rows, special_rows, raw_path = build_month_expected(hospital_dir, month, profile)
            if raw_path is None and not all_rows:
                print(f"  {month}月: skip 无材料", flush=True)
                continue

            full_csv = hospital_dir / f"{month}月期待价格校正清单.csv"
            spec_csv = hospital_dir / f"{month}月期待特色校正清单.csv"
            write_expected_csv(full_csv, all_rows)
            write_expected_csv(spec_csv, special_rows)
            print(
                f"  {month}月: 全量{len(all_rows)} 特色{len(special_rows)} 原始={raw_path.name if raw_path else '?'}",
                flush=True,
            )

            if not raw_path:
                continue

            _, proc_path, _ = (
                pick_june_pair(hospital_dir) if month == 6 else pick_month_pair(hospital_dir, month)
            )
            if proc_path and not periods_aligned(raw_path, proc_path):
                res = MonthResult(
                    hospital=hospital,
                    month=month,
                    raw_file=raw_path.name,
                    expected_all=len(all_rows),
                    expected_special=len(special_rows),
                    status="skip",
                    message=f"原始/处理后账期不一致（{raw_path.name} vs {proc_path.name}）",
                )
                all_results.append(res)
                print(f"    → skip: {res.message}", flush=True)
                continue

            # 验收口径：有特色期待用特色清单；否则零期待 pass_zero
            expected_for_test = special_rows
            zero_keys = build_zero_diff_keys(hospital_dir, month)
            res = compare_month(token, hospital, month, expected_for_test, raw_path, zero_keys)
            all_results.append(res)
            print(f"    → {res.status}: {res.message}", flush=True)

    text = render_index(all_results)
    OUTPUT_INDEX.write_text(text, encoding="utf-8")
    print("\n" + text)
    print(f"\nWritten: {OUTPUT_INDEX}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
