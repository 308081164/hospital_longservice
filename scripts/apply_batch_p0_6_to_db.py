#!/usr/bin/env python3
"""Apply phase-batch-p0.6.json — enable billing for acceptance-pass hospitals only."""

from __future__ import annotations

import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / "backend/src/main/resources/billing-seeds/phase-batch-p0.6.json"
MARKER = "billing_seed_batch_p0_6_v1"


def mysql_query(sql: str) -> str:
    cmd = [
        "docker", "exec", "hospital-mysql", "sh", "-c",
        f'mysql -uhospital -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 hospital -N -e "{sql}"',
    ]
    return subprocess.check_output(cmd, text=True).strip()


def mysql_file(sql_path: Path) -> None:
    subprocess.check_call(["docker", "cp", str(sql_path), "hospital-mysql:/tmp/p0_6.sql"])
    subprocess.check_call([
        "docker", "exec", "hospital-mysql", "sh", "-c",
        'mysql -uhospital -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 hospital < /tmp/p0_6.sql',
    ])


def esc(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "''")


def main() -> int:
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    enable = seed.get("enableBilling") or []
    if not enable:
        print("ERROR: enableBilling is empty")
        return 1

    lines = ["SET NAMES utf8mb4;", "START TRANSACTION;"]
    in_list = ", ".join(f"'{esc(c)}'" for c in enable)
    lines.append(f"UPDATE customer SET billing_enabled=1 WHERE code IN ({in_list});")
    print(f"Enable billing for {len(enable)} customers")

    if seed.get("disableAllOthers"):
        lines.append(f"UPDATE customer SET billing_enabled=0 WHERE code NOT IN ({in_list});")
        disabled = mysql_query(
            f"SELECT COUNT(*) FROM customer WHERE code NOT IN ({in_list}) AND billing_enabled=1"
        )
        print(f"Disable billing for all other customers (was {disabled} enabled)")

    lines.append(
        f"INSERT INTO sys_setting (setting_key, setting_value) "
        f"SELECT '{MARKER}', '1' FROM DUAL "
        f"WHERE NOT EXISTS (SELECT 1 FROM sys_setting WHERE setting_key='{MARKER}');"
    )
    lines.append("COMMIT;")

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".sql", delete=False) as f:
        f.write("\n".join(lines))
        sql_path = Path(f.name)
    print("Applying P0.6...")
    mysql_file(sql_path)
    sql_path.unlink()

    on = mysql_query("SELECT COUNT(*) FROM customer WHERE billing_enabled=1")
    off = mysql_query("SELECT COUNT(*) FROM customer WHERE billing_enabled=0")
    print(f"Done — marker {MARKER} · enabled={on} disabled={off}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
