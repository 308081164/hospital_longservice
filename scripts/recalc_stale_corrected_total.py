#!/usr/bin/env python3
"""Batch recalculate stale hospital_reconciliation_row.corrected_total_price.

Problem (附一 export P0 report §8.2): rows where expected_unit_price already reflects
discount but corrected_total_price still equals imported total_price. Export layer now
guards at read time, but DB rows remain inconsistent until recalculated.

Stale pattern:
  - expected_unit_price IS NOT NULL and pack_count > 0
  - corrected_total_price ~= total_price (import total never rewritten)
  - corrected_total_price != expected_unit_price * pack_count

Fix:
  corrected_total_price = ROUND(expected_unit_price * pack_count, 2)
  difference = total_price - corrected_total_price (when total_price present)

Usage:
  # Dry-run: count + sample rows for ZYY-D1 jobs only
  python3 scripts/recalc_stale_corrected_total.py --customer-code ZYY-D1 --dry-run

  # Apply to specific jobs
  python3 scripts/recalc_stale_corrected_total.py --job-id 724 --apply

  # All stale rows (any hospital)
  python3 scripts/recalc_stale_corrected_total.py --all --dry-run

Requires deploy/mysql-hospital-cli.sh (local docker or DEPLOY_PATH on server).
Default is dry-run; pass --apply to write.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MYSQL_CLI = ROOT / "deploy" / "mysql-hospital-cli.sh"

STALE_WHERE = """
expected_unit_price IS NOT NULL
AND pack_count > 0
AND total_price IS NOT NULL
AND ABS(corrected_total_price - total_price) <= 0.01
AND ABS(corrected_total_price - expected_unit_price * pack_count) > 0.01
""".strip()

ZYY_D1_HOSPITAL_FILTER = "j.hospital_name LIKE '%附属第一%'"


def mysql_query(sql: str) -> str:
    if not MYSQL_CLI.is_file():
        raise SystemExit(f"mysql helper not found: {MYSQL_CLI}")
    db = os.environ.get("MYSQL_DATABASE", "hospital")
    env = os.environ.copy()
    env.setdefault("DEPLOY_PATH", str(ROOT))
    out = subprocess.check_output(
        ["bash", str(MYSQL_CLI), "--exec-root", "-N", "-e", sql, db],
        text=True,
        cwd=str(ROOT),
        env=env,
        stderr=subprocess.STDOUT,
    )
    lines = [line.strip() for line in out.splitlines() if line.strip()]
    return lines[-1] if lines else ""


def mysql_rows(sql: str) -> list[str]:
    if not MYSQL_CLI.is_file():
        raise SystemExit(f"mysql helper not found: {MYSQL_CLI}")
    db = os.environ.get("MYSQL_DATABASE", "hospital")
    env = os.environ.copy()
    env.setdefault("DEPLOY_PATH", str(ROOT))
    out = subprocess.check_output(
        ["bash", str(MYSQL_CLI), "--exec-root", "-N", "-e", sql, db],
        text=True,
        cwd=str(ROOT),
        env=env,
        stderr=subprocess.STDOUT,
    )
    return [line.strip() for line in out.splitlines() if line.strip()]


def build_scope_sql(args: argparse.Namespace) -> str:
    parts: list[str] = []
    if args.job_id:
        ids = ",".join(str(i) for i in args.job_id)
        parts.append(f"r.job_id IN ({ids})")
    if args.customer_code:
        code = args.customer_code.replace("'", "''")
        if code == "ZYY-D1":
            parts.append(ZYY_D1_HOSPITAL_FILTER)
        else:
            parts.append(f"j.hospital_name LIKE '%{code}%'")
    if not parts and not args.all:
        raise SystemExit("Specify --job-id, --customer-code ZYY-D1, or --all")
    return " AND ".join(parts) if parts else "1=1"


def count_stale(scope: str) -> int:
    sql = f"""
SELECT COUNT(*)
FROM hospital_reconciliation_row r
JOIN hospital_reconciliation_job j ON j.id = r.job_id
WHERE {STALE_WHERE}
  AND ({scope})
"""
    return int(mysql_query(sql) or "0")


def sample_stale(scope: str, limit: int = 10) -> list[str]:
    sql = f"""
SELECT CONCAT('job=', r.job_id, ' row=', r.id, ' pack=', LEFT(r.pack_name, 40),
              ' old=', r.corrected_total_price, ' new=', ROUND(r.expected_unit_price * r.pack_count, 2))
FROM hospital_reconciliation_row r
JOIN hospital_reconciliation_job j ON j.id = r.job_id
WHERE {STALE_WHERE}
  AND ({scope})
ORDER BY r.job_id DESC, r.id
LIMIT {limit}
"""
    return mysql_rows(sql)


def apply_fix(scope: str) -> int:
    sql = f"""
UPDATE hospital_reconciliation_row r
JOIN hospital_reconciliation_job j ON j.id = r.job_id
SET r.corrected_total_price = ROUND(r.expected_unit_price * r.pack_count, 2),
    r.difference = CASE
        WHEN r.total_price IS NULL THEN r.difference
        ELSE ROUND(r.total_price - ROUND(r.expected_unit_price * r.pack_count, 2), 2)
    END
WHERE {STALE_WHERE}
  AND ({scope})
"""
    before = count_stale(scope)
    if before == 0:
        return 0
    mysql_query(sql)
    after = count_stale(scope)
    return before - after


def main() -> int:
    parser = argparse.ArgumentParser(description="Recalculate stale corrected_total_price rows")
    parser.add_argument("--job-id", type=int, action="append", default=[])
    parser.add_argument("--customer-code", help="Filter jobs, e.g. ZYY-D1")
    parser.add_argument("--all", action="store_true", help="All hospitals (use with care)")
    parser.add_argument("--apply", action="store_true", help="Write updates (default: dry-run)")
    parser.add_argument("--dry-run", action="store_true", help="Explicit dry-run (default behaviour)")
    parser.add_argument("--sample", type=int, default=10, help="Sample rows to print in dry-run")
    args = parser.parse_args()

    scope = build_scope_sql(args)
    stale = count_stale(scope)
    mode = "APPLY" if args.apply else "DRY-RUN"
    print(f"[{mode}] stale rows matching scope: {stale}")

    if stale == 0:
        return 0

    for line in sample_stale(scope, args.sample):
        print(f"  {line}")

    if not args.apply:
        print("Dry-run only. Re-run with --apply to update.")
        return 0

    updated = apply_fix(scope)
    print(f"Updated rows: {updated}")
    remaining = count_stale(scope)
    print(f"Remaining stale in scope: {remaining}")
    return 0 if remaining == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
