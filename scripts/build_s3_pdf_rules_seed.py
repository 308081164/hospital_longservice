#!/usr/bin/env python3
"""Build billing-seeds/phase-s3-pdf-align-20260722.json for S3 partial hospitals."""

from __future__ import annotations

import csv
import json
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
P0 = ROOT / "backend/src/main/resources/billing-seeds/phase-batch-p0.json"
OUT = ROOT / "backend/src/main/resources/billing-seeds/phase-s3-pdf-align-20260722.json"
TEST = ROOT / "测试用例"

sys.path.insert(0, str(ROOT / "scripts"))
from batch_june_price_reconciliation import FOLDER_CODE_OVERRIDE, resolve_profile, load_seed_profiles  # noqa: E402

S3_PARTIAL_FOLDERS = [
    "国药总医院第二院区",
    "国药总医院第三院区",
    "哈尔滨市第二医院",
    "哈尔滨市第五医院",
    "哈尔滨市第五医院（二门诊）",
    "黑龙江省医院（南岗院区）",
    "黑龙江省医院（香坊院区）",
    "祖研-黑龙江省中医医院（三辅院区）",
    "南岗区妇产医院",
    "道外区人民医院",
    "太平人民医院",
    "三精肾病医院",
    "黑龙江中医药大学附属第二医院（南岗）",
    "黑龙江中医药大学附属第二医院（哈南分院）",
]

PDF_GLOBS = [
    "参考/特殊价格单/*.pdf",
    "参考/特殊价格单/**/*.pdf",
]


def load_p0_by_code() -> dict[str, dict]:
    data = json.loads(P0.read_text(encoding="utf-8"))
    return {p["code"]: p for p in data.get("profiles", []) if p.get("code")}


def pack_keyword(pack_name: str) -> str:
    name = (pack_name or "").strip()
    if "/" in name:
        name = name.split("/")[0].strip()
    if not name:
        return pack_name or ""
    return name[:80]


def rules_from_june_csv(folder: str, code: str, existing_names: set[str]) -> list[dict]:
    path = TEST / folder / "6月期待价格校正清单.csv"
    if not path.exists():
        return []
    by_price: dict[float, set[str]] = defaultdict(set)
    with path.open(encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for row in reader:
            cov = (row.get("规则覆盖") or "").strip()
            if cov in ("", "default_heuristic"):
                continue
            try:
                proc = float(row.get("处理后单价") or "")
            except ValueError:
                continue
            kw = pack_keyword(row.get("包名") or "")
            if kw:
                by_price[proc].add(kw)
    rules: list[dict] = []
    pri = 200
    for price in sorted(by_price.keys()):
        keywords = sorted(by_price[price])
        if not keywords:
            continue
        name = f"PDF/期待价{price:g}"
        if name in existing_names:
            continue
        rules.append(
            {
                "ruleType": "FIXED_PRICE",
                "name": name,
                "priority": pri,
                "price": price,
                "keywords": keywords[:40],
                "skipPackaging": True,
                "skipDiscount": True,
            }
        )
        existing_names.add(name)
        pri += 1
    return rules


def pdf_text(path: Path) -> str:
    try:
        out = subprocess.check_output(["pdftotext", "-layout", str(path), "-"], stderr=subprocess.DEVNULL)
        return out.decode("utf-8", errors="replace")
    except (FileNotFoundError, subprocess.CalledProcessError):
        return ""


def rules_from_pdf(folder: str, existing_names: set[str]) -> list[dict]:
    base = TEST / folder
    pdfs: list[Path] = []
    for pat in PDF_GLOBS:
        pdfs.extend(base.glob(pat))
    if not pdfs:
        return []
    text = "\n".join(pdf_text(p) for p in pdfs[:3])
    if not text.strip():
        return []
    # Heuristic: lines with decimal price (2–4 digits)
    price_hits: dict[float, list[str]] = defaultdict(list)
    for line in text.splitlines():
        line = line.strip()
        if len(line) < 4:
            continue
        m = re.search(r"(\d+\.\d{1,2})\s*$", line)
        if not m:
            m = re.search(r"(\d+\.\d{1,2})", line)
        if not m:
            continue
        try:
            price = float(m.group(1))
        except ValueError:
            continue
        if price < 0.5 or price > 5000:
            continue
        label = re.sub(r"\s+\d+\.\d{1,2}.*$", "", line).strip()
        label = re.sub(r"[\d\s元/\\.]+$", "", label).strip()
        if len(label) >= 2 and len(label) <= 60:
            price_hits[price].append(label)
    rules: list[dict] = []
    pri = 300
    for price in sorted(price_hits.keys())[:80]:
        kws = list(dict.fromkeys(price_hits[price]))[:15]
        if not kws:
            continue
        name = f"PDF价{price:g}"
        if name in existing_names:
            continue
        rules.append(
            {
                "ruleType": "FIXED_PRICE",
                "name": name,
                "priority": pri,
                "price": price,
                "keywords": kws,
                "skipPackaging": True,
                "skipDiscount": True,
            }
        )
        existing_names.add(name)
        pri += 1
    return rules


def main() -> int:
    p0_by_code = load_p0_by_code()
    profiles_old = load_seed_profiles()
    out_profiles: list[dict] = []

    for folder in S3_PARTIAL_FOLDERS:
        prof = resolve_profile(folder, profiles_old)
        if not prof:
            print(f"skip no profile: {folder}", file=sys.stderr)
            continue
        code = FOLDER_CODE_OVERRIDE.get(folder, prof.code)
        p0 = p0_by_code.get(code, {})
        rules: list[dict] = list(p0.get("productRules") or [])
        names = {r.get("name") for r in rules if r.get("name")}
        rules.extend(rules_from_june_csv(folder, code, names))
        rules.extend(rules_from_pdf(folder, names))
        if not rules and code == "GUOYAO-3":
            # inherit main campus pattern placeholder — 7折等见 export，产品价走标准+期待清单
            rules = list(p0_by_code.get("GUOYAO-MAIN", {}).get("productRules") or [])
        if not rules and code == "HRB-WY-EM":
            rules = list(p0_by_code.get("HRB-WY", {}).get("productRules") or [])
        node = {
            "code": code,
            "name": p0.get("name") or prof.name or folder,
            "billingEnabled": True,
            "billingPricingMode": p0.get("billingPricingMode") or prof.pricing_mode or "hybrid",
            "notes": f"2026-07-22 S3：P0 规则 + 6月期待清单/PDF 补录（{folder}）",
            "productRules": rules,
        }
        out_profiles.append(node)
        print(f"{folder} -> {code}: {len(rules)} rules")

    payload = {
        "version": "1",
        "description": "S3 十四院：P0 产品规则 + 期待价/PDF 补种（幂等 insert by rule name）",
        "profiles": out_profiles,
    }
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
