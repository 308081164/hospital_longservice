#!/usr/bin/env python3
"""结款函灭菌口径 triage：Job correctedTotal vs export 灭菌行 vs 处理后结款函灭菌行。

用法:
  python3 scripts/settlement_triage.py --hospital 道外区人民医院
  python3 scripts/settlement_triage.py --job 626
  python3 scripts/settlement_triage.py --all-standard
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE = ROOT / "测试用例"
EXPORT_DIR = TEST_CASE / ".s8_exports"
RECON_MD = TEST_CASE / "批量6月系统对账结果.md"

sys.path.insert(0, str(ROOT / "scripts"))
from batch_s8_export_compare import docker_curl, get_token, parse_job_table  # noqa: E402
from batch_s8_settlement_compare import (  # noqa: E402
    BILL_SETTLEMENT_ONLY,
    extract_settlement_items,
    normalize_settlement_label,
    pick_settlement_file,
)

STERILIZE_LABELS = frozenset(
    {
        "灭菌费用",
        "灭菌费",
        "高温灭菌费用",
        "低温灭菌费用",
    }
)


def parse_job_total(job_id: int, token: str) -> float | None:
    raw = docker_curl(
        [
            "-H",
            f"Authorization: Bearer {token}",
            f"http://127.0.0.1:8000/api/hospital-reconciliations/{job_id}",
        ]
    )
    data = json.loads(raw)
    if data.get("code") != 200:
        return None
    job = data.get("data") or {}
    for key in ("correctedTotalPrice", "totalPrice"):
        val = job.get(key)
        if isinstance(val, (int, float)):
            return float(val)
    return None


def sterilize_from_items(items: dict[str, float]) -> float:
    total = 0.0
    for label, amount in items.items():
        norm = normalize_settlement_label(label)
        if norm in STERILIZE_LABELS or "灭菌" in norm:
            total += amount
    return round(total, 2)


def export_settlement_path(folder: str, job_id: int) -> Path | None:
    path = EXPORT_DIR / f"job{job_id}_{folder}_settlement.xlsx"
    return path if path.is_file() else None


def triage_hospital(folder: str, jobs: dict[str, int], token: str) -> dict:
    job_id = jobs.get(folder)
    proc = pick_settlement_file(TEST_CASE / folder)
    result: dict = {
        "folder": folder,
        "job_id": job_id,
        "job_sterilize_base": None,
        "processed_sterilize": None,
        "export_sterilize": None,
        "delta_job_vs_processed": None,
        "delta_export_vs_processed": None,
        "suspected_cause": "",
        "processed_file": str(proc.relative_to(ROOT)) if proc else None,
        "export_file": None,
    }
    if not job_id or not proc:
        result["suspected_cause"] = "缺少 Job 或处理后结款函"
        return result

    job_base = parse_job_total(job_id, token)
    proc_items = extract_settlement_items(proc)
    proc_ster = sterilize_from_items(proc_items)

    export_path = export_settlement_path(folder, job_id)
    export_ster = None
    if export_path:
        export_items = extract_settlement_items(export_path)
        export_ster = sterilize_from_items(export_items)
        result["export_file"] = str(export_path.relative_to(ROOT))

    result["job_sterilize_base"] = job_base
    result["processed_sterilize"] = proc_ster
    result["export_sterilize"] = export_ster
    if job_base is not None:
        result["delta_job_vs_processed"] = round(proc_ster - job_base, 2)
    if export_ster is not None:
        result["delta_export_vs_processed"] = round(proc_ster - export_ster, 2)

    d_job = result["delta_job_vs_processed"]
    d_exp = result["delta_export_vs_processed"]
    if d_job is not None and abs(d_job) > 50 and (d_exp is None or abs(d_exp) < 1):
        result["suspected_cause"] = "Job 账单合计与结款灭菌口径不同 → 需 settlement_only 折扣或独立策略"
    elif d_exp is not None and abs(d_exp) > 50:
        result["suspected_cause"] = "export 灭菌行与处理后表不一致 → 查 enricher/策略种子"
    elif d_job is not None and abs(d_job) <= 1 and d_exp is not None and abs(d_exp) <= 1:
        result["suspected_cause"] = "灭菌口径一致 · 差额可能在物流/加急/低消行"
    else:
        result["suspected_cause"] = "需人工读处理后结款函行结构"
    return result


def print_result(r: dict) -> None:
    print(f"\n=== {r['folder']} (Job #{r.get('job_id')}) ===")
    print(f"  Job correctedTotal:     {r.get('job_sterilize_base')}")
    print(f"  处理后灭菌合计:         {r.get('processed_sterilize')}")
    print(f"  export 灭菌合计:        {r.get('export_sterilize')}")
    print(f"  Δ processed−Job:        {r.get('delta_job_vs_processed')}")
    print(f"  Δ processed−export:     {r.get('delta_export_vs_processed')}")
    print(f"  疑似: {r.get('suspected_cause')}")
    if r.get("processed_file"):
        print(f"  处理后: {r['processed_file']}")
    if r.get("export_file"):
        print(f"  export: {r['export_file']}")


def main() -> int:
    parser = argparse.ArgumentParser(description="结款函灭菌口径 triage")
    parser.add_argument("--hospital", action="append", help="医院文件夹名（可重复）")
    parser.add_argument("--job", type=int, help="仅 triage 指定 Job 对应医院")
    parser.add_argument(
        "--all-standard",
        action="store_true",
        help="Phase1 标准两行院：道外/华夏/武警/省二南岗/悦美",
    )
    args = parser.parse_args()

    jobs = parse_job_table()
    if args.job:
        folder = next((k for k, v in jobs.items() if v == args.job), None)
        if not folder:
            print(f"Job #{args.job} 未在 {RECON_MD.name} 映射", file=sys.stderr)
            return 1
        hospitals = [folder]
    elif args.hospital:
        hospitals = args.hospital
    elif args.all_standard:
        hospitals = [
            "道外区人民医院",
            "哈尔滨华夏眼科医院",
            "武警黑龙江省总队医院",
            "黑龙江省第二医院（南岗院区）",
            "悦美芳华医疗门诊医院",
        ]
    else:
        parser.print_help()
        return 2

    token = get_token()
    results = [triage_hospital(h, jobs, token) for h in hospitals]
    for r in results:
        print_result(r)
    return 0


if __name__ == "__main__":
    sys.exit(main())
