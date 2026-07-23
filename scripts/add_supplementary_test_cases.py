#!/usr/bin/env python3
"""Incrementally add supplementary bill data into 测试用例/."""

from __future__ import annotations

import json
import shutil
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from reorganize_test_cases import (  # noqa: E402
    DATA_EXTS,
    REF_DIR,
    TARGET_DIR,
    collect_data_files,
    collect_hospitals,
    copy_files,
    match_file,
    reference_hints,
)

# 补充源目录已归档至 测试用例/{医院}/ 与 测试用例/待匹配/；路径保留供重新导入时使用
SUPP_RAW_DIR = ROOT / "铂康" / "账单原始数据-4(1)" / "账单原始数据-4"
SUPP_PROC_DIR = ROOT / "铂康" / "已做完账单(1)"
UNMATCHED_DIR = TARGET_DIR / "待匹配"


def ensure_folder(hospital_dir: Path, kind: str) -> Path:
    """Return 原始表格/ or 处理后表格/, upgrading 【缺】 folders when adding files."""
    missing = f"【缺】{kind}"
    normal = kind
    missing_path = hospital_dir / missing
    normal_path = hospital_dir / normal

    if normal_path.exists():
        return normal_path
    if missing_path.exists():
        missing_path.rename(normal_path)
        return normal_path
    normal_path.mkdir(parents=True, exist_ok=True)
    return normal_path


def dest_name_for_processed(src: Path, rel_base: Path) -> str:
    rel = src.relative_to(rel_base)
    if len(rel.parts) > 1:
        return "__".join(rel.parts)
    return rel.name


def file_already_present(dest_dir: Path, name: str) -> bool:
    return (dest_dir / name).exists()


def main() -> None:
    hospitals = collect_hospitals()
    hint_map = {h: reference_hints(h) for h in hospitals}

    raw_files = [
        p
        for p in collect_data_files(SUPP_RAW_DIR, recursive=False)
        if not p.name.startswith("~$")
    ]
    proc_files = [
        p
        for p in collect_data_files(SUPP_PROC_DIR, recursive=True)
        if not p.name.startswith("~$")
    ]

    raw_map: dict[str, list[Path]] = defaultdict(list)
    proc_map: dict[str, list[Path]] = defaultdict(list)
    raw_orphans: list[tuple[str, int]] = []
    proc_orphans: list[tuple[str, int]] = []

    for p in raw_files:
        h, score = match_file(p, hospitals, hint_map)
        if h:
            raw_map[h].append(p)
        else:
            raw_orphans.append((p.name, score))

    for p in proc_files:
        h, score = match_file(p, hospitals, hint_map)
        if h:
            proc_map[h].append(p)
        else:
            proc_orphans.append((str(p.relative_to(SUPP_PROC_DIR)), score))

    stats = {
        "raw_added": 0,
        "raw_skipped_existing": 0,
        "processed_added": 0,
        "processed_skipped_existing": 0,
        "hospitals_touched": set(),
    }
    per_hospital: dict[str, dict[str, list[str]]] = defaultdict(
        lambda: {"raw_added": [], "raw_skipped": [], "processed_added": [], "processed_skipped": []}
    )

    for hospital in hospitals:
        hospital_dir = TARGET_DIR / hospital
        if not hospital_dir.exists():
            hospital_dir.mkdir(parents=True, exist_ok=True)

        for src in raw_map.get(hospital, []):
            dest_dir = ensure_folder(hospital_dir, "原始表格")
            name = src.name
            if file_already_present(dest_dir, name):
                stats["raw_skipped_existing"] += 1
                per_hospital[hospital]["raw_skipped"].append(name)
                continue
            shutil.copy2(src, dest_dir / name)
            stats["raw_added"] += 1
            stats["hospitals_touched"].add(hospital)
            per_hospital[hospital]["raw_added"].append(name)

        for src in proc_map.get(hospital, []):
            dest_dir = ensure_folder(hospital_dir, "处理后表格")
            name = dest_name_for_processed(src, SUPP_PROC_DIR)
            if file_already_present(dest_dir, name):
                stats["processed_skipped_existing"] += 1
                per_hospital[hospital]["processed_skipped"].append(name)
                continue
            shutil.copy2(src, dest_dir / name)
            stats["processed_added"] += 1
            stats["hospitals_touched"].add(hospital)
            per_hospital[hospital]["processed_added"].append(name)

    report = {
        "sources": {
            "raw": str(SUPP_RAW_DIR),
            "processed": str(SUPP_PROC_DIR),
        },
        "raw_source_files": len(raw_files),
        "processed_source_files": len(proc_files),
        "raw_added": stats["raw_added"],
        "raw_skipped_existing": stats["raw_skipped_existing"],
        "processed_added": stats["processed_added"],
        "processed_skipped_existing": stats["processed_skipped_existing"],
        "hospitals_touched_count": len(stats["hospitals_touched"]),
        "hospitals_touched": sorted(stats["hospitals_touched"]),
        "raw_orphans": raw_orphans,
        "processed_orphans": proc_orphans,
        "per_hospital": {
            h: v
            for h, v in sorted(per_hospital.items())
            if any(v[k] for k in v)
        },
    }

    report_path = TARGET_DIR / "supplementary_add_report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(json.dumps({k: v for k, v in report.items() if k != "per_hospital"}, ensure_ascii=False, indent=2))
    if raw_orphans:
        print("\n--- 未匹配原始文件 ---")
        for name, score in raw_orphans:
            print(f"  {name} (score={score})")
    if proc_orphans:
        print("\n--- 未匹配处理后文件 ---")
        for name, score in proc_orphans[:30]:
            print(f"  {name} (score={score})")
        if len(proc_orphans) > 30:
            print(f"  ... 共 {len(proc_orphans)} 个")


if __name__ == "__main__":
    main()
