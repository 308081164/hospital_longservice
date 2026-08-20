#!/usr/bin/env python3
"""Ingest 铂康/8.19新增/第二批 into 测试用例/ folder structure."""

from __future__ import annotations

import argparse
import json
import shutil
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "铂康" / "8.19新增" / "第二批"
TEST_CASE_DIR = ROOT / "测试用例"
MANIFEST_PATH = TEST_CASE_DIR / "819第二批入库清单.json"

# (源文件名, 规范医院目录名, 目标文件名)
PROC_FILES: list[tuple[str, str, str]] = [
    ("呼兰中医院.xlsx", "呼兰中医院", "7月__呼兰中医院账单.xlsx"),
    ("哈尔滨基准生物科技有限公司.xlsx", "基准生物", "7月__基准生物账单.xlsx"),
    ("哈尔滨市道里区妇幼保健院.xlsx", "道里区妇幼保健院", "7月__道里妇幼账单.xlsx"),
    ("平房区人民医院.xlsx", "哈尔滨市平房区人民医院", "7月__平房区人民账单.xlsx"),
    ("春语医疗美容医院.xlsx", "春语医美", "7月__春语账单.xlsx"),
    ("省监狱管理局医院.xlsx", "省监狱管理局医院", "7月__省监狱账单.xlsx"),
    ("索菲医疗美容门诊.xlsx", "索菲医美", "7月__索菲账单.xlsx"),
    ("黑龙江总工会医院.xlsx", "总工会", "7月__总工会账单.xlsx"),
    ("黑龙江省妇幼保健院（人口）.xlsx", "黑龙江省妇幼保健院（人口）", "7月__人口账单.xlsx"),
    ("黑龙江省社会康复医院xlsx.xlsx", "黑龙江省社会康复医院", "7月__社会康复账单.xlsx"),
]

# 7 月 strict 成对：已有 7 月原始表 + 本批次处理后
STRICT_JULY_FOLDERS = {
    "黑龙江省妇幼保健院（人口）",
    "索菲医美",
    "总工会",
}

# 错位 raw 文件：从 处理后表格 移回 原始表格
RAW_RELOCATIONS: list[tuple[str, str, str]] = [
    ("索菲医美", "7月__索菲7月原始.xlsx", "7月__索菲7月原始.xlsx"),
    ("总工会", "7月__总工会7月原始.xlsx", "7月__总工会7月原始.xlsx"),
]

# 仅含处理后目录的院，补建 原始表格/
RAW_DIR_ONLY: list[str] = [
    "基准生物",
    "道里区妇幼保健院",
    "春语医美",
    "省监狱管理局医院",
    "哈尔滨市平房区人民医院",
]


@dataclass
class IngestEntry:
    source: str
    dest: str
    hospital: str
    kind: str  # proc | raw_reloc
    month: int | None
    strict_eligible: bool
    note: str = ""


def ensure_dirs(hospital: str, *, raw: bool = False, proc: bool = False) -> Path:
    base = TEST_CASE_DIR / hospital
    if raw:
        (base / "原始表格").mkdir(parents=True, exist_ok=True)
    if proc:
        (base / "处理后表格").mkdir(parents=True, exist_ok=True)
    return base


def relocate_raw(hospital: str, src_name: str, dest_name: str, *, dry_run: bool) -> IngestEntry | None:
    wrong = TEST_CASE_DIR / hospital / "处理后表格" / src_name
    dest = TEST_CASE_DIR / hospital / "原始表格" / dest_name
    if not wrong.is_file():
        return None
    if not dry_run:
        ensure_dirs(hospital, raw=True)
        if dest.exists() and dest.stat().st_size == wrong.stat().st_size:
            wrong.unlink(missing_ok=True)
        else:
            shutil.move(str(wrong), str(dest))
    return IngestEntry(
        source=str(wrong.relative_to(ROOT)),
        dest=str(dest.relative_to(ROOT)),
        hospital=hospital,
        kind="raw_reloc",
        month=7,
        strict_eligible=hospital in STRICT_JULY_FOLDERS,
        note="7月原始表从处理后表格归位",
    )


def ingest(*, dry_run: bool = False) -> list[IngestEntry]:
    entries: list[IngestEntry] = []

    for hospital in RAW_DIR_ONLY:
        if not dry_run:
            ensure_dirs(hospital, raw=True, proc=True)

    for hospital, src_name, dest_name in RAW_RELOCATIONS:
        entry = relocate_raw(hospital, src_name, dest_name, dry_run=dry_run)
        if entry:
            entries.append(entry)

    for rel_src, hospital, dest_name in PROC_FILES:
        src = SOURCE_DIR / rel_src
        if not src.is_file():
            continue
        if not dry_run:
            ensure_dirs(hospital, proc=True)
            if hospital in RAW_DIR_ONLY:
                ensure_dirs(hospital, raw=True)
            dest = TEST_CASE_DIR / hospital / "处理后表格" / dest_name
            shutil.copy2(src, dest)
            dest_rel = str(dest.relative_to(ROOT))
        else:
            dest_rel = f"测试用例/{hospital}/处理后表格/{dest_name}"
        entries.append(
            IngestEntry(
                source=str(src.relative_to(ROOT)),
                dest=dest_rel,
                hospital=hospital,
                kind="proc",
                month=7,
                strict_eligible=hospital in STRICT_JULY_FOLDERS,
                note="819第二批7月处理后",
            )
        )

    return entries


def write_manifest(entries: list[IngestEntry]) -> None:
    payload = {
        "source_dir": str(SOURCE_DIR.relative_to(ROOT)),
        "strict_july_folders": sorted(STRICT_JULY_FOLDERS),
        "entries": [asdict(e) for e in entries],
    }
    MANIFEST_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    p = argparse.ArgumentParser(description="入库 铂康/8.19新增/第二批 至 测试用例/")
    p.add_argument("--write", action="store_true", help="执行复制/归位并写入 manifest")
    p.add_argument("--dry-run", action="store_true", help="仅预览 manifest，不写文件")
    args = p.parse_args()
    if not SOURCE_DIR.is_dir():
        print(f"源目录不存在: {SOURCE_DIR}", file=__import__("sys").stderr)
        return 1
    entries = ingest(dry_run=args.dry_run and not args.write)
    if args.write:
        entries = ingest(dry_run=False)
        write_manifest(entries)
        print(f"已入库 {len(entries)} 项 → {MANIFEST_PATH}")
    else:
        print(json.dumps([asdict(e) for e in entries], ensure_ascii=False, indent=2))
    strict = [e for e in entries if e.strict_eligible]
    print(f"\n7 月 strict 可测: {len({e.hospital for e in strict})} 院")
    for h in sorted({e.hospital for e in strict}):
        print(f"  - {h}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
