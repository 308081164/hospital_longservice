#!/usr/bin/env python3
"""G7: baseline productRules 须独立维护——禁止多品类 @contains 关键词共条。"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINE_DIR = ROOT / "backend/src/main/resources/billing-rules/baseline"

CONTAINS_SUFFIX = re.compile(r"@contains$", re.I)
STRIP_MODE = re.compile(r"@(?:contains|exact(?:_token)?|needle_box)$", re.I)


def base_keyword(raw: str) -> str:
    text = raw.strip()
    text = STRIP_MODE.sub("", text)
    return text.strip()


def contains_keywords(rule: dict) -> list[str]:
    out: list[str] = []
    for kw in rule.get("keywords") or []:
        text = str(kw).strip()
        if CONTAINS_SUFFIX.search(text) or "@contains" in text.lower():
            out.append(base_keyword(text))
    return out


def violates_independence(rule: dict) -> str | None:
    kws = contains_keywords(rule)
    if len(kws) <= 1:
        return None
    bases = {k for k in kws if k}
    if len(bases) <= 1:
        return None
    name = rule.get("name", "")
    return f"{name}: 多品类 @contains 共条 {sorted(bases)}"


def main() -> int:
    failures: list[str] = []
    for path in sorted(BASELINE_DIR.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        code = data.get("customerCode") or path.stem
        for rule in data.get("productRules") or []:
            if not rule.get("isActive", True):
                continue
            msg = violates_independence(rule)
            if msg:
                failures.append(f"{code}: {msg}")
    if failures:
        print("G7 FAIL: 规则独立维护违规", file=sys.stderr)
        for line in failures:
            print(line, file=sys.stderr)
        return 1
    print("G7 PASS: 全部 baseline 规则 @contains 关键词独立维护")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
