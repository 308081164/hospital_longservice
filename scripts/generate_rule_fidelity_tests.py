#!/usr/bin/env python3
"""Generate rule-fidelity test fixtures from manifest + 测试用例 Excel pairs."""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict, dataclass, field
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from batch_june_price_reconciliation import (  # noqa: E402
    FOLDER_CODE_OVERRIDE,
    TEST_CASE_DIR,
    ExpectedPriceRow,
    extract_expected_price_rows,
    iter_compare_pairs,
    load_seed_profiles,
    parse_workbook,
    pick_month_pair,
    resolve_profile,
)

MANIFEST = ROOT / "backend/src/main/resources/billing-seeds/billing-rules-manifest.json"
OUT_CATALOG = ROOT / "backend/src/test/resources/rule-fidelity-catalog.json"
OUT_GOLDEN = ROOT / "backend/src/test/resources/rule-fidelity-excel-goldens.json"
OUT_REPORT = ROOT / "测试用例/rule-fidelity-generation-report.json"

KNOWN_NEGATIVE: list[dict] = [
    {
        "id": "guoyao2_july_extra_bite",
        "hospital": "国药总医院第二院区",
        "customerCode": "GUOYAO-2",
        "sheet": "手术室",
        "shipNo": "1620721",
        "packName": "咬针器-1/W6050",
        "type": "高温纸塑袋75*200",
        "packageMaterial": "高温纸塑袋75*200",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 16.5,
        "totalPrice": 16.5,
        "note": "7月 raw=proc，不得 warning",
    },
    {
        "id": "guoyao2_july_extra_tourniquet",
        "hospital": "国药总医院第二院区",
        "customerCode": "GUOYAO-2",
        "sheet": "手术室",
        "shipNo": "1620721",
        "packName": "驱血带(高温)/Z2032",
        "type": "敷料包(无纺布包)",
        "packageMaterial": "无纺布-90×90-50g",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 13.0,
        "totalPrice": 13.0,
        "note": "7月 raw=proc，不得 warning",
    },
    {
        "id": "jiuzhou_june_extra_renliu",
        "hospital": "黑龙江九洲妇科医院",
        "customerCode": "JIUZHOU-FK",
        "sheet": "手术室",
        "shipNo": "1608599",
        "packName": "人流包-22件/w9050",
        "type": "高温无纺布-90×90-50g",
        "packageMaterial": "无纺布-90×90-50g",
        "instrumentCount": 11,
        "packCount": 11,
        "unitPrice": 121.0,
        "totalPrice": 121.0,
        "note": "6月 raw=proc，不得 warning",
    },
    {
        "id": "zuyan_beauty_extra_scissors",
        "hospital": "祖研-黑龙江省中医医院（南岗院区）",
        "customerCode": "ZUYAN-NG",
        "sheet": "美容科",
        "shipNo": "1614899",
        "packName": "剪刀-3/z1530",
        "type": "高温纸塑袋75*300",
        "packageMaterial": "高温纸塑袋75*300",
        "instrumentCount": 3,
        "packCount": 1,
        "unitPrice": 22.0,
        "totalPrice": 22.0,
        "note": "6月 raw=proc，不得 warning",
    },
]

BINGCHENG_V8_CASES: list[dict] = [
    {
        "id": "bingcheng_huanzuan_1pc",
        "customerCode": "BINGCHENG-YM",
        "hospital": "哈尔滨冰城医疗美容医院",
        "packName": "环钻包",
        "instrumentCount": 5,
        "packCount": 1,
        "unitPrice": 27.5,
        "expectedUnitPrice": 8.5,
        "mustNotHitRule": "环钻27.5",
        "mustHitRules": ["冰城环钻包按件5.5", "冰城环钻包无纺布加价3"],
    },
    {
        "id": "bingcheng_huanzuan_2pc",
        "customerCode": "BINGCHENG-YM",
        "hospital": "哈尔滨冰城医疗美容医院",
        "packName": "环钻包",
        "instrumentCount": 10,
        "packCount": 2,
        "unitPrice": 27.5,
        "expectedUnitPrice": 33.5,
        "mustNotHitRule": "环钻27.5",
        "mustHitRules": ["冰城环钻包按件5.5", "冰城环钻包无纺布加价3"],
    },
]


@dataclass
class CatalogCase:
    id: str
    customerCode: str
    customerName: str
    ruleName: str
    ruleType: str
    positive: bool
    row: dict
    note: str = ""


@dataclass
class GoldenCase:
    id: str
    hospital: str
    customerCode: str
    month: int
    kind: str  # expected | negative
    row: dict
    note: str = ""


def _synthetic_row(hospital: str, pack_name: str, keywords: list[str]) -> dict:
    kw = keywords[0] if keywords else pack_name
    return {
        "hospitalName": hospital,
        "department": "手术室",
        "type": "高温纸塑袋75*200",
        "packName": pack_name or kw,
        "packageMaterial": "高温纸塑袋75*200",
        "instrumentCount": 5,
        "packCount": 1,
        "unitPrice": 22.0,
        "totalPrice": 22.0,
    }


def build_catalog(manifest: dict) -> list[CatalogCase]:
    cases: list[CatalogCase] = []
    for code, node in sorted(manifest.get("customers", {}).items()):
        name = node.get("name") or code
        for rule in node.get("productRules") or []:
            if not rule.get("isActive", True):
                continue
            rule_name = rule.get("name") or ""
            rule_type = rule.get("ruleType") or ""
            keywords = list(rule.get("keywords") or [])
            pack = keywords[0] if keywords else rule_name
            pos_row = _synthetic_row(name, pack, keywords)
            cases.append(
                CatalogCase(
                    id=f"{code}__{rule_name}__hit",
                    customerCode=code,
                    customerName=name,
                    ruleName=rule_name,
                    ruleType=rule_type,
                    positive=True,
                    row=pos_row,
                    note="active rule should match",
                )
            )
            neg_pack = "不匹配规则专用包名XYZ"
            if keywords:
                for ex in rule.get("excludeKeywords") or []:
                    neg_pack = f"{ex}测试包"
                    break
            neg_row = _synthetic_row(name, neg_pack, [])
            cases.append(
                CatalogCase(
                    id=f"{code}__{rule_name}__miss",
                    customerCode=code,
                    customerName=name,
                    ruleName=rule_name,
                    ruleType=rule_type,
                    positive=False,
                    row=neg_row,
                    note="should not match this rule",
                )
            )
    return cases


def _customer_code(hospital: str, profiles: dict) -> str:
    if hospital in FOLDER_CODE_OVERRIDE:
        return FOLDER_CODE_OVERRIDE[hospital]
    profile = resolve_profile(hospital, profiles)
    return profile.code if profile else ""


def build_excel_goldens(profiles: dict, months: list[int], *, hospital_filter: set[str] | None = None) -> tuple[list[GoldenCase], dict]:
    goldens: list[GoldenCase] = []
    stats = {"hospitals_scanned": 0, "pairs_found": 0, "expected_rows": 0, "negative_rows": 0}

    for hospital_dir in sorted(TEST_CASE_DIR.iterdir()):
        if not hospital_dir.is_dir() or hospital_dir.name.startswith("."):
            continue
        if hospital_dir.name in {"待匹配", ".cli_smoke"}:
            continue
        if hospital_filter and hospital_dir.name not in hospital_filter:
            continue
        stats["hospitals_scanned"] += 1
        hospital = hospital_dir.name
        code = _customer_code(hospital, profiles)

        for month in months:
            raw_path, proc_path, note = pick_month_pair(hospital_dir, month)
            if not raw_path or not proc_path:
                continue
            expected, _, _, _ = extract_expected_price_rows(hospital_dir, month)
            stats["pairs_found"] += 1
            raw_wb = parse_workbook(raw_path)
            proc_wb = parse_workbook(proc_path)
            for er in expected:
                stats["expected_rows"] += 1
                goldens.append(
                    GoldenCase(
                        id=f"{hospital}__{month}__E__{er.ship_no}__{er.pack_name[:20]}",
                        hospital=hospital,
                        customerCode=code,
                        month=month,
                        kind="expected",
                        row={
                            "sheet": er.sheet,
                            "shipNo": er.ship_no,
                            "packName": er.pack_name,
                            "packCount": er.pack_count,
                            "rawUnit": er.raw_unit,
                            "expectedUnitPrice": er.proc_unit,
                            "type": "",
                            "packageMaterial": "",
                            "instrumentCount": 1,
                        },
                        note=er.note or note,
                    )
                )
            # sample up to 3 unchanged rows per pair as negative guards
            neg_added = 0
            for sheet, raw, proc in iter_compare_pairs(raw_wb, proc_wb):
                if neg_added >= 3:
                    break
                if raw.unit_price is None or proc.unit_price is None:
                    continue
                if abs(raw.unit_price - proc.unit_price) > 0.001:
                    continue
                stats["negative_rows"] += 1
                neg_added += 1
                goldens.append(
                    GoldenCase(
                        id=f"{hospital}__{month}__U__{raw.ship_no}__{neg_added}",
                        hospital=hospital,
                        customerCode=code,
                        month=month,
                        kind="negative",
                        row={
                            "sheet": sheet,
                            "shipNo": str(raw.ship_no),
                            "packName": raw.pack_name,
                            "packCount": raw.pack_count,
                            "unitPrice": raw.unit_price,
                            "totalPrice": raw.total_price,
                            "type": raw.pack_type or "",
                            "packageMaterial": raw.material or "",
                            "instrumentCount": int(raw.instrument_count or 1),
                        },
                        note="raw=proc unchanged guard",
                    )
                )

    for item in KNOWN_NEGATIVE:
        goldens.append(
            GoldenCase(
                id=item["id"],
                hospital=item["hospital"],
                customerCode=item["customerCode"],
                month=0,
                kind="negative",
                row={k: v for k, v in item.items() if k not in {"id", "hospital", "customerCode", "note"}},
                note=item.get("note", ""),
            )
        )

    return goldens, stats


def main() -> int:
    p = argparse.ArgumentParser(description="Generate rule fidelity fixtures")
    p.add_argument("--write", action="store_true")
    p.add_argument("--months", default="6,7")
    p.add_argument("--all-hospitals", action="store_true", help="scan all 测试用例 dirs (slow)")
    args = p.parse_args()
    months = [int(x) for x in args.months.split(",") if x.strip()]

    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    catalog = build_catalog(manifest)
    profiles = load_seed_profiles()
    hospital_filter = None
    if not args.all_hospitals:
        hospital_filter = set()
        for code, node in manifest.get("customers", {}).items():
            if node.get("billingEnabled") or (node.get("active_rule_count") or 0) > 0:
                name = node.get("name")
                if name:
                    hospital_filter.add(name)
        hospital_filter.update(FOLDER_CODE_OVERRIDE.keys())
        hospital_filter.update(
            {
                "哈尔滨冰城医疗美容医院",
                "国药总医院第二院区",
                "黑龙江菁华上德生殖妇产医院",
                "黑龙江九洲妇科医院",
                "祖研-黑龙江省中医医院（南岗院区）",
            }
        )
    goldens, stats = build_excel_goldens(profiles, months, hospital_filter=hospital_filter)

    payload_catalog = {
        "generatedAt": date.today().isoformat(),
        "sourceManifest": str(MANIFEST.relative_to(ROOT)),
        "caseCount": len(catalog),
        "bingchengV8Cases": BINGCHENG_V8_CASES,
        "cases": [asdict(c) for c in catalog],
    }
    payload_golden = {
        "generatedAt": date.today().isoformat(),
        "caseCount": len(goldens),
        "stats": stats,
        "cases": [asdict(c) for c in goldens],
    }
    report = {
        "generatedAt": date.today().isoformat(),
        "catalogCases": len(catalog),
        "excelGoldenCases": len(goldens),
        "stats": stats,
    }

    if args.write:
        OUT_CATALOG.parent.mkdir(parents=True, exist_ok=True)
        OUT_CATALOG.write_text(json.dumps(payload_catalog, ensure_ascii=False, indent=2), encoding="utf-8")
        OUT_GOLDEN.write_text(json.dumps(payload_golden, ensure_ascii=False, indent=2), encoding="utf-8")
        OUT_REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"catalog: {OUT_CATALOG} ({len(catalog)} cases)")
        print(f"golden:  {OUT_GOLDEN} ({len(goldens)} cases)")
    else:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
