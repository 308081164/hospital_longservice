#!/usr/bin/env python3
"""Import raw bills for SC11 SKIP hospitals and export warning details (no E/W strict compare)."""

from __future__ import annotations

import argparse
import csv
import json
import sys
import time
from dataclasses import asdict, dataclass, field
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE = ROOT / "测试用例"
sys.path.insert(0, str(ROOT / "scripts"))

from batch_june_price_reconciliation import pick_month_pair  # noqa: E402
from batch_june_system_test import fetch_warnings, import_bill, init_api_from_args  # noqa: E402
from lib.api_client import configure_client, get_client  # noqa: E402
from special_v8_strict_excel_audit import V8_HOSPITALS, skip_result  # noqa: E402


@dataclass
class RawProbeResult:
    customer_label: str
    folder: str | None
    status: str
    message: str = ""
    raw_file: str = ""
    month: int | None = None
    job_id: int | None = None
    warning_count: int = 0
    warnings: list[dict] = field(default_factory=list)


def list_raw_candidates(hospital_dir: Path) -> list[tuple[int, Path]]:
    raw_dir = hospital_dir / "原始表格"
    if not raw_dir.is_dir():
        return []
    out: list[tuple[int, Path]] = []
    for path in sorted(raw_dir.glob("*.xlsx")):
        name = path.name
        month = None
        for m in range(1, 13):
            if f"{m}月" in name or (m == 6 and "6." in name):
                month = m
                break
        out.append((month or 0, path))
    out.sort(key=lambda x: (-(x[0] == 6), -x[0], x[1].name))
    return out


def probe_hospital(token: str, meta, *, prefer_month: int) -> RawProbeResult:
    folder = meta.folder
    if not folder:
        return RawProbeResult(
            customer_label=meta.customer_label,
            folder=None,
            status="NO_FOLDER",
            message=meta.skip_reason or "无测试用例目录",
        )

    hospital_dir = TEST_CASE / folder
    if not hospital_dir.is_dir():
        return RawProbeResult(
            customer_label=meta.customer_label,
            folder=folder,
            status="NO_FOLDER",
            message="测试用例目录不存在",
        )

    _, proc_path, pair_note = pick_month_pair(hospital_dir, prefer_month)
    strict_skip = skip_result(meta, month=prefer_month).message

    candidates = list_raw_candidates(hospital_dir)
    if not candidates:
        return RawProbeResult(
            customer_label=meta.customer_label,
            folder=folder,
            status="NO_RAW",
            message=f"{strict_skip}；原始表格无 xlsx",
        )

    month, raw_path = candidates[0]
    for m, path in candidates:
        if m == prefer_month:
            month, raw_path = m, path
            break

    try:
        job = import_bill(token, folder, raw_path)
        job_id = job.get("id")
        time.sleep(0.5)
        warnings = fetch_warnings(token, job_id)
    except Exception as exc:
        return RawProbeResult(
            customer_label=meta.customer_label,
            folder=folder,
            status="ERROR",
            message=str(exc),
            raw_file=raw_path.name,
            month=month or None,
        )

    return RawProbeResult(
        customer_label=meta.customer_label,
        folder=folder,
        status="OK",
        message=f"严格对账 SKIP：{strict_skip}；已做原始单导入探测",
        raw_file=raw_path.name,
        month=month or None,
        job_id=job_id,
        warning_count=len(warnings),
        warnings=warnings,
    )


def write_warning_tsv(folder: str, job_id: int, warnings: list[dict]) -> Path:
    out_dir = TEST_CASE / folder
    out = out_dir / f"raw_probe_job{job_id}_warnings.tsv"
    fields = [
        "sheetName", "orderNo", "packName", "packCount", "unitPrice", "ruleUnit",
        "pricingRule", "status", "difference",
    ]
    with out.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields, delimiter="\t", extrasaction="ignore")
        w.writeheader()
        for row in warnings:
            w.writerow({
                "sheetName": row.get("sheetName") or row.get("sheet_name") or "",
                "orderNo": row.get("orderNo") or row.get("ship_no") or "",
                "packName": row.get("packName") or row.get("pack_name") or "",
                "packCount": row.get("packCount") or row.get("pack_count") or "",
                "unitPrice": row.get("unitPrice") or row.get("unit_price") or "",
                "ruleUnit": row.get("expectedUnitPrice") or row.get("expected_unit_price") or "",
                "pricingRule": row.get("pricingRule") or row.get("pricing_rule") or "",
                "status": row.get("status") or "",
                "difference": row.get("difference") or "",
            })
    return out


def render_markdown(results: list[RawProbeResult], *, prefer_month: int) -> str:
    lines = [
        "# SC11 SKIP 院原始单导入探测报告",
        "",
        f"> 生成日期：{date.today().isoformat()}",
        f"> 目标账期：{prefer_month} 月（无则回退最近原始单）",
        "> 说明：仅导入原始账单并导出系统 warning，**不做 E/W 严格对账**",
        "",
        "## 二十院缺漏说明",
        "",
        "- 主表 19 行 = 6 PASS + 13 SKIP（有测试目录但缺 strict 材料）",
        "- **第 20 家「省监狱管理局」**：Excel 有序号 20，但 `V8_HOSPITALS.folder=None`，",
        "  严格对账脚本 `include_v8_skips` 要求 `h.folder` 非空，故**未写入主表**；",
        "  仅出现在报告 §3 与 JSON `v8_hospitals` 元数据。",
        "",
        "## 探测结果",
        "",
        "| 客户名 | 测试目录 | 状态 | 原始文件 | W 条数 | Job | 备注 |",
        "|--------|---------|------|----------|--------|-----|------|",
    ]
    for r in results:
        if r.customer_label == "省监狱管理局" and r.status == "NO_FOLDER":
            lines.append(
                f"| {r.customer_label} | — | NO_FOLDER | — | — | — | {r.message} |"
            )
            continue
        lines.append(
            f"| {r.customer_label} | {r.folder or '—'} | {r.status} | "
            f"{r.raw_file or '—'} | {r.warning_count if r.status=='OK' else '—'} | "
            f"{r.job_id or '—'} | {r.message[:60]} |"
        )

    detail = [r for r in results if r.status == "OK" and r.warnings]
    if detail:
        lines.extend(["", "## Warning 明细（可快速比对）", ""])
        for r in detail:
            lines.append(f"### {r.customer_label}（Job {r.job_id}，{r.warning_count} 条）")
            lines.append("")
            lines.append(
                "| 科室 | 发货单号 | 包名 | 包数 | 账单价 | 规则价 | pricingRule |"
            )
            lines.append("|------|---------|------|------|--------|--------|-------------|")
            for w in r.warnings[:50]:
                sheet = w.get("sheetName") or w.get("sheet_name") or ""
                ship = w.get("orderNo") or w.get("ship_no") or ""
                pack = w.get("packName") or w.get("pack_name") or ""
                pc = w.get("packCount") or w.get("pack_count") or ""
                up = w.get("unitPrice") or w.get("unit_price") or ""
                ru = w.get("expectedUnitPrice") or w.get("expected_unit_price") or ""
                rule = w.get("pricingRule") or w.get("pricing_rule") or ""
                lines.append(f"| {sheet} | {ship} | {pack} | {pc} | {up} | {ru} | {rule} |")
            if len(r.warnings) > 50:
                lines.append("")
                lines.append(f"（另有 {len(r.warnings)-50} 条，见 TSV）")
            lines.append("")

    no_raw = [r for r in results if r.status == "NO_RAW"]
    if no_raw:
        lines.extend(["", "## 无法做原始单探测（缺原始 xlsx）", ""])
        for r in no_raw:
            lines.append(f"- **{r.customer_label}**：{r.message}")

    return "\n".join(lines) + "\n"


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--month", type=int, default=6)
    p.add_argument("--mode", choices=["docker", "direct"], default="docker")
    p.add_argument("--api-base", default="http://127.0.0.1:8000")
    p.add_argument("--out-date", default=date.today().strftime("%Y%m%d"))
    args = p.parse_args()

    configure_client(api_base=args.api_base, mode=args.mode)
    init_api_from_args(argparse.Namespace(mode=args.mode, api_base=args.api_base))
    token = get_client().login()

    results: list[RawProbeResult] = []
    for meta in V8_HOSPITALS:
        if meta.testable:
            continue
        results.append(probe_hospital(token, meta, prefer_month=args.month))
        r = results[-1]
        if r.status == "OK" and r.folder and r.warnings:
            tsv = write_warning_tsv(r.folder, r.job_id or 0, r.warnings)
            print(f"{r.customer_label}: {r.warning_count} warnings -> {tsv.name}")
        else:
            print(f"{r.customer_label}: {r.status} — {r.message}")

    out_md = TEST_CASE / f"SC11-SKIP院原始单探测报告-{args.out_date}.md"
    out_json = TEST_CASE / f"SC11-SKIP院原始单探测报告-{args.out_date}.json"
    payload = {
        "generated": date.today().isoformat(),
        "prefer_month": args.month,
        "results": [{**asdict(r), "warnings": r.warnings} for r in results],
    }
    out_md.write_text(render_markdown(results, prefer_month=args.month), encoding="utf-8")
    out_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n报告: {out_md}\n      {out_json}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
