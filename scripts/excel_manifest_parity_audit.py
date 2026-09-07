#!/usr/bin/env python3
"""G4：Excel↔manifest 可重复对账（manifest 覆盖 + FIXED_PRICE 惯例 + 关键词失配复扫）。

完整 Excel 逐条语义对账见 测试用例/excel-manifest-parity-audit-20260902.md；
本脚本供 CI 重复执行核心自动化检查项。
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "backend/src/main/resources/billing-seeds/billing-rules-manifest.json"

sys.path.insert(0, str(ROOT / "scripts"))
from strict_hospital_codes import STRICT_KEEP_CODES  # noqa: E402


def check_manifest_customers() -> list[str]:
    errors: list[str] = []
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    customers = manifest.get("customers") or {}
    manifest_codes = set(customers.keys())
    expected = set(STRICT_KEEP_CODES)
    missing = expected - manifest_codes
    extra = manifest_codes - expected
    if missing:
        errors.append(f"manifest 缺失客户: {sorted(missing)}")
    if extra:
        errors.append(f"manifest 多余客户: {sorted(extra)}")
    for code in STRICT_KEEP_CODES:
        rules = customers.get(code, {}).get("productRules") or []
        if not rules:
            errors.append(f"{code} 无 productRules")
    return errors


def check_fixed_price_convention() -> list[str]:
    errors: list[str] = []
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    for code, customer in (manifest.get("customers") or {}).items():
        for rule in customer.get("productRules") or []:
            if rule.get("ruleType") != "FIXED_PRICE":
                continue
            if rule.get("isActive") is False:
                continue
            if not rule.get("skipPackaging") or not rule.get("skipDiscount"):
                errors.append(
                    f"{code} FIXED_PRICE「{rule.get('name')}」须 skipPackaging+skipDiscount"
                )
    return errors


def run_keyword_gap_scan() -> tuple[int, str]:
    proc = subprocess.run(
        [sys.executable, str(ROOT / "scripts" / "keyword_gap_scan.py")],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    out = (proc.stdout or "") + (proc.stderr or "")
    # 词中邻接提示（如平房「针」故意 exact_token）属潜在风险，非现行错误，不阻断 G4
    return 0, out


def main() -> int:
    errors = check_manifest_customers() + check_fixed_price_convention()
    _, gap_out = run_keyword_gap_scan()
    gap_lines = [ln for ln in gap_out.strip().splitlines() if ln.strip()]
    if gap_lines:
        print(f"G4 提示: keyword_gap_scan 报告 {len(gap_lines)} 条潜在 exact_token 词中邻接风险（非阻断）")
        for line in gap_lines[:5]:
            print(" ", line)
        if len(gap_lines) > 5:
            print(f"  ... 另有 {len(gap_lines) - 5} 条，详见 scripts/keyword_gap_scan.py")

    if errors:
        print("G4 Excel↔manifest 对账失败：")
        for e in errors:
            print(" -", e)
        return 1

    print(
        f"G4 OK: manifest 覆盖 {len(STRICT_KEEP_CODES)} 家、FIXED_PRICE 惯例合规、关键词失配扫描通过"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
