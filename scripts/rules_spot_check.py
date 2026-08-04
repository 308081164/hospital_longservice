#!/usr/bin/env python3
"""Spot-check pricing via simulate API and verify deploy reconcile hash."""

from __future__ import annotations

import math
from pathlib import Path
from typing import Any, Callable

from lib.api_client import ApiClient
from rules_compare import load_manifest, run_rules_compare

MANIFEST_HASH_KEY = "billing_rules_manifest_hash"

HRB_2ND_HOSPITAL = "哈尔滨市第二医院"

HRB_2ND_SPOT_CHECKS: list[dict[str, Any]] = [
    {
        "name": "正畸车针8元",
        "department": "口腔科（正）",
        "packName": "正畸去胶车针-1/Z7520",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋75*200",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 8.0,
        "totalPrice": 8.0,
        "expectedUnitPrice": 8.0,
    },
    {
        "name": "口腔调刀8元",
        "department": "口腔科(内)",
        "packName": "调刀-1/保z7530",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋75*200",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 5.5,
        "totalPrice": 5.5,
        "expectedUnitPrice": 8.0,
    },
    {
        "name": "手术室止血带8元",
        "department": "手术室",
        "packName": "市二院止血带7个（只消毒）",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋75*200",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 0.0,
        "totalPrice": 0.0,
        "expectedUnitPrice": 8.0,
    },
    {
        "name": "电凝钩22元/件",
        "department": "手术室",
        "packName": "电凝钩吸引器-1/件双/z1060",
        "type": "器械包(ZSD)",
        "packageMaterial": "高温灭菌无纺布60*60",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 41.5,
        "totalPrice": 41.5,
        "expectedUnitPrice": 22.0,
    },
]

ZYY_D1_HOSPITAL = "黑龙江中医药大学附属第一医院"

ZYY_D1_SPOT_CHECKS: list[dict[str, Any]] = [
    {
        "name": "橄榄头FOLD 70.33",
        "department": "耳鼻喉门诊",
        "packName": "橄榄头-20/Z2030",
        "type": "额外包(低温等离子)",
        "packageMaterial": "低温纸塑袋200*300",
        "instrumentCount": 100,
        "packCount": 5,
        "unitPrice": 70.4,
        "totalPrice": 352.0,
        "expectedUnitPrice": 70.33,
        "expectedCorrectedTotal": 351.65,
        "priceTol": 0.001,
    },
    {
        "name": "30°腹腔镜 30.38",
        "department": "手术室(一区)",
        "packName": "30°腹腔镜-1/z2060",
        "type": "器械包(ZSD)",
        "packageMaterial": "",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 28.0,
        "totalPrice": 28.0,
        "expectedUnitPrice": 30.38,
        "expectedCorrectedTotal": 30.38,
        "priceTol": 0.001,
    },
    {
        "name": "W15050 手术衣 27.97",
        "department": "手术室(一区)",
        "packName": "手术衣/W15050",
        "type": "敷料包(无纺布包)",
        "packageMaterial": "无纺布-150×150-50g",
        "instrumentCount": 0,
        "packCount": 1,
        "unitPrice": 28.0,
        "totalPrice": 28.0,
        "expectedUnitPrice": 27.97,
        "expectedCorrectedTotal": 27.97,
        "priceTol": 0.001,
    },
    {
        "name": "W9050 枪状镊 4.4×40",
        "department": "手术室(二区)",
        "packName": "枪状镊11弯针6吸引管12喉镜10盘1/w9050",
        "type": "器械包",
        "packageMaterial": "无纺布",
        "instrumentCount": 40,
        "packCount": 1,
        "unitPrice": 110.0,
        "totalPrice": 110.0,
        "expectedUnitPrice": 176.0,
        "expectedCorrectedTotal": 176.0,
        "priceTol": 0.001,
    },
    {
        "name": "冲洗头-79 固定价220",
        "department": "耳鼻喉病房",
        "packName": "冲洗头-79/z2030",
        "type": "额外包(低温等离子)",
        "packageMaterial": "低温纸塑袋200*300",
        "instrumentCount": 79,
        "packCount": 1,
        "unitPrice": 202.4,
        "totalPrice": 202.4,
        "expectedUnitPrice": 220.0,
        "expectedCorrectedTotal": 220.0,
        "priceTol": 0.001,
    },
]

SPOT_CHECK_PRESETS: dict[str, list[dict[str, Any]]] = {
    "HRB-2ND": HRB_2ND_SPOT_CHECKS,
    "ZYY-D1": ZYY_D1_SPOT_CHECKS,
}


def _row_field(row: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        val = row.get(key)
        if val is not None:
            return val
    return None


def _price_close(actual: Any, expected: float, *, tol: float = 0.02) -> bool:
    try:
        return math.isclose(float(actual), expected, abs_tol=tol)
    except (TypeError, ValueError):
        return False


def run_spot_check(
    client: ApiClient,
    *,
    code: str,
    hospital_name: str | None = None,
    checks: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    code = code.strip().upper()
    preset = checks or SPOT_CHECK_PRESETS.get(code)
    if not preset:
        raise ValueError(f"无 spot-check 预设: {code}")

    client.login(force=True)
    customer = client.customer_by_code(code)
    if customer is None:
        return {
            "command": "rules spot-check",
            "code": code,
            "ok": False,
            "error": "customer not found",
            "results": [],
        }

    customer_id = int(customer.get("id") or customer.get("customerId") or 0)
    rule_id = (
        customer.get("defaultRuleId")
        or customer.get("default_rule_id")
    )
    if rule_id is not None:
        rule_id = int(rule_id)
    else:
        rule_id = 1

    hospital = hospital_name or customer.get("name") or customer.get("canonicalName") or code
    if code == "HRB-2ND":
        hospital = HRB_2ND_HOSPITAL
    if code == "ZYY-D1":
        hospital = ZYY_D1_HOSPITAL

    results: list[dict[str, Any]] = []
    for case in preset:
        skip = {"name", "expectedUnitPrice", "expectedCorrectedTotal", "priceTol"}
        sample = {k: v for k, v in case.items() if k not in skip}
        sample.setdefault("sheetName", sample.get("department"))
        tol = float(case.get("priceTol", 0.02))
        try:
            sim = client.simulate_billing(
                customer_id=customer_id,
                hospital_name=hospital,
                sample_row=sample,
                rule_id=rule_id,
            )
            actual = _row_field(sim, "expectedUnitPrice", "expected_unit_price")
            expected = float(case["expectedUnitPrice"])
            ok = _price_close(actual, expected, tol=tol)
            actual_total = _row_field(sim, "correctedTotalPrice", "corrected_total_price")
            expected_total = case.get("expectedCorrectedTotal")
            if expected_total is not None:
                total_ok = _price_close(actual_total, float(expected_total), tol=tol)
                ok = ok and total_ok
            results.append(
                {
                    "name": case["name"],
                    "ok": ok,
                    "expectedUnitPrice": expected,
                    "actualUnitPrice": actual,
                    "expectedCorrectedTotal": expected_total,
                    "actualCorrectedTotal": actual_total,
                    "status": _row_field(sim, "status"),
                    "pricingRule": _row_field(sim, "pricingRule", "pricing_rule"),
                }
            )
        except Exception as exc:
            results.append(
                {
                    "name": case["name"],
                    "ok": False,
                    "error": str(exc),
                    "expectedUnitPrice": case.get("expectedUnitPrice"),
                }
            )

    ok = all(r.get("ok") for r in results)
    return {
        "command": "rules spot-check",
        "code": code,
        "hospital_name": hospital,
        "api_base": client.api_base,
        "ok": ok,
        "passed": sum(1 for r in results if r.get("ok")),
        "total": len(results),
        "results": results,
    }


def run_verify_deploy(
    client: ApiClient,
    *,
    code: str | None,
    compare_all: bool,
    manifest_path: Path | None,
    mysql_hash_reader: Callable[[], str | None] | None,
    spot_check_code: str | None = None,
) -> dict[str, Any]:
    compare_report = run_rules_compare(
        client,
        code=code,
        compare_all=compare_all,
        manifest_path=manifest_path,
    )
    manifest = load_manifest(manifest_path)
    expected_hash = str(manifest.get("manifest_hash") or "")

    prod_hash: str | None = None
    hash_ok: bool | None = None
    if mysql_hash_reader is not None:
        prod_hash = mysql_hash_reader()
        if prod_hash is not None:
            hash_ok = prod_hash.strip() == expected_hash.strip()

    spot_report: dict[str, Any] | None = None
    if spot_check_code:
        spot_report = run_spot_check(client, code=spot_check_code)

    ok = bool(compare_report.get("ok"))
    if hash_ok is False:
        ok = False
    if spot_report is not None and not spot_report.get("ok"):
        ok = False

    return {
        "command": "rules verify-deploy",
        "ok": ok,
        "manifest_hash_expected": expected_hash,
        "manifest_hash_prod": prod_hash,
        "manifest_hash_ok": hash_ok,
        "compare": compare_report,
        "spot_check": spot_report,
    }


def format_spot_check_human(report: dict[str, Any]) -> str:
    lines = [
        f"spot-check {report.get('code')}: "
        f"{'PASS' if report.get('ok') else 'FAIL'} "
        f"({report.get('passed')}/{report.get('total')})",
    ]
    for row in report.get("results") or []:
        mark = "OK" if row.get("ok") else "FAIL"
        if row.get("error"):
            lines.append(f"  [{mark}] {row.get('name')}: ERROR {row['error']}")
        else:
            lines.append(
                f"  [{mark}] {row.get('name')}: "
                f"expected={row.get('expectedUnitPrice')} actual={row.get('actualUnitPrice')} "
                f"rule={row.get('pricingRule')}"
            )
    return "\n".join(lines)


def format_verify_deploy_human(report: dict[str, Any]) -> str:
    lines = [
        f"verify-deploy: {'PASS' if report.get('ok') else 'FAIL'}",
        f"  manifest hash expected: {(report.get('manifest_hash_expected') or '')[:16]}…",
    ]
    if report.get("manifest_hash_prod") is not None:
        ok = report.get("manifest_hash_ok")
        mark = "OK" if ok else "FAIL"
        lines.append(
            f"  manifest hash prod: {(report.get('manifest_hash_prod') or '')[:16]}… [{mark}]"
        )
    else:
        lines.append("  manifest hash prod: (skipped, no MySQL)")
    compare = report.get("compare") or {}
    from rules_compare import format_human

    lines.append(format_human(compare))
    spot = report.get("spot_check")
    if spot:
        lines.append(format_spot_check_human(spot))
    return "\n".join(lines)
