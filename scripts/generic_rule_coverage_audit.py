#!/usr/bin/env python3
"""G9：通用特殊收费（全医院适用）覆盖闸门。

背景（2026-10-28）：G7 只审「各医院特殊收费」，通用表从未被任何闸门覆盖，
导致「骨科租赁器械包」「包名称带双」长期未实现却无人发现。

规则：
- Excel「通用特殊收费」中的每个条目，必须在 scripts/generic_rule_coverage.json 登记；
- 未登记 = 新增条目未 triage → 失败；
- 登记为 gap 的条目允许存在（需带 pending_ref，指向待客户反馈文档），仅提示不失败。
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_XLSX = ROOT / "docs/source/特殊收费20261026.xlsx"
COVERAGE = ROOT / "scripts/generic_rule_coverage.json"


def excel_items(xlsx: Path) -> list[str]:
    wb = openpyxl.load_workbook(xlsx, data_only=True)
    ws = wb["通用特殊收费"]
    merged = {}
    for rng in ws.merged_cells.ranges:
        value = ws.cell(rng.min_row, rng.min_col).value
        for r in range(rng.min_row, rng.max_row + 1):
            for c in range(rng.min_col, rng.max_col + 1):
                merged[(r, c)] = value
    items: list[str] = []
    for r in range(2, ws.max_row + 1):
        raw = merged.get((r, 2)) or ws.cell(r, 2).value
        if raw is None:
            continue
        text = str(raw).strip()
        if text and text not in items:
            items.append(text)
    wb.close()
    return items


def main() -> int:
    xlsx = DEFAULT_XLSX
    if not xlsx.is_file():
        print(f"ERROR: Excel 不存在: {xlsx}", file=sys.stderr)
        return 1
    coverage = json.loads(COVERAGE.read_text(encoding="utf-8"))
    registered: dict = coverage.get("items") or {}
    items = excel_items(xlsx)

    untriaged = [i for i in items if i not in registered]
    gaps = [i for i in items if i in registered and registered[i].get("status") == "gap"]
    stale = [k for k in registered if k not in items]

    print(f"通用表条目 {len(items)} 条；已登记 {len(items) - len(untriaged)} 条；缺口 {len(gaps)} 条")
    for g in gaps:
        ref = registered[g].get("pending_ref", "")
        print(f"  [GAP] {g} -> {ref}")

    if stale:
        print(f"提示: 登记表中有 {len(stale)} 条已不在 Excel（可清理）: {stale}")
    if untriaged:
        print(f"失败: 以下通用条目未登记覆盖状态（Excel 新增？）: {untriaged}", file=sys.stderr)
        return 1
    print("G9 OK: 通用表条目均已 triage（缺口均为已登记的等待客户项）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
