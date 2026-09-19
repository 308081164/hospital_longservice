#!/usr/bin/env python3
"""从铂康内勤规则 Excel 生成 clerk-rules baseline（权威来源）。"""
from __future__ import annotations

import hashlib
import json
import re
import shutil
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAPPING = ROOT / "docs/clerk-rules-excel-mapping.json"
CLERK_DIR = ROOT / "backend/src/main/resources/clerk-rules"
BASELINE_DIR = CLERK_DIR / "baseline"
ATTACH_SRC = ROOT / "铂康/内勤要求/附件"
ATTACH_DST = CLERK_DIR / "attachments"
SOURCE_VERSION = "铂康内勤规则-20250911"

ATTACHMENT_MAP = {
    "DAOWAI-RM": "1哈尔滨市道外人民医院价格单.pdf",
    "NG-FUCHAN": "2哈尔滨市南岗区妇产医院价格单.pdf",
    "SANJING-SB": "3哈尔滨市三精肾脏病专科医院价格单.pdf",
    "TAIPING-RM": "4哈尔滨市道外区太平人民医院价格单_000214.pdf",
    "ZY3-DIANLI": [
        "5黑龙江中医药大学附属第三医院8月器械把数.xlsx",
        "6黑龙江中医药大学附属第三医院8月器械把数.xlsx",
    ],
}

SETTLEMENT_LAYOUT_SHEETS = {
    "GUOYAO-MAIN": "国药结款函",
    "GUOYAO-2": "国药结款函",
    "GUOYAO-3": "国药结款函",
    "HULAN-TCM": "呼兰中医院结款函",
    "RENSHENG": "仁胜医院结款函",
    "XIANGFANG-ZY": "香坊三辅结款函",
    "SANFU-SQ": "香坊三辅结款函",
    "ZUYAN-NG": "祖研分科室",
    "ZUYAN-XA": "祖研分科室",
    "ZUYAN-SF": "祖研分科室",
    "ZY3-DIANLI": "附三结款函",
}


def _rule(name: str, rule_type: str, stage: str, params: dict, source_text: str, source_ref: str, **extra):
    return {
        "ruleType": rule_type,
        "name": name,
        "stage": stage,
        "isActive": True,
        "params": params,
        "sourceText": source_text,
        "sourceRef": source_ref,
        **extra,
    }


def parse_rules_for_hospital(code: str, name: str, rules: list[dict], campus_hint: str | None) -> list[dict]:
    out: list[dict] = []
    prio = 10
    for r in rules:
        text = r["text"]
        ref = f"excel:{code}#{r['item']}"

        if ("七折" in text or "7折" in text) and ("账单" in text or "金额为" in text):
            out.append(_rule(
                "账单七折", "DISCOUNT_OVERLAY", "bill_export",
                {"rate": 0.7, "skipWhenFixedPrice": True},
                text, ref, priority=prio,
            ))
            prio += 10
            continue
        if "六折" in text or "6折" in text:
            out.append(_rule("账单六折", "DISCOUNT_OVERLAY", "bill_export", {"rate": 0.6}, text, ref, priority=prio))
            prio += 10
            continue
        if "8.91" in text or ("16.5" in text and "75折" in text):
            out.append(_rule(
                "太平阶梯折扣", "PIECE_TIER_DISCOUNT", "bill_export",
                {
                    "pieceTierDiscounts": [
                        {"minPieces": 1, "maxPieces": 1, "rate": 1.0},
                        {"minPieces": 2, "maxPieces": 2, "rate": 0.75},
                        {"minPieces": 3, "maxPieces": 3, "rate": 0.891, "originalUnitPriceEquals": 16.5},
                        {"minPieces": 4, "maxPieces": 999, "rate": 0.75},
                    ]
                },
                text, ref, priority=prio,
            ))
            if "价格单" in text or "参考附件" in text:
                att = ATTACHMENT_MAP.get(code)
                if att:
                    out.append(_rule(
                        "合同价表", "SEPARATE_PRICING_SYSTEM", "bill_export",
                        {"attachmentRef": f"attachments/{att}", "role": "price_list"},
                        text, ref, priority=prio + 1,
                    ))
            prio += 20
            continue
        if ("75折" in text or "七五折" in text) and "8.91" not in text:
            rate = 0.75
            stage = "bill_export" if "账单" in text else "settlement"
            out.append(_rule("七五折", "DISCOUNT_OVERLAY", stage, {"rate": rate, "skipWhenFixedPrice": True}, text, ref, priority=prio))
            prio += 10
            continue
        if "高温5折" in text and "低温7折" in text:
            out.append(_rule("结款高温五折", "SETTLEMENT_DISCOUNT", "settlement", {"rate": 0.5, "temperature": "HT"}, text, ref, priority=prio))
            out.append(_rule("结款低温七折", "SETTLEMENT_DISCOUNT", "settlement", {"rate": 0.7, "temperature": "LT"}, text, ref, priority=prio + 1))
            prio += 20
            continue
        if "灭菌费" in text and "8折" in text:
            out.append(_rule("结款灭菌八折", "SETTLEMENT_DISCOUNT", "settlement", {"rate": 0.8, "target": "sterilize"}, text, ref, priority=prio))
            prio += 10
            continue
        if "9折" in text or "九折" in text:
            out.append(_rule("结款九折", "SETTLEMENT_DISCOUNT", "settlement", {"rate": 0.9, "target": "sterilize"}, text, ref, priority=prio))
            prio += 10
            continue
        if "低消" in text:
            m = re.search(r"低消(\d+)", text)
            min_charge = float(m.group(1)) if m else 0
            out.append(_rule(
                f"低消{int(min_charge)}", "SETTLEMENT_MIN_CHARGE", "settlement",
                {"minCharge": min_charge, "baseComponents": ["sterilize", "logistics"]},
                text, ref, priority=prio,
            ))
            prio += 10
            continue
        if "物流卡" in text:
            out.append(_rule("物流卡抵扣", "LOGISTICS_CARD_DEDUCT", "settlement", {"deductFromCard": True}, text, ref, priority=prio))
            prio += 10
            continue
        if "不收物流" in text or "没有物流" in text or "结款函没有物流" in text:
            out.append(_rule("不收物流费", "LOGISTICS_WAIVE", "settlement", {"feePerTrip": 0}, text, ref, priority=prio))
            prio += 10
            continue
        if re.search(r"物流\d+", text) or "物流80.5" in text:
            fee = 80.5
            m = re.search(r"物流(\d+(?:\.\d+)?)", text)
            if m:
                fee = float(m.group(1))
            urgent = 98.0 if "加急98" in text else None
            urgent_rate = 0.3 if "加急30%" in text else None
            params = {"feePerTrip": fee}
            if urgent is not None:
                params["urgentFeePerTrip"] = urgent
            if urgent_rate is not None:
                params["urgentRate"] = urgent_rate
            out.append(_rule("物流费", "LOGISTICS_FEE", "settlement", params, text, ref, priority=prio))
            prio += 10
            continue
        if "减免四次物流" in text:
            out.append(_rule("每月减免四次物流", "LOGISTICS_WAIVE", "settlement", {"freeCountPerMonth": 4}, text, ref, priority=prio))
            prio += 10
            continue
        if "价格单" in text or "参考附件" in text:
            att = ATTACHMENT_MAP.get(code)
            att_ref = f"attachments/{att}" if isinstance(att, str) else None
            out.append(_rule(
                "合同价表", "SEPARATE_PRICING_SYSTEM", "bill_export",
                {"attachmentRef": att_ref, "role": "price_list"},
                text, ref, priority=prio,
            ))
            prio += 10
            continue
        if re.search(r"\d+元/把", text) or "两把器械" in text or "三把器械" in text:
            price = 3.0
            min_pieces = 2
            if "2.75" in text:
                price = 2.75
            if "三把" in text:
                min_pieces = 3
            out.append(_rule(
                f"{min_pieces}把以上{price}元", "PIECE_TIER_DISCOUNT", "bill_export",
                {"minPieces": min_pieces, "unitPrice": price, "temperature": "HT"},
                text, ref, priority=prio,
            ))
            prio += 10
            continue
        if "分科室汇总" in text or "账单不分科室" in text:
            out.append(_rule("分科室汇总", "DEPT_SPLIT", "both", {"mode": "by_department"}, text, ref, priority=prio))
            prio += 10
            continue
        if "一个结款函" in text or "共用一个结款函" in text:
            out.append(_rule("合并结款函", "MERGED_SETTLEMENT", "settlement", {"group": "shared"}, text, ref, priority=prio))
            prio += 10
            continue
        if "器械把数量" in text and code.startswith("GUOYAO"):
            out.append(_rule(
                "国药结款核算", "GUOYAO_SETTLEMENT_FORMULA", "settlement",
                {"instrumentPackDivisor": 5.5, "allocationDivisor": 15, "campus": campus_hint or "main"},
                text, ref, priority=prio,
            ))
            prio += 10
            continue
        if "金额为0" in text or "0元" in text:
            out.append(_rule(
                "零元行包材补价", "ZERO_ROW_PACKAGING_FEE", "bill_export",
                {"paperPlastic": 8, "nonwoven": 20, "eto": 35},
                text, ref, priority=prio,
            ))
            prio += 10
            continue
        if "器械数量列" in text:
            out.append(_rule("保留器械数量列", "EXPORT_LAYOUT", "bill_export", {"keepColumns": ["器械数"]}, text, ref, priority=prio))
            prio += 10
            continue
        if "原价和7折" in text or "7折后价格" in text:
            out.append(_rule("结款原价与七折并列", "EXPORT_LAYOUT", "settlement", {"showOriginalAndDiscount": True, "discountRate": 0.7}, text, ref, priority=prio))
            prio += 10
            continue
        if "把数表" in text or "包装表" in text:
            out.append(_rule("月度附表", "MONTHLY_SUPPLEMENT_REPORT", "both", {"reportTypes": ["instrument_count", "packaging_fee"]}, text, ref, priority=prio))
            prio += 10
            continue
        if "手术室（备包）" in text:
            out.append(_rule("备包单独结款", "SETTLEMENT_PACK_SPLIT", "settlement", {"deptKeyword": "手术室（备包）"}, text, ref, priority=prio))
            prio += 10
            continue
        if "美容针10件" in text:
            if campus_hint in (None, "ng") and "南岗" in text:
                out.append(_rule("南岗美容针10件5.5", "FIXED_PRICE_EXPORT", "bill_export", {"keywords": ["美容针"], "threshold": 10, "unitPrice": 5.5}, text, ref, priority=prio))
                prio += 10
            if campus_hint in (None, "sf") and "三辅" in text:
                out.append(_rule("三辅美容针10件5.5", "FIXED_PRICE_EXPORT", "bill_export", {"keywords": ["美容针"], "threshold": 10, "unitPrice": 5.5}, text, ref, priority=prio))
                prio += 10
            continue
        if "物流费" in text and "周一" in text:
            out.append(_rule("按周物流费", "LOGISTICS_FEE", "settlement", {"feePerTrip": 50, "billingWeekdays": [1, 3, 5], "splitAcrossCampuses": True}, text, ref, priority=prio))
            prio += 10
            continue
        if "8元/件" in text:
            out.append(_rule("合同外器械8元", "FIXED_PRICE_EXPORT", "bill_export", {"fallbackUnitPrice": 8.0}, text, ref, priority=prio))
            prio += 10
            continue
        if "合并当天物流50" in text:
            out.append(_rule("合并物流50", "LOGISTICS_FEE", "settlement", {"feePerTrip": 50, "mergeSameDay": True}, text, ref, priority=prio))
            prio += 10
            continue
        if "和市五院" in text:
            out.append(_rule("与市五合并结款", "MERGED_SETTLEMENT", "settlement", {"mergeWith": "HRB-WY"}, text, ref, priority=prio))
            prio += 10
            continue

        out.append(_rule("待人工结构化", "ATTACHMENT_REF", "both", {"note": text}, text, ref, priority=prio, isActive=False))
        prio += 10
    return out


def copy_attachments():
    ATTACH_DST.mkdir(parents=True, exist_ok=True)
    if not ATTACH_SRC.exists():
        return
    for f in ATTACH_SRC.iterdir():
        if f.is_file() and not f.name.startswith("."):
            shutil.copy2(f, ATTACH_DST / f.name)


def compute_hash(files: list[Path]) -> str:
    h = hashlib.sha256()
    for p in sorted(files):
        h.update(p.name.encode())
        h.update(p.read_bytes())
    return h.hexdigest()


def main():
    data = json.loads(MAPPING.read_text(encoding="utf-8"))
    copy_attachments()

    if BASELINE_DIR.exists():
        for old in BASELINE_DIR.glob("*.json"):
            old.unlink()

    customers = []
    for h in data["hospitals"]:
        code = h["code"]
        rules = parse_rules_for_hospital(code, h["name"], h["rules"], h.get("campusHint"))
        attachment_refs = []
        if code in ATTACHMENT_MAP:
            att = ATTACHMENT_MAP[code]
            if isinstance(att, list):
                attachment_refs = [f"attachments/{a}" for a in att]
            else:
                attachment_refs = [f"attachments/{att}"]
        if code in SETTLEMENT_LAYOUT_SHEETS:
            attachment_refs.append(f"settlement-layout/{SETTLEMENT_LAYOUT_SHEETS[code]}.png")

        baseline = {
            "customerCode": code,
            "customerName": h["name"],
            "sourceVersion": SOURCE_VERSION,
            "notes": f"铂康内勤规则 Excel 序号 {h['excelSeq']}",
            "attachmentRefs": attachment_refs,
            "rules": rules,
        }
        path = BASELINE_DIR / f"{code}.json"
        path.write_text(json.dumps(baseline, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        customers.append({"code": code, "file": f"baseline/{code}.json", "ruleCount": len(rules)})

    files = list(BASELINE_DIR.glob("*.json"))
    index = {
        "baseline_hash": compute_hash(files),
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "source": str(MAPPING),
        "customer_count": len(customers),
        "customers": sorted(customers, key=lambda x: x["code"]),
    }
    (CLERK_DIR / "index.json").write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(customers)} baselines, hash={index['baseline_hash'][:16]}…")


if __name__ == "__main__":
    main()
