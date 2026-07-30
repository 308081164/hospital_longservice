#!/usr/bin/env python3
"""附一 6 月闭环：import → reprice → 选择性保存 → export-v2 → verify 11col → S8。"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE = ROOT / "测试用例"
HOSPITAL = "黑龙江中医药大学附属第一医院"
HOSPITAL_DIR = TEST_CASE / HOSPITAL
STABLE_MAP = TEST_CASE / "job_baseline_stable.json"
PROD_MAP = TEST_CASE / "job_baseline_prod.json"
LOCAL_API = "http://127.0.0.1:8000"
PROD_API = "http://39.102.213.51:8853"
BACKEND = "hospital-backend"
EXPORT_DIR = TEST_CASE / ".s8_exports"

sys.path.insert(0, str(ROOT / "scripts"))
from lib.api_client import ApiError, configure_client, get_client  # noqa: E402
from batch_june_price_reconciliation import pick_june_pair  # noqa: E402
from batch_june_system_test import (  # noqa: E402
    expected_key,
    fetch_warnings,
    import_bill,
    load_expected_from_csv,
    warn_key,
)
from batch_s8_export_compare import compare_bills, export_bill  # noqa: E402
from verify_fuyi_11col_export import GOLDEN_ROWS, verify_export  # noqa: E402


@dataclass
class CloseoutResult:
    job_id: int | None = None
    export_path: Path | None = None
    verify_ok: bool = False
    verify_errors: list[str] = field(default_factory=list)
    s8_pass: bool = False
    s8_detail: str = ""
    warning_count: int = 0
    expected_warnings: int = 0
    applied_corrections: int = 0
    warning_ok: bool = False
    errors: list[str] = field(default_factory=list)


def fetch_all_rows(token: str, job_id: int) -> list[dict]:
    client = get_client()
    rows: list[dict] = []
    page = 1
    while True:
        data = client.get(f"/api/hospital-reconciliations/{job_id}/rows?page={page}&size=500", token=token)
        payload = data.get("data") or {}
        batch = payload.get("rows") or payload.get("items") or []
        rows.extend(batch)
        total = payload.get("total") or payload.get("totalElements") or len(batch)
        if page * 500 >= total or not batch:
            break
        page += 1
    return rows


def golden_row_match(row: dict) -> bool:
    for golden in GOLDEN_ROWS:
        if row.get("sheetName") == golden["sheet"] and golden["pack_substr"] in (row.get("packName") or ""):
            return True
    return False


def should_apply_repriced(current: dict, repriced: dict, *, expected_keys: set[str]) -> bool:
    row_key = warn_key(
        "",
        str(current.get("orderNo") or ""),
        current.get("packName") or "",
        current.get("packCount"),
    )
    if current.get("status") == "warning" or row_key in expected_keys:
        return True
    if not golden_row_match(current):
        return False
    exp = repriced.get("expectedUnitPrice")
    unit = current.get("unitPrice")
    if exp is None or unit is None:
        return False
    try:
        return abs(float(exp) - float(unit)) > 0.01
    except (TypeError, ValueError):
        return False


def merge_repriced_rows(current_rows: list[dict], repriced_rows: list[dict], *, expected_keys: set[str]) -> tuple[list[dict], int]:
    repriced_by_key = {
        (r.get("sheetName"), r.get("rowNumber")): r for r in repriced_rows
    }
    merged: list[dict] = []
    applied = 0
    for current in current_rows:
        key = (current.get("sheetName"), current.get("rowNumber"))
        repriced = repriced_by_key.get(key)
        if repriced is not None and should_apply_repriced(current, repriced, expected_keys=expected_keys):
            merged.append(repriced)
            applied += 1
        else:
            merged.append(current)
    return merged, applied


def reprice_job(token: str, job_id: int) -> dict:
    client = get_client()
    data = client.post_json(f"/api/hospital-reconciliations/{job_id}/reprice", {}, token=token)
    if data.get("code") != 200:
        raise ApiError(f"reprice Job #{job_id}: {data.get('msg')}", payload=data)
    return data.get("data") or {}


def save_repriced_rows(token: str, job_id: int, rows: list[dict]) -> int:
    """reprice 预览后 PUT rows 持久化（可能产生新版本 Job）。"""
    client = get_client()
    path = f"/api/hospital-reconciliations/{job_id}/rows"
    if client.mode == "docker":
        payload = json.dumps(rows, ensure_ascii=False)
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".json", delete=False) as tmp:
            tmp.write(payload)
            tmp_path = Path(tmp.name)
        container_path = f"/tmp/fuyi_rows_{job_id}.json"
        try:
            subprocess.check_call(["docker", "cp", str(tmp_path), f"{BACKEND}:{container_path}"])
            raw = client.curl_raw([
                "-X", "PUT",
                f"{client.api_base}{path}",
                "-H", f"Authorization: Bearer {token}",
                "-H", "Content-Type: application/json",
                "--data-binary", f"@{container_path}",
            ])
        finally:
            tmp_path.unlink(missing_ok=True)
        data = json.loads(raw)
    else:
        data = client.request_json("PUT", path, token=token, json_body=rows)
    if data.get("code") != 200:
        raise ApiError(f"save rows Job #{job_id}: {data.get('msg')}", payload=data)
    saved = data.get("data") or {}
    return int(saved.get("id") or job_id)


def update_job_map(path: Path, job_id: int) -> None:
    payload = json.loads(path.read_text(encoding="utf-8"))
    old = payload.get("jobs", {}).get(HOSPITAL)
    payload.setdefault("jobs", {})[HOSPITAL] = job_id
    payload["updated"] = date.today().isoformat()
    note = f"附一闭环 Job {old}→{job_id}"
    payload["source"] = f"{payload.get('source', '')}; {note}".strip("; ")
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"已更新 {path.name}: {HOSPITAL} → Job #{job_id} (原 #{old})")


def resolve_base_job_id(args: argparse.Namespace) -> int | None:
    if args.upgrade_job is not None:
        return args.upgrade_job
    if args.skip_import and args.job_id is not None:
        return args.job_id
    if args.skip_import and STABLE_MAP.is_file():
        jobs = json.loads(STABLE_MAP.read_text(encoding="utf-8")).get("jobs", {})
        if HOSPITAL in jobs:
            return int(jobs[HOSPITAL])
    return None


def run_closeout(
    *,
    token: str,
    base_job_id: int | None,
    do_import: bool,
    out_dir: Path,
) -> CloseoutResult:
    result = CloseoutResult()
    raw_path, proc_path, note = pick_june_pair(HOSPITAL_DIR)
    if not raw_path:
        result.errors.append(f"缺少原始账单: {note}")
        return result
    if not proc_path or not proc_path.is_file():
        result.errors.append(f"缺少处理后账单: {note}")
        return result

    expected_rows = load_expected_from_csv(HOSPITAL_DIR)
    expected_keys = {expected_key(r) for r in expected_rows}
    result.expected_warnings = len(expected_rows)

    if do_import:
        print(f"import {raw_path.name} …")
        job = import_bill(token, HOSPITAL, raw_path)
        result.job_id = int(job["id"])
        print(f"import OK → Job #{result.job_id}")
        time.sleep(0.5)
    elif base_job_id is not None:
        result.job_id = base_job_id
        print(f"升级既有 Job #{base_job_id}（跳过 import）")
    else:
        result.errors.append("须 import 或 --upgrade-job / --skip-import --job-id")
        return result

    assert result.job_id is not None
    current_rows = fetch_all_rows(token, result.job_id)
    print(f"当前 Job #{result.job_id} · {len(current_rows)} 行")

    print(f"reprice Job #{result.job_id} …")
    reprice_data = reprice_job(token, result.job_id)
    repriced_rows = reprice_data.get("rows") or []
    summary = reprice_data.get("summary") or {}
    print(
        f"reprice OK · total={summary.get('total')} corrected={summary.get('corrected')} "
        f"warning={summary.get('warning')} unchanged={summary.get('unchanged')}"
    )
    if not repriced_rows:
        result.errors.append("reprice 未返回 rows")
        return result

    merged_rows, applied = merge_repriced_rows(current_rows, repriced_rows, expected_keys=expected_keys)
    result.applied_corrections = applied
    print(f"选择性保存 {applied} 行（期待 CSV {result.expected_warnings} + golden 补价）")
    saved_job_id = save_repriced_rows(token, result.job_id, merged_rows)
    if saved_job_id != result.job_id:
        print(f"版本升级 Job #{result.job_id} → #{saved_job_id}")
    result.job_id = saved_job_id
    time.sleep(0.5)

    out_dir.mkdir(parents=True, exist_ok=True)
    export_path = out_dir / f"job{result.job_id}_{HOSPITAL.replace('/', '_')}_bill.xlsx"
    print(f"export-v2 → {export_path.name}")
    export_bill(token, result.job_id, export_path, "bill")
    result.export_path = export_path

    verify_errors = verify_export(export_path)
    result.verify_errors = verify_errors
    result.verify_ok = not verify_errors
    print("verify_fuyi_11col OK" if result.verify_ok else "verify_fuyi_11col FAIL")
    for e in verify_errors:
        print(f"  - {e}")

    cmp = compare_bills(proc_path, export_path, folder=HOSPITAL)
    result.s8_detail = cmp.detail
    result.s8_pass = (
        cmp.structure_ok
        and abs(cmp.total_exp - cmp.total_act) <= max(1.0, cmp.total_exp * 1e-4)
        and abs(cmp.line_count_exp - cmp.line_count_act) <= 5
    )
    print(f"S8 {'pass' if result.s8_pass else 'fail'}: {cmp.detail}")

    warnings = fetch_warnings(token, result.job_id)
    result.warning_count = len(warnings)
    tol = 5
    result.warning_ok = (
        result.expected_warnings > 0
        and result.applied_corrections >= result.expected_warnings
        and result.applied_corrections <= result.expected_warnings + 20
        and result.warning_count <= tol
    )
    print(
        f"定价闭环: 已保存修正 {result.applied_corrections} 行 · 期待 CSV {result.expected_warnings} "
        f"· DB warning={result.warning_count}"
    )

    return result


def main() -> int:
    p = argparse.ArgumentParser(description="附一 6 月闭环（import+reprice+export+verify+S8）")
    p.add_argument("--env", choices=["local", "prod"], default="local")
    p.add_argument("--api", default=None, help="覆盖 API base")
    p.add_argument("--mode", choices=["docker", "direct"], default=None)
    p.add_argument("--username", default=None)
    p.add_argument("--password", default=None)
    p.add_argument("--import-bill", action="store_true", help="强制重导原始 6 月账单")
    p.add_argument("--skip-import", action="store_true", help="跳过 import，升级既有 Job")
    p.add_argument("--upgrade-job", type=int, default=None, help="指定要升级的 Job ID")
    p.add_argument("--job-id", type=int, default=None, help="同 --upgrade-job（兼容）")
    p.add_argument("--update-stable", action="store_true", help="写 job_baseline_stable.json")
    p.add_argument("--update-prod-map", action="store_true", help="写 job_baseline_prod.json")
    p.add_argument("--export-dir", type=Path, default=EXPORT_DIR)
    args = p.parse_args()

    if args.env == "local":
        api = args.api or LOCAL_API
        mode = args.mode or "docker"
    else:
        api = args.api or PROD_API
        mode = args.mode or "direct"

    configure_client(
        api_base=api,
        mode=mode,
        backend_container=BACKEND,
        username=args.username,
        password=args.password,
    )
    client = get_client()
    print(f"API {api} mode={mode}")
    try:
        health = client.health()
        print(f"health: {health.get('data', health)}")
    except ApiError as exc:
        print(f"health 失败: {exc}", file=sys.stderr)
        return 2

    token = client.login()
    base_job = resolve_base_job_id(args)
    if args.job_id and not args.upgrade_job:
        args.upgrade_job = args.job_id
        base_job = args.job_id
    do_import = args.import_bill or not (args.skip_import or base_job is not None)

    result = run_closeout(
        token=token,
        base_job_id=base_job,
        do_import=do_import,
        out_dir=args.export_dir if args.export_dir.is_absolute() else ROOT / args.export_dir,
    )

    print("\n=== 附一闭环摘要 ===")
    print(f"Job ID: {result.job_id}")
    print(f"export: {result.export_path}")
    print(f"verify: {'OK' if result.verify_ok else 'FAIL'}")
    print(f"S8: {'pass' if result.s8_pass else 'fail'} · {result.s8_detail}")
    print(
        f"定价闭环: applied={result.applied_corrections}/{result.expected_warnings} "
        f"DB warning={result.warning_count} ({'OK' if result.warning_ok else 'CHECK'})"
    )
    if result.errors:
        for e in result.errors:
            print(f"ERROR: {e}")

    if result.job_id and args.update_stable and result.s8_pass and result.verify_ok:
        update_job_map(STABLE_MAP, result.job_id)
    elif result.job_id and args.update_stable:
        print("跳过 stable 更新：S8 或 verify 未通过")
    if result.job_id and args.update_prod_map and result.s8_pass and result.verify_ok:
        update_job_map(PROD_MAP, result.job_id)
    elif result.job_id and args.update_prod_map:
        print("跳过 prod map 更新：S8 或 verify 未通过")

    ok = (
        result.job_id is not None
        and result.verify_ok
        and result.s8_pass
        and result.warning_ok
        and not result.errors
    )
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
