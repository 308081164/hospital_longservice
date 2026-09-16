#!/usr/bin/env python3
"""Generate FIXED_PRICE appendix for baseline inventory markdown."""

import json
import glob
import os
import re
from datetime import datetime

BASELINE_DIR = "backend/src/main/resources/billing-rules/baseline"
TARGET_FILE = "docs/Fixed校正价规则全库清单-按医院分类-20260916.md"
SCAN_DATE = "2026-09-16"
APPENDIX_MARKER = "## 附录：当前 baseline FIXED_PRICE 全量明细（按医院分类）"


def is_fixed_rule(rule: dict) -> bool:
    rt = (rule.get("ruleType") or "").upper()
    return rt in ("FIXED_PRICE", "FIXED")


def fmt_list(val) -> str:
    if val is None:
        return ""
    if isinstance(val, list):
        return "；".join(str(v) for v in val)
    return str(val)


def fmt_bool(val) -> str:
    if val is None:
        return ""
    return "是" if val else "否"


def fmt_remark(rule: dict) -> str:
    parts = []
    if rule.get("conditionsJson"):
        parts.append(f"conditionsJson={rule['conditionsJson']}")
    if rule.get("temperature") is not None:
        parts.append(f"temperature={rule['temperature']}")
    return "；".join(parts)


def load_hospitals():
    hospitals = []
    for path in sorted(glob.glob(os.path.join(BASELINE_DIR, "*.json"))):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        fixed = [r for r in data.get("productRules", []) if is_fixed_rule(r)]
        fixed.sort(key=lambda r: (r.get("priority", 0), r.get("name", "")))
        hospitals.append(
            {
                "code": data.get("customerCode", ""),
                "name": data.get("customerName", ""),
                "fixed_rules": fixed,
            }
        )
    hospitals.sort(key=lambda h: h["name"])
    return hospitals


def generate_appendix(hospitals) -> str:
    total_rules = sum(len(h["fixed_rules"]) for h in hospitals)
    hospitals_with = sum(1 for h in hospitals if h["fixed_rules"])
    lines = [
        "",
        "---",
        "",
        APPENDIX_MARKER,
        "",
        f"> 扫描时间：{SCAN_DATE}",
        f"> 规则总数：{total_rules} 条 / {len(hospitals)} 家医院（其中 {hospitals_with} 家含 FIXED_PRICE）",
        "",
        "### 汇总表",
        "",
        "| 医院 | CODE | fixed_price_count |",
        "|------|------|-------------------|",
    ]
    for h in hospitals:
        lines.append(f"| {h['name']} | {h['code']} | {len(h['fixed_rules'])} |")

    lines.append("")

    for h in hospitals:
        lines.append(f"### {h['name']}（{h['code']}）")
        lines.append("")
        if not h["fixed_rules"]:
            lines.append("本医院无 FIXED_PRICE 规则")
            lines.append("")
            continue

        lines.append(
            "| # | 规则名 | ruleType | 优先级 | 价格 | 关键词 | matchMode | keywordMatchMode "
            "| acceptedTypes | acceptedPrices | maxInstrumentCount | skipPackaging | skipDiscount | isActive | 备注 |"
        )
        lines.append(
            "|---|--------|----------|--------|------|--------|-----------|------------------"
            "|---------------|----------------|---------------------|---------------|--------------|----------|------|"
        )
        for i, rule in enumerate(h["fixed_rules"], 1):
            name = (rule.get("name") or "").replace("|", "\\|")
            lines.append(
                "| {idx} | {name} | {ruleType} | {priority} | {price} | {keywords} | {matchMode} | {keywordMatchMode} "
                "| {acceptedTypes} | {acceptedPrices} | {maxInstrumentCount} | {skipPackaging} | {skipDiscount} | {isActive} | {remark} |".format(
                    idx=i,
                    name=name,
                    ruleType=rule.get("ruleType", ""),
                    priority=rule.get("priority", ""),
                    price=rule.get("price", ""),
                    keywords=fmt_list(rule.get("keywords")).replace("|", "\\|"),
                    matchMode=rule.get("matchMode", ""),
                    keywordMatchMode=rule.get("keywordMatchMode", ""),
                    acceptedTypes=fmt_list(rule.get("acceptedTypes")).replace("|", "\\|"),
                    acceptedPrices=fmt_list(rule.get("acceptedPrices")),
                    maxInstrumentCount=rule.get("maxInstrumentCount", ""),
                    skipPackaging=fmt_bool(rule.get("skipPackaging")),
                    skipDiscount=fmt_bool(rule.get("skipDiscount")),
                    isActive=fmt_bool(rule.get("isActive")),
                    remark=fmt_remark(rule).replace("|", "\\|"),
                )
            )
        lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def main():
    hospitals = load_hospitals()
    appendix = generate_appendix(hospitals)

    with open(TARGET_FILE, encoding="utf-8") as f:
        content = f.read()

    if APPENDIX_MARKER in content:
        content = content[: content.index(APPENDIX_MARKER)].rstrip()
    else:
        content = content.rstrip()

    new_content = content + appendix
    with open(TARGET_FILE, "w", encoding="utf-8") as f:
        f.write(new_content)

    total = sum(len(h["fixed_rules"]) for h in hospitals)
    with_rules = sum(1 for h in hospitals if h["fixed_rules"])
    print(f"total={total} hospitals_with={with_rules} file={TARGET_FILE}")


if __name__ == "__main__":
    main()
