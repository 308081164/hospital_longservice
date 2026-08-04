#!/usr/bin/env python3
"""Generate human-readable billing rules catalog markdown from manifest."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT = ROOT / "docs/医院特色计价规则清单.md"

import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from billing_rules_manifest import build_manifest  # noqa: E402

MODE_DESC = {
    "special_only": "仅特色规则：未命中规则时保留原价",
    "hybrid": "混合模式：未命中规则时走标准灭菌阶梯价",
    "standard": "标准模式：按通用灭菌定价表计费",
}

RULE_TYPE_LABEL = {
    "FIXED_PRICE": "固定单价",
    "PRICE_PER_INSTRUMENT": "按件计价",
    "FOLD": "折算规则",
    "PRICE_MULTIPLIER": "倍数加价",
    "EXTRA_FEE": "附加费",
    "ZERO_PRICE": "零价覆盖",
}


def _keywords_text(keywords: list[Any] | None) -> str:
    if not keywords:
        return "（无关键词，靠产品绑定或全局）"
    return "、".join(str(k) for k in keywords[:6]) + ("…" if len(keywords) > 6 else "")


def _parse_conditions(conditions_json: str | None) -> list[str]:
    if not conditions_json:
        return []
    notes: list[str] = []
    try:
        conds = json.loads(conditions_json)
        if isinstance(conds, list):
            for cond in conds:
                if not isinstance(cond, dict):
                    continue
                field = cond.get("field")
                value = cond.get("value")
                if field == "department":
                    if isinstance(value, list):
                        notes.append("科室：" + " / ".join(str(v) for v in value))
                    elif value:
                        notes.append(f"科室：{value}")
                elif field == "originalUnitPrice":
                    notes.append(f"原价={value}元")
                elif field == "exportApply" and value:
                    notes.append("仅导出账单生效")
    except json.JSONDecodeError:
        pass
    return notes


def describe_rule(rule: dict[str, Any]) -> str:
    rule_type = str(rule.get("ruleType") or "FIXED_PRICE")
    label = RULE_TYPE_LABEL.get(rule_type, rule_type)
    parts: list[str] = [f"包名含「{_keywords_text(rule.get('keywords'))}」"]

    cond_notes = _parse_conditions(rule.get("conditionsJson"))
    parts.extend(cond_notes)

    exclude = rule.get("excludeKeywords")
    if exclude:
        parts.append("排除：" + "、".join(str(x) for x in exclude[:4]))

    price = rule.get("price")
    fold = rule.get("foldRatio")
    threshold = rule.get("threshold")

    if rule_type == "FIXED_PRICE" and price is not None:
        parts.append(f"→ 固定 **{price:g} 元/包**")
    elif rule_type == "PRICE_PER_INSTRUMENT" and price is not None:
        parts.append(f"→ **{price:g} 元/件**")
    elif rule_type == "FOLD":
        if threshold is not None and fold is not None:
            parts.append(f"→ **{threshold} 件算 {fold:g} 件**")
        elif threshold is not None:
            parts.append(f"→ 阈值 {threshold} 件折算")
    elif price is not None:
        parts.append(f"→ 价格 {price:g}")

    if rule.get("skipPackaging"):
        parts.append("（不计包装费）")
    if rule.get("skipDiscount"):
        parts.append("（不计折扣）")

    return "；".join(parts) if parts else label


def render_customer(code: str, entry: dict[str, Any]) -> str:
    name = entry.get("name") or code
    mode = entry.get("billingPricingMode") or "standard"
    mode_line = MODE_DESC.get(mode, mode)
    rules: list[dict[str, Any]] = list(entry.get("productRules") or [])
    active = [r for r in rules if r.get("isActive", True)]
    inactive = [r for r in rules if not r.get("isActive", True)]

    lines = [
        f"## {name}（`{code}`）",
        "",
        f"- **计价模式**：`{mode}` — {mode_line}",
        f"- **规则数**：启用 {len(active)} 条" + (f"，已停用 {len(inactive)} 条" if inactive else ""),
        "",
    ]

    if active:
        lines.append("| 规则名 | 类型 | 说明 |")
        lines.append("|--------|------|------|")
        for rule in sorted(active, key=lambda r: (r.get("priority") or 100, r.get("name") or "")):
            rtype = str(rule.get("ruleType") or "FIXED_PRICE")
            lines.append(
                f"| {rule.get('name', '')} | {RULE_TYPE_LABEL.get(rtype, rtype)} | {describe_rule(rule)} |"
            )
        lines.append("")

    if inactive:
        lines.append("<details><summary>已停用规则</summary>")
        lines.append("")
        lines.append("| 规则名 | 说明 |")
        lines.append("|--------|------|")
        for rule in sorted(inactive, key=lambda r: r.get("name") or ""):
            lines.append(f"| {rule.get('name', '')} | {describe_rule(rule)} |")
        lines.append("")
        lines.append("</details>")
        lines.append("")

    return "\n".join(lines)


def _is_inactive_customer(entry: dict[str, Any]) -> bool:
    status = str(entry.get("status") or "").strip().lower()
    return status == "inactive"


def _audit_duplicate_names(active_entries: list[tuple[str, dict[str, Any]]]) -> list[str]:
    """Return markdown lines for duplicate canonical names among active customers."""
    from collections import defaultdict

    by_name: dict[str, list[str]] = defaultdict(list)
    for code, entry in active_entries:
        name = (entry.get("name") or code).strip()
        if name:
            by_name[name].append(code)
    dupes = {n: codes for n, codes in by_name.items() if len(codes) > 1}
    if not dupes:
        return ["- **规范名重复审计**：无（启用客户规范名均唯一）", ""]
    lines = [
        "- **规范名重复审计**：以下规范名对应多个启用客户码，请合并或更名：",
        "",
        "| 规范名 | 客户码 |",
        "|--------|--------|",
    ]
    for name in sorted(dupes):
        lines.append(f"| {name} | {', '.join(sorted(dupes[name]))} |")
    lines.append("")
    return lines


def build_catalog_md(manifest: dict[str, Any] | None = None) -> str:
    manifest = manifest or build_manifest()
    customers: dict[str, Any] = manifest.get("customers") or {}

    with_rules = [
        (code, customers[code])
        for code in sorted(customers)
        if customers[code].get("productRules")
    ]
    active_entries = [(c, e) for c, e in with_rules if not _is_inactive_customer(e)]
    inactive_entries = [(c, e) for c, e in with_rules if _is_inactive_customer(e)]

    total_rules = sum(int(c.get("rule_count") or 0) for c in customers.values())
    active_rules = sum(int(c.get("active_rule_count") or 0) for c in customers.values())
    generated = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    mhash = (manifest.get("manifest_hash") or "")[:16]

    lines = [
        "# 医院特色计价规则清单",
        "",
        "> 自动生成，供与客户逐项核对。修改 seed 后请运行：",
        "> `python3 scripts/billing_rules_catalog.py --write`",
        "",
        f"- **生成时间**：{generated}",
        f"- **Manifest hash**：`{mhash}…`",
        f"- **客户数**：{len(customers)} · **规则总数**：{total_rules}（启用 {active_rules}）",
        f"- **清单收录**：{len(active_entries)} 家启用客户"
        + (f"，{len(inactive_entries)} 家已停用见附录" if inactive_entries else ""),
        "",
    ]
    lines.extend(_audit_duplicate_names(active_entries))
    lines.extend(
        [
            "## 计价模式说明",
            "",
            "| 模式 | 含义 |",
            "|------|------|",
            "| `special_only` | 仅特色规则；未命中则保留原价 |",
            "| `hybrid` | 混合；未命中则走标准灭菌价 |",
            "| `standard` | 标准灭菌定价表 |",
            "",
            "---",
            "",
        ]
    )

    for code, entry in active_entries:
        lines.append(render_customer(code, entry))

    if inactive_entries:
        lines.append("<details><summary>已停用客户（legacy，不纳入对外核对）</summary>")
        lines.append("")
        for code, entry in inactive_entries:
            lines.append(render_customer(code, entry))
        lines.append("</details>")
        lines.append("")

    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate billing rules catalog markdown")
    parser.add_argument("--write", action="store_true", help="Write markdown file")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="Output path")
    args = parser.parse_args()

    md = build_catalog_md()
    if args.write:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(md, encoding="utf-8")
        print(f"wrote {args.out}")
        return 0
    print(md)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
