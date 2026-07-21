#!/usr/bin/env python3
"""Apply phase-batch-p0.4.json patches to MySQL (utf8mb4)."""

from __future__ import annotations

import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / "backend/src/main/resources/billing-seeds/phase-batch-p0.4.json"
MARKER = "billing_seed_batch_p0_4_v1"


def mysql_query(sql: str) -> str:
    cmd = [
        "docker", "exec", "hospital-mysql", "sh", "-c",
        f'mysql -uhospital -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 hospital -N -e "{sql}"',
    ]
    return subprocess.check_output(cmd, text=True).strip()


def mysql_file(sql_path: Path) -> None:
    subprocess.check_call(["docker", "cp", str(sql_path), "hospital-mysql:/tmp/p0_4.sql"])
    subprocess.check_call([
        "docker", "exec", "hospital-mysql", "sh", "-c",
        'mysql -uhospital -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 hospital < /tmp/p0_4.sql',
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
            print(f"Update customer {upd['code']}: {sets}")

    for code in seed.get("deactivateBillingPolicies", []):
        code_esc = esc(code)
        lines.append(
            f"UPDATE customer_billing_policy SET is_active=0 "
            f"WHERE customer_id=(SELECT id FROM customer WHERE code='{code_esc}');"
        )
        lines.append(
            f"UPDATE customer_discount SET is_active=0 "
            f"WHERE customer_id=(SELECT id FROM customer WHERE code='{code_esc}');"
        )
        print(f"Deactivate policies/discounts for {code}")

    for alias_entry in seed.get("customerAliases", []):
        code = esc(alias_entry["code"])
        alias = esc(alias_entry["alias"])
        match_type = esc(alias_entry.get("matchType", "exact"))
        lines.append(
            f"INSERT INTO customer_alias (customer_id, alias, match_type, source, priority, is_active) "
            f"SELECT id, '{alias}', '{match_type}', 'p0.4_seed', 10, 1 FROM customer WHERE code='{code}' "
            f"ON DUPLICATE KEY UPDATE match_type='{match_type}', is_active=1;"
        )
        print(f"Alias {alias_entry['code']} <- {alias_entry['alias']}")

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
        rtype = esc(rule.get("ruleType", "FIXED_PRICE"))
        price = rule.get("price", 0)
        priority = rule.get("priority", 100)
        skip_p = 1 if rule.get("skipPackaging", False) else 0
        skip_d = 1 if rule.get("skipDiscount", False) else 0
        threshold = rule.get("threshold")
        fold_ratio = rule.get("foldRatio")
        if rtype == "FOLD":
            lines.append(
                f"INSERT INTO customer_product_rule "
                f"(customer_id, rule_type, name, priority, keywords, threshold, fold_ratio, "
                f"skip_packaging, skip_discount, is_active) "
                f"SELECT id, '{rtype}', '{name}', {priority}, '{kw}', "
                f"{threshold if threshold is not None else 'NULL'}, "
                f"{fold_ratio if fold_ratio is not None else 'NULL'}, "
                f"{skip_p}, {skip_d}, 1 FROM customer WHERE code='{code}';"
            )
        else:
            ap = esc(json.dumps(rule.get("acceptedPrices") or [], ensure_ascii=False))
            mm = esc(rule.get("matchMode", "first"))
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

    print(f"Applying P0.4 ({len(lines)} statements)...")
    mysql_file(sql_path)
    sql_path.unlink()
    print(f"Done — marker {MARKER}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
