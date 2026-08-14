#!/usr/bin/env python3
"""生产 Job 补充修复：外来器械导入 + 全量 reprice 保存（对齐 stable S8 export）。"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from fuyi_june_closeout import fetch_all_rows, reprice_job, save_repriced_rows  # noqa: E402
from lib.api_client import ApiError, configure_client, get_client  # noqa: E402

PROD_API = "http://39.102.213.51:8853"

# hospital -> (processed 外来器械/器械把数 xlsx 相对路径, 可选)
EXTERNAL_IMPORTS: dict[str, str] = {
    "黑龙江省中医药大学附属第三医院（电力）": "测试用例/黑龙江省中医药大学附属第三医院（电力）/处理后表格/6月__黑龙江省中医药大学附属第三医院6月器械把数.xlsx",
    "哈尔滨市第五医院": "测试用例/哈尔滨市第五医院/处理后表格/6月__哈尔滨市第五医院2026年5月9日-2026年6月8日外来器械.xlsx",
}

# 本轮 fail_prod_lag 待修复（不含材料阻塞 4 院）
REPRICE_TARGETS = [
    "黑龙江中医药大学附属第一医院",
    "黑龙江省中医药大学附属第三医院（电力）",
    "国药总医院第三院区",
    "哈尔滨市第五医院",
    "新发红十字医院",
    "黑龙江省医院（南岗院区）",
    "黑龙江省医院（香坊院区）",
    "祖研-黑龙江省中医医院（三辅院区）",
    "黑龙江中医药大学附属第二医院（哈南分院）",
    "哈尔滨冰城医疗美容医院",
]


def import_external_direct(job_id: int, xlsx: Path) -> int:
    client = get_client()
    client.login(force=True)
    token = client._token
    if not xlsx.is_file():
        raise FileNotFoundError(xlsx)
    # multipart via curl subprocess for direct mode
    import subprocess

    raw = subprocess.check_output(
        [
            "curl",
            "-sS",
            "-X",
            "POST",
            f"{client.api_base}/api/hospital-reconciliations/{job_id}/external-instruments/import",
            "-H",
            f"Authorization: Bearer {token}",
            "-F",
            f"file=@{xlsx.resolve()}",
        ],
        text=True,
    )
    data = json.loads(raw)
    if data.get("code") != 200:
        raise ApiError(f"external import Job #{job_id}: {data.get('msg')}", payload=data)
    count = data.get("data")
    print(f"  external import Job #{job_id}: {count} rows from {xlsx.name}")
    return int(count or 0)


def reprice_save_all(job_id: int) -> int:
    client = get_client()
    client.login(force=True)
    token = client._token
    print(f"  reprice Job #{job_id} …")
    reprice_data = reprice_job(token, job_id)
    repriced_rows = reprice_data.get("rows") or []
    summary = reprice_data.get("summary") or {}
    print(
        f"  reprice OK · total={summary.get('total')} corrected={summary.get('corrected')} "
        f"warning={summary.get('warning')}"
    )
    if not repriced_rows:
        print("  skip save: no repriced rows")
        return job_id
    new_id = save_repriced_rows(token, job_id, repriced_rows)
    if new_id != job_id:
        print(f"  version upgrade #{job_id} → #{new_id}")
    return new_id


def load_prod_jobs(path: Path) -> dict[str, int]:
    data = json.loads(path.read_text(encoding="utf-8"))
    return {k: int(v) for k, v in data.get("jobs", {}).items()}


def update_prod_map(path: Path, hospital: str, job_id: int) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    data.setdefault("jobs", {})[hospital] = job_id
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    p = argparse.ArgumentParser(description="生产 S8 补充：外来器械 + reprice 全量保存")
    p.add_argument("--api-base", default=PROD_API)
    p.add_argument("--mode", choices=["docker", "direct"], default="direct")
    p.add_argument("--job-map", type=Path, default=ROOT / "测试用例/job_baseline_prod.json")
    p.add_argument("--hospital", action="append", default=[])
    args = p.parse_args()

    configure_client(mode=args.mode, api_base=args.api_base)
    jobs = load_prod_jobs(args.job_map)
    targets = args.hospital or REPRICE_TARGETS

    for name in targets:
        job_id = jobs.get(name)
        if not job_id:
            print(f"⏭ {name}: 无 prod Job")
            continue
        print(f">> {name} Job #{job_id}")
        rel = EXTERNAL_IMPORTS.get(name)
        if rel:
            try:
                import_external_direct(job_id, ROOT / rel)
            except Exception as exc:
                print(f"  external import FAIL: {exc}")
        try:
            new_id = reprice_save_all(job_id)
            if new_id != job_id:
                update_prod_map(args.job_map, name, new_id)
                jobs[name] = new_id
        except Exception as exc:
            print(f"  reprice FAIL: {exc}")
    print(f"完成。Job map: {args.job_map}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
