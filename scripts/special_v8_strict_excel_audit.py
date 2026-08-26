#!/usr/bin/env python3
"""Strict Excel audit for special-pricing v8 hospitals.

Compare three row sets:
  E — expected corrections from raw vs processed Excel diff
  W — pricing-related import warnings (status=warning), excluding field-consistency-only
  P — processed Excel unit prices (ground-truth self-check)

PASS iff E.keys == W.keys and all unit prices within target (strict < 0.01 yuan → ERROR).
Field-consistency warnings (包材名称/器械数 vs 包名) do not count as 多报.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from dataclasses import asdict, dataclass, field
from datetime import date
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE_DIR = ROOT / "测试用例"
MANIFEST_PATH = TEST_CASE_DIR / "814新增入库清单.json"
BACKEND = "hospital-backend"
API = "http://127.0.0.1:8000"

sys.path.insert(0, str(ROOT / "scripts"))
from batch_june_price_reconciliation import (  # noqa: E402
    TARGET_MONTH,
    ExpectedPriceRow,
    audit_hospital,
    expected_csv_name,
    extract_expected_price_rows,
    iter_compare_pairs,
    load_seed_profiles,
    parse_workbook,
    pick_month_pair,
)
from batch_june_system_test import fetch_warnings, import_bill, init_api_from_args  # noqa: E402
from ingest_bokang_814_batch import load_manifest, strict_july_hospitals  # noqa: E402
from lib.api_client import configure_client, get_client  # noqa: E402


@dataclass(frozen=True)
class V8Hospital:
    customer_label: str
    folder: str | None
    testable: bool
    skip_reason: str = ""


V8_HOSPITALS: list[V8Hospital] = [
    V8Hospital("冰城医美", "哈尔滨冰城医疗美容医院", True),
    V8Hospital("电机厂", "国药总医院第二院区", True),
    V8Hospital("方南南", "方南南医院", True),
    V8Hospital("东北农大", "东北农业大学", True),
    V8Hospital("市五院主院区", "哈尔滨市第五医院", False, "ground truth 陈旧待更新（标准包装/特色费未反映）"),
    V8Hospital("松电慢病", "松电慢病", False, "无 6/7 月 raw+proc 成对"),
    V8Hospital("航天风华", "航天风华", False, "无 6/7 月 raw+proc 成对"),
    V8Hospital("市五院二门诊", "哈尔滨市第五医院（二门诊）", True),
    V8Hospital("九州", "黑龙江九洲妇科医院", True),
    V8Hospital("博尚", "博尚医院", False, "无 6/7 月 raw+proc 成对"),
    V8Hospital("海员松北", "黑龙江省海员总医院（松北）", True),
    V8Hospital("省妇幼人口", "黑龙江省妇幼保健院（人口）", True),
    V8Hospital("祖研南岗", "祖研-黑龙江省中医医院（南岗院区）", True),
    V8Hospital("社会康复", "黑龙江省社会康复医院", True),
    V8Hospital("道里妇幼", "道里区妇幼保健院", False, "无 6/7 月 raw+proc 成对"),
    V8Hospital("春语医美", "春语医美", True),
    V8Hospital("总工会", "总工会", True),
    V8Hospital("基准生物", "基准生物", False, "无原始表格"),
    V8Hospital("索菲医美", "索菲医美", True),
    V8Hospital("省监狱管理局", "省监狱管理局医院", True),
    V8Hospital("呼兰中医", "呼兰中医院", False, "ground truth 陈旧待更新（低温纸塑袋费未反映）"),
    V8Hospital("平房区人民", "哈尔滨市平房区人民医院", True),
]

V8_TESTABLE_FOLDERS: list[str] = [h.folder for h in V8_HOSPITALS if h.testable and h.folder]
V8_BY_FOLDER: dict[str, V8Hospital] = {h.folder: h for h in V8_HOSPITALS if h.folder}
V8_BY_LABEL: dict[str, V8Hospital] = {h.customer_label: h for h in V8_HOSPITALS}

PRICE_ERROR_TOLERANCE = Decimal("0.01")

FIELD_CONSISTENCY_CODES = frozenset({
    "BAG_SIZE_MISMATCH",
    "MATERIAL_CLASS_MISMATCH",
    "INSTRUMENT_COUNT_MISMATCH",
})


@dataclass
class DiffRow:
    diff_type: str
    sheet: str
    ship_no: str
    pack_name: str
    pack_count: float | None
    raw_unit: float | None = None
    proc_unit: float | None = None
    system_unit: float | None = None
    proc_verify_unit: float | None = None
    pricing_rule: str = ""
    key: str = ""


@dataclass
class HospitalStrictResult:
    hospital: str
    customer_label: str
    status: str = "pending"
    message: str = ""
    job_id: int | None = None
    raw_file: str = ""
    proc_file: str = ""
    expected_count: int = 0
    warning_count: int = 0
    pricing_warning_count: int = 0
    non_pricing_warning_ignored: int = 0
    matched_count: int = 0
    missed: list[DiffRow] = field(default_factory=list)
    extra: list[DiffRow] = field(default_factory=list)
    price_mismatch: list[DiffRow] = field(default_factory=list)
    proc_mismatch: list[DiffRow] = field(default_factory=list)
    dedupe_note: str = ""
    fully_aligned: bool = False
    month: int = TARGET_MONTH
    section: str = ""


def to_decimal(value: Any) -> Decimal | None:
    if value is None or value == "":
        return None
    try:
        return Decimal(str(value)).quantize(Decimal("0.01"))
    except (InvalidOperation, ValueError, TypeError):
        return None


def price_diff(a: Any, b: Any) -> Decimal | None:
    da, db = to_decimal(a), to_decimal(b)
    if da is None or db is None:
        return None
    return abs(da - db)


def has_pricing_error(a: Any, b: Any) -> bool:
    diff = price_diff(a, b)
    return diff is not None and diff >= PRICE_ERROR_TOLERANCE


def parse_billing_notes(row: dict[str, Any]) -> dict[str, Any]:
    notes = row.get("billingNotes") or row.get("billing_notes") or {}
    if isinstance(notes, str):
        try:
            notes = json.loads(notes)
        except json.JSONDecodeError:
            notes = {}
    return notes if isinstance(notes, dict) else {}


def field_consistency_violation_codes(billing_notes: dict[str, Any]) -> set[str]:
    fc = billing_notes.get("fieldConsistency")
    if isinstance(fc, dict):
        violations = fc.get("violations") or []
    elif billing_notes.get("type") == "field_consistency":
        violations = billing_notes.get("violations") or []
    else:
        return set()
    codes: set[str] = set()
    for item in violations:
        if isinstance(item, dict) and item.get("code"):
            codes.add(str(item["code"]))
    return codes


def is_field_consistency_only_warning(row: dict[str, Any]) -> bool:
    """Warnings driven only by 包材/器械字段核对，与计价规则偏差无关。"""
    codes = field_consistency_violation_codes(parse_billing_notes(row))
    if not codes or not codes.issubset(FIELD_CONSISTENCY_CODES):
        return False
    unit = row.get("unitPrice")
    expected = row.get("expectedUnitPrice")
    if unit is None or expected is None:
        return True
    diff = price_diff(unit, expected)
    return diff is None or diff < PRICE_ERROR_TOLERANCE


def should_exclude_from_pricing_warnings(row: dict[str, Any]) -> bool:
    """Exclude warnings unrelated to pricing rules (field consistency or no unit delta ≥0.01)."""
    if is_field_consistency_only_warning(row):
        return True
    unit = row.get("unitPrice")
    expected = row.get("expectedUnitPrice")
    if unit is not None and expected is not None and not has_pricing_error(unit, expected):
        return True
    return False


def decimal_exact(a: Any, b: Any) -> bool:
    da, db = to_decimal(a), to_decimal(b)
    if da is None and db is None:
        return True
    if da is None or db is None:
        return False
    return da == db


def pack_count_norm(pack_count: float | int | None) -> str:
    pc = pack_count if pack_count is not None else 0
    try:
        return f"{float(pc):.4g}"
    except (TypeError, ValueError):
        return str(pc)


def compare_key(ship_no: str, pack_name: str, pack_count: float | int | None) -> str:
    """附一/814 口径：发货单 + 包名 + 包数（科室不参与 E/W 对齐）。"""
    return f"{ship_no}|{pack_name}|{pack_count_norm(pack_count)}"


def strict_key(sheet: str, ship_no: str, pack_name: str, pack_count: float | int | None) -> str:
    return compare_key(ship_no, pack_name, pack_count)


def resolve_hospital(name: str) -> tuple[V8Hospital | None, str | None]:
    if name in V8_BY_FOLDER:
        h = V8_BY_FOLDER[name]
        return h, h.folder
    if name in V8_BY_LABEL:
        h = V8_BY_LABEL[name]
        return h, h.folder
    # 精确目录名优先：避免 "哈尔滨市第五医院" 被模糊匹配到 "（二门诊）"
    if (TEST_CASE_DIR / name).is_dir():
        return None, name
    for h in V8_HOSPITALS:
        if h.folder and name in h.folder:
            return h, h.folder
        if name in h.customer_label:
            return h, h.folder
    return None, folder_for_non_v8(name)


def folder_for_non_v8(name: str) -> str | None:
    if (TEST_CASE_DIR / name).is_dir():
        return name
    return None


def build_proc_price_map(hospital_dir: Path, month: int) -> dict[str, Decimal | None]:
    raw_path, proc_path, _ = pick_month_pair(hospital_dir, month)
    if not raw_path or not proc_path:
        return {}
    raw_wb = parse_workbook(raw_path)
    proc_wb = parse_workbook(proc_path)
    proc_map: dict[str, Decimal | None] = {}
    for sheet, raw, proc in iter_compare_pairs(raw_wb, proc_wb):
        key = strict_key(sheet, str(raw.ship_no), raw.pack_name, raw.pack_count)
        proc_map[key] = to_decimal(proc.unit_price)
    return proc_map


def expected_entry(row: ExpectedPriceRow) -> tuple[str, dict[str, Any]]:
    key = strict_key(row.sheet, row.ship_no, row.pack_name, row.pack_count)
    return key, {
        "sheet": row.sheet,
        "ship_no": row.ship_no,
        "pack_name": row.pack_name,
        "pack_count": row.pack_count,
        "raw_unit": row.raw_unit,
        "proc_unit": row.proc_unit,
    }


def warning_entry(w: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    sheet = str(w.get("sheetName") or "")
    ship_no = str(w.get("orderNo") or "")
    pack_name = str(w.get("packName") or "")
    pack_count = w.get("packCount")
    key = strict_key(sheet, ship_no, pack_name, pack_count)
    return key, {
        "sheet": sheet,
        "ship_no": ship_no,
        "pack_name": pack_name,
        "pack_count": pack_count,
        "system_unit": w.get("expectedUnitPrice"),
        "raw_unit": w.get("unitPrice"),
        "pricing_rule": str(w.get("pricingRule") or ""),
        "field_consistency_only": is_field_consistency_only_warning(w),
    }


def diff_from_expected(key: str, meta: dict[str, Any], diff_type: str) -> DiffRow:
    return DiffRow(
        diff_type=diff_type,
        key=key,
        sheet=meta["sheet"],
        ship_no=meta["ship_no"],
        pack_name=meta["pack_name"],
        pack_count=meta.get("pack_count"),
        raw_unit=meta.get("raw_unit"),
        proc_unit=meta.get("proc_unit"),
        system_unit=meta.get("system_unit"),
        proc_verify_unit=meta.get("proc_verify_unit"),
        pricing_rule=meta.get("pricing_rule", ""),
    )


def audit_hospital_strict(token: str, folder_name: str, *, month: int = TARGET_MONTH) -> HospitalStrictResult:
    meta = V8_BY_FOLDER.get(folder_name)
    result = HospitalStrictResult(
        hospital=folder_name,
        customer_label=meta.customer_label if meta else folder_name,
        month=month,
    )
    hospital_dir = TEST_CASE_DIR / folder_name

    if not hospital_dir.is_dir():
        result.status = "SKIP"
        result.message = "测试用例目录不存在"
        return result

    expected_rows, raw_path, proc_path, pair_note = extract_expected_price_rows(hospital_dir, month)
    if not raw_path or not proc_path:
        # 自动 fallback：主月份无配对时试另一月份（部分院只有单月 raw+proc 成对）
        fallback_month = 7 if month == TARGET_MONTH else TARGET_MONTH
        fb_rows, fb_raw, fb_proc, fb_note = extract_expected_price_rows(hospital_dir, fallback_month)
        if fb_raw and fb_proc:
            month = fallback_month
            result.month = month
            expected_rows, raw_path, proc_path, pair_note = fb_rows, fb_raw, fb_proc, fb_note
        else:
            result.status = "SKIP"
            result.message = pair_note
            return result

    result.raw_file = raw_path.name
    result.proc_file = proc_path.name
    result.expected_count = len(expected_rows)

    e_map: dict[str, dict[str, Any]] = {}
    for row in expected_rows:
        key, meta_row = expected_entry(row)
        if key in e_map:
            result.dedupe_note = "期待集合存在 strict key 重复（已保留首条）"
        else:
            e_map[key] = meta_row

    proc_prices = build_proc_price_map(hospital_dir, month)
    for key, meta_row in e_map.items():
        proc_val = proc_prices.get(key)
        meta_row["proc_verify_unit"] = float(proc_val) if proc_val is not None else None
        if not decimal_exact(meta_row.get("proc_unit"), proc_val):
            result.proc_mismatch.append(diff_from_expected(key, meta_row, "PROC_MISMATCH"))

    try:
        job = import_bill(token, folder_name, raw_path)
        result.job_id = job.get("id")
        time.sleep(0.5)
        warnings = fetch_warnings(token, result.job_id)
    except Exception as exc:
        result.status = "ERROR"
        result.message = str(exc)
        return result

    result.warning_count = len(warnings)
    w_map: dict[str, dict[str, Any]] = {}
    for w in warnings:
        if should_exclude_from_pricing_warnings(w):
            result.non_pricing_warning_ignored += 1
            continue
        key, meta_row = warning_entry(w)
        if key in w_map:
            result.dedupe_note = result.dedupe_note or "warning 集合存在 strict key 重复（已保留首条）"
        else:
            w_map[key] = meta_row
    result.pricing_warning_count = len(w_map)

    e_keys = set(e_map)
    w_keys = set(w_map)
    matched_keys = e_keys & w_keys
    result.matched_count = len(matched_keys)

    for key in sorted(e_keys - w_keys):
        result.missed.append(diff_from_expected(key, e_map[key], "MISSED"))

    for key in sorted(w_keys - e_keys):
        result.extra.append(diff_from_expected(key, w_map[key], "EXTRA"))

    for key in sorted(matched_keys):
        e_meta = e_map[key]
        w_meta = w_map[key]
        proc_unit = e_meta.get("proc_unit")
        system_unit = w_meta.get("system_unit")
        proc_verify = e_meta.get("proc_verify_unit")
        if has_pricing_error(system_unit, proc_unit) or has_pricing_error(system_unit, proc_verify):
            merged = {**e_meta, **w_meta}
            result.price_mismatch.append(diff_from_expected(key, merged, "PRICE_ERROR"))

    has_pricing_error_rows = bool(result.missed or result.price_mismatch or result.proc_mismatch)
    has_extra = bool(result.extra)
    result.fully_aligned = not has_pricing_error_rows and not has_extra and len(e_map) == len(w_map)

    if result.expected_count == 0 and result.pricing_warning_count == 0:
        result.status = "PASS"
        msg = "零期待校正，零计价 warning"
        if result.non_pricing_warning_ignored:
            msg += f"（忽略非计价 warning {result.non_pricing_warning_ignored} 条）"
        result.message = msg
    elif result.fully_aligned:
        result.status = "PASS"
        unique_warnings = len(w_map)
        if result.warning_count != unique_warnings:
            result.dedupe_note = result.dedupe_note or (
                f"warning 行 {result.warning_count} 条，计价相关 strict key 去重后 {unique_warnings} 条"
            )
        result.message = (
            f"完全对应：{result.expected_count} 条，目标单价偏差 < {PRICE_ERROR_TOLERANCE} 元"
        )
        if result.non_pricing_warning_ignored:
            result.message += f"；忽略非计价 warning {result.non_pricing_warning_ignored} 条"
    elif has_pricing_error_rows:
        result.status = "ERROR"
        parts = []
        if result.missed:
            parts.append(f"漏检 {len(result.missed)}")
        if result.price_mismatch:
            parts.append(f"计价误差≥{PRICE_ERROR_TOLERANCE} {len(result.price_mismatch)}")
        if result.proc_mismatch:
            parts.append(f"ground truth 自洽失败 {len(result.proc_mismatch)}")
        if result.extra:
            parts.append(f"多报 {len(result.extra)}")
        result.message = "；".join(parts)
    else:
        result.status = "FAIL"
        parts = []
        if result.extra:
            parts.append(f"多报 {len(result.extra)}")
        result.message = "；".join(parts) if parts else "条目或单价未完全对应"
        if result.non_pricing_warning_ignored:
            result.message += f"；忽略非计价 warning {result.non_pricing_warning_ignored} 条"

    return result


def skip_result(h: V8Hospital, *, month: int = TARGET_MONTH, section: str = "") -> HospitalStrictResult:
    return HospitalStrictResult(
        hospital=h.folder or h.customer_label,
        customer_label=h.customer_label,
        status="SKIP",
        message=h.skip_reason or "无法严格对账",
        month=month,
        section=section,
    )


def skip_folder(name: str, reason: str, *, month: int = 7, section: str = "july") -> HospitalStrictResult:
    meta = V8_BY_FOLDER.get(name)
    return HospitalStrictResult(
        hospital=name,
        customer_label=meta.customer_label if meta else name,
        status="SKIP",
        message=reason,
        month=month,
        section=section,
    )


def batch814_skip_results() -> list[HospitalStrictResult]:
    data = load_manifest()
    strict_set = set(data.get("strict_july_folders") or strict_july_hospitals())
    seen: set[str] = set()
    skips: list[HospitalStrictResult] = []
    for entry in data.get("entries", []):
        hospital = entry.get("hospital")
        if not hospital or hospital == "待匹配" or hospital in strict_set or hospital in seen:
            continue
        seen.add(hospital)
        skips.append(
            skip_folder(
                hospital,
                entry.get("note") or "814 入库但不可 7 月 strict 成对",
                month=7,
                section="july",
            )
        )
    pop_dir = TEST_CASE_DIR / "黑龙江省妇幼保健院（人口）"
    raw7 = pop_dir / "原始表格" / "7月__人口原始.xlsx"
    proc7 = list((pop_dir / "处理后表格").glob("7月__*.xlsx")) if (pop_dir / "处理后表格").is_dir() else []
    if raw7.is_file() and not proc7:
        skips.append(
            skip_folder(
                "黑龙江省妇幼保健院（人口）",
                "有 7 月原始，缺 7 月处理后",
                month=7,
                section="july",
            )
        )
    return skips


def render_section_table(results: list[HospitalStrictResult]) -> list[str]:
    lines = [
        "| 客户名 | 测试目录 | 状态 | E | W(计价) | 非计价忽略 | 漏检 | 多报 | 计价误差 | 完全对应 | Job |",
        "|--------|---------|------|---|---------|------------|------|------|----------|---------|-----|",
    ]
    for r in results:
        fully = "是" if r.fully_aligned else ("—" if r.status == "SKIP" else "否")
        if r.status == "SKIP":
            lines.append(
                f"| {r.customer_label} | {r.hospital} | SKIP | - | - | - | - | - | {r.message} | - |"
            )
            continue
        job = str(r.job_id) if r.job_id else "-"
        lines.append(
            f"| {r.customer_label} | {r.hospital} | {r.status} | {r.expected_count} | "
            f"{r.pricing_warning_count} | {r.non_pricing_warning_ignored} | {len(r.missed)} | "
            f"{len(r.extra)} | {len(r.price_mismatch)} | "
            f"{fully} | {job} |"
        )
    return lines


def render_markdown(
    sections: list[tuple[str, list[HospitalStrictResult]]],
    *,
    generated: str,
    api_base: str,
) -> str:
    all_results = [r for _, rs in sections for r in rs]
    pass_count = sum(1 for r in all_results if r.status == "PASS")
    fail_count = sum(1 for r in all_results if r.status == "FAIL")
    skip_count = sum(1 for r in all_results if r.status == "SKIP")
    error_count = sum(1 for r in all_results if r.status == "ERROR")

    lines = [
        "# 814 新增严格 Excel 对账报告",
        "",
        f"> 生成日期：{generated}",
        f"> API：`{api_base}`",
        "> 判定：E/W/P 三方比对；W 仅含计价 correction（单价偏差≥0.01）；"
        "包材/器械字段核对及同价 warning 不计入多报；"
        f"目标单价偏差 ≥ {PRICE_ERROR_TOLERANCE} 元 → ERROR",
        "",
        "## 总览",
        "",
        f"- PASS：**{pass_count}**  FAIL：**{fail_count}**  SKIP：**{skip_count}**  ERROR：**{error_count}**",
        "",
    ]

    section_titles = {
        "july": "## §1 7 月（814 新增）",
        "june": "## §2 6 月（既有 v8 可测院）",
    }
    for key, results in sections:
        lines.extend(["", section_titles.get(key, f"## {key}"), ""])
        lines.extend(render_section_table(results))

    untestable = [h for h in V8_HOSPITALS if not h.testable]
    lines.extend(["", "## §3 13 院缺材料 / 无法测清单", ""])
    lines.append("| # | 客户名 | 原因 |")
    lines.append("|---|--------|------|")
    for i, h in enumerate(untestable, 1):
        lines.append(f"| {i} | {h.customer_label} | {h.skip_reason} |")

    detail_results = [
        r for r in all_results
        if r.status in {"FAIL", "ERROR"} or r.missed or r.extra or r.price_mismatch
    ]
    if detail_results:
        lines.extend(["", "## §4 差异明细附录", ""])
        for r in detail_results:
            if r.status == "SKIP":
                continue
            lines.append(f"### {r.customer_label}（{r.hospital}）— {r.status} · {r.month}月")
            if r.message:
                lines.append("")
                lines.append(f"- {r.message}")
            if r.dedupe_note:
                lines.append(f"- dedupe：{r.dedupe_note}")
            lines.append("")

            def append_diff_table(title: str, rows: list[DiffRow]) -> None:
                if not rows:
                    return
                lines.append(f"#### {title}（{len(rows)} 条）")
                lines.append("")
                lines.append(
                    "| 类型 | 科室 | 发货单号 | 包名 | 包数 | 原单价 | 处理后 | 系统 ruleUnit | 说明 |"
                )
                lines.append("|------|------|---------|------|------|--------|--------|--------------|------|")
                for d in rows:
                    lines.append(
                        f"| {d.diff_type} | {d.sheet} | {d.ship_no} | {d.pack_name} | "
                        f"{d.pack_count or ''} | {d.raw_unit or ''} | {d.proc_unit or ''} | "
                        f"{d.system_unit or ''} | {d.pricing_rule or ''} |"
                    )
                lines.append("")

            append_diff_table("漏检 missed", r.missed)
            append_diff_table("多报 extra", r.extra)
            append_diff_table("计价误差 price_error (≥0.01)", r.price_mismatch)
            append_diff_table("ground truth 自洽 proc_mismatch", r.proc_mismatch)

    return "\n".join(lines) + "\n"


def result_to_dict(r: HospitalStrictResult) -> dict[str, Any]:
    data = asdict(r)
    data["fully_aligned"] = r.fully_aligned
    return data


def write_reports(
    sections: list[tuple[str, list[HospitalStrictResult]]],
    *,
    api_base: str,
    out_date: str | None = None,
    report_prefix: str = "814新增严格Excel对账报告",
) -> tuple[Path, Path]:
    day = out_date or date.today().strftime("%Y%m%d")
    md_path = TEST_CASE_DIR / f"{report_prefix}-{day}.md"
    json_path = TEST_CASE_DIR / f"{report_prefix}-{day}.json"
    all_results = [r for _, rs in sections for r in rs]

    payload = {
        "generated": date.today().isoformat(),
        "api_base": api_base,
        "summary": {
            "pass": sum(1 for r in all_results if r.status == "PASS"),
            "fail": sum(1 for r in all_results if r.status == "FAIL"),
            "skip": sum(1 for r in all_results if r.status == "SKIP"),
            "error": sum(1 for r in all_results if r.status == "ERROR"),
        },
        "sections": {key: [result_to_dict(r) for r in results] for key, results in sections},
        "v8_hospitals": [asdict(h) for h in V8_HOSPITALS],
    }

    md_path.write_text(
        render_markdown(sections, generated=payload["generated"], api_base=api_base),
        encoding="utf-8",
    )
    json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return md_path, json_path


def refresh_expected_csv(folder_names: list[str], month: int) -> None:
    profiles = load_seed_profiles()
    for name in folder_names:
        audit_hospital(name, profiles, month=month)
        print(f"已刷新 CSV: {TEST_CASE_DIR / name / expected_csv_name(month)}")


def run_section_audit(
    *,
    hospitals: list[str],
    month: int,
    section: str,
    api_base: str,
    refresh_csv: bool,
    include_v8_skips: bool = False,
    include_814_skips: bool = False,
    restrict_v8_testable: bool = False,
) -> list[HospitalStrictResult]:
    if refresh_csv:
        refresh_expected_csv(hospitals, month)

    init_api_from_args(argparse.Namespace(mode="docker", api_base=api_base))
    token = get_client().login()

    results: list[HospitalStrictResult] = []
    audited: set[str] = set()

    for name in hospitals:
        meta, folder = resolve_hospital(name)
        target = folder or name
        if target in audited:
            continue
        audited.add(target)
        if restrict_v8_testable and target not in V8_TESTABLE_FOLDERS:
            results.append(
                HospitalStrictResult(
                    hospital=target,
                    customer_label=meta.customer_label if meta else target,
                    status="SKIP",
                    message="不在 v8 可测清单",
                    month=month,
                    section=section,
                )
            )
            continue
        if meta and not meta.testable and restrict_v8_testable:
            if include_v8_skips:
                r = skip_result(meta, month=month, section=section)
                results.append(r)
            continue
        r = audit_hospital_strict(token, target, month=month)
        r.section = section
        results.append(r)
        print(f"\n== 严格对账 ({r.month}月): {target} ==")
        print(f"  {r.status}: {r.message} (E={r.expected_count}, W={r.warning_count})")

    if include_v8_skips:
        for h in V8_HOSPITALS:
            if not h.testable and h.folder and h.folder not in audited:
                results.append(skip_result(h, month=month, section=section))
    if include_814_skips:
        for r in batch814_skip_results():
            if r.hospital not in audited:
                results.append(r)

    return results


def parse_report_sections(raw: str | None) -> list[str]:
    if not raw:
        return []
    return [p.strip().lower() for p in raw.split(",") if p.strip()]


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="特殊收费 v8 严格 Excel 对账（E/W/P，Decimal 零容差）")
    p.add_argument("--mode", choices=["docker", "direct"], default="docker")
    p.add_argument("--api-base", default=API)
    p.add_argument("--username", default=None)
    p.add_argument("--password", default=None)
    p.add_argument("--month", type=int, default=TARGET_MONTH, help="账期月份，默认 6")
    p.add_argument("--batch", choices=["814"], help="814 新增批次（7 月 strict 可测院）")
    p.add_argument(
        "--report-sections",
        help="合并报告章节，逗号分隔，如 july,june",
    )
    p.add_argument("--hospital", action="append", default=[], help="测试用例目录名或 v8 客户简称")
    p.add_argument("--all-v8-testable", action="store_true", help="批量跑 v8 可测院")
    p.add_argument("--all-v8", action="store_true", help="20 院全量（含 SKIP 清单）")
    p.add_argument("--refresh-csv", action="store_true", help="审计前刷新 {month}月期待价格校正清单.csv")
    p.add_argument("--out-date", help="报告文件名日期 YYYYMMDD（默认今天）")
    p.add_argument("--fail-on-fail", action="store_true", help="任一 FAIL/ERROR 时 exit 1")
    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    configure_client(
        api_base=args.api_base,
        mode=args.mode,
        backend_container=BACKEND,
        username=args.username,
        password=args.password,
    )

    sections_spec = parse_report_sections(args.report_sections)
    sections: list[tuple[str, list[HospitalStrictResult]]] = []

    if sections_spec:
        if "july" in sections_spec or args.batch == "814":
            july_hospitals = strict_july_hospitals()
            sections.append(
                (
                    "july",
                    run_section_audit(
                        hospitals=july_hospitals,
                        month=7,
                        section="july",
                        api_base=args.api_base,
                        refresh_csv=args.refresh_csv,
                        include_814_skips=True,
                    ),
                )
            )
        if "june" in sections_spec:
            sections.append(
                (
                    "june",
                    run_section_audit(
                        hospitals=V8_TESTABLE_FOLDERS.copy(),
                        month=6,
                        section="june",
                        api_base=args.api_base,
                        refresh_csv=args.refresh_csv,
                        restrict_v8_testable=True,
                    ),
                )
            )
    elif args.batch == "814":
        sections.append(
            (
                "july",
                run_section_audit(
                    hospitals=strict_july_hospitals(),
                    month=7,
                    section="july",
                    api_base=args.api_base,
                    refresh_csv=args.refresh_csv,
                    include_814_skips=True,
                ),
            )
        )
    elif args.all_v8:
        sections.append(
            (
                "june",
                run_section_audit(
                    hospitals=V8_TESTABLE_FOLDERS.copy(),
                    month=args.month,
                    section="june",
                    api_base=args.api_base,
                    refresh_csv=args.refresh_csv,
                    include_v8_skips=True,
                    restrict_v8_testable=True,
                ),
            )
        )
    elif args.all_v8_testable:
        sections.append(
            (
                "june",
                run_section_audit(
                    hospitals=V8_TESTABLE_FOLDERS.copy(),
                    month=args.month,
                    section="june",
                    api_base=args.api_base,
                    refresh_csv=args.refresh_csv,
                    restrict_v8_testable=True,
                ),
            )
        )
    elif args.hospital:
        sections.append(
            (
                f"month{args.month}",
                run_section_audit(
                    hospitals=list(args.hospital),
                    month=args.month,
                    section=f"month{args.month}",
                    api_base=args.api_base,
                    refresh_csv=args.refresh_csv,
                ),
            )
        )
    else:
        print("请指定 --hospital、--batch 814、--report-sections 或 --all-v8-testable", file=sys.stderr)
        return 2

    prefix = "814新增严格Excel对账报告" if len(sections) > 1 or args.batch == "814" else "特殊收费v8严格Excel对账报告"
    md_path, json_path = write_reports(sections, api_base=args.api_base, out_date=args.out_date, report_prefix=prefix)
    print(f"\n报告已写入:\n  {md_path}\n  {json_path}")

    all_results = [r for _, rs in sections for r in rs]
    fails = [r for r in all_results if r.status in {"FAIL", "ERROR"}]
    if fails:
        print(f"\n未通过: {len(fails)} 家")
        for r in fails:
            print(f"  - {r.customer_label} ({r.hospital}, {r.month}月): {r.message}")
    if args.fail_on_fail and fails:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
