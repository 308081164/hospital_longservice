#!/usr/bin/env python3
"""Fix corrupted UTF-8 keywords in batch P0 rules and re-insert."""

from __future__ import annotations

import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / "backend/src/main/resources/billing-seeds/phase-batch-p0.json"


def mysql_file(sql_path: Path) -> None:
    subprocess.check_call([
        "docker", "cp", str(sql_path), "hospital-mysql:/tmp/fix_p0.sql",
    ])
    subprocess.check_call([
        "docker", "exec", "hospital-mysql", "sh", "-c",
        'mysql -uhospital -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 hospital < /tmp/fix_p0.sql',
    ])


def esc_sql(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "''")


def main() -> None:
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    lines = ["SET NAMES utf8mb4;", "START TRANSACTION;"]

    for profile in seed["profiles"]:
        code = esc_sql(profile["code"])
        lines.append(f"-- {profile['name']}")
        lines.append(
            f"DELETE FROM customer_product_rule WHERE customer_id=(SELECT id FROM customer c WHERE c.code='{code}') "
            f"AND name LIKE '校正价%';"
        )
        lines.append(
            f"UPDATE customer SET billing_pricing_mode='{esc_sql(profile.get('billingPricingMode', 'hybrid'))}', "
            f"billing_enabled={1 if profile.get('billingEnabled', True) else 0} "
            f"WHERE code='{code}';"
        )
        for rule in profile.get("productRules", []):
            kw = esc_sql(json.dumps(rule.get("keywords") or [], ensure_ascii=False))
            ex = rule.get("excludeKeywords")
            ex_sql = f"'{esc_sql(json.dumps(ex, ensure_ascii=False))}'" if ex else "NULL"
            name = esc_sql(rule["name"])
            rtype = esc_sql(rule.get("ruleType", "FIXED_PRICE"))
            price = rule.get("price", 0)
            priority = rule.get("priority", 100)
            skip_p = 1 if rule.get("skipPackaging", False) else 0
            skip_d = 1 if rule.get("skipDiscount", False) else 0
            temp = rule.get("temperature")
            temp_sql = f"'{esc_sql(temp)}'" if temp else "NULL"
            lines.append(
                f"INSERT INTO customer_product_rule "
                f"(customer_id, rule_type, name, priority, price, keywords, exclude_keywords, "
                f"temperature, skip_packaging, skip_discount, is_active, match_mode) "
                f"SELECT id, '{rtype}', '{name}', {priority}, {price}, '{kw}', {ex_sql}, "
                f"{temp_sql}, {skip_p}, {skip_d}, 1, 'first' FROM customer WHERE code='{code}';"
            )

    lines.append("COMMIT;")

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".sql", delete=False) as f:
        f.write("\n".join(lines))
        sql_path = Path(f.name)

    print(f"Applying {sql_path} ({len(lines)} statements)...")
    mysql_file(sql_path)
    sql_path.unlink()
    print("Done — P0 rules re-inserted with utf8mb4")


if __name__ == "__main__":
    main()
