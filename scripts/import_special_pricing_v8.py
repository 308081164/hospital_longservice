#!/usr/bin/env python3
"""Export 特殊收费(8).xlsx to structured JSON for seed authoring."""

from __future__ import annotations

import json
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
SPECIAL_XLSX = Path(
    "/Users/yangxinghui/Library/Containers/com.tencent.xinWeChat/Data/Documents/"
    "xwechat_files/wxid_7qwn4vnuj7xo22_508c/temp/drag/特殊收费(8).xlsx"
)
OUTPUT = ROOT / "测试用例" / ".special_pricing_v8_extract.json"


def main() -> None:
    if not SPECIAL_XLSX.is_file():
        raise FileNotFoundError(SPECIAL_XLSX)
    per_hospital = pd.read_excel(SPECIAL_XLSX, sheet_name="各医院特殊收费")
    unified = pd.read_excel(SPECIAL_XLSX, sheet_name="通用特殊收费")
    per_hospital["医院名称"] = per_hospital["医院名称"].ffill()
    per_hospital["医院序号"] = per_hospital["医院序号"].ffill()

    hospitals: list[str] = []
    rules: dict[str, list[dict]] = {}
    for (_, name), grp in per_hospital.groupby(["医院序号", "医院名称"], sort=False):
        hname = str(name).strip()
        hospitals.append(hname)
        rows = []
        for _, r in grp.iterrows():
            pkg = r.get("包名称（或包名中特殊信息）")
            if pd.isna(pkg) and pd.isna(r.get("收费规则")):
                continue
            rows.append(
                {
                    "item": str(r.get("项目序号") or "").strip(),
                    "kw": str(pkg or "").strip(),
                    "type": str(r.get("包类型") or "").strip(),
                    "inst": str(r.get("包内器械件数") or "").strip(),
                    "rule": str(r.get("收费规则") or "").strip(),
                    "note": str(r.get("备注") or "").strip(),
                }
            )
        rules[hname] = rows

    unified_rules = []
    current_seq = None
    for _, r in unified.iterrows():
        if not pd.isna(r.get("序号")):
            current_seq = r.get("序号")
        pkg = r.get("包名称（或包名中特殊信息）")
        if pd.isna(pkg) and pd.isna(r.get("收费规则")):
            continue
        unified_rules.append(
            {
                "seq": current_seq,
                "package": str(pkg or "").strip(),
                "pack_type": str(r.get("包类型") or "").strip(),
                "instrument_count": str(r.get("包内器械件数") or "").strip(),
                "pricing_rule": str(r.get("收费规则") or "").strip(),
                "note": str(r.get("备注") or "").strip(),
            }
        )

    payload = {"hospitals": hospitals, "rules": rules, "unified_rules": unified_rules}
    OUTPUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {OUTPUT} ({len(hospitals)} hospitals)")


if __name__ == "__main__":
    main()
