#!/usr/bin/env python3
"""Extended pack_name analysis: P99 breakdown, stems, price feasibility."""
import re
import hashlib
from collections import Counter, defaultdict

SQL = r"D:\Hui_Files\MyProjects\guangsha_technology\hospital-all-master\hospital-all-master\铂康\建表语句\hospital_reconciliation_row.sql"
COLS = [
    "id", "job_id", "sheet_name", "row_number", "delivery_date", "order_no",
    "type", "category_no", "pack_name", "package_material", "pack_count",
    "instrument_count", "unit_price", "total_price", "expected_unit_price",
    "corrected_total_price", "difference", "status", "pricing_rule", "notes_json", "created_at",
]


def parse_values(s):
    vals, i, n = [], 0, len(s)
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


def extract_stem(pn):
    """Match BokangSqlInsertParser.extractPackNameStem"""
    if not pn:
        return None
    dash = pn.find("-")
    if dash > 0:
        return pn[:dash].strip()
    slash = pn.find("/")
    if slash > 0:
        return pn[:slash].strip()
    return pn.strip()


def classify_p99(pn):
    if re.search(r"ZSD\d+", pn):
        return "ZSD编码包"
    if re.search(r"/[Ww]\d+", pn):
        return "W码器械包"
    if re.search(r"针\d+", pn) and "/" in pn:
        return "混合计数包"
    if re.search(r"\d+件", pn) and "/" not in pn:
        return "仅件数无编码"
    if re.search(r"双[zZ]", pn):
        return "双袋变体"
    if re.search(r"敷料", pn):
        return "敷料子项"
    if not re.search(r"[/\-－]", pn):
        return "纯包名无后缀"
    return "其他复合"


def main():
    rows = []
    variants = {}
    stems = set()
    pack_type_mat = set()
    pack_only = set()

    with open(SQL, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            if not line.startswith("INSERT"):
                continue
            m = re.search(r"VALUES\s*\((.*)\);\s*$", line)
            if not m:
                continue
            vals = parse_values(m.group(1))
            if len(vals) < 21:
                continue
            d = dict(zip(COLS, vals))
            pn = d["pack_name"]
            if not pn or not pn.strip():
                continue
            t, pm = d["type"] or "", d["package_material"] or ""
            pack_only.add(pn)
            pack_type_mat.add((pn, t, pm))
            stems.add(extract_stem(pn))

            key = (pn, t, pm)
            if key not in variants:
                variants[key] = {
                    "count": 0,
                    "prices": Counter(),
                    "unit_prices": Counter(),
                    "ic": Counter(),
                    "rules": Counter(),
                    "pack_counts": Counter(),
                }
            v = variants[key]
            v["count"] += 1
            for fld, bucket in [
                ("expected_unit_price", "prices"),
                ("unit_price", "unit_prices"),
                ("instrument_count", "ic"),
                ("pricing_rule", "rules"),
                ("pack_count", "pack_counts"),
            ]:
                val = d[fld]
                if val not in (None, "NULL", ""):
                    try:
                        if fld in ("expected_unit_price", "unit_price"):
                            v[bucket][float(val)] += 1
                        else:
                            v[bucket][val] += 1
                    except ValueError:
                        pass

    p99 = Counter()
    with open(SQL, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            if not line.startswith("INSERT"):
                continue
            m = re.search(r"VALUES\s*\((.*)\);\s*$", line)
            if not m:
                continue
            vals = parse_values(m.group(1))
            pn = vals[8] if len(vals) > 8 else ""
            if not pn:
                continue
            # rows not matching P1,P4,P5,P6,P8 slash Z
            if not (
                re.search(r"[-－]\d+件?/[ZzWw]\d+", pn)
                or re.search(r"[-－]\d+[（(][^）)]+[）)][ZzWw]/\d+", pn)
                or re.search(r"针架\d+针\d+/[ZzWw]\d+", pn)
                or (re.search(r"[\d.]+\-?\d*件?/[ZzWw]\d+", pn) and "钻头" in pn)
                or re.search(r"/[ZzWw]\d+", pn)
            ):
                p99[classify_p99(pn)] += 1

    print("=== DEDUP KEYS ===")
    print(f"distinct pack_name only: {len(pack_only)}")
    print(f"distinct stem (Bokang): {len(stems)}")
    print(f"distinct pack|type|material: {len(pack_type_mat)}")

    print("\n=== P99 SUB-BREAKDOWN (26% bucket) ===")
    total = sum(p99.values())
    for k, c in p99.most_common():
        print(f"  {k}: {c} ({100*c/total:.1f}%)")

    # Price stability by pricing_rule
    rule_price_var = defaultdict(set)
    for key, v in variants.items():
        for rule in v["rules"]:
            for p in v["prices"]:
                rule_price_var[rule].add(p)

    print("\n=== PRICING_RULE PRICE DIVERSITY (rules with most distinct prices) ===")
    rule_div = [(len(ps), rule) for rule, ps in rule_price_var.items()]
    rule_div.sort(reverse=True)
    for n, rule in rule_div[:10]:
        print(f"  {n:4d} distinct prices  {rule}")

    # Per-piece reverse for 高温纸塑袋20cm
    print("\n=== 20cm PAPER PLASTIC: instrument_count vs expected_unit_price ===")
    ic_price = defaultdict(Counter)
    with open(SQL, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            if not line.startswith("INSERT"):
                continue
            m = re.search(r"VALUES\s*\((.*)\);\s*$", line)
            if not m:
                continue
            vals = parse_values(m.group(1))
            if len(vals) < 21:
                continue
            if vals[18] != "高温纸塑袋20cm计费":
                continue
            try:
                ic = int(float(vals[11]))
                ep = float(vals[14]) if vals[14] not in (None, "NULL", "") else None
            except (ValueError, TypeError):
                continue
            if ep is not None:
                ic_price[ic][ep] += 1

    for ic in sorted(ic_price.keys())[:12]:
        top = ic_price[ic].most_common(3)
        print(f"  ic={ic:2d}: {top}")

    # 机扩针 samples
    print("\n=== 机扩针架 variants (price by needle count) ===")
    for key, v in sorted(variants.items(), key=lambda x: -x[1]["count"]):
        pn = key[0]
        if "机扩针架" in pn and "100*200" in key[2]:
            med = v["prices"].most_common(1)
            ic = v["ic"].most_common(1)
            print(f"  {pn[:30]:30s} n={v['count']:4d} ic={ic} price={med}")

    # Same pack_name different material
    pn_mats = defaultdict(set)
    for pn, t, pm in pack_type_mat:
        pn_mats[pn].add(pm)
    multi_mat = [(pn, mats) for pn, mats in pn_mats.items() if len(mats) > 3]
    print(f"\n=== pack_name with >3 materials: {len(multi_mat)} ===")
    for pn, mats in sorted(multi_mat, key=lambda x: -len(x[1]))[:8]:
        print(f"  {pn[:40]:40s} -> {len(mats)} materials")


if __name__ == "__main__":
    main()
