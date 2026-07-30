#!/usr/bin/env python3
"""Batch-import June raw bills via backend API and compare warnings vs expected CSV."""

from __future__ import annotations

import argparse
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

sys.path.insert(0, str(ROOT / "scripts"))
from lib.api_client import configure_client, get_client  # noqa: E402
from batch_june_price_reconciliation import (  # noqa: E402
    TODO_HOSPITALS,
    ExpectedPriceRow,
    extract_expected_price_rows,
    pick_june_pair,
)

OUTPUT_INDEX = TEST_CASE_DIR / "批量6月系统对账结果.md"
PRICING_JOB_JSON = TEST_CASE_DIR / "job_baseline_pricing.json"
APPENDIX_MARKER = "## 附录 · 20260728 计价验收轨 Job"


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


def _parse_table_lines(lines: list[str]) -> dict[str, CompareResult]:
    out: dict[str, CompareResult] = {}
    for line in lines:
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


def _split_md_sections(text: str) -> tuple[str, list[str], list[str]]:
    """Return (preamble+stable_block, stable_table_lines, appendix_table_lines)."""
    if APPENDIX_MARKER not in text:
        marker = "| 医院 |"
        idx = text.find(marker)
        preamble = text[:idx].rstrip() if idx >= 0 else text.rstrip()
        table_lines = [ln for ln in text.splitlines() if ln.startswith("|")]
        return preamble + "\n\n", table_lines, []
    head, appendix = text.split(APPENDIX_MARKER, 1)
    stable_lines = [ln for ln in head.splitlines() if ln.startswith("|")]
    appendix_lines = [ln for ln in appendix.splitlines() if ln.startswith("|")]
    preamble = head[: head.find("| 医院 |")].rstrip() if "| 医院 |" in head else head.rstrip()
    return preamble + "\n\n", stable_lines, appendix_lines


def parse_existing_results() -> dict[str, CompareResult]:
    if not OUTPUT_INDEX.is_file():
        return {}
    _, stable_lines, _ = _split_md_sections(OUTPUT_INDEX.read_text(encoding="utf-8"))
    return _parse_table_lines(stable_lines)


def parse_pricing_results() -> dict[str, CompareResult]:
    if not OUTPUT_INDEX.is_file():
        return {}
    _, _, appendix_lines = _split_md_sections(OUTPUT_INDEX.read_text(encoding="utf-8"))
    return _parse_table_lines(appendix_lines)


def merge_partial_results(
    prior: dict[str, CompareResult], partial: list[CompareResult]
) -> list[CompareResult]:
    by_name = dict(prior)
    for r in partial:
        by_name[r.hospital] = r
    merged = [by_name[h] for h in TODO_HOSPITALS if h in by_name]
    extras = [by_name[k] for k in by_name if k not in TODO_HOSPITALS]
    return merged + extras


def init_api_from_args(args: argparse.Namespace | None = None) -> None:
    if args is None:
        configure_client(api_base=API, mode="docker", backend_container=BACKEND)
        return
    configure_client(
        api_base=getattr(args, "api_base", None) or API,
        mode=getattr(args, "mode", "docker"),
        backend_container=BACKEND,
        username=getattr(args, "username", None),
        password=getattr(args, "password", None),
    )


def docker_curl(args: list[str]) -> str:
    return get_client().curl_raw(args)


def get_token() -> str:
    return get_client().login()


def import_bill(token: str, hospital: str, file_path: Path) -> dict:
    client = get_client()
    if client.mode == "docker":
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
    else:
        data = client.post_multipart(
            "/api/hospital-reconciliations/import",
            {
                "rule_id": str(RULE_ID),
                "operator_name": OPERATOR,
                "hospital_name": hospital,
            },
            "source_file",
            file_path,
            token=token,
        )
    if data.get("code") != 200:
        raise RuntimeError(f"import failed: {data.get('msg')} ({hospital})")
    return data["data"]


def fetch_warnings(token: str, job_id: int) -> list[dict]:
    warnings: list[dict] = []
    page = 1
    client = get_client()
    while True:
        path = f"/api/hospital-reconciliations/{job_id}/rows?page={page}&size=500"
        if client.mode == "docker":
            raw = docker_curl([f"{API}{path}", "-H", f"Authorization: Bearer {token}"])
            data = json.loads(raw)
        else:
            data = client.request_json("GET", path, token=token)
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
            result.message = (
                f"期待 {result.expected} 条零漏检，但多报 {result.extra}"
                f"（extra_inventory · 不等于规则回归）"
            )
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


def render_dual_index(
    stable: dict[str, CompareResult],
    pricing: dict[str, CompareResult],
    preamble: str,
) -> str:
    """Render md with stable main table + pricing appendix."""
    stable_order = [h for h in TODO_HOSPITALS if h in stable]
    stable_rows = [stable[h] for h in stable_order]
    pricing_order = list(dict.fromkeys([*TODO_HOSPITALS, *pricing.keys()]))
    pricing_rows = [pricing[h] for h in pricing_order if h in pricing]

    lines = [preamble.strip(), "", "## 稳定基线 Job（S4 + S8 主表）", ""]
    lines.extend(_render_table(stable_rows))
    lines.extend(["", "## 待规则微调（稳定基线）", ""])
    fails = [r for r in stable_rows if r.status in {"fail", "fail_extra", "error"}]
    if fails:
        for r in fails:
            lines.append(f"- **{r.hospital}**：{r.message or r.status}")
    else:
        lines.append("- （无）")
    lines.extend(["", "---", "", APPENDIX_MARKER + "（656–692+）", ""])
    lines.append(
        "> 定点重导写入此节。**S4 判定**：期待 CSV **零漏检**（`missed=0`）即 pricing pass；"
        "`fail_extra` 为 extra_inventory。"
    )
    lines.append("")
    lines.extend(_render_table(pricing_rows))
    pricing_fails = [r for r in pricing_rows if r.missed > 0 or r.status == "fail"]
    if pricing_fails:
        lines.extend(["", "## 待规则微调（计价轨）", ""])
        for r in pricing_fails:
            lines.append(f"- **{r.hospital}**：{r.message}")
            if r.missed_keys:
                lines.append(f"  - 漏检样例：`{r.missed_keys[0]}`")
    return "\n".join(lines) + "\n"


def _render_table(results: list[CompareResult]) -> list[str]:
    lines = [
        "| 医院 | Job | 期待 | 系统warning | 命中 | 漏检 | 多报 | 状态 |",
        "|------|-----|------|------------|------|------|------|------|",
    ]
    for r in results:
        lines.append(
            f"| {r.hospital} | {r.job_id or '—'} | {r.expected} | {r.system_warnings} | "
            f"{r.matched} | {r.missed} | {r.extra} | {r.status} |"
        )
    return lines


def write_pricing_job_json(pricing: dict[str, CompareResult]) -> None:
    jobs = {h: r.job_id for h, r in pricing.items() if r.job_id}
    payload = {
        "version": "1",
        "description": "20260728 计价验收轨 Job（定点重导）",
        "updated": time.strftime("%Y-%m-%d"),
        "jobs": jobs,
    }
    PRICING_JOB_JSON.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Batch S4 pricing import + warning compare")
    p.add_argument("hospitals", nargs="*", help="医院名；默认全量 TODO_HOSPITALS")
    p.add_argument("--api-base", default=None)
    p.add_argument("--mode", choices=["docker", "direct"], default="docker")
    p.add_argument("--username", default=None)
    p.add_argument("--password", default=None)
    return p.parse_args()


def main() -> int:
    args = parse_args()
    if args.api_base:
        global API  # noqa: PLW0603
        API = args.api_base.rstrip("/")
    init_api_from_args(args)
    only = args.hospitals if args.hospitals else list(TODO_HOSPITALS)
    partial_run = bool(args.hospitals)
    token = get_token()
    results: list[CompareResult] = []
    for name in only:
        print(f"Processing {name}...", flush=True)
        results.append(compare_hospital(token, name))

    if partial_run:
        text = OUTPUT_INDEX.read_text(encoding="utf-8") if OUTPUT_INDEX.is_file() else ""
        preamble, _, _ = _split_md_sections(text)
        if not preamble.strip():
            preamble = (
                "# 批量 6 月系统对账结果\n\n"
                "> **S8 稳定基线**：见 `job_baseline_stable.json`。\n"
            )
        stable = parse_existing_results()
        if not stable:
            stable = {h: CompareResult(hospital=h) for h in TODO_HOSPITALS}
        pricing = parse_pricing_results()
        for r in results:
            pricing[r.hospital] = r
        write_pricing_job_json(pricing)
        text = render_dual_index(stable, pricing, preamble)
    else:
        stable = {r.hospital: r for r in results}
        pricing = dict(stable)
        write_pricing_job_json(pricing)
        preamble = (
            "# 批量 6 月系统对账结果\n\n"
            "> **S8 稳定基线**：见 `job_baseline_stable.json`。\n"
        )
        text = render_dual_index(stable, pricing, preamble)

    OUTPUT_INDEX.write_text(text, encoding="utf-8")
    print(text)
    print(f"\nWritten: {OUTPUT_INDEX}")
    if PRICING_JOB_JSON.is_file():
        print(f"Pricing jobs: {PRICING_JOB_JSON}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
