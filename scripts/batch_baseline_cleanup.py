#!/usr/bin/env python3
"""批量清理 baseline：删停用、删 Excel 外规则、收紧 FOLD 关键词、同步 manifest。"""

from __future__ import annotations

import hashlib
import json
import subprocess
import sys
from copy import deepcopy
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINE_DIR = ROOT / "backend/src/main/resources/billing-rules/baseline"
MANIFEST_PATH = ROOT / "backend/src/main/resources/billing-seeds/billing-rules-manifest.json"
TEST_MANIFEST_PATH = ROOT / "backend/src/test/resources/billing-rules-manifest.json"

sys.path.insert(0, str(ROOT / "scripts"))
from strict_hospital_codes import STRICT_BY_CODE, STRICT_KEEP_CODES  # noqa: E402

# 各院 FOLD 关键词收紧（仅保留 Excel「包名称带X」词）
FOLD_KEYWORD_FIXES: dict[str, list[str]] = {
    "NEAU-YY": [
        "根管针@contains",
        "机锉@contains",
        "牙探针@contains",
        "根管锉@contains",
    ],
}

# 按名称彻底删除（含曾停用）
DELETE_RULE_NAMES: dict[str, set[str]] = {
    "XINFA-HSZ": {
        "新发棉球纸塑袋",
        "新发镜头0度35",
        "新发镜头12度28",
        "新发镜头30度35",
        "校正价66.0",
        "穿刺器帽22",
    },
    "HLJ-FY-RK": {
        "妇幼人口垫片5合1免包材",
        "妇幼人口垫片5合1含包材",
        "妇幼人口针包5合1免包材",
        "妇幼人口针包5合1含包材",
        "妇幼人口全冠套装个针包材2.5",
        "妇幼人口宫腔镜镜头0元锁价35",
        "妇幼人口腹腔镜镜头0元锁价35",
    },
    "HULAN-TCM": {
        "呼兰中医export 腹腔镜297",
        "外科包固定价",
        "校正价275.0",
        "阑尾包固定价",
    },
    "ZUYAN-SF": {
        "三辅探针瘘管刨刀包",
    },
}

def canonical_hash(obj: object) -> str:
    text = json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def load_baseline(code: str) -> dict:
    return json.loads((BASELINE_DIR / f"{code}.json").read_text(encoding="utf-8"))


def save_baseline(code: str, data: dict) -> None:
    path = BASELINE_DIR / f"{code}.json"
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def apply_fixes(code: str, data: dict) -> dict[str, list[str]]:
    changes: dict[str, list[str]] = {
        "deleted": [],
        "keywords_tightened": [],
        "keywords_added": [],
    }
    rules = data.get("productRules") or []
    delete_names = DELETE_RULE_NAMES.get(code, set())
    new_rules: list[dict] = []

    for rule in rules:
        name = rule.get("name", "")
        if not rule.get("isActive", True):
            changes["deleted"].append(f"{name}（停用）")
            continue
        if name in delete_names:
            changes["deleted"].append(name)
            continue
        new_rules.append(rule)

    if code in FOLD_KEYWORD_FIXES:
        expected = FOLD_KEYWORD_FIXES[code]
        for rule in new_rules:
            if rule.get("ruleType") == "FOLD":
                old_kw = list(rule.get("keywords") or [])
                if old_kw != expected:
                    rule["keywords"] = expected
                    changes["keywords_tightened"].append(
                        f"{rule.get('name')}: {old_kw} → {expected}"
                    )

    data["productRules"] = new_rules
    return changes


def sync_manifest(codes: list[str]) -> None:
    import subprocess

    subprocess.run(
        [sys.executable, str(ROOT / "scripts/baseline_to_manifest.py"), "--write"],
        check=True,
        cwd=ROOT,
    )


def main() -> int:
    report: dict[str, dict] = {}
    touched: list[str] = []

    for code in STRICT_KEEP_CODES:
        data = load_baseline(code)
        before = len(data.get("productRules") or [])
        changes = apply_fixes(code, data)
        after = len(data.get("productRules") or [])
        if changes["deleted"] or changes["keywords_tightened"] or changes["keywords_added"] or before != after:
            save_baseline(code, data)
            touched.append(code)
        report[code] = {
            "label": STRICT_BY_CODE[code].label,
            "before": before,
            "after": after,
            **changes,
        }

    if touched:
        sync_manifest(touched)
        subprocess.run(
            [sys.executable, str(ROOT / "scripts/refresh_baseline_index.py")],
            check=True,
            cwd=ROOT,
        )

    out = ROOT / "测试用例/baseline_cleanup_report.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if v["deleted"] or v["keywords_tightened"]}, ensure_ascii=False, indent=2))
    print(f"Touched {len(touched)} hospitals; report → {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
