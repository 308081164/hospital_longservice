#!/usr/bin/env python3
"""Apply phase-batch-p0.5.2.json patches to MySQL (utf8mb4)."""

from __future__ import annotations

import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / "backend/src/main/resources/billing-seeds/phase-batch-p0.5.2.json"
MARKER = "billing_seed_batch_p0_5_2_v1"


def mysql_query(sql: str) -> str:
    cmd = [
        "docker", "exec", "hospital-mysql", "sh", "-c",
        f'mysql -uhospital -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 hospital -N -e "{sql}"',
    ]
    return subprocess.check_output(cmd, text=True).strip()


def mysql_file(sql_path: Path) -> None:
    subprocess.check_call(["docker", "cp", str(sql_path), "hospital-mysql:/tmp/p0_5_2.sql"])
    subprocess.check_call([
        "docker", "exec", "hospital-mysql", "sh", "-c",
        'mysql -uhospital -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 hospital < /tmp/p0_5_2.sql',
    ])


def esc(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "''")


def parse_json_list(raw: str | None) -> list[str]:
    if not raw or raw == "NULL":
        return []
    try:
        val = json.loads(raw)
        return list(val) if isinstance(val, list) else []
    except json.JSONDecodeError:
        return []


def main() -> int:
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    lines = ["SET NAMES utf8mb4;", "START TRANSACTION;"]

    for patch in seed.get("ruleUpdates", []):
        code = patch["code"]
        rule_name = patch["ruleName"]
        cid = mysql_query(f"SELECT id FROM customer WHERE code='{esc(code)}' LIMIT 1")
        if not cid:
            print(f"WARN: customer {code} not found")
            continue
        row = mysql_query(
            "SELECT keywords, exclude_keywords, match_mode, accepted_prices FROM customer_product_rule "
            f"WHERE customer_id={cid} AND name='{esc(rule_name)}' LIMIT 1"
        )
        if not row:
            print(f"WARN: rule {code}/{rule_name} not found")
            continue
        parts = row.split("\t")
        keywords = patch.get("setKeywords") or parse_json_list(parts[0] if parts else "")
        exclude = parse_json_list(parts[1] if len(parts) > 1 else "")
        match_mode = patch.get("setMatchMode") or (parts[2] if len(parts) > 2 else "first")
        accepted = patch.get("setAcceptedPrices")
        if accepted is None and len(parts) > 3:
            try:
                accepted = json.loads(parts[3]) if parts[3] and parts[3] != "NULL" else []
            except json.JSONDecodeError:
                accepted = []

        for rm in patch.get("removeKeywords", []):
            keywords = [k for k in keywords if k != rm]
        for add in patch.get("addKeywords", []):
            if add not in keywords:
                keywords.append(add)
        for add_ex in patch.get("addExcludeKeywords", []):
            if add_ex not in exclude:
                exclude.append(add_ex)

        kw_json = esc(json.dumps(keywords, ensure_ascii=False))
        ex_json = esc(json.dumps(exclude, ensure_ascii=False)) if exclude else "NULL"
        ex_sql = f"'{ex_json}'" if exclude else "NULL"
        ap_json = esc(json.dumps(accepted or [], ensure_ascii=False))
        lines.append(
            f"UPDATE customer_product_rule SET keywords='{kw_json}', exclude_keywords={ex_sql}, "
            f"match_mode='{esc(match_mode)}', accepted_prices='{ap_json}' "
            f"WHERE customer_id={cid} AND name='{esc(rule_name)}';"
        )
        print(f"Updated {code}/{rule_name}")

    for rule in seed.get("newRules", []):
        code = esc(rule["code"])
        name = esc(rule["name"])
        exists = mysql_query(
            f"SELECT COUNT(*) FROM customer_product_rule r JOIN customer c ON r.customer_id=c.id "
            f"WHERE c.code='{code}' AND r.name='{name}'"
        )
        if exists != "0":
            print(f"Skip existing {rule['code']}/{rule['name']}")
            continue
        kw = esc(json.dumps(rule.get("keywords") or [], ensure_ascii=False))
        ap = esc(json.dumps(rule.get("acceptedPrices") or [], ensure_ascii=False))
        mats = esc(json.dumps(rule.get("materials") or [], ensure_ascii=False))
        rtype = esc(rule.get("ruleType", "FIXED_PRICE"))
        mm = esc(rule.get("matchMode", "first"))
        price = rule.get("price", 0)
        priority = rule.get("priority", 100)
        skip_p = 1 if rule.get("skipPackaging", False) else 0
        skip_d = 1 if rule.get("skipDiscount", False) else 0
        mat_sql = f"'{mats}'" if rule.get("materials") else "NULL"
        lines.append(
            f"INSERT INTO customer_product_rule "
            f"(customer_id, rule_type, name, priority, price, keywords, materials, match_mode, accepted_prices, "
            f"skip_packaging, skip_discount, is_active) "
            f"SELECT id, '{rtype}', '{name}', {priority}, {price}, '{kw}', {mat_sql}, '{mm}', '{ap}', "
            f"{skip_p}, {skip_d}, 1 FROM customer WHERE code='{code}';"
        )
        print(f"Insert {rule['code']}/{rule['name']}")

    for deact in seed.get("deactivateRules", []):
        code = esc(deact["code"])
        name = esc(deact["ruleName"])
        lines.append(
            f"UPDATE customer_product_rule r JOIN customer c ON r.customer_id=c.id "
            f"SET r.is_active=0 WHERE c.code='{code}' AND r.name='{name}';"
        )
        print(f"Deactivate {deact['code']}/{deact['ruleName']}")

    lines.append(
        f"INSERT INTO sys_setting (setting_key, setting_value) "
        f"SELECT '{MARKER}', '1' FROM DUAL "
        f"WHERE NOT EXISTS (SELECT 1 FROM sys_setting WHERE setting_key='{MARKER}');"
    )
    lines.append("COMMIT;")

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".sql", delete=False) as f:
        f.write("\n".join(lines))
        sql_path = Path(f.name)
    print(f"Applying P0.5.2 ({len(lines)} statements)...")
    mysql_file(sql_path)
    sql_path.unlink()
    print(f"Done — marker {MARKER}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
