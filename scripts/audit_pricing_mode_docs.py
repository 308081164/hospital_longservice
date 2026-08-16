#!/usr/bin/env python3
"""Audit and optionally fix hybrid pricing mode wording in markdown docs."""

from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCAN_DIRS = [ROOT / "docs", ROOT / "测试用例"]
FOOTNOTE = "\n> **2026-08 引擎口径**：`hybrid` 未命中特色规则时走标准灭菌阶梯（含 standardPricingOverride）；`special_only` 未命中时保留原价。\n"

REPLACEMENTS = [
    (re.compile(r"hybrid\s*未命中特色规则"), "hybrid 走标准灭菌路径"),
    (re.compile(r"hybrid.*保留原价"), "hybrid 未命中走标准灭菌价"),
    (re.compile(r"混合（未命中保留原价[^）]*）"), "混合（未命中走标准灭菌阶梯价）"),
    (re.compile(r"混合模式：未命中规则时走标准灭菌阶梯价"), "混合模式：未命中规则时走标准灭菌阶梯价"),
]

WRONG_PATTERNS = [
    re.compile(r"hybrid.*保留原价"),
    re.compile(r"hybrid\s*未命中特色规则"),
    re.compile(r"混合（未命中保留原价"),
]


def scan_file(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    issues: list[str] = []
    for i, line in enumerate(text.splitlines(), 1):
        for pattern in WRONG_PATTERNS:
            if pattern.search(line) and "2026-08 引擎口径" not in line:
                issues.append(f"{path.relative_to(ROOT)}:{i}: {line.strip()}")
    return issues


def fix_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    original = text
    for pattern, repl in REPLACEMENTS:
        text = pattern.sub(repl, text)
    if text != original:
        path.write_text(text, encoding="utf-8")
        return True
    return False


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fix", action="store_true", help="Apply safe text replacements")
    parser.add_argument("--footnote", action="store_true", help="Append footnote to files with issues")
    args = parser.parse_args()

    all_issues: list[str] = []
    for base in SCAN_DIRS:
        if not base.exists():
            continue
        for path in sorted(base.rglob("*.md")):
            issues = scan_file(path)
            if issues:
                all_issues.extend(issues)
                if args.fix:
                    fix_file(path)
                if args.footnote and "2026-08 引擎口径" not in path.read_text(encoding="utf-8"):
                    with path.open("a", encoding="utf-8") as fh:
                        fh.write(FOOTNOTE)

    print(f"Scanned markdown under docs/ and 测试用例/")
    print(f"Issues found: {len(all_issues)}")
    for item in all_issues[:80]:
        print(item)
    if len(all_issues) > 80:
        print(f"... and {len(all_issues) - 80} more")


if __name__ == "__main__":
    main()
