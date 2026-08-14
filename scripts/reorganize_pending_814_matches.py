#!/usr/bin/env python3
"""Move 814/v8-related files from 测试用例/待匹配/ into hospital folders."""

from __future__ import annotations

import argparse
import json
import shutil
from dataclasses import asdict, dataclass
from fnmatch import fnmatch
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE_DIR = ROOT / "测试用例"
PENDING = TEST_CASE_DIR / "待匹配"
MANIFEST_OUT = TEST_CASE_DIR / "814待匹配归位清单.json"

# (target_hospital, kind, glob_patterns)
MOVE_RULES: list[tuple[str, str, list[str]]] = [
    ("黑龙江省妇幼保健院（人口）", "raw", ["7月__人口原始.xlsx"]),
    ("黑龙江省妇幼保健院（人口）", "proc", ["*人口*"]),
    ("博尚医院", "proc", ["*博尚*"]),
    ("道里区妇幼保健院", "proc", ["*道里妇幼*"]),
    ("春语医美", "proc", ["*春语*"]),
    ("总工会", "proc", ["*总工会*"]),
    ("基准生物", "proc", ["*基准生物*"]),
    ("索菲医美", "proc", ["*索菲*"]),
    ("黑龙江省海员总医院（道外）", "proc", ["*道外海员*", "*海员总医院（道外）*"]),
    ("东北农业大学", "proc", ["*东北农大*"]),
    ("方南南医院", "proc", ["*方南南*"]),
    ("哈尔滨市公安医院", "proc", ["*公安*"]),
    ("和平社区", "proc", ["*和平社区*"]),
    ("奥美", "proc", ["*奥美*"]),
    ("媛尚美", "proc", ["*媛尚美*"]),
    ("黑龙江中医药大学附属第四医院", "proc", ["*中医附四*"]),
    ("航天风华", "proc", ["*航天*"]),
    ("松电慢病", "proc", ["*松电*"]),
    ("黑龙江省海员总医院（松北）", "proc", ["*松北海员*", "*海员总医院（松北）*"]),
]


@dataclass
class MoveEntry:
    source: str
    dest: str
    hospital: str
    kind: str


def _kind_dir(kind: str) -> str:
    return "原始表格" if kind == "raw" else "处理后表格"


def _ensure_hospital_dirs(hospital: str, *, raw: bool = False, proc: bool = False) -> None:
    base = TEST_CASE_DIR / hospital
    if raw:
        (base / "原始表格").mkdir(parents=True, exist_ok=True)
    if proc:
        (base / "处理后表格").mkdir(parents=True, exist_ok=True)


def collect_moves() -> list[MoveEntry]:
    raw_dir = PENDING / "原始表格"
    proc_dir = PENDING / "处理后表格"
    claimed: set[Path] = set()
    moves: list[MoveEntry] = []

    for hospital, kind, patterns in MOVE_RULES:
        src_root = raw_dir if kind == "raw" else proc_dir
        if not src_root.is_dir():
            continue
        for path in sorted(src_root.iterdir()):
            if path.suffix.lower() not in {".xlsx", ".xls"}:
                continue
            if path in claimed:
                continue
            if not any(fnmatch(path.name, pat) for pat in patterns):
                continue
            claimed.add(path)
            _ensure_hospital_dirs(hospital, raw=(kind == "raw"), proc=(kind == "proc"))
            dest = TEST_CASE_DIR / hospital / _kind_dir(kind) / path.name
            moves.append(
                MoveEntry(
                    source=str(path.relative_to(ROOT)),
                    dest=str(dest.relative_to(ROOT)),
                    hospital=hospital,
                    kind=kind,
                )
            )
    return moves


def apply_moves(moves: list[MoveEntry], *, dry_run: bool) -> None:
    for entry in moves:
        src = ROOT / entry.source
        dest = ROOT / entry.dest
        if dry_run:
            print(f"  {entry.source} -> {entry.dest}")
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        if dest.is_file():
            dest.unlink()
        shutil.move(str(src), str(dest))


def write_manifest(moves: list[MoveEntry]) -> None:
    payload = {
        "source_dir": str(PENDING.relative_to(ROOT)),
        "move_count": len(moves),
        "entries": [asdict(m) for m in moves],
    }
    MANIFEST_OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    p = argparse.ArgumentParser(description="814 待匹配文件归位")
    p.add_argument("--write", action="store_true", help="执行搬迁")
    p.add_argument("--dry-run", action="store_true", help="仅预览")
    args = p.parse_args()
    if not PENDING.is_dir():
        print(f"待匹配目录不存在: {PENDING}")
        return 1

    moves = collect_moves()
    print(f"匹配 {len(moves)} 个文件")
    apply_moves(moves, dry_run=not args.write)

    if args.write:
        write_manifest(moves)
        print(f"已写入 manifest: {MANIFEST_OUT}")
    elif args.dry_run:
        print("使用 --write 执行搬迁")
    else:
        for m in moves[:10]:
            print(f"  {m.source} -> {m.dest}")
        if len(moves) > 10:
            print(f"  ... 共 {len(moves)} 项，使用 --dry-run 查看全部")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
