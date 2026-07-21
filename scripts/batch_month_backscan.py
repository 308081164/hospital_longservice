#!/usr/bin/env python3
"""For zero-diff hospitals, scan backwards from June: 5月→4月→3月→… until price diffs found."""

from __future__ import annotations

import csv
import sys
from dataclasses import dataclass
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from batch_june_price_reconciliation import (  # noqa: E402
    TODO_HOSPITALS,
    ExpectedPriceRow,
    audit_hospital,
    extract_expected_price_rows,
    load_seed_profiles,
    write_hospital_csv,
)
from analyze_test_case_excel import (  # noqa: E402
    match_raw_processed,
    parse_workbook,
)

TEST_CASE_DIR = ROOT / "测试用例"
OUTPUT = TEST_CASE_DIR / "批量零差异医院逐月回溯报告.md"

# From 6月 index: hospitals with 0 expected price corrections in June
ZERO_DIFF_JUNE = [
    "国药总医院主院区",
    "国药总医院第二院区",
    "国药总医院第三院区",
    "哈尔滨市第五医院（二门诊）",
    "祖研-黑龙江省中医医院（香安院区）",
    "南岗区妇产医院",
    "黑龙江省社会康复医院",
    "太平人民医院",
    "黑龙江维多利亚妇产医院",
    "黑龙江九洲妇科医院",
    "呼兰区红十字医院",
    "呼兰中医院",
    "黑龙江中医药大学附属第二医院（哈南分院）",
    "哈尔滨仁胜医院",
    "香坊中医院",
    "悦美芳华医疗门诊医院",
]

SCAN_MONTHS = [5, 4, 3, 2, 1]


@dataclass
class BackscanResult:
    hospital: str
    first_diff_month: int | None = None
    diff_count: int = 0
    all_zero: bool = True
    notes: str = ""


def pick_month_pair(hospital_dir: Path, month: int):
    from analyze_test_case_excel import pick_processed_bill

    raw_dir = hospital_dir / "原始表格"
    proc_dir = hospital_dir / "处理后表格"
    raw_files = [p for p in raw_dir.iterdir() if p.suffix.lower() in {".xlsx", ".xls"}]
    proc_files = [p for p in proc_dir.iterdir() if p.suffix.lower() in {".xlsx", ".xls"}]
    mapping = match_raw_processed(raw_files, proc_files)
    if month not in mapping:
        return None, None, f"无{month}月原始/处理后配对"
    raw, bill, _ = mapping[month]
    if not bill:
        return None, None, f"无{month}月处理后账单"
    return raw, bill, "ok"


def extract_for_month(hospital_dir: Path, month: int) -> tuple[list[ExpectedPriceRow], str]:
    from batch_june_price_reconciliation import (
        iter_compare_pairs,
        nums_close,
        price_note,
        row_match_key,
    )

    raw_path, proc_path, note = pick_month_pair(hospital_dir, month)
    if not raw_path or not proc_path:
        return [], note

    raw_wb = parse_workbook(raw_path)
    proc_wb = parse_workbook(proc_path)
    expected: list[ExpectedPriceRow] = []
    seen: set[tuple[str, str, str, str]] = set()

    for sheet, raw, proc in iter_compare_pairs(raw_wb, proc_wb):
        if nums_close(raw.unit_price, proc.unit_price) and nums_close(raw.total_price, proc.total_price):
            continue
        if not nums_close(raw.pack_count, proc.pack_count):
            continue
        dedupe = (sheet, str(raw.ship_no), raw.pack_name, f"{raw.pack_count or 0:.4g}")
        if dedupe in seen:
            continue
        seen.add(dedupe)
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
                note=price_note(raw, proc, "单价"),
            )
        )
    return expected, "ok"


def backscan_hospital(name: str) -> BackscanResult:
    hospital_dir = TEST_CASE_DIR / name
    result = BackscanResult(hospital=name)

    if not hospital_dir.is_dir():
        result.notes = "目录不存在"
        result.all_zero = False
        return result

    for month in SCAN_MONTHS:
        rows, note = extract_for_month(hospital_dir, month)
        if note != "ok" and not rows:
            continue
        if rows:
            result.first_diff_month = month
            result.diff_count = len(rows)
            result.all_zero = False
            out = hospital_dir / f"{month}月期待价格校正清单.csv"
            with out.open("w", encoding="utf-8-sig", newline="") as f:
                w = csv.writer(f)
                w.writerow([
                    "科室", "原始行", "发货单号", "包名", "包数",
                    "原单价", "处理后单价", "原总价", "处理后总价", "说明",
                ])
                for r in rows:
                    w.writerow([
                        r.sheet, r.raw_row or "", r.ship_no, r.pack_name, r.pack_count or "",
                        r.raw_unit, r.proc_unit, r.raw_total, r.proc_total, r.note,
                    ])
            result.notes = f"在{month}月发现 {len(rows)} 条价格差异"
            return result

    result.notes = "6月及回溯至1月均为零差异"
    return result


def main() -> int:
    results = [backscan_hospital(n) for n in ZERO_DIFF_JUNE]
    lines = [
        "# 零差异医院逐月回溯报告",
        "",
        f"> 生成日期：{date.today().isoformat()}",
        "> 对象：6月零差异的 16 家医院，依次对比 5月→4月→3月→2月→1月",
        "",
        "| 医院 | 首次出现差异月份 | 差异条数 | 结论 |",
        "|------|----------------|---------|------|",
    ]
    found = []
    for r in results:
        month = f"{r.first_diff_month}月" if r.first_diff_month else "—"
        lines.append(f"| {r.hospital} | {month} | {r.diff_count} | {r.notes} |")
        if r.first_diff_month:
            found.append(r)

    lines.extend(["", "## 摘要", ""])
    if found:
        lines.append(f"- **回溯发现差异**：{len(found)} 家")
        for r in found:
            lines.append(f"  - {r.hospital}：{r.notes} → `{r.first_diff_month}月期待价格校正清单.csv`")
    else:
        lines.append("- **全部 16 家**在 1–6 月均无价格差异（原始与处理后完全一致）")

    text = "\n".join(lines)
    OUTPUT.write_text(text, encoding="utf-8")
    print(text)
    print(f"\nWritten: {OUTPUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
