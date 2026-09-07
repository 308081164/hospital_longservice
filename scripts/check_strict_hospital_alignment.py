#!/usr/bin/env python3
"""G5：校验三处特殊计价医院清单与 STRICT_KEEP_CODES（29 家）一致。"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from strict_hospital_codes import STRICT_HOSPITALS, STRICT_KEEP_CODES  # noqa: E402


def load_manifest_codes() -> list[str]:
    import importlib.util

    spec = importlib.util.spec_from_file_location(
        "billing_rules_manifest", ROOT / "scripts" / "billing_rules_manifest.py"
    )
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(mod)
    return list(mod.STRICT_KEEP_CODES)


def load_java_strict_codes() -> list[str]:
    java = (ROOT / "backend/src/main/java/com/hospital/backend/config/BillingSeedMigrationRunner.java").read_text(
        encoding="utf-8"
    )
    m = re.search(r"STRICT_KEEP_CODES\s*=\s*java\.util\.List\.of\((.*?)\);", java, re.S)
    if not m:
        raise RuntimeError("无法在 BillingSeedMigrationRunner 中解析 STRICT_KEEP_CODES")
    block = m.group(1)
    return re.findall(r'"([A-Z0-9-]+)"', block)


def load_v8_audit_codes() -> list[str]:
    audit = (ROOT / "scripts/special_v8_strict_excel_audit.py").read_text(encoding="utf-8")
    if "from strict_hospital_codes import" in audit:
        return [h.code for h in STRICT_HOSPITALS]
    raise RuntimeError("special_v8_strict_excel_audit.py 未从 strict_hospital_codes 导入清单")


def main() -> int:
    errors: list[str] = []
    manifest = load_manifest_codes()
    java = load_java_strict_codes()
    v8 = load_v8_audit_codes()
    expected = STRICT_KEEP_CODES

    for name, actual in [
        ("billing_rules_manifest.STRICT_KEEP_CODES", manifest),
        ("BillingSeedMigrationRunner.STRICT_KEEP_CODES", java),
        ("special_v8_strict_excel_audit (via strict_hospital_codes)", v8),
    ]:
        if actual != expected:
            errors.append(f"{name} 与权威清单不一致：期望 {len(expected)} 家，实际 {len(actual)} 家")
            missing = set(expected) - set(actual)
            extra = set(actual) - set(expected)
            if missing:
                errors.append(f"  缺失: {sorted(missing)}")
            if extra:
                errors.append(f"  多余: {sorted(extra)}")

    if len(STRICT_HOSPITALS) != len(expected):
        errors.append(f"strict_hospital_codes.STRICT_HOSPITALS 数量 {len(STRICT_HOSPITALS)} != {len(expected)}")

    if errors:
        print("G5 医院清单对齐失败：")
        for e in errors:
            print(" -", e)
        return 1

    print(f"G5 OK: 三处清单均为 {len(expected)} 家特殊计价医院")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
