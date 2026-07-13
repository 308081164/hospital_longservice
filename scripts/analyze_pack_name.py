#!/usr/bin/env python3
"""Stream-analyze hospital_reconciliation_row.sql for pack_name patterns."""
import re
import hashlib
import sys
from collections import Counter, defaultdict

SQL = r"D:\Hui_Files\MyProjects\guangsha_technology\hospital-all-master\hospital-all-master\铂康\建表语句\hospital_reconciliation_row.sql"

COLS = [
    "id", "job_id", "sheet_name", "row_number", "delivery_date", "order_no",
    "type", "category_no", "pack_name", "package_material", "pack_count",
    "instrument_count", "unit_price", "total_price", "expected_unit_price",
    "corrected_total_price", "difference", "status", "pricing_rule", "notes_json", "created_at",
]


def parse_values(s: str) -> list:
    vals = []
    i, n = 0, len(s)
    while i < n:
        while i < n and s[i] in " \t,":
            i += 1
        if i >= n:
            break
        if s[i] == "'":
            i += 1
            buf = []
            while i < n:
                if s[i] == "'":
                    if i + 1 < n and s[i + 1] == "'":
                        buf.append("'")
                        i += 2
                    else:
                        i += 1
                        break
                else:
                    buf.append(s[i])
                    i += 1
            vals.append("".join(buf))
        elif s[i : i + 4].upper() == "NULL":
            vals.append(None)
            i += 4
        else:
            j = i
            while j < n and s[j] != ",":
                j += 1
            vals.append(s[i:j].strip())
            i = j
    return vals


def norm_material(m):
    if not m:
        return ""
    return m.strip().replace("×", "*").replace("x", "*").replace("X", "*").lower()


def fingerprint(pn, t, pm):
    inp = f"{(pn or '').strip().lower()}|{(t or '').strip().lower()}|{norm_material(pm or '')}"
    return "fp-" + hashlib.sha256(inp.encode()).hexdigest()[:16]


def classify_pack_name(pn: str) -> str:
    if not pn:
        return "EMPTY"
    if re.search(r"ZSD\d+", pn):
        return "P8_器械包ZSD"
    if re.search(r"针架\d+针\d+/[ZzWw]\d+", pn):
        return "P5_针架复合"
    if re.search(r"[-－]\d+件?/[ZzWw]\d+", pn):
        return "P1_件数斜杠Z码"
    if re.search(r"[-－]\d+[（(][^）)]+[）)][ZzWw]/\d+", pn):
        return "P4_颜色Z斜杠"
    if re.search(r"[-－]\d+/[ZzWw]\d+", pn):
        return "P2_件数斜杠Z码"
    if re.search(r"--\d+/[ZzWw]\d+", pn):
        return "P3_双连字符"
    if re.search(r"[\d.]+\-?\d*件?/[ZzWw]\d+", pn) and "钻头" in pn:
        return "P6_规格钻头"
    if re.search(r"[\d.]+/[ZzWw]\d+", pn) and "钻头" in pn:
        return "P7_规格钻头无件数"
    if re.search(r"/[ZzWw]\d+", pn):
        return "P8_仅斜杠编码"
    if re.search(r"敷料/[Ww]\d+", pn):
        return "P9_敷料W码"
    if re.search(r"\d+(?:\.\d+)?\s*[×x*]\s*\d+", pn):
        return "P10_尺寸规格"
    if re.search(r"双\d+/[ZzWw]", pn):
        return "P11_双袋"
    return "P99_其他"


def main():
    row_count = 0
    empty_pack = 0
    pack_names = set()
    types = set()
    materials = set()
    variants = {}
    pattern_hits = Counter()
    type_counter = Counter()
    pn_counter = Counter()
    pricing_rules = Counter()
    unmatched = []
    price_sets = defaultdict(set)

    with open(SQL, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            if not line.startswith("INSERT"):
                continue
            m = re.search(r"VALUES\s*\((.*)\);\s*$", line)
            if not m:
                continue
            row_count += 1
            vals = parse_values(m.group(1))
            if len(vals) < 21:
                continue
            d = dict(zip(COLS, vals))
            pn = d["pack_name"]
            t = d["type"] or ""
            pm = d["package_material"] or ""
            type_counter[t] += 1

            if not pn or not pn.strip():
                empty_pack += 1
                continue

            pack_names.add(pn)
            types.add(t)
            materials.add(pm)
            pn_counter[pn] += 1
            pid = classify_pack_name(pn)
            pattern_hits[pid] += 1
            if pid == "P99_其他" and len(unmatched) < 40:
                unmatched.append(pn)

            key = (pn, t, pm)
            if key not in variants:
                variants[key] = {"count": 0, "prices": [], "ic": set(), "rules": Counter()}
            v = variants[key]
            v["count"] += 1
            if d["expected_unit_price"] not in (None, "NULL", ""):
                try:
                    p = float(d["expected_unit_price"])
                    v["prices"].append(p)
                    price_sets[key].add(p)
                except ValueError:
                    pass
            if d["instrument_count"] not in (None, "NULL", ""):
                try:
                    v["ic"].add(int(float(d["instrument_count"])))
                except ValueError:
                    pass
            if d["pricing_rule"]:
                v["rules"][d["pricing_rule"]] += 1
                pricing_rules[d["pricing_rule"]] += 1

    multi_price = [(k, price_sets[k], variants[k]["count"]) for k in variants if len(price_sets[k]) > 1]

    print("=== BASIC STATS ===")
    print(f"rows: {row_count}")
    print(f"empty pack_name: {empty_pack}")
    print(f"valid rows: {row_count - empty_pack}")
    print(f"distinct pack_name: {len(pack_names)}")
    print(f"distinct type: {len(types)}")
    print(f"distinct package_material: {len(materials)}")
    print(f"distinct variant (pack|type|material): {len(variants)}")
    print(f"variants with >1 expected_unit_price: {len(multi_price)}")

    print("\n=== TYPE DISTRIBUTION ===")
    for t, c in type_counter.most_common():
        print(f"  {t}: {c} ({100 * c / row_count:.2f}%)")

    print("\n=== PATTERN DISTRIBUTION ===")
    total = sum(pattern_hits.values())
    for pid, c in pattern_hits.most_common():
        print(f"  {pid}: {c} ({100 * c / total:.2f}%)")

    print("\n=== TOP 20 pack_name ===")
    for pn, c in pn_counter.most_common(20):
        print(f"  {c:7d}  {pn}")

    print("\n=== UNMATCHED SAMPLES ===")
    for s in unmatched[:25]:
        print(f"  {s}")

    print("\n=== MULTI-PRICE VARIANTS (top 15 spread) ===")
    spreads = []
    for k, prices, cnt in multi_price:
        ps = sorted(prices)
        spreads.append((ps[-1] - ps[0], k, ps, cnt))
    spreads.sort(reverse=True)
    for spread, k, ps, cnt in spreads[:15]:
        print(f"  spread={spread:.2f} n={cnt} prices={ps} | {k[0][:35]} | {k[1][:15]} | {k[2][:20]}")

    print("\n=== PRICING_RULE TOP 12 ===")
    for pr, c in pricing_rules.most_common(12):
        print(f"  {c:7d}  {pr}")

    # Sample: 拔髓针 price analysis
    print("\n=== 拔髓针-5件/Z7520 SAMPLE ===")
    for key, v in variants.items():
        if key[0] == "拔髓针-5件/Z7520":
            ps = sorted(price_sets[key]) if key in price_sets else []
            print(f"  material={key[2]} type={key[1]} count={v['count']} prices={ps} ic={sorted(v['ic'])}")


if __name__ == "__main__":
    main()
