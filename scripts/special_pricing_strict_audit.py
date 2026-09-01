#!/usr/bin/env python3
"""【路径 A】特殊计价医院逐家严格测试 — 锁定材料版。

口径约定见 docs/测试路径约定.md（.cursor/rules/billing-test-paths.mdc 强制）：
- 医院清单：STRICT_KEEP_CODES 22 家 + 正式新引入院（当前 +4）
- 材料/账期：锁定 测试用例/特殊计价严格测试-材料锁定.json
  （22 家 = 2026-08-27 基线报告实际使用的 raw+proc；新 4 家 = 测试用例目录内最优成对账期）
- 报告命名：测试用例/特殊计价严格对账报告-YYYYMMDD.{json,md}

禁止把本脚本用于 billing-seed EXPECTED 26 清单（那是路径 B 规则同步检查）。

用法：
    python3 scripts/special_pricing_strict_audit.py            # 全量（22+4）
    python3 scripts/special_pricing_strict_audit.py --only-new # 仅新 4 家
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from batch_june_price_reconciliation import pick_month_pair  # noqa: E402
from batch_june_system_test import init_api_from_args  # noqa: E402
from lib.api_client import configure_client, get_client  # noqa: E402
from special_v8_strict_excel_audit import (  # noqa: E402
    TEST_CASE_DIR,
    HospitalStrictResult,
    audit_hospital_strict,
    result_to_dict,
)

BASELINE_0827 = TEST_CASE_DIR / "特殊收费v8严格Excel对账报告-20260827.json"
LOCK_MANIFEST = TEST_CASE_DIR / "特殊计价严格测试-材料锁定.json"

# 正式新引入的特殊计价院（v17，2026-08-30 录入）
NEW_HOSPITALS: list[tuple[str, str]] = [
    ("HULAN-RM", "哈尔滨市呼兰区第一人民医院"),
    ("XINFA-HSZ", "新发红十字医院"),
    ("YUANDONG-XN", "黑龙江省远东心脑血管医院"),
    ("ZUYAN-SF", "祖研-黑龙江省中医医院（三辅院区）"),
]

# 新院账期 fallback 顺序（与 0827 基线脚本一致）
NEW_MONTH_FALLBACK = (7, 5, 4, 8, 6, 3)


def build_lock_manifest() -> dict:
    """从 0827 基线报告生成材料锁定清单；新 4 家按 fallback 探测成对账期。"""
    baseline = json.loads(BASELINE_0827.read_text(encoding="utf-8"))
    entries: list[dict] = []
    for section_rows in (baseline.get("sections") or {}).values():
        for h in section_rows:
            entries.append({
                "hospital": h["hospital"],
                "customer_label": h.get("customer_label") or h["hospital"],
                "group": "baseline22",
                "lock_month": h.get("month"),
                "lock_raw_file": h.get("raw_file") or None,
                "lock_proc_file": h.get("proc_file") or None,
                "baseline_status": h.get("status"),
                "baseline_skip_reason": h.get("message") if h.get("status") == "SKIP" else None,
            })
    for code, folder in NEW_HOSPITALS:
        month = None
        raw = proc = None
        d = TEST_CASE_DIR / folder
        if d.is_dir():
            for m in NEW_MONTH_FALLBACK:
                r, p, _ = pick_month_pair(d, m)
                if r and p:
                    month, raw, proc = m, r.name, p.name
                    break
        entries.append({
            "hospital": folder,
            "customer_label": folder,
            "customer_code": code,
            "group": "new_v17",
            "lock_month": month,
            "lock_raw_file": raw,
            "lock_proc_file": proc,
            "baseline_status": None,
            "baseline_skip_reason": None if month else "测试用例目录无 raw+proc 成对账单",
        })
    manifest = {
        "generated": date.today().isoformat(),
        "baseline_report": BASELINE_0827.name,
        "policy": "路径A：22家锁定0827基线材料；新引入院按 7→5→4→8→6→3 探测首个 raw+proc 成对账期",
        "hospitals": entries,
    }
    LOCK_MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return manifest


def run_audit(*, only_new: bool = False) -> dict:
    manifest = json.loads(LOCK_MANIFEST.read_text(encoding="utf-8")) if LOCK_MANIFEST.exists() else build_lock_manifest()
    configure_client(api_base="http://127.0.0.1:8000", mode="docker", backend_container="hospital-backend")
    init_api_from_args(argparse.Namespace(mode="docker", api_base="http://127.0.0.1:8000"))
    token = get_client().login()

    results: list[dict] = []
    for entry in manifest["hospitals"]:
        if only_new and entry["group"] != "new_v17":
            continue
        folder = entry["hospital"]
        month = entry.get("lock_month")
        if entry["group"] == "baseline22" and entry.get("baseline_status") == "SKIP":
            # 基线 SKIP 是业务判定（如 ground truth 陈旧），保持 SKIP 以保证同口径对比
            r = HospitalStrictResult(
                hospital=folder,
                customer_label=entry.get("customer_label") or folder,
                status="SKIP",
                message=f"基线SKIP（保持一致）：{entry.get('baseline_skip_reason') or '缺材料'}",
                month=month or 0,
            )
        elif not month:
            r = HospitalStrictResult(
                hospital=folder,
                customer_label=entry.get("customer_label") or folder,
                status="SKIP",
                message=entry.get("baseline_skip_reason") or "缺少 raw+proc 成对账单",
                month=0,
            )
        else:
            r = audit_hospital_strict(token, folder, month=month)
            # 材料偏离检查：实际使用的文件必须与锁定清单一致
            if entry.get("lock_raw_file") and (
                r.raw_file != entry["lock_raw_file"] or r.proc_file != entry["lock_proc_file"]
            ):
                r.dedupe_note = (r.dedupe_note or "") + (
                    f"【材料偏离】锁定 {entry['lock_raw_file']}|{entry['lock_proc_file']}，"
                    f"实际 {r.raw_file}|{r.proc_file}"
                )
        row = result_to_dict(r)
        row["group"] = entry["group"]
        row["customer_code"] = entry.get("customer_code")
        row["baseline_status"] = entry.get("baseline_status")
        results.append(row)
        print(
            f"{r.status} [{entry['group']}] {folder} ({r.month}月): {r.message} | "
            f"E={r.expected_count} W={r.pricing_warning_count} "
            f"missed={len(r.missed)} extra={len(r.extra)} priceErr={len(r.price_mismatch)}",
            flush=True,
        )

    summary: dict[str, int] = {}
    for row in results:
        summary[row["status"].lower()] = summary.get(row["status"].lower(), 0) + 1
    payload = {
        "generated": date.today().isoformat(),
        "api_base": "http://127.0.0.1:8000",
        "path": "A-特殊计价医院逐家严格测试",
        "lock_manifest": LOCK_MANIFEST.name,
        "hospitals": results,
        "summary": summary,
    }
    out = TEST_CASE_DIR / f"特殊计价严格对账报告-{date.today():%Y%m%d}.json"
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nWrote {out}")
    print("SUMMARY:", summary)
    return payload


def main() -> int:
    parser = argparse.ArgumentParser(description="路径A：特殊计价医院逐家严格测试（锁定材料）")
    parser.add_argument("--lock-only", action="store_true", help="只生成材料锁定清单，不跑测试")
    parser.add_argument("--only-new", action="store_true", help="只跑新引入院")
    args = parser.parse_args()
    if args.lock_only:
        m = build_lock_manifest()
        print(f"Wrote {LOCK_MANIFEST} ({len(m['hospitals'])} hospitals)")
        return 0
    run_audit(only_new=args.only_new)
    return 0


if __name__ == "__main__":
    sys.exit(main())
