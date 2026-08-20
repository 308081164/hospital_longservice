#!/usr/bin/env python3
"""List active system productRules not covered by customer 特殊收费 Excel rows."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import customer_rules_compare as crc  # noqa: E402

SPECIAL_XLSX = ROOT / "铂康" / "特殊收费(13).xlsx"


def expected_rule_names_for_bingcheng() -> set[str]:
    return {
        "冰城环钻包按件5.5",
        "冰城环钻包无纺布加价3",
        "冰城整形手术包按件5.5",
        "冰城整形手术包无纺布加价3",
        "冰城脂充包按件5.5",
        "冰城脂充包无纺布加价5",
    }


def main() -> None:
    crc.SPECIAL_XLSX = SPECIAL_XLSX
    manifest = crc.load_manifest()
    hospitals, _unified = crc.parse_special_excel()

    print(f"# 系统多余 active 规则审计（对照 {SPECIAL_XLSX.name}）\n")

    for h in hospitals:
        name = h["name"]
        code, sys_name, status = crc.HOSPITAL_MAP.get(name, (None, "—", "未映射"))
        active = crc.system_rules_for(code, manifest)
        report = crc.compare_hospital(h, manifest)
        matched = set(report.matched_rules)

        if code == "BINGCHENG-YM":
            expected = expected_rule_names_for_bingcheng()
        else:
            expected = matched

        extras = [
            r
            for r in active
            if r.get("name") not in expected and r.get("name") not in matched
        ]

        print(f"## {name}")
        print(f"- 系统映射：`{code or '—'}` · {sys_name} · {status}")
        print(f"- 客户 Excel 规则行：{len(h['rules'])} · 系统 active 规则：{len(active)}")
        if report.matched_rules:
            print(f"- 已匹配：{', '.join(report.matched_rules)}")
        if extras:
            print("- **超出客户 Excel 的 active 规则：**")
            for r in extras:
                print(f"  - `{r.get('name')}` ({r.get('ruleType')}) · {crc.rule_desc(r)}")
        else:
            print("- **超出客户 Excel 的 active 规则：**无")
        critical = [c for c in report.conflicts if c.severity == "critical"]
        if critical:
            print("- **严重冲突：**")
            for c in critical:
                print(f"  - {c.field}：客户 `{c.customer_value}` vs 系统 `{c.system_value}`")
        print()


if __name__ == "__main__":
    main()
