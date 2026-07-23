#!/usr/bin/env python3
"""Collect unmatched bill files into 测试用例/待匹配/."""

from __future__ import annotations

import json
import shutil
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET_DIR = ROOT / "测试用例"
UNMATCHED_DIR = TARGET_DIR / "待匹配"

RAW_AI_DIR = ROOT / "铂康" / "AI账单（原始未处理的）"
PROC_2026_DIR = ROOT / "铂康" / "2026年账单(正确的)"
PROC_DONE_DIR = ROOT / "铂康" / "已做完账单(1)"
RAW_DATA4_DIR = ROOT / "铂康" / "账单原始数据-4(1)" / "账单原始数据-4"

DATA_EXTS = {".xlsx", ".xls", ".csv"}


def collect_data_files(folder: Path, recursive: bool = False) -> list[Path]:
    if not folder.exists():
        return []
    paths = folder.rglob("*") if recursive else folder.iterdir()
    return [
        p
        for p in paths
        if p.is_file()
        and p.suffix.lower() in DATA_EXTS
        and not p.name.startswith("~$")
    ]


def dest_name_for_rel(src: Path, rel_base: Path) -> str:
    rel = src.relative_to(rel_base)
    if len(rel.parts) > 1:
        return "__".join(rel.parts)
    return rel.name


def build_organized_inventory() -> tuple[set[str], set[str]]:
    """Return (raw_basenames, processed_dest_names) already in hospital folders."""
    raw_names: set[str] = set()
    proc_names: set[str] = set()
    for hospital_dir in TARGET_DIR.iterdir():
        if not hospital_dir.is_dir() or hospital_dir.name == "待匹配":
            continue
        raw_dir = hospital_dir / "原始表格"
        proc_dir = hospital_dir / "处理后表格"
        if raw_dir.is_dir():
            raw_names.update(p.name for p in raw_dir.iterdir() if p.is_file())
        if proc_dir.is_dir():
            proc_names.update(p.name for p in proc_dir.iterdir() if p.is_file())
    return raw_names, proc_names


def unique_dest_name(dest_dir: Path, filename: str) -> str:
    dest = dest_dir / filename
    if not dest.exists():
        return filename
    stem = Path(filename).stem
    suffix = Path(filename).suffix
    i = 1
    while True:
        candidate = f"{stem}_{i}{suffix}"
        if not (dest_dir / candidate).exists():
            return candidate
        i += 1


def copy_unmatched(
    src: Path,
    dest_dir: Path,
    dest_name: str,
    source_hint: str | None = None,
) -> tuple[str, str]:
    """Copy file; prefix with source hint on duplicate basename. Returns (dest_name, status)."""
    dest_dir.mkdir(parents=True, exist_ok=True)
    final_name = dest_name
    if (dest_dir / final_name).exists():
        stem = Path(dest_name).stem
        suffix = Path(dest_name).suffix
        hint = source_hint or "dup"
        final_name = f"{hint}__{stem}{suffix}"
        final_name = unique_dest_name(dest_dir, final_name)
        status = "duplicate_renamed"
    else:
        status = "copied"
    shutil.copy2(src, dest_dir / final_name)
    return final_name, status


def main() -> None:
    UNMATCHED_DIR.mkdir(parents=True, exist_ok=True)
    raw_dest = UNMATCHED_DIR / "原始表格"
    proc_dest = UNMATCHED_DIR / "处理后表格"
    raw_dest.mkdir(exist_ok=True)
    proc_dest.mkdir(exist_ok=True)

    organized_raw, organized_proc = build_organized_inventory()

    report: dict = {
        "raw_copied": [],
        "processed_copied": [],
        "raw_skipped_organized": [],
        "processed_skipped_organized": [],
        "duplicate_renamed": [],
        "ambiguous": [],
        "sources_missing": [],
    }

    # --- Raw: AI账单 ---
    for src in collect_data_files(RAW_AI_DIR, recursive=False):
        name = src.name
        if name in organized_raw:
            report["raw_skipped_organized"].append({"source": "AI账单", "file": name})
            continue
        dest_name, status = copy_unmatched(src, raw_dest, name, "AI账单")
        report["raw_copied"].append({"source": "AI账单", "src": str(src), "dest": dest_name})
        if status == "duplicate_renamed":
            report["duplicate_renamed"].append({"dest": dest_name, "src": str(src)})

    # --- Raw: 账单原始数据-4 ---
    for src in collect_data_files(RAW_DATA4_DIR, recursive=False):
        name = src.name
        if name in organized_raw:
            report["raw_skipped_organized"].append({"source": "账单原始数据-4", "file": name})
            continue
        dest_name, status = copy_unmatched(src, raw_dest, name, "账单原始数据-4")
        report["raw_copied"].append(
            {"source": "账单原始数据-4", "src": str(src), "dest": dest_name}
        )
        if status == "duplicate_renamed":
            report["duplicate_renamed"].append({"dest": dest_name, "src": str(src)})

    # --- Processed: 2026年账单(正确的) ---
    if not PROC_2026_DIR.exists():
        report["sources_missing"].append(str(PROC_2026_DIR))
    for src in collect_data_files(PROC_2026_DIR, recursive=True):
        dest_name = dest_name_for_rel(src, PROC_2026_DIR)
        if dest_name in organized_proc:
            report["processed_skipped_organized"].append(
                {"source": "2026年账单", "file": dest_name}
            )
            continue
        final_name, status = copy_unmatched(src, proc_dest, dest_name, "2026年账单")
        report["processed_copied"].append(
            {"source": "2026年账单", "src": str(src.relative_to(PROC_2026_DIR)), "dest": final_name}
        )
        if status == "duplicate_renamed":
            report["duplicate_renamed"].append({"dest": final_name, "src": str(src)})

    # --- Processed: 已做完账单(1) ---
    if not PROC_DONE_DIR.exists():
        report["sources_missing"].append(str(PROC_DONE_DIR))
    for src in collect_data_files(PROC_DONE_DIR, recursive=True):
        dest_name = dest_name_for_rel(src, PROC_DONE_DIR)
        if dest_name in organized_proc:
            report["processed_skipped_organized"].append(
                {"source": "已做完账单", "file": dest_name}
            )
            continue
        final_name, status = copy_unmatched(src, proc_dest, dest_name, "已做完账单")
        report["processed_copied"].append(
            {"source": "已做完账单", "src": str(src.relative_to(PROC_DONE_DIR)), "dest": final_name}
        )
        if status == "duplicate_renamed":
            report["duplicate_renamed"].append({"dest": final_name, "src": str(src)})

    # Also ensure known orphans from supplementary report are present
    supp_report = TARGET_DIR / "supplementary_add_report.json"
    if supp_report.exists():
        data = json.loads(supp_report.read_text(encoding="utf-8"))
        for rel_path, _score in data.get("processed_orphans", []):
            src = PROC_DONE_DIR / rel_path
            if not src.exists():
                report["ambiguous"].append({"reason": "orphan_source_missing", "path": rel_path})
                continue
            dest_name = dest_name_for_rel(src, PROC_DONE_DIR)
            dest_file = proc_dest / dest_name
            if dest_file.exists():
                continue
            if dest_name in organized_proc:
                continue
            final_name, status = copy_unmatched(src, proc_dest, dest_name, "已做完账单")
            report["processed_copied"].append(
                {"source": "已做完账单(orphan)", "src": rel_path, "dest": final_name}
            )

    report["counts"] = {
        "raw": len(list(raw_dest.iterdir())),
        "processed": len(list(proc_dest.iterdir())),
    }

    report_path = UNMATCHED_DIR / "collection_report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(json.dumps(report["counts"], ensure_ascii=False, indent=2))
    print(f"raw_copied: {len(report['raw_copied'])}")
    print(f"processed_copied: {len(report['processed_copied'])}")
    print(f"raw_skipped_organized: {len(report['raw_skipped_organized'])}")
    print(f"processed_skipped_organized: {len(report['processed_skipped_organized'])}")
    print(f"duplicate_renamed: {len(report['duplicate_renamed'])}")


if __name__ == "__main__":
    main()
