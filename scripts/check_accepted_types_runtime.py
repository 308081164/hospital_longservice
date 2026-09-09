#!/usr/bin/env python3
"""G6：baseline acceptedTypes 须能与账单常见 type 列等价（packTypeEquivalent 口径）。

rules compare 只比对 DB/manifest 存储一致性，无法发现「规则在库但 type 门控永不命中」。
本脚本扫描 29 家 baseline 的 acceptedTypes，确保每条至少能匹配一种真实账单写法。
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINE_DIR = ROOT / "backend/src/main/resources/billing-rules/baseline"

# 账单 type 列常见写法（半角括号）；与 PackTypeRegistry 别名 + packTypeEquivalent 对齐
BILLING_TYPE_SAMPLES: dict[str, list[str]] = {
    "额外包低温等离子": ["额外包(低温等离子)", "额外包（低温等离子）"],
    "额外包（低温等离子）": ["额外包(低温等离子)"],
    "额外包（纸塑袋）": ["额外包(纸塑袋)"],
    "额外包（ETO）": ["额外包(ETO)", "额外包(EO)"],
    "额外包": ["额外包(纸塑袋)", "额外包(低温等离子)", "额外包(ETO)"],
    "单包装（低温老肯）": ["单包装包(老肯低温)", "单包装包（老肯低温）"],
    "单包装": ["单包装包"],
    "器械包": ["器械包", "器械包(ZSD)", "器械包(低温等离子)"],
    "器械包（低温等离子）": ["器械包(低温等离子)"],
    "敷料包（无纺布）": ["敷料包(无纺布包)", "敷料包(无纺布)"],
}


def _normalize(label: str) -> str:
    return (
        label.strip()
        .replace("（", "(")
        .replace("）", ")")
        .replace(" ", "")
        .lower()
    )


def _load_aliases() -> dict[str, str]:
    data = json.loads(
        (ROOT / "backend/src/main/resources/pack-type-material-map.json").read_text(encoding="utf-8")
    )
    alias_to_canonical: dict[str, str] = {}
    for node in data.get("packTypes", []):
        canonical = str(node.get("canonical") or "").strip()
        if not canonical:
            continue
        alias_to_canonical[_normalize(canonical)] = canonical
        for alias in node.get("aliases", []):
            alias_to_canonical[_normalize(str(alias))] = canonical
    return alias_to_canonical


def pack_type_equivalent(expected: str, actual: str, alias_to_canonical: dict[str, str]) -> bool:
    exp_key = _normalize(expected)
    act_key = _normalize(actual)
    if exp_key == "额外包" and act_key.startswith("额外包"):
        act_canon = alias_to_canonical.get(act_key)
        return act_canon is not None and act_canon.startswith("额外包")
    exp_canon = alias_to_canonical.get(exp_key)
    act_canon = alias_to_canonical.get(act_key)
    if exp_canon and act_canon:
        return exp_canon == act_canon
    return exp_key == act_key


def main() -> int:
    alias_to_canonical = _load_aliases()
    failures: list[str] = []
    checked = 0

    for path in sorted(BASELINE_DIR.glob("*.json")):
        baseline = json.loads(path.read_text(encoding="utf-8"))
        code = baseline.get("customerCode") or path.stem
        for rule in baseline.get("productRules") or []:
            for accepted in rule.get("acceptedTypes") or []:
                if not accepted:
                    continue
                checked += 1
                samples = BILLING_TYPE_SAMPLES.get(accepted)
                if samples is None:
                    samples = [accepted.replace("（", "(").replace("）", ")")]
                if not any(pack_type_equivalent(accepted, sample, alias_to_canonical) for sample in samples):
                    failures.append(
                        f"{code} · {rule.get('name')} · acceptedTypes={accepted!r} "
                        f"无法匹配账单样本 {samples}"
                    )

    if failures:
        print("G6 FAIL: acceptedTypes 运行时 type 门控可能永不命中", file=sys.stderr)
        for line in failures:
            print(f"  - {line}", file=sys.stderr)
        return 1

    print(f"G6 OK: {checked} 条 acceptedTypes 均可匹配账单 type 列样本")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
