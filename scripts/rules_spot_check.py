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

FNN_YY_HOSPITAL = "方南南医院"
MEIYI_YL_HOSPITAL = "美意医疗"
YILI_YL_HOSPITAL = "易丽医疗"
JIAYI_YL_HOSPITAL = "佳医医疗"

FNN_YY_SPOT_CHECKS: list[dict[str, Any]] = [
    {
        "name": "方南南机扩针8件含包材",
        "department": "口腔科",
        "packName": "机扩针-8/Z7520",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋250*200",
        "instrumentCount": 8,
        "packCount": 1,
        "unitPrice": 22,
        "totalPrice": 22,
        "expectedUnitPrice": 16.5,
        "priceTol": 0.02,
    },
    {
        "name": "方南南机扩针20件免包材",
        "department": "口腔科",
        "packName": "机扩针-20/Z7520",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋250*200",
        "instrumentCount": 20,
        "packCount": 1,
        "unitPrice": 22,
        "totalPrice": 22,
        "expectedUnitPrice": 22.0,
        "priceTol": 0.02,
    },
]

MEIYI_YL_SPOT_CHECKS: list[dict[str, Any]] = [
    {
        "name": "美意洞巾纸塑25cm4元",
        "department": "手术室",
        "packName": "洞巾",
        "type": "敷料包(纸塑袋)",
        "packageMaterial": "高温纸塑袋250*200",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 4,
        "totalPrice": 4,
        "expectedUnitPrice": 4.0,
        "priceTol": 0.02,
    },
]

YILI_YL_SPOT_CHECKS: list[dict[str, Any]] = [
    {
        "name": "易丽孔巾纸塑25cm4元",
        "department": "手术室",
        "packName": "孔巾",
        "type": "敷料包(纸塑袋)",
        "packageMaterial": "高温纸塑袋250*200",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 4,
        "totalPrice": 4,
        "expectedUnitPrice": 4.0,
        "priceTol": 0.02,
    },
]

JIAYI_YL_SPOT_CHECKS: list[dict[str, Any]] = [
    {
        "name": "佳医眼包敷料纸塑25cm4元",
        "department": "手术室",
        "packName": "眼包敷料",
        "type": "敷料包(纸塑袋)",
        "packageMaterial": "高温纸塑袋250*200",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 4,
        "totalPrice": 4,
        "expectedUnitPrice": 4.0,
        "priceTol": 0.02,
    },
]

GUOYAO_2_HOSPITAL = "国药总医院第二院区"
BINGCHENG_YM_HOSPITAL = "哈尔滨冰城医疗美容医院"

BINGCHENG_YM_SPOT_CHECKS: list[dict[str, Any]] = [
    {
        "name": "冰城环钻包27.5",
        "department": "手术室",
        "packName": "环钻包",
        "type": "器械包(ZSD)",
        "packageMaterial": "高温灭菌无纺布60*60",
        "instrumentCount": 2,
        "packCount": 1,
        "unitPrice": 30.5,
        "totalPrice": 61.0,
        "expectedUnitPrice": 27.5,
        "priceTol": 0.02,
    },
]

GUOYAO_2_SPOT_CHECKS: list[dict[str, Any]] = [
    {
        "name": "电机厂缝合针8元",
        "department": "手术室",
        "packName": "缝合针-6/Z7520",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋75*200",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 8,
        "totalPrice": 8,
        "expectedUnitPrice": 8.0,
    },
    {
        "name": "电机厂指针10件5合1",
        "department": "手术室",
        "packName": "指针-10/z7537",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋75*370",
        "instrumentCount": 10,
        "packCount": 1,
        "unitPrice": 13.5,
        "totalPrice": 13.5,
        "expectedUnitPrice": 13.5,
        "priceTol": 0.02,
    },
    {
        "name": "电机厂指针12件免袋",
        "department": "手术室",
        "packName": "指针-12/z7537",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋75*370",
        "instrumentCount": 12,
        "packCount": 1,
        "unitPrice": 16.5,
        "totalPrice": 16.5,
        "expectedUnitPrice": 16.5,
        "priceTol": 0.02,
    },
    {
        "name": "电机厂双1件纸塑袋",
        "department": "手术室",
        "packName": "双-1/z2060",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋75*200",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 8.0,
        "totalPrice": 8.0,
        "expectedUnitPrice": 8.0,
        "priceTol": 0.02,
    },
    {
        "name": "电机厂双3件内层袋",
        "department": "手术室",
        "packName": "双-3/z2060",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋75*200",
        "instrumentCount": 3,
        "packCount": 1,
        "unitPrice": 19.0,
        "totalPrice": 19.0,
        "expectedUnitPrice": 20.5,
        "priceTol": 0.5,
    },
]

HRB_WY_EM_SPOT_CHECKS: list[dict[str, Any]] = [
    {
        "name": "市五二门诊驱血带W50",
        "department": "手术室",
        "packName": "驱血带/W5050",
        "type": "敷料包(无纺布包)",
        "packageMaterial": "无纺布W50*50",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 25.0,
        "totalPrice": 25.0,
        "expectedUnitPrice": 25.0,
    },
    {
        "name": "市五二门诊驱血带W90",
        "department": "手术室",
        "packName": "驱血带/W9090",
        "type": "敷料包(无纺布包)",
        "packageMaterial": "无纺布W90*90",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 30.0,
        "totalPrice": 30.0,
        "expectedUnitPrice": 30.0,
    },
]

HRB_HEU_SPOT_CHECKS: list[dict[str, Any]] = [
    {
        "name": "孔巾20cm4元",
        "department": "五官科",
        "packName": "孔巾/Z2032",
        "type": "敷料包(纸塑袋)",
        "packageMaterial": "高温纸塑袋200*320",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 4.0,
        "totalPrice": 4.0,
        "expectedUnitPrice": 4.0,
        "priceTol": 0.02,
    },
]

NEAU_YY_SPOT_CHECKS: list[dict[str, Any]] = [
    {
        "name": "农大根管锉8件含包材",
        "department": "口腔科",
        "packName": "根管锉",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋75*200",
        "instrumentCount": 8,
        "packCount": 1,
        "unitPrice": 16.5,
        "totalPrice": 16.5,
        "expectedUnitPrice": 16.5,
        "priceTol": 0.5,
    },
]

HRB_SD_MB_SPOT_CHECKS: list[dict[str, Any]] = [
    {
        "name": "松电机扩针8件含包材",
        "department": "口腔科",
        "packName": "机扩针",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋75*200",
        "instrumentCount": 8,
        "packCount": 1,
        "unitPrice": 16.5,
        "totalPrice": 16.5,
        "expectedUnitPrice": 16.5,
        "priceTol": 0.5,
    },
]

HRB_HTFH_SPOT_CHECKS: list[dict[str, Any]] = [
    {
        "name": "航天镍钛锉8件含包材",
        "department": "口腔科",
        "packName": "镍钛锉",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋75*200",
        "instrumentCount": 8,
        "packCount": 1,
        "unitPrice": 16.5,
        "totalPrice": 16.5,
        "expectedUnitPrice": 16.5,
        "priceTol": 0.5,
    },
]

STANDARD_COTTON_SPOT_CHECKS: list[dict[str, Any]] = [
    {
        "name": "棉球15cm纸塑袋2.5",
        "department": "生殖手术室",
        "packName": "棉球/Z1526",
        "type": "敷料包(纸塑袋)",
        "packageMaterial": "高温纸塑袋 150*260",
        "instrumentCount": 0,
        "packCount": 1,
        "unitPrice": 2.5,
        "totalPrice": 2.5,
        "expectedUnitPrice": 2.5,
        "expectedStatus": "unchanged",
    },
    {
        "name": "棉球缸25cm件费加袋费16",
        "department": "生殖手术室",
        "packName": "棉球缸-1/z2530",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋 250*300",
        "instrumentCount": 1,
        "packCount": 1,
        "unitPrice": 16.0,
        "totalPrice": 16.0,
        "expectedUnitPrice": 16.0,
        "expectedStatus": "unchanged",
    },
    {
        "name": "高温纸塑3件免袋费16.5",
        "department": "手术室",
        "packName": "普通器械-3/Z7520",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋 150*260",
        "instrumentCount": 3,
        "packCount": 1,
        "unitPrice": 16.5,
        "totalPrice": 16.5,
        "expectedUnitPrice": 16.5,
        "expectedStatus": "unchanged",
    },
    {
        "name": "高温纸塑4件仅件费22",
        "department": "手术室",
        "packName": "普通器械-4/Z7520",
        "type": "额外包(纸塑袋)",
        "packageMaterial": "高温纸塑袋 150*260",
        "instrumentCount": 4,
        "packCount": 1,
        "unitPrice": 22.0,
        "totalPrice": 22.0,
        "expectedUnitPrice": 22.0,
        "expectedStatus": "unchanged",
    },
]

SPOT_CHECK_PRESETS: dict[str, list[dict[str, Any]]] = {
    "HRB-2ND": HRB_2ND_SPOT_CHECKS,
    "ZYY-D1": ZYY_D1_SPOT_CHECKS,
    "FNN-YY": FNN_YY_SPOT_CHECKS,
    "MEIYI-YL": MEIYI_YL_SPOT_CHECKS,
    "YILI-YL": YILI_YL_SPOT_CHECKS,
    "JIAYI-YL": JIAYI_YL_SPOT_CHECKS,
    "GUOYAO-2": GUOYAO_2_SPOT_CHECKS,
    "BINGCHENG-YM": BINGCHENG_YM_SPOT_CHECKS,
    "HRB-WY-EM": HRB_WY_EM_SPOT_CHECKS,
    "HRB-HEU": HRB_HEU_SPOT_CHECKS,
    "NEAU-YY": NEAU_YY_SPOT_CHECKS,
    "HRB-SD-MB": HRB_SD_MB_SPOT_CHECKS,
    "HRB-HTFH": HRB_HTFH_SPOT_CHECKS,
    "STANDARD": STANDARD_COTTON_SPOT_CHECKS,
}

# spot-check code → 实际客户 code（用于全局标准规则验证）
SPOT_CHECK_CUSTOMER_ALIAS: dict[str, str] = {
    # HLFB-SF：billing_enabled、无 pathOverride、仅「车针」特色规则，不干扰标准价用例
    "STANDARD": "HLFB-SF",
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
    lookup_code = SPOT_CHECK_CUSTOMER_ALIAS.get(code, code)
    customer = client.customer_by_code(lookup_code)
    if customer is None:
        return {
            "command": "rules spot-check",
            "code": code,
            "ok": False,
            "error": f"customer not found: {lookup_code}",
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
    if code == "FNN-YY":
        hospital = FNN_YY_HOSPITAL
    if code == "MEIYI-YL":
        hospital = MEIYI_YL_HOSPITAL
    if code == "YILI-YL":
        hospital = YILI_YL_HOSPITAL
    if code == "JIAYI-YL":
        hospital = JIAYI_YL_HOSPITAL
    if code == "GUOYAO-2":
        hospital = GUOYAO_2_HOSPITAL
    if code == "BINGCHENG-YM":
        hospital = BINGCHENG_YM_HOSPITAL

    simulate_rule_id = rule_id
    if code == "STANDARD":
        simulate_rule_id = 1
        hospital = hospital_name or customer.get("name") or customer.get("canonicalName") or code

    results: list[dict[str, Any]] = []
    for case in preset:
        skip = {"name", "expectedUnitPrice", "expectedCorrectedTotal", "priceTol", "expectedStatus"}
        sample = {k: v for k, v in case.items() if k not in skip}
        sample.setdefault("sheetName", sample.get("department"))
        tol = float(case.get("priceTol", 0.02))
        try:
            sim = client.simulate_billing(
                customer_id=customer_id,
                hospital_name=hospital,
                sample_row=sample,
                rule_id=simulate_rule_id,
            )
            actual = _row_field(sim, "expectedUnitPrice", "expected_unit_price")
            expected = float(case["expectedUnitPrice"])
            ok = _price_close(actual, expected, tol=tol)
            actual_total = _row_field(sim, "correctedTotalPrice", "corrected_total_price")
            expected_total = case.get("expectedCorrectedTotal")
            if expected_total is not None:
                total_ok = _price_close(actual_total, float(expected_total), tol=tol)
                ok = ok and total_ok
            expected_status = case.get("expectedStatus")
            if expected_status is not None:
                ok = ok and str(_row_field(sim, "status") or "") == str(expected_status)
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
