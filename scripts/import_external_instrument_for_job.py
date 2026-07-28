#!/usr/bin/env python3
"""Import processed 外来器械 xlsx into Job external_instrument via backend API."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from batch_s8_export_compare import BACKEND, API, get_token  # noqa: E402

DEFAULT_IMPORTS = {
    613: ROOT
    / "测试用例/哈尔滨市第五医院/处理后表格/6月__哈尔滨市第五医院2026年5月9日-2026年6月8日外来器械.xlsx",
}


def import_file(job_id: int, xlsx: Path) -> None:
    if not xlsx.is_file():
        raise FileNotFoundError(xlsx)
    container_path = f"/tmp/external_import_{job_id}.xlsx"
    subprocess.check_call(
        ["docker", "cp", str(xlsx.resolve()), f"{BACKEND}:{container_path}"],
        cwd=ROOT,
    )
    token = get_token()
    raw = subprocess.check_output(
        [
            "docker",
            "exec",
            BACKEND,
            "curl",
            "-sS",
            "-X",
            "POST",
            f"{API}/api/hospital-reconciliations/{job_id}/external-instruments/import",
            "-H",
            f"Authorization: Bearer {token}",
            "-F",
            f"file=@{container_path}",
        ],
        text=True,
        cwd=ROOT,
    )
    data = json.loads(raw)
    if data.get("code") != 200:
        raise RuntimeError(f"import failed Job #{job_id}: {data}")
    count = data.get("data")
    print(f"✅ Job #{job_id}: imported {count} rows from {xlsx.name}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Import external instrument Excel for Job")
    parser.add_argument("--job-id", type=int, required=True)
    parser.add_argument("--file", type=Path, default=None)
    args = parser.parse_args()

    xlsx = args.file or DEFAULT_IMPORTS.get(args.job_id)
    if xlsx is None:
        print("Provide --file for this job-id", file=sys.stderr)
        return 2
    import_file(args.job_id, xlsx)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
