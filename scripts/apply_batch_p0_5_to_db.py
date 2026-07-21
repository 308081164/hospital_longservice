#!/usr/bin/env python3
"""Apply phase-batch-p0.5.json — re-enable billing for L9-L61 hospitals."""

from __future__ import annotations

import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / "backend/src/main/resources/billing-seeds/phase-batch-p0.5.json"
MARKER = "billing_seed_batch_p0_5_v1"


def mysql_file(sql_path: Path) -> None:
    subprocess.check_call(["docker", "cp", str(sql_path), "hospital-mysql:/tmp/p0_5.sql"])
    subprocess.check_call([
        "docker", "exec", "hospital-mysql", "sh", "-c",
        'mysql -uhospital -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 hospital < /tmp/p0_5.sql',
    ])


def esc(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "''")


def main() -> int:
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    lines = ["SET NAMES utf8mb4;", "START TRANSACTION;"]
    for upd in seed.get("customerUpdates", []):
        code = esc(upd["code"])
        sets = []
        if "billingPricingMode" in upd:
            sets.append(f"billing_pricing_mode='{esc(upd['billingPricingMode'])}'")
        if "billingEnabled" in upd:
            sets.append(f"billing_enabled={1 if upd['billingEnabled'] else 0}")
        if sets:
            lines.append(f"UPDATE customer SET {', '.join(sets)} WHERE code='{code}';")
            print(f"Update {upd['code']}: {sets}")
    lines.append(
        f"INSERT INTO sys_setting (setting_key, setting_value) "
        f"SELECT '{MARKER}', '1' FROM DUAL "
        f"WHERE NOT EXISTS (SELECT 1 FROM sys_setting WHERE setting_key='{MARKER}');"
    )
    lines.append("COMMIT;")
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".sql", delete=False) as f:
        f.write("\n".join(lines))
        sql_path = Path(f.name)
    print(f"Applying P0.5...")
    mysql_file(sql_path)
    sql_path.unlink()
    print(f"Done — marker {MARKER}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
