#!/usr/bin/env python3
"""Batch-import June raw bills via backend API and compare warnings vs expected CSV."""

from __future__ import annotations

import csv
import json
import subprocess
import sys
import time
import urllib.parse
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE_DIR = ROOT / "测试用例"
BACKEND = "hospital-backend"
API = "http://127.0.0.1:8000"
RULE_ID = 1
OPERATOR = "batch-audit"
TOLERANCE = 0.05

# Reuse hospital list + june pair finder
sys.path.insert(0, str(ROOT / "scripts"))
from batch_june_price_reconciliation import (  # noqa: E402
    TODO_HOSPITALS,
    ExpectedPriceRow,
    extract_expected_price_rows,
    pick_june_pair,
)

OUTPUT_INDEX = TEST_CASE_DIR / "批量6月系统对账结果.md"


@dataclass
class CompareResult:
    hospital: str
    job_id: int | None = None
    expected: int = 0
    system_warnings: int = 0
    matched: int = 0
    missed: int = 0
    extra: int = 0
    status: str = "pending"
    message: str = ""
    missed_keys: list[str] = field(default_factory=list)
    extra_keys: list[str] = field(default_factory=list)


def load_md_preamble() -> str:
    if not OUTPUT_INDEX.is_file():
        return ""
    text = OUTPUT_INDEX.read_text(encoding="utf-8")
    marker = "| 医院 |"
    idx = text.find(marker)
    if idx < 0:
        return ""
    return text[:idx].rstrip() + "\n\n"


def parse_existing_results() -> dict[str, CompareResult]:
    out: dict[str, CompareResult] = {}
    if not OUTPUT_INDEX.is_file():
        return out
    for line in OUTPUT_INDEX.read_text(encoding="utf-8").splitlines():
        if not line.startswith("|") or line.startswith("| 医院") or line.startswith("|------"):
            continue
        parts = [p.strip() for p in line.split("|") if p.strip()]
        if len(parts) < 8:
            continue
        try:
            job_id = int(parts[1])
        except ValueError:
            job_id = None
        out[parts[0]] = CompareResult(
            hospital=parts[0],
            job_id=job_id,
            expected=int(parts[2]),
            system_warnings=int(parts[3]),
            matched=int(parts[4]),
            missed=int(parts[5]),
            extra=int(parts[6]),
            status=parts[7],
        )
    return out


def merge_partial_results(
    prior: dict[str, CompareResult], partial: list[CompareResult]
) -> list[CompareResult]:
    by_name = dict(prior)
    for r in partial:
        by_name[r.hospital] = r
    merged = [by_name[h] for h in TODO_HOSPITALS if h in by_name]
    extras = [by_name[k] for k in by_name if k not in TODO_HOSPITALS]
    return merged + extras


def docker_curl(args: list[str]) -> str:
    cmd = ["docker", "exec", BACKEND, "curl", "-sS", *args]
    return subprocess.check_output(cmd, text=True)


def get_token() -> str:
    raw = docker_curl([
        "-X", "POST", f"{API}/api/v1/base/access_token",
        "-H", "Content-Type: application/json",
        "-d", '{"username":"admin","password":"admin123"}',
    ])
    data = json.loads(raw)
    if data.get("code") != 200:
        raise RuntimeError(f"login failed: {data}")
    return data["data"]["access_token"]


def import_bill(token: str, hospital: str, file_path: Path) -> dict:
    # Copy file into container temp path
    container_path = f"/tmp/batch_{file_path.name}"
    subprocess.check_call(["docker", "cp", str(file_path), f"{BACKEND}:{container_path}"])
    raw = docker_curl([
        "-X", "POST", f"{API}/api/hospital-reconciliations/import",
        "-H", f"Authorization: Bearer {token}",
        "-F", f"source_file=@{container_path}",
        "-F", f"rule_id={RULE_ID}",
        "-F", f"operator_name={OPERATOR}",
        "-F", f"hospital_name={hospital}",
    ])
    data = json.loads(raw)
    if data.get("code") != 200:
        raise RuntimeError(f"import failed: {data.get('msg')} ({hospital})")
    return data["data"]


def fetch_warnings(token: str, job_id: int) -> list[dict]:
    warnings: list[dict] = []
    page = 1
    while True:
        raw = docker_curl([
            f"{API}/api/hospital-reconciliations/{job_id}/rows?page={page}&size=500",
            "-H", f"Authorization: Bearer {token}",
        ])
        data = json.loads(raw)
        if data.get("code") != 200:
            raise RuntimeError(data.get("msg"))
        payload = data["data"]
        rows = payload.get("rows") or payload.get("items") or []
        for r in rows:
            if r.get("status") == "warning":
                warnings.append(r)
        total = payload.get("total") or payload.get("totalElements") or len(rows)
        if page * 500 >= total or not rows:
            break
        page += 1
    return warnings


def warn_key(sheet: str, ship_no: str, pack_name: str, pack_count: float | int | None) -> str:
    pc = pack_count if pack_count is not None else 0
    try:
        pc_norm = f"{float(pc):.4g}"
    except (TypeError, ValueError):
        pc_norm = str(pc)
    return f"{ship_no}|{pack_name}|{pc_norm}"


def expected_key(row: ExpectedPriceRow) -> str:
    return warn_key("", row.ship_no, row.pack_name, row.pack_count)


def load_expected_from_csv(hospital_dir: Path) -> list[ExpectedPriceRow]:
    csv_path = hospital_dir / "6月期待价格校正清单.csv"
    if not csv_path.exists():
        return []
    rows: list[ExpectedPriceRow] = []
    with csv_path.open(encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for r in reader:
            rows.append(
                ExpectedPriceRow(
                    sheet=r["科室"],
                    ship_no=r["发货单号"],
                    pack_name=r["包名"],
                    pack_count=float(r["包数"]) if r.get("包数") else None,
                    raw_unit=float(r["原单价"]) if r.get("原单价") else None,
                    proc_unit=float(r["处理后单价"]) if r.get("处理后单价") else None,
                    raw_total=float(r["原总价"]) if r.get("原总价") else None,
                    proc_total=float(r["处理后总价"]) if r.get("处理后总价") else None,
                    raw_row=None,
                    proc_row=None,
                )
            )
    return rows


def compare_hospital(token: str, name: str) -> CompareResult:
    result = CompareResult(hospital=name)
    hospital_dir = TEST_CASE_DIR / name

    raw_path, proc_path, note = pick_june_pair(hospital_dir)
    if not raw_path:
        result.status = "skip"
        result.message = note
        return result

    expected_rows = load_expected_from_csv(hospital_dir)
    if not expected_rows:
        _, _, _, _ = extract_expected_price_rows(hospital_dir)
        expected_rows = load_expected_from_csv(hospital_dir)

    result.expected = len(expected_rows)
    exp_keys = {expected_key(r) for r in expected_rows}

    try:
        job = import_bill(token, name, raw_path)
        result.job_id = job.get("id")
        time.sleep(0.5)
        warnings = fetch_warnings(token, result.job_id)
        result.system_warnings = len(warnings)

        sys_keys = {
            warn_key("", str(w.get("orderNo") or ""), w.get("packName") or "", w.get("packCount"))
            for w in warnings
        }

        matched = sum(1 for r in expected_rows if expected_key(r) in sys_keys)
        missed = [expected_key(r) for r in expected_rows if expected_key(r) not in sys_keys]
        extra_count = len(sys_keys - exp_keys)

        result.matched = matched
        result.missed = len(missed)
        result.extra = extra_count
        result.missed_keys = missed[:10]

        if result.expected == 0:
            result.status = "pass_zero" if result.system_warnings == 0 else "fail_extra"
            result.message = "零期待" + ("" if result.system_warnings == 0 else f"，但系统有 {result.system_warnings} 条 warning")
        elif result.missed == 0 and result.extra == 0:
            result.status = "pass"
            result.message = f"期待 {result.expected} 条，零漏检零多报"
        elif result.missed == 0 and result.extra > 0:
            result.status = "fail_extra"
            result.message = f"期待 {result.expected} 条零漏检，但多报 {result.extra}（需规则多报价或补期待清单）"
        else:
            result.status = "fail"
            result.message = f"命中 {matched}/{result.expected}，漏检 {result.missed}，多报 {result.extra}"

        # Save system warnings tsv
        out_tsv = hospital_dir / "6月系统warning.tsv"
        with out_tsv.open("w", encoding="utf-8") as f:
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
        result.status = "error"
        result.message = str(exc)

    return result


def render_index(results: list[CompareResult], preamble: str = "") -> str:
    head = preamble.strip()
    if not head:
        head = "# 批量 6 月系统对账结果"
    lines = [
        head,
        "",
        "| 医院 | Job | 期待 | 系统warning | 命中 | 漏检 | 多报 | 状态 |",
        "|------|-----|------|------------|------|------|------|------|",
    ]
    for r in results:
        lines.append(
            f"| {r.hospital} | {r.job_id or '—'} | {r.expected} | {r.system_warnings} | "
            f"{r.matched} | {r.missed} | {r.extra} | {r.status} |"
        )
    lines.append("")
    fails = [r for r in results if r.status in {"fail", "fail_extra", "error"}]
    if fails:
        lines.append("## 待规则微调")
        lines.append("")
        for r in fails:
            lines.append(f"- **{r.hospital}**：{r.message}")
            if r.missed_keys:
                lines.append(f"  - 漏检样例：`{r.missed_keys[0]}`")
    return "\n".join(lines)


def main() -> int:
    only = sys.argv[1:] if len(sys.argv) > 1 else list(TODO_HOSPITALS)
    partial_run = len(sys.argv) > 1
    token = get_token()
    results: list[CompareResult] = []
    for name in only:
        print(f"Processing {name}...", flush=True)
        results.append(compare_hospital(token, name))
    preamble = load_md_preamble() if partial_run else ""
    if partial_run:
        prior = parse_existing_results()
        results = merge_partial_results(prior, results) if prior else results
    text = render_index(results, preamble=preamble)
    OUTPUT_INDEX.write_text(text, encoding="utf-8")
    print(text)
    print(f"\nWritten: {OUTPUT_INDEX}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
