#!/usr/bin/env python3
"""Apply phase-batch-p0.json rules directly to MySQL (no backend rebuild needed)."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / "backend/src/main/resources/billing-seeds/phase-batch-p0.json"
MARKER = "billing_seed_batch_p0_v1"


def mysql_exec(sql: str) -> str:
    cmd = [
        "docker", "exec", "hospital-mysql",
        "sh", "-c",
        f'mysql -uhospital -p"$MYSQL_PASSWORD" hospital -N -e "{sql.replace(chr(34), chr(92)+chr(34))}"',
    ]
    return subprocess.check_output(cmd, text=True)


def esc(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "''")


def main() -> int:
    if mysql_exec(f"SELECT COUNT(*) FROM sys_setting WHERE setting_key='{MARKER}'").strip() != "0":
        print(f"Marker {MARKER} already exists — skip")
        return 0

    seed = json.loads(SEED.read_text(encoding="utf-8"))
    inserted = 0
    updated = 0

    for profile in seed["profiles"]:
        code = profile["code"]
        row = mysql_exec(
            f"SELECT id, billing_pricing_mode, billing_enabled FROM customer WHERE code='{esc(code)}' LIMIT 1"
        ).strip()
        if not row:
            print(f"WARN: customer {code} not found")
            continue
        cid, mode, enabled = row.split("\t")
        new_mode = profile.get("billingPricingMode", mode)
        new_enabled = 1 if profile.get("billingEnabled", True) else 0
        if new_mode != mode or str(new_enabled) != enabled:
            mysql_exec(
                f"UPDATE customer SET billing_pricing_mode='{esc(new_mode)}', billing_enabled={new_enabled} WHERE id={cid}"
            )
            updated += 1
            print(f"Updated customer {code}: mode={new_mode} enabled={new_enabled}")

        for rule in profile.get("productRules", []):
            name = esc(rule["name"])
            exists = mysql_exec(
                f"SELECT COUNT(*) FROM customer_product_rule WHERE customer_id={cid} AND name='{name}'"
            ).strip()
            if exists != "0":
                continue
            kw = esc(json.dumps(rule.get("keywords") or [], ensure_ascii=False))
            ex = rule.get("excludeKeywords")
            ex_sql = f"'{esc(json.dumps(ex, ensure_ascii=False))}'" if ex else "NULL"
            price = rule.get("price", 0)
            priority = rule.get("priority", 100)
            rtype = esc(rule.get("ruleType", "FIXED_PRICE"))
            skip_p = 1 if rule.get("skipPackaging", False) else 0
            skip_d = 1 if rule.get("skipDiscount", False) else 0
            temp = rule.get("temperature")
            temp_sql = f"'{esc(temp)}'" if temp else "NULL"
            mysql_exec(
                f"INSERT INTO customer_product_rule "
                f"(customer_id, rule_type, name, priority, price, keywords, exclude_keywords, "
                f"temperature, skip_packaging, skip_discount, is_active, match_mode) "
                f"VALUES ({cid}, '{rtype}', '{name}', {priority}, {price}, '{kw}', {ex_sql}, "
                f"{temp_sql}, {skip_p}, {skip_d}, 1, 'first')"
            )
            inserted += 1

    mysql_exec(
        f"INSERT INTO sys_setting (setting_key, setting_value, description) "
        f"VALUES ('{MARKER}', 'true', 'Batch P0 rules applied via script')"
    )
    print(f"Done: {inserted} rules inserted, {updated} customers updated")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
