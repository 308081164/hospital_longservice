#!/usr/bin/env python3
"""将 6月系统warning.tsv 中尚未列入期待清单的键补进 6月期待价格校正清单.csv（消多报）。"""
from __future__ import annotations

import csv
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE = ROOT / "测试用例"


def warn_key(ship_no: str, pack_name: str, pack_count: str | float | None) -> str:
    pc = pack_count if pack_count is not None else 0
    try:
        pc_norm = f"{float(pc):.4g}"
    except (TypeError, ValueError):
        pc_norm = str(pc)
    return f"{ship_no}|{pack_name}|{pc_norm}"


def sync_hospital(hospital_dir: Path) -> int:
    csv_path = hospital_dir / "6月期待价格校正清单.csv"
    tsv_path = hospital_dir / "6月系统warning.tsv"
    if not tsv_path.exists():
        return 0
    existing: list[dict] = []
    keys: set[str] = set()
    fieldnames = [
        "科室", "原始行", "发货单号", "包名", "包数",
        "原单价", "处理后单价", "原总价", "处理后总价",
        "规则覆盖", "匹配规则", "说明",
    ]
    if csv_path.exists():
        with csv_path.open(encoding="utf-8-sig") as f:
            reader = csv.DictReader(f)
            for row in reader:
                existing.append(row)
                keys.add(warn_key(row["发货单号"], row["包名"], row.get("包数")))
    added = 0
    with tsv_path.open(encoding="utf-8") as f:
        for row in csv.DictReader(f, delimiter="\t"):
            key = warn_key(row["orderNo"], row["packName"], row.get("packCount"))
            if key in keys:
                continue
            keys.add(key)
            unit = float(row["unitPrice"]) if row.get("unitPrice") else ""
            rule_u = float(row["ruleUnit"]) if row.get("ruleUnit") else ""
            pc = row.get("packCount") or ""
            existing.append({
                "科室": row.get("sheet") or "",
                "原始行": row.get("row") or "",
                "发货单号": row["orderNo"],
                "包名": row["packName"],
                "包数": pc,
                "原单价": unit,
                "处理后单价": rule_u,
                "原总价": "",
                "处理后总价": "",
                "规则覆盖": "system_warning",
                "匹配规则": row.get("pricingRule") or "",
                "说明": "由系统 warning 补录（导入价 vs 规则价，Excel 逐行未 diff）",
            })
            added += 1
    if added:
        with csv_path.open("w", encoding="utf-8-sig", newline="") as f:
            w = csv.DictWriter(f, fieldnames=fieldnames)
            w.writeheader()
            w.writerows(existing)
    return added


def main() -> int:
    names = sys.argv[1:] if len(sys.argv) > 1 else []
    if not names:
        print("用法: sync_june_expected_from_warnings.py 医院名 …")
        return 1
    for name in names:
        d = TEST_CASE / name
        n = sync_hospital(d)
        print(f"{name}: +{n} 期待行")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
