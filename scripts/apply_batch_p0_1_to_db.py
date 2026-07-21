#!/usr/bin/env python3
"""Apply phase-batch-p0.1.json patches to MySQL (utf8mb4)."""

from __future__ import annotations

import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / "backend/src/main/resources/billing-seeds/phase-batch-p0.1.json"
MARKER = "billing_seed_batch_p0_1_v1"


def mysql_query(sql: str) -> str:
    cmd = [
        "docker", "exec", "hospital-mysql", "sh", "-c",
        f'mysql -uhospital -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 hospital -N -e "{sql}"',
    ]
    return subprocess.check_output(cmd, text=True).strip()


def mysql_file(sql_path: Path) -> None:
    subprocess.check_call(["docker", "cp", str(sql_path), "hospital-mysql:/tmp/p0_1.sql"])
    subprocess.check_call([
        "docker", "exec", "hospital-mysql", "sh", "-c",
        'mysql -uhospital -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 hospital < /tmp/p0_1.sql',
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

    for upd in seed.get("customerUpdates", []):
        code = esc(upd["code"])
        sets = []
        if "billingPricingMode" in upd:
            sets.append(f"billing_pricing_mode='{esc(upd['billingPricingMode'])}'")
        if "billingEnabled" in upd:
            sets.append(f"billing_enabled={1 if upd['billingEnabled'] else 0}")
        if sets:
            lines.append(f"UPDATE customer SET {', '.join(sets)} WHERE code='{code}';")

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

    for patch in seed.get("ruleUpdates", []):
        code = patch["code"]
        rule_name = patch["ruleName"]
        cid = mysql_query(f"SELECT id FROM customer WHERE code='{esc(code)}' LIMIT 1")
        if not cid:
            print(f"WARN: customer {code} not found")
            continue
        row = mysql_query(
            "SELECT keywords, exclude_keywords FROM customer_product_rule "
            f"WHERE customer_id={cid} AND name='{esc(rule_name)}' LIMIT 1"
        )
        if not row:
            print(f"WARN: rule {code}/{rule_name} not found")
            continue
        parts = row.split("\t")
        kw_raw = parts[0] if parts else ""
        ex_raw = parts[1] if len(parts) > 1 else ""
        keywords = parse_json_list(kw_raw)
        exclude = parse_json_list(ex_raw)

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
        lines.append(
            f"UPDATE customer_product_rule SET keywords='{kw_json}', exclude_keywords={ex_sql} "
            f"WHERE customer_id={cid} AND name='{esc(rule_name)}';"
        )
        print(f"Updated rule {code}/{rule_name}: kw={keywords}, ex={exclude}")

    lines.append(
        f"INSERT INTO sys_setting (setting_key, setting_value) "
        f"SELECT '{MARKER}', '1' FROM DUAL "
        f"WHERE NOT EXISTS (SELECT 1 FROM sys_setting WHERE setting_key='{MARKER}');"
    )
    lines.append("COMMIT;")

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".sql", delete=False) as f:
        f.write("\n".join(lines))
        sql_path = Path(f.name)

    print(f"Applying P0.1 ({len(lines)} statements)...")
    mysql_file(sql_path)
    sql_path.unlink()
    print(f"Done — marker {MARKER}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
