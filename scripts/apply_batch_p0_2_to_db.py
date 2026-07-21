#!/usr/bin/env python3
"""Apply phase-batch-p0.2.json patches to MySQL (utf8mb4)."""

from __future__ import annotations

import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / "backend/src/main/resources/billing-seeds/phase-batch-p0.2.json"
MARKER = "billing_seed_batch_p0_2_v1"


def mysql_query(sql: str) -> str:
    cmd = [
        "docker", "exec", "hospital-mysql", "sh", "-c",
        f'mysql -uhospital -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 hospital -N -e "{sql}"',
    ]
    return subprocess.check_output(cmd, text=True).strip()


def mysql_file(sql_path: Path) -> None:
    subprocess.check_call(["docker", "cp", str(sql_path), "hospital-mysql:/tmp/p0_2.sql"])
    subprocess.check_call([
        "docker", "exec", "hospital-mysql", "sh", "-c",
        'mysql -uhospital -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 hospital < /tmp/p0_2.sql',
    ])


def esc(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "''")


def main() -> int:
    if mysql_query(f"SELECT COUNT(*) FROM sys_setting WHERE setting_key='{MARKER}'").strip() != "0":
        print(f"Marker {MARKER} already exists — re-applying updates anyway")

    seed = json.loads(SEED.read_text(encoding="utf-8"))
    lines = ["SET NAMES utf8mb4;", "START TRANSACTION;"]

    for patch in seed.get("ruleUpdates", []):
        code = esc(patch["code"])
        name = esc(patch["ruleName"])
        if "conditionsJson" in patch:
            cj = esc(patch["conditionsJson"])
            lines.append(
                f"UPDATE customer_product_rule SET conditions_json='{cj}' "
                f"WHERE customer_id=(SELECT id FROM customer WHERE code='{code}') AND name='{name}';"
            )
            print(f"Update {patch['code']}/{patch['ruleName']} conditions")

    for rule in seed.get("newRules", []):
        code = esc(rule["code"])
        name = esc(rule["name"])
        exists = mysql_query(
            f"SELECT COUNT(*) FROM customer_product_rule r JOIN customer c ON r.customer_id=c.id "
            f"WHERE c.code='{code}' AND r.name='{name}'"
        )
        if exists != "0":
            print(f"Skip existing rule {rule['code']}/{rule['name']}")
            continue
        kw = esc(json.dumps(rule.get("keywords") or [], ensure_ascii=False))
        ap = esc(json.dumps(rule.get("acceptedPrices") or [], ensure_ascii=False))
        rtype = esc(rule.get("ruleType", "FIXED_PRICE"))
        mm = esc(rule.get("matchMode", "first"))
        price = rule.get("price", 0)
        priority = rule.get("priority", 100)
        skip_p = 1 if rule.get("skipPackaging", False) else 0
        skip_d = 1 if rule.get("skipDiscount", False) else 0
        lines.append(
            f"INSERT INTO customer_product_rule "
            f"(customer_id, rule_type, name, priority, price, keywords, match_mode, accepted_prices, "
            f"skip_packaging, skip_discount, is_active) "
            f"SELECT id, '{rtype}', '{name}', {priority}, {price}, '{kw}', '{mm}', '{ap}', "
            f"{skip_p}, {skip_d}, 1 FROM customer WHERE code='{code}';"
        )
        print(f"Insert rule {rule['code']}/{rule['name']}")

    lines.append(
        f"INSERT INTO sys_setting (setting_key, setting_value) "
        f"SELECT '{MARKER}', '1' FROM DUAL "
        f"WHERE NOT EXISTS (SELECT 1 FROM sys_setting WHERE setting_key='{MARKER}');"
    )
    lines.append("COMMIT;")

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".sql", delete=False) as f:
        f.write("\n".join(lines))
        sql_path = Path(f.name)

    print(f"Applying P0.2 ({len(lines)} statements)...")
    mysql_file(sql_path)
    sql_path.unlink()
    print(f"Done — marker {MARKER}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
