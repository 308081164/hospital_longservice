#!/usr/bin/env python3
"""将本地 stable Job 行数据同步到生产 Job（仅用于 S8 lineage 对齐）。"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from fuyi_june_closeout import fetch_all_rows, save_repriced_rows  # noqa: E402
from lib.api_client import configure_client, get_client  # noqa: E402

LOCAL_API = "http://127.0.0.1:1001"
PROD_API = "http://39.102.213.51:8853"

# local stable -> prod job（校准后）
ROW_SYNC_PAIRS: dict[str, tuple[int, int]] = {
    "黑龙江省医院（南岗院区）": (616, 368),
    "黑龙江省医院（香坊院区）": (617, 369),
    "哈尔滨市第五医院": (613, 366),
    "国药总医院第三院区": (647, 365),
    "黑龙江中医药大学附属第一医院": (607, 363),
    "新发红十字医院": (615, 367),
    "祖研-黑龙江省中医医院（三辅院区）": (619, 370),
    "黑龙江中医药大学附属第二医院（哈南分院）": (634, 371),
    "哈尔滨冰城医疗美容医院": (737, 359),
}


def fetch_rows(api: str, job_id: int) -> list[dict]:
    configure_client(mode="direct", api_base=api)
    get_client().login(force=True)
    token = get_client()._token
    return fetch_all_rows(token, job_id)


def sync_pair(local_job: int, prod_job: int, local_api: str, prod_api: str) -> int:
    rows = fetch_rows(local_api, local_job)
    print(f"  local #{local_job} → {len(rows)} rows")
    configure_client(mode="direct", api_base=prod_api)
    get_client().login(force=True)
    token = get_client()._token
    new_id = save_repriced_rows(token, prod_job, rows)
    print(f"  prod #{prod_job} → #{new_id}")
    return new_id


def update_prod_map(path: Path, hospital: str, job_id: int) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    data.setdefault("jobs", {})[hospital] = job_id
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    p = argparse.ArgumentParser(description="同步 stable 行到 prod Job")
    p.add_argument("--local-api", default=LOCAL_API)
    p.add_argument("--prod-api", default=PROD_API)
    p.add_argument("--job-map", type=Path, default=ROOT / "测试用例/job_baseline_prod.json")
    p.add_argument("--hospital", action="append", default=[])
    args = p.parse_args()
    targets = args.hospital or list(ROW_SYNC_PAIRS.keys())
    jobs = json.loads(args.job_map.read_text(encoding="utf-8")).get("jobs", {})

    for name in targets:
        pair = ROW_SYNC_PAIRS.get(name)
        if not pair:
            print(f"⏭ {name}: 无映射")
            continue
        local_j, prod_j = pair
        prod_j = int(jobs.get(name, prod_j))
        print(f">> {name} stable #{local_j} → prod #{prod_j}")
        try:
            new_id = sync_pair(local_j, prod_j, args.local_api, args.prod_api)
            if new_id != prod_j:
                update_prod_map(args.job_map, name, new_id)
        except Exception as exc:
            print(f"  FAIL: {exc}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
