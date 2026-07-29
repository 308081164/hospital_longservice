#!/usr/bin/env python3
"""校准生产 Job 映射：对 TODO 37 院查询最近 reconciliation Job，写入 job_baseline_prod.json。"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE = ROOT / "测试用例"
PROD_JOB_MAP = TEST_CASE / "job_baseline_prod.json"
CALIBRATION_LOG = TEST_CASE / "job_baseline_prod_calibration.json"

sys.path.insert(0, str(ROOT / "scripts"))
from lib.api_client import configure_client  # noqa: E402
from batch_june_price_reconciliation import TODO_HOSPITALS  # noqa: E402


def pick_latest_job(rows: list[dict]) -> dict | None:
    if not rows:
        return None

    def sort_key(r: dict) -> tuple:
        status = str(r.get("status") or "").lower()
        completed = 0 if status in {"completed", "done", "success", "finished"} else 1
        ts = (
            r.get("createdAt")
            or r.get("createTime")
            or r.get("created_at")
            or r.get("updateTime")
            or ""
        )
        jid = r.get("id") or r.get("jobId") or r.get("job_id") or 0
        try:
            jid = int(jid)
        except (TypeError, ValueError):
            jid = 0
        return (completed, str(ts), jid)

    return max(rows, key=sort_key)


def main() -> int:
    parser = argparse.ArgumentParser(description="校准生产 job_baseline_prod.json")
    parser.add_argument("--api", default="http://39.102.213.51:8853")
    parser.add_argument("--mode", choices=["docker", "direct"], default="direct")
    parser.add_argument("--username", default=None)
    parser.add_argument("--password", default=None)
    parser.add_argument("--dry-run", action="store_true", help="只写 calibration 日志，不更新 prod map")
    args = parser.parse_args()

    client = configure_client(mode=args.mode, api_base=args.api, username=args.username, password=args.password)
    client.login(force=True)

    prior: dict[str, int] = {}
    if PROD_JOB_MAP.is_file():
        prior = {k: int(v) for k, v in json.loads(PROD_JOB_MAP.read_text(encoding="utf-8")).get("jobs", {}).items()}

    entries: list[dict] = []
    new_jobs: dict[str, int] = {}

    for name in TODO_HOSPITALS:
        old_id = prior.get(name)
        try:
            rows = client.list_reconciliations(hospital_name=name)
        except Exception as exc:
            entries.append({"hospital": name, "status": "error", "error": str(exc), "old_job_id": old_id})
            continue

        best = pick_latest_job(rows)
        if not best:
            entries.append({"hospital": name, "status": "missing", "old_job_id": old_id, "candidates": 0})
            continue

        job_id = int(best.get("id") or best.get("jobId") or best.get("job_id"))
        status = "unchanged" if old_id == job_id else ("new" if old_id is None else "changed")
        entries.append(
            {
                "hospital": name,
                "status": status,
                "job_id": job_id,
                "old_job_id": old_id,
                "job_status": best.get("status"),
                "createdAt": best.get("createdAt") or best.get("createTime"),
                "candidates": len(rows),
            }
        )
        new_jobs[name] = job_id

    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "api_base": args.api,
        "summary": {
            "total": len(TODO_HOSPITALS),
            "found": sum(1 for e in entries if e.get("job_id")),
            "missing": sum(1 for e in entries if e.get("status") == "missing"),
            "changed": sum(1 for e in entries if e.get("status") == "changed"),
            "unchanged": sum(1 for e in entries if e.get("status") == "unchanged"),
            "error": sum(1 for e in entries if e.get("status") == "error"),
        },
        "entries": entries,
    }
    CALIBRATION_LOG.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(payload["summary"], ensure_ascii=False, indent=2))
    print(f"校准日志: {CALIBRATION_LOG}")

    if not args.dry_run:
        out = {
            "version": "1",
            "description": "生产环境 S8/S4 Job 映射（calibrate_prod_job_map.py 自动生成）",
            "updated": datetime.now().strftime("%Y-%m-%d"),
            "source": f"API {args.api}",
            "maintain_note": "校准命令: python3 scripts/calibrate_prod_job_map.py",
            "jobs": new_jobs,
        }
        PROD_JOB_MAP.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"已更新: {PROD_JOB_MAP}")

    return 1 if payload["summary"]["missing"] or payload["summary"]["error"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
