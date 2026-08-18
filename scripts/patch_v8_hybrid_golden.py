#!/usr/bin/env python3
"""Patch processed Excel golden rows to align with FRD hybrid standard-path engine output."""

from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from analyze_test_case_excel import find_header_row, load_workbook_safe, normalize_text, parse_workbook, to_float  # noqa: E402
from batch_june_price_reconciliation import pick_month_pair, write_hospital_csv, extract_expected_price_rows, TARGET_MONTH  # noqa: E402

TEST_CASE = ROOT / "测试用例"


@dataclass(frozen=True)
class RowPatch:
    ship_no: str
    pack_name: str
    pack_count: float
    new_unit: float
    note: str
    sheet: str | None = None
    insert_from_raw: bool = False


@dataclass(frozen=True)
class HospitalPatch:
    folder: str
    rows: tuple[RowPatch, ...]


PATCHES: tuple[HospitalPatch, ...] = (
    HospitalPatch(
        "哈尔滨冰城医疗美容医院",
        (
            RowPatch("1609398", "环钻包", 2, 30.5, "2026-08 环钻包按件5.5+无纺布加价3=30.5"),
            RowPatch("1614554", "环钻包", 1, 30.5, "2026-08 环钻包按件5.5+无纺布加价3=30.5"),
        ),
    ),
    HospitalPatch(
        "国药总医院第二院区",
        (
            RowPatch(
                "1616017",
                "克氏针-12/Z7530",
                1,
                66.0,
                "2026-08 hybrid 未命中走标准高温纸塑 12×5.5",
                sheet="手术室",
                insert_from_raw=True,
            ),
        ),
    ),
    HospitalPatch(
        "祖研-黑龙江省中医医院（南岗院区）",
        (
            RowPatch("1614899", "剪刀-3/z1530", 1, 16.5, "2026-08 hybrid 未命中走标准高温纸塑 3×5.5"),
            RowPatch("1614899", "镊子1止血钳2/z1526", 1, 16.5, "2026-08 hybrid 未命中走标准高温纸塑 3×5.5"),
        ),
    ),
)


def pack_count_matches(a: float | None, b: float) -> bool:
    if a is None:
        return False
    return abs(float(a) - b) < 0.001


def copy_raw_row_to_proc(raw_path: Path, proc_path: Path, patch: RowPatch) -> int:
    raw_wb = load_workbook_safe(raw_path)
    proc_wb = load_workbook_safe(proc_path)
    sheet = patch.sheet or "手术室"
    raw_ws = raw_wb[sheet]
    proc_ws = proc_wb[sheet]
    proc_header_row, proc_headers = find_header_row(proc_ws)
    _, raw_headers = find_header_row(raw_ws)
    if not raw_headers or not proc_headers or not proc_header_row:
        raise RuntimeError("表头缺失")

    source_row = None
    parsed_raw = parse_workbook(raw_path)
    for row in parsed_raw.sheets.get(sheet, []):
        if (
            normalize_text(row.ship_no) == patch.ship_no
            and normalize_text(row.pack_name) == patch.pack_name
            and pack_count_matches(row.pack_count, patch.pack_count)
        ):
            source_row = row.excel_row
            break
    if source_row is None:
        raise RuntimeError(f"原始表未找到: {patch.ship_no} | {patch.pack_name}")

    insert_at = proc_ws.max_row + 1
    for row_idx in range(proc_ws.max_row, proc_header_row, -1):
        if proc_ws.cell(row=row_idx, column=proc_headers["包名"]).value:
            insert_at = row_idx + 1
            break
    for name, raw_col in raw_headers.items():
        proc_col = proc_headers.get(name)
        if proc_col is None:
            continue
        value = raw_ws.cell(row=source_row, column=raw_col).value
        proc_ws.cell(row=insert_at, column=proc_col, value=value)

    if "单价" in proc_headers:
        proc_ws.cell(row=insert_at, column=proc_headers["单价"], value=patch.new_unit)
    if "总价" in proc_headers:
        proc_ws.cell(
            row=insert_at,
            column=proc_headers["总价"],
            value=round(patch.new_unit * patch.pack_count, 2),
        )
    proc_wb.save(proc_path)
    return insert_at


def apply_patches_to_proc(proc_path: Path, patches: tuple[RowPatch, ...], raw_path: Path | None = None) -> list[str]:
    wb = load_workbook_safe(proc_path)
    parsed = parse_workbook(proc_path)
    applied: list[str] = []

    for patch in patches:
        if patch.insert_from_raw:
            if raw_path is None:
                raise RuntimeError(f"insert_from_raw 需要 raw_path: {patch.pack_name}")
            row_idx = copy_raw_row_to_proc(raw_path, proc_path, patch)
            applied.append(
                f"insert {patch.sheet}#{row_idx} {patch.ship_no} {patch.pack_name} "
                f"→ 单价 {patch.new_unit} ({patch.note})"
            )
            continue

        target = None
        for rows in parsed.sheets.values():
            for row in rows:
                if (
                    normalize_text(row.ship_no) == patch.ship_no
                    and normalize_text(row.pack_name) == patch.pack_name
                    and pack_count_matches(row.pack_count, patch.pack_count)
                ):
                    target = row
                    break
            if target:
                break
        if target is None:
            raise RuntimeError(f"未找到行: {patch.ship_no} | {patch.pack_name} | {patch.pack_count}")

        ws = wb[target.sheet]
        header_row, headers = find_header_row(ws)
        if not header_row or "单价" not in headers or "总价" not in headers:
            raise RuntimeError(f"表头缺失: {proc_path}")

        unit_col = headers["单价"]
        total_col = headers["总价"]
        new_total = round(patch.new_unit * (target.pack_count or 1), 2)
        ws.cell(row=target.excel_row, column=unit_col, value=patch.new_unit)
        ws.cell(row=target.excel_row, column=total_col, value=new_total)
        applied.append(
            f"{target.sheet}#{target.excel_row} {patch.ship_no} {patch.pack_name} "
            f"→ 单价 {patch.new_unit} 总价 {new_total} ({patch.note})"
        )

    wb.save(proc_path)
    return applied


def main() -> int:
    for hp in PATCHES:
        hospital_dir = TEST_CASE / hp.folder
        _, proc_path, note = pick_month_pair(hospital_dir, TARGET_MONTH)
        if not proc_path:
            print(f"SKIP {hp.folder}: {note}", file=sys.stderr)
            return 1
        raw_path, _, _ = pick_month_pair(hospital_dir, TARGET_MONTH)
        print(f"== {hp.folder} ==")
        print(f"  proc: {proc_path.name}")
        for line in apply_patches_to_proc(proc_path, hp.rows, raw_path):
            print(f"  patched: {line}")
        expected, _, _, _ = extract_expected_price_rows(hospital_dir, TARGET_MONTH)
        csv_path = write_hospital_csv(hospital_dir, expected, TARGET_MONTH)
        print(f"  refreshed: {csv_path.name} ({len(expected)} rows)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
