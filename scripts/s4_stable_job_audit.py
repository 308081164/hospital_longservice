#!/usr/bin/env python3
"""S4 stable Job 只读审计：拉取现有 Job warnings，与期待 CSV 比对，不重导 import。"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict, dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE_DIR = ROOT / "测试用例"
STABLE_JOB_JSON = TEST_CASE_DIR / "job_baseline_stable.json"
STABLE_MD = TEST_CASE_DIR / "批量6月系统对账结果.md"
OUTPUT_JSON = TEST_CASE_DIR / "s4_stable_job_audit.json"

sys.path.insert(0, str(ROOT / "scripts"))
from lib.api_client import configure_client  # noqa: E402
from batch_june_price_reconciliation import TODO_HOSPITALS, pick_june_pair  # noqa: E402
from batch_june_system_test import (  # noqa: E402
    CompareResult,
    _parse_table_lines,
    expected_key,
    fetch_warnings,
    get_token,
    load_expected_from_csv,
    warn_key,
)


@dataclass
class AuditRow:
    hospital: str
    job_id: int | None = None
    expected: int = 0
    system_warnings: int = 0
    matched: int = 0
    missed: int = 0
    extra: int = 0
    status: str = "pending"
    message: str = ""
    md_missed: int | None = None
    md_extra: int | None = None
    md_status: str | None = None
    md_drift: bool = False
    missed_keys: list[str] = field(default_factory=list)


def load_stable_jobs(job_map: Path | None = None) -> dict[str, int]:
    path = job_map or STABLE_JOB_JSON
    data = json.loads(path.read_text(encoding="utf-8"))
    return {k: int(v) for k, v in data["jobs"].items()}


def load_md_stable_table() -> dict[str, CompareResult]:
    if not STABLE_MD.is_file():
        return {}
    text = STABLE_MD.read_text(encoding="utf-8")
    marker = "## 附录"
    if marker in text:
        text = text.split(marker, 1)[0]
    lines = [ln for ln in text.splitlines() if ln.startswith("|")]
    return _parse_table_lines(lines)


def classify_status(missed: int, extra: int, expected: int) -> str:
    if missed > 0:
        return "fail"
    if expected == 0 and extra == 0:
        return "pass_zero"
    if missed == 0 and extra == 0:
        return "pass"
    if missed == 0 and extra > 0:
        return "fail"
    return "fail"


def audit_hospital(token: str, name: str, job_id: int, md_row: CompareResult | None) -> AuditRow:
    row = AuditRow(hospital=name, job_id=job_id)
    hospital_dir = TEST_CASE_DIR / name

    raw_path, _, note = pick_june_pair(hospital_dir)
    if not raw_path:
        row.status = "skip"
        row.message = note
        return row

    expected_rows = load_expected_from_csv(hospital_dir)
    row.expected = len(expected_rows)
    exp_keys = {expected_key(r) for r in expected_rows}

    try:
        warnings = fetch_warnings(token, job_id)
    except Exception as exc:
        row.status = "error"
        row.message = str(exc)
        return row

    row.system_warnings = len(warnings)
    sys_keys = {
        warn_key("", str(w.get("orderNo") or ""), w.get("packName") or "", w.get("packCount"))
        for w in warnings
    }

    row.matched = sum(1 for r in expected_rows if expected_key(r) in sys_keys)
    missed = [expected_key(r) for r in expected_rows if expected_key(r) not in sys_keys]
    row.missed = len(missed)
    row.extra = len(sys_keys - exp_keys)
    row.missed_keys = missed[:10]
    row.status = classify_status(row.missed, row.extra, row.expected)

    if md_row:
        row.md_missed = md_row.missed
        row.md_extra = md_row.extra
        row.md_status = md_row.status
        row.md_drift = (
            row.missed != md_row.missed
            or row.extra != md_row.extra
            or row.status != md_row.status
        )
        if row.md_drift:
            row.message = (
                f"与 MD 主表漂移: live missed={row.missed} extra={row.extra} status={row.status} "
                f"vs md missed={md_row.missed} extra={md_row.extra} status={md_row.status}"
            )

    return row


def main() -> int:
    parser = argparse.ArgumentParser(description="S4 stable Job 只读 warning 审计")
    parser.add_argument("--job-map", type=Path, default=STABLE_JOB_JSON)
    parser.add_argument("--output", type=Path, default=OUTPUT_JSON)
    parser.add_argument("--mode", choices=["docker", "direct"], default="docker")
    parser.add_argument("--api", default="http://127.0.0.1:8000")
    args = parser.parse_args()

    configure_client(mode=args.mode, api_base=args.api)
    jobs = load_stable_jobs(args.job_map)
    md_table = load_md_stable_table()
    token = get_token()

    rows: list[AuditRow] = []
    for name in TODO_HOSPITALS:
        job_id = jobs.get(name)
        if not job_id:
            rows.append(AuditRow(hospital=name, status="skip", message=f"{args.job_map.name} 无映射"))
            continue
        md_row = md_table.get(name)
        rows.append(audit_hospital(token, name, job_id, md_row))

    drift = [r for r in rows if r.md_drift]
    missed_fail = [r for r in rows if r.missed > 0]
    extra_fail = [r for r in rows if r.extra > 0 and r.missed == 0]

    payload = {
        "generated_at": __import__("datetime").datetime.now().isoformat(timespec="seconds"),
        "job_map": str(args.job_map),
        "summary": {
            "total": len(rows),
            "pass": sum(1 for r in rows if r.status in {"pass", "pass_zero"}),
            "fail": sum(1 for r in rows if r.status == "fail"),
            "fail_extra": len(extra_fail),
            "fail_missed": len(missed_fail),
            "skip": sum(1 for r in rows if r.status == "skip"),
            "error": sum(1 for r in rows if r.status == "error"),
            "md_drift": len(drift),
        },
        "rows": [asdict(r) for r in rows],
    }
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(payload["summary"], ensure_ascii=False, indent=2))
    print(f"\n已写入: {args.output}")
    return 1 if missed_fail or extra_fail or drift else 0


if __name__ == "__main__":
    raise SystemExit(main())
