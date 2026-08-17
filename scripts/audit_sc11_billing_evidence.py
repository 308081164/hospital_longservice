#!/usr/bin/env python3
"""Cross-index SC11 fixtures against 测试用例 billing evidence and manifest."""

from __future__ import annotations

import csv
import json
import re
from collections import defaultdict
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "backend/src/test/resources/pricing-engine/rule-type-registry.json"
FIXTURES = ROOT / "backend/src/test/resources/pricing-engine/sc11-fixtures.json"
MANIFEST = ROOT / "backend/src/main/resources/billing-seeds/billing-rules-manifest.json"
TEST_CASES = ROOT / "测试用例"
AUDIT_JSON = ROOT / "docs/sc11-billing-evidence-audit.json"
AUDIT_MD = ROOT / "docs/sc11-billing-evidence-audit.md"

REPORT_814 = TEST_CASES / "814新增严格Excel对账报告-20260814.json"
REPORT_V8 = TEST_CASES / "特殊收费v8严格Excel对账报告-20260814.json"

SC11_TYPES = [
    f"SC11-T{i:02d}" if i != 3 else None
    for i in range(1, 17)
]
SC11_TYPES = [t for t in SC11_TYPES if t]
SC11_TYPES.insert(3, "SC11-T03b")  # after T03

TYPE_KEYWORDS: dict[str, list[str]] = {
    "SC11-T01": ["环钻", "手术包", "脂充"],
    "SC11-T02": ["缝合针"],
    "SC11-T03": ["双"],
    "SC11-T03b": ["双"],
    "SC11-T04": ["指针", "P钻", "根管", "机扩", "机锉", "牙探", "镍钛", "棉花针", "洗髓", "车针", "克氏", "银质", "内热", "拔髓", "扩大", "根扩", "卷棉"],
    "SC11-T04b": ["根管锉"],
    "SC11-T05": ["指针", "机扩", "克氏", "银质", "内热", "车针", "拔髓", "扩大", "根扩", "卷棉"],
    "SC11-T06": ["排针"],
    "SC11-T07": ["棉球", "纱布", "驱血带"],
    "SC11-T08": ["方盘", "旋切", "16.5", "22", "44", "氩氦", "种植盒", "车针盒"],
    "SC11-T09": ["孔巾"],
    "SC11-T10": ["敷料", "纱布块"],
    "SC11-T11": ["胶帽", "密封"],
    "SC11-T12": ["塑料管", "管子"],
    "SC11-T13": ["镜头", "检查镜", "镜鞘"],
    "SC11-T14": ["面吸针"],
    "SC11-T15": ["软镜"],
    "SC11-T16": ["普通器械", "Z7526"],
}

HOSPITAL_TO_CODE = {
    "哈尔滨冰城医疗美容医院": "BINGCHENG-YM",
    "国药总医院第二院区": "GUOYAO-2",
    "方南南医院": "FNN-YY",
    "祖研-黑龙江省中医医院（南岗院区）": "ZUYAN-NG",
    "黑龙江省社会康复医院": "SHKF-YY",
    "黑龙江九洲妇科医院": "JIUZHOU-FK",
    "哈尔滨市第五医院（二门诊）": "HRB-WY-EM",
    "NEAU-YY": "NEAU-YY",
    "黑龙江中医药大学附属第一医院": "ZYY-D1",
    "哈尔滨市第二医院": "HRB-2ND",
}


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def scan_correction_csvs() -> list[dict]:
    rows: list[dict] = []
    for path in TEST_CASES.rglob("*期待价格校正清单.csv"):
        if "/." in str(path):
            continue
        hospital_dir = path.parent.name
        month = "7月" if "7月" in path.name else "6月" if "6月" in path.name else path.name
        with path.open(encoding="utf-8-sig", newline="") as f:
            reader = csv.DictReader(f)
            for line_no, row in enumerate(reader, start=2):
                pack = (row.get("包名") or "").strip()
                if not pack:
                    continue
                try:
                    before = float(row.get("原单价") or 0)
                    after = float(row.get("处理后单价") or 0)
                except ValueError:
                    continue
                rows.append(
                    {
                        "source": str(path.relative_to(ROOT)),
                        "line": line_no,
                        "hospitalDir": hospital_dir,
                        "month": month,
                        "department": (row.get("科室") or "").strip(),
                        "shipmentNo": (row.get("发货单号") or "").strip(),
                        "packName": pack,
                        "packCount": row.get("包数", ""),
                        "unitPriceBefore": before,
                        "unitPriceAfter": after,
                        "ruleCoverage": (row.get("规则覆盖") or "").strip(),
                        "matchedRule": (row.get("匹配规则") or "").strip(),
                    }
                )
    return rows


def scan_pending_match_docs() -> list[dict]:
    out: list[dict] = []
    pending = TEST_CASES / "待匹配"
    if not pending.exists():
        return out
    for path in pending.glob("*.md"):
        text = path.read_text(encoding="utf-8")
        codes = re.findall(r"\*\*([A-Z0-9-]+)\*\*", text)
        out.append({"source": str(path.relative_to(ROOT)), "customerCodes": list(dict.fromkeys(codes)), "text": text[:500]})
    return out


def load_audit_reports() -> dict:
    reports = {}
    for key, path in [("814", REPORT_814), ("v8", REPORT_V8)]:
        if path.exists():
            reports[key] = load_json(path)
    return reports


def manifest_profile(manifest: dict, code: str) -> dict:
    node = manifest.get("customers", {}).get(code, {})
    return {
        "code": code,
        "name": node.get("name"),
        "billingPricingMode": node.get("billingPricingMode"),
        "billingEnabled": node.get("billingEnabled"),
        "activeRuleCount": node.get("active_rule_count", 0),
    }


def classify_csv_for_type(sc11_type: str, csv_rows: list[dict]) -> list[dict]:
    kws = TYPE_KEYWORDS.get(sc11_type, [])
    hits = []
    for row in csv_rows:
        pack = row["packName"]
        if any(kw in pack for kw in kws):
            hits.append(row)
        elif sc11_type == "SC11-T07" and any(kw in pack.upper() for kw in ["W60", "W90", "W120", "W150", "W50", "W70"]):
            hits.append(row)
    return hits


def audit_fixture(fixture: dict, manifest: dict) -> dict:
    code = fixture.get("customerCode", "")
    profile = manifest_profile(manifest, code) if code else {}
    valid = fixture.get("valid", True)
    evidence = fixture.get("billingEvidence", "none")
    return {
        "id": fixture.get("id"),
        "sc11Type": fixture.get("sc11Type"),
        "customerCode": code,
        "valid": valid,
        "skipParameterized": fixture.get("skipParameterized", False),
        "billingEvidence": evidence,
        "invalidReason": fixture.get("invalidReason"),
        "manifest": profile,
        "source": fixture.get("source"),
        "expect": fixture.get("expect", {}),
    }


def build_type_summary(
    sc11_type: str,
    registry_entries: list[dict],
    fixtures: list[dict],
    csv_hits: list[dict],
    reports: dict,
) -> dict:
    valid_confirmed = [
        f for f in fixtures if f.get("valid", True) and f.get("billingEvidence") == "confirmed"
    ]
    valid_any = [f for f in fixtures if f.get("valid", True) and not f.get("skipParameterized")]
    verdict = "invalid_pending_materials"
    if valid_confirmed:
        verdict = "confirmed"
    elif csv_hits:
        verdict = "csv_evidence_no_valid_fixture"

    return {
        "sc11Type": sc11_type,
        "registryRuleCount": len(registry_entries),
        "fixtureCount": len(fixtures),
        "validFixtureCount": len(valid_any),
        "confirmedFixtureCount": len(valid_confirmed),
        "csvEvidenceRows": len(csv_hits),
        "csvSamples": csv_hits[:5],
        "verdict": verdict,
        "confirmedFixtures": [f["id"] for f in valid_confirmed],
        "invalidFixtures": [
            f["id"] for f in fixtures if not f.get("valid", True) or f.get("skipParameterized")
        ],
    }


def render_md(audit: dict) -> str:
    lines = [
        "# SC11 规则与账单实践对齐审计",
        "",
        f"| 属性 | 值 |",
        f"|------|-----|",
        f"| 生成日期 | {audit['generatedAt']} |",
        f"| Fixture 总数 | {audit['summary']['fixtureTotal']} |",
        f"| Valid 可跑 | {audit['summary']['validRunnable']} |",
        f"| Confirmed 有账单 | {audit['summary']['confirmedFixtures']} |",
        f"| Invalid 待补材料 | {audit['summary']['invalidFixtures']} |",
        "",
        "## 16 类逐类结论",
        "",
        "| 类型 | Registry 规则数 | CSV 账单行 | Confirmed Fixture | 判定 |",
        "|------|----------------|-----------|-------------------|------|",
    ]
    for t in audit["byType"]:
        lines.append(
            f"| {t['sc11Type']} | {t['registryRuleCount']} | {t['csvEvidenceRows']} | "
            f"{t['confirmedFixtureCount']} ({', '.join(t['confirmedFixtures']) or '-'}) | {t['verdict']} |"
        )

    lines.extend(["", "## Confirmed Fixture 明细", ""])
    for f in audit["fixtures"]:
        if f.get("billingEvidence") == "confirmed" and f.get("valid", True):
            lines.append(f"- **{f['id']}** ({f['sc11Type']}) — {f.get('source', '')}")

    lines.extend(["", "## Invalid / 待补材料", ""])
    pending_types = [t["sc11Type"] for t in audit["byType"] if t["verdict"] != "confirmed"]
    lines.append(f"待补材料类型（{len(pending_types)}）：`{'`, `'.join(pending_types)}`")
    lines.append("")
    lines.append("对齐 [`测试用例/814新增严格Excel对账报告-20260814.md`](测试用例/814新增严格Excel对账报告-20260814.md) §3 优先补录。")
    lines.append("")
    lines.append("## Fixture 全表")
    lines.append("")
    lines.append("| id | 类型 | valid | evidence | customer | mode | enabled |")
    lines.append("|----|------|-------|----------|----------|------|---------|")
    for f in audit["fixtures"]:
        m = f.get("manifest") or {}
        lines.append(
            f"| {f['id']} | {f['sc11Type']} | {f.get('valid', True)} | {f.get('billingEvidence', 'none')} | "
            f"{f.get('customerCode', '')} | {m.get('billingPricingMode', '-')} | {m.get('billingEnabled', '-')} |"
        )
    return "\n".join(lines) + "\n"


def main() -> None:
    registry = load_json(REGISTRY)
    fixtures_doc = load_json(FIXTURES)
    manifest = load_json(MANIFEST)
    csv_rows = scan_correction_csvs()
    pending_docs = scan_pending_match_docs()
    reports = load_audit_reports()

    registry_by_type: dict[str, list] = defaultdict(list)
    for section in ("hospital", "generic", "tier"):
        for entry in registry.get("entries", {}).get(section, []):
            registry_by_type[entry.get("sc11Type", "")].append(entry)

    fixtures = fixtures_doc.get("fixtures", [])
    fixture_audits = [audit_fixture(f, manifest) for f in fixtures]

    by_type = []
    for sc11_type in SC11_TYPES:
        type_fixtures = [f for f in fixtures if f.get("sc11Type") == sc11_type]
        csv_hits = classify_csv_for_type(sc11_type, csv_rows)
        by_type.append(
            build_type_summary(
                sc11_type,
                registry_by_type.get(sc11_type, []),
                type_fixtures,
                csv_hits,
                reports,
            )
        )

    audit = {
        "generatedAt": date.today().isoformat(),
        "sources": {
            "registry": str(REGISTRY.relative_to(ROOT)),
            "fixtures": str(FIXTURES.relative_to(ROOT)),
            "manifest": str(MANIFEST.relative_to(ROOT)),
            "csvFilesScanned": len(list(TEST_CASES.rglob("*期待价格校正清单.csv"))),
            "csvRowsWithPack": len(csv_rows),
            "pendingMatchDocs": len(pending_docs),
        },
        "reports": {
            "814Summary": reports.get("814", {}).get("summary"),
            "v8Summary": reports.get("v8", {}).get("summary"),
        },
        "summary": {
            "fixtureTotal": len(fixtures),
            "validRunnable": sum(
                1
                for f in fixtures
                if f.get("valid", True) and not f.get("skipParameterized", False)
            ),
            "confirmedFixtures": sum(
                1
                for f in fixtures
                if f.get("valid", True) and f.get("billingEvidence") == "confirmed"
            ),
            "invalidFixtures": sum(
                1 for f in fixtures if not f.get("valid", True) or f.get("skipParameterized", False)
            ),
            "typesConfirmed": sum(1 for t in by_type if t["verdict"] == "confirmed"),
            "typesPendingMaterials": sum(1 for t in by_type if t["verdict"] != "confirmed"),
        },
        "byType": by_type,
        "fixtures": fixture_audits,
        "pendingMatchDocs": pending_docs,
    }

    AUDIT_JSON.parent.mkdir(parents=True, exist_ok=True)
    AUDIT_JSON.write_text(json.dumps(audit, ensure_ascii=False, indent=2), encoding="utf-8")
    AUDIT_MD.write_text(render_md(audit), encoding="utf-8")
    print(f"Wrote {AUDIT_JSON}")
    print(f"Wrote {AUDIT_MD}")
    print(
        f"Summary: {audit['summary']['confirmedFixtures']} confirmed fixtures, "
        f"{audit['summary']['typesConfirmed']}/16 types confirmed"
    )


if __name__ == "__main__":
    main()
