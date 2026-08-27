#!/usr/bin/env python3
"""Merge billing-seeds/*.json into per-customer expected productRules manifest."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
SEED_DIR = ROOT / "backend/src/main/resources/billing-seeds"
BACKEND_MANIFEST = SEED_DIR / "billing-rules-manifest.json"
TEST_MANIFEST = ROOT / "backend/src/test/resources/billing-rules-manifest.json"
LEGACY_TEST_MANIFEST = ROOT / "测试用例/billing_rules_manifest.json"
SKIP_FILES = {"billing-rules-manifest.json"}

# ExtraCustomerDeactivationRunner.INACTIVE_EXTRA_CODES
# 注意：须与 Java 侧 ExtraCustomerDeactivationRunner 保持一致。
# NEAU-YY / HRB-SD-MB / HL-ZGH / HRB-HTFH 是活跃计费客户，不得停用。
INACTIVE_EXTRA_CODES = [
    "HRB-XK",
    "HRB-AM",
    "HRB-ASM",
    "HRB-BY",
    "HRB-CY",
    "HRB-BNXS",
    "HRB-CJ",
    "WCSRMYY",
    "YMYXZX",
    "HY-HYY",
    "ZYY-DSFY",
    "HLFB-SF",
    "HRB-DLFB",
    "HRB-MHM",
    "ZXYSJT",
]

# 最终仅保留的 22 家特殊计价客户（严格测试口径）。
# 须与 BillingSeedMigrationRunner.STRICT_KEEP_CODES 保持一致。
# 生成 manifest 时删除非 22 家客户，确保清单与部署后的 DB 一致。
STRICT_KEEP_CODES = [
    "BINGCHENG-YM",
    "GUOYAO-2",
    "FNN-YY",
    "NEAU-YY",
    "HRB-WY",
    "HRB-SD-MB",
    "HRB-HTFH",
    "HRB-WY-EM",
    "JIUZHOU-FK",
    "BOSHANG-YY",
    "HAIYUAN-SB",
    "HLJ-FY-RK",
    "ZUYAN-NG",
    "SHKF-YY",
    "DL-FUCHAN",
    "CHUNYU-YL",
    "HL-ZGH",
    "JZSW-BIO",
    "SUOFEI-YL",
    "HLJ-JYGLJ-YY",
    "HULAN-TCM",
    "PFQ-RM",
]

# 种子文件仅以 code 引用客户、未携带规范名时，回退到此映射。
# 否则 manifest 的 name 会退化为 code，导致编译后的规则 hospitals 字段无法匹配
# 账单中的医院全称（SpecialCharge11CoverageTest 等 fixture 因此失效）。
# 来源：HardcodedRulesMigrationRunner.seedMissingCustomers + customer 表 canonical_name。
CUSTOMER_CANONICAL_NAMES = {
    "ERYY-SB": "黑龙江省第二医院（松北区）",
    "HRB-HTFH": "哈尔滨航天风华医院",
    "HRB-MHM": "哈尔滨美涵美医疗美容有限公司",
    "HRB-SD-MB": "哈尔滨道外区松电慢性病专科门诊部",
    "NEAU-YY": "东北农业大学医院",
    "WCSRMYY": "五常市人民医院",
}

# HardcodedRulesMigrationRunner.seedEngineProductRules 直接 seed 的规则（非 seed 文件）。
# manifest 生成器须显式补录，否则 compare 会把它们误判为 prod 的 extra。
# 字段命名与 manifest productRules 一致：FIXED_PRICE/PRICE_PER_INSTRUMENT 用 price，
# FOLD 用 foldRatio+threshold，EXTRA_FEE 用 fee。
HARDCODED_RULES: dict[str, list[dict[str, Any]]] = {
    "ERYY-NG": [
        {"ruleType": "FIXED_PRICE", "name": "黑龙江省第二医院（南岗区）3.6空心钉工具包固定单价", "priority": 10, "price": 205.45, "keywords": ["3.6空心钉工具包"], "skipPackaging": True, "skipDiscount": True},
        {"ruleType": "FIXED_PRICE", "name": "黑龙江省第二医院（南岗区）3.6空心钉固定单价", "priority": 20, "price": 13.3, "keywords": ["3.6空心钉"], "skipPackaging": True, "skipDiscount": True},
        {"ruleType": "FIXED_PRICE", "name": "黑龙江省第二医院（南岗区）7.3空心钉固定单价", "priority": 30, "price": 13.3, "keywords": ["7.3空心钉"], "skipPackaging": True, "skipDiscount": True},
        {"ruleType": "FIXED_PRICE", "name": "黑龙江省第二医院（南岗区）手术衣无纺布固定单价", "priority": 40, "price": 26.6, "keywords": ["手术衣"], "excludeKeywords": ["无纺布"], "skipPackaging": True, "skipDiscount": True},
        {"ruleType": "FIXED_PRICE", "name": "黑龙江省第二医院（南岗区）手术衣纸塑袋固定单价", "priority": 50, "price": 28.0, "keywords": ["手术衣"], "excludeKeywords": ["纸塑袋"], "skipPackaging": True, "skipDiscount": True},
        {"ruleType": "FIXED_PRICE", "name": "黑龙江省第二医院（南岗区）钉固定单价", "priority": 60, "price": 140.0, "keywords": ["钉"], "skipPackaging": True, "skipDiscount": True},
        {"ruleType": "FIXED_PRICE", "name": "黑龙江省第二医院（南岗区）软镜固定单价", "priority": 70, "price": 210.0, "keywords": ["软镜"], "skipPackaging": True, "skipDiscount": True},
        {"ruleType": "FIXED_PRICE", "name": "黑龙江省第二医院（南岗区）泌尿显微镜头固定单价", "priority": 80, "price": 210.0, "keywords": ["泌尿显微镜头"], "skipPackaging": True, "skipDiscount": True},
        {"ruleType": "FIXED_PRICE", "name": "黑龙江省第二医院（南岗区）小腔包固定单价", "priority": 90, "price": 49.7, "keywords": ["小腔包"], "skipPackaging": True, "skipDiscount": True},
    ],
    "NEAU-YY": [
        {"ruleType": "PRICE_PER_INSTRUMENT", "name": "东北农业大学医院洁牙机尖每件 5.5 元", "priority": 10, "price": 5.5, "keywords": ["洁牙机尖"], "skipPackaging": True, "skipDiscount": False},
    ],
    "HRB-SD-MB": [
        {"ruleType": "FOLD", "name": "松电机扩针 5 件算 1 件", "priority": 10, "keywords": ["机扩针"], "threshold": 5, "foldRatio": 5},
    ],
    "HL-ZGH": [
        {"ruleType": "EXTRA_FEE", "name": "镜头租借公司筐加收", "priority": 10, "fee": 8.0, "keywords": ["镜头", "检查镜"]},
    ],
}


def _text(node: dict[str, Any], key: str, default: str | None = None) -> str | None:
    val = node.get(key, default)
    if val is None:
        return default
    return str(val)


def _rule_name(rule: dict[str, Any]) -> str | None:
    name = rule.get("name")
    if name is None:
        return None
    text = str(name).strip()
    return text or None


def _normalize_rule(rule: dict[str, Any]) -> dict[str, Any]:
    out = copy.deepcopy(rule)
    out.setdefault("ruleType", "FIXED_PRICE")
    out.setdefault("priority", 100)
    out.setdefault("isActive", True)
    out.setdefault("matchMode", "first")
    out.setdefault("skipPackaging", False)
    out.setdefault("skipDiscount", False)
    if "keywords" in out and out["keywords"] is None:
        out["keywords"] = []
    return out


def _merge_profile_rules(
    existing: dict[str, dict[str, Any]],
    incoming: list[dict[str, Any]],
    *,
    code: str,
    deactivated: set[tuple[str, str]],
) -> None:
    for raw in incoming or []:
        rule = _normalize_rule(raw)
        name = _rule_name(rule)
        if not name:
            continue
        rule["name"] = name
        if (code, name) in deactivated:
            rule["isActive"] = False
        existing[name] = rule


def _apply_rule_update(rules: dict[str, dict[str, Any]], patch: dict[str, Any]) -> None:
    rule_name = _text(patch, "ruleName")
    if not rule_name:
        return
    rule = rules.get(rule_name)
    if rule is None:
        return
    if "setPrice" in patch:
        rule["price"] = patch["setPrice"]
    if "setFoldRatio" in patch:
        rule["foldRatio"] = patch["setFoldRatio"]
    if "setThreshold" in patch:
        rule["threshold"] = patch["setThreshold"]
    if "setRuleType" in patch:
        rule["ruleType"] = patch["setRuleType"]
    if "setPriority" in patch:
        rule["priority"] = patch["setPriority"]
    if "setMatchMode" in patch:
        rule["matchMode"] = patch["setMatchMode"]
    if "setBillingMode" in patch:
        rule["billingMode"] = patch["setBillingMode"]
    if "setPieceCountSource" in patch:
        rule["pieceCountSource"] = patch["setPieceCountSource"]
    if "setSkipDiscount" in patch:
        rule["skipDiscount"] = bool(patch["setSkipDiscount"])
    if "setSkipPackaging" in patch:
        rule["skipPackaging"] = bool(patch["setSkipPackaging"])
    if "setKeywords" in patch:
        rule["keywords"] = list(patch["setKeywords"] or [])
    if "addKeywords" in patch:
        keywords = list(rule.get("keywords") or [])
        for kw in patch["addKeywords"] or []:
            if kw and kw not in keywords:
                keywords.append(kw)
        rule["keywords"] = keywords
    if "removeKeywords" in patch:
        remove = set(patch["removeKeywords"] or [])
        rule["keywords"] = [kw for kw in (rule.get("keywords") or []) if kw not in remove]
    if "setExcludeKeywords" in patch:
        rule["excludeKeywords"] = list(patch["setExcludeKeywords"] or [])
    if "addExcludeKeywords" in patch:
        exclude = list(rule.get("excludeKeywords") or [])
        for ex in patch["addExcludeKeywords"] or []:
            if ex and ex not in exclude:
                exclude.append(ex)
        rule["excludeKeywords"] = exclude
    if "setMinInstrumentCount" in patch:
        rule["minInstrumentCount"] = patch["setMinInstrumentCount"]
    if "setMaxInstrumentCount" in patch:
        rule["maxInstrumentCount"] = patch["setMaxInstrumentCount"]
    if "setIsActive" in patch:
        rule["isActive"] = bool(patch["setIsActive"])
    if "setName" in patch:
        new_name = _text(patch, "setName")
        if new_name:
            del rules[rule_name]
            rule["name"] = new_name
            rules[new_name] = rule


def _apply_customer_update(customers: dict[str, dict[str, Any]], patch: dict[str, Any]) -> None:
    code = _text(patch, "code")
    if not code:
        return
    entry = customers.setdefault(code, {"code": code, "productRules": {}})
    entry["code"] = code
    if patch.get("name"):
        entry["name"] = patch["name"]
    if patch.get("billingPricingMode"):
        entry["billingPricingMode"] = patch["billingPricingMode"]
        entry["_mode_from_customer_update"] = True
    if "standardPricingOverride" in patch:
        entry["standardPricingOverride"] = patch["standardPricingOverride"]
    if patch.get("billingEnabled") is not None:
        entry["billingEnabled"] = bool(patch["billingEnabled"])
    if patch.get("setCanonicalName"):
        entry["name"] = patch["setCanonicalName"]
    if patch.get("setStatus"):
        entry["status"] = patch["setStatus"]


def _apply_enable_billing(
    customers: dict[str, dict[str, Any]],
    enable_codes: list[str],
    *,
    disable_all_others: bool,
) -> None:
    enable_set = {code.strip().upper() for code in enable_codes if code}
    for code in enable_set:
        entry = customers.setdefault(code, {"code": code, "productRules": {}})
        entry["billingEnabled"] = True
    if disable_all_others:
        for code, entry in customers.items():
            if code not in enable_set:
                entry["billingEnabled"] = False


def build_manifest() -> dict[str, Any]:
    customers: dict[str, dict[str, Any]] = {}
    deactivated: set[tuple[str, str]] = set()
    pending_price_updates: list[dict[str, Any]] = []

    for path in sorted(SEED_DIR.glob("*.json")):
        if path.name in SKIP_FILES:
            continue
        data = json.loads(path.read_text(encoding="utf-8"))

        for profile in data.get("profiles") or []:
            code = _text(profile, "code")
            if not code:
                continue
            entry = customers.setdefault(code, {"code": code, "productRules": {}})
            if profile.get("name"):
                entry["name"] = profile["name"]
            profile_rules = profile.get("productRules") or []
            if profile.get("billingPricingMode"):
                if not entry.get("_mode_from_customer_update"):
                    entry["billingPricingMode"] = profile["billingPricingMode"]
            if "standardPricingOverride" in profile:
                entry["standardPricingOverride"] = profile["standardPricingOverride"]
            if profile.get("billingEnabled") is not None:
                entry["billingEnabled"] = bool(profile["billingEnabled"])
            rules: dict[str, dict[str, Any]] = entry.setdefault("productRules", {})
            _merge_profile_rules(rules, profile_rules, code=code, deactivated=deactivated)

        for patch in data.get("customerUpdates") or []:
            _apply_customer_update(customers, patch)

        enable_billing = data.get("enableBilling")
        if enable_billing:
            codes = [str(c) for c in enable_billing]
            _apply_enable_billing(
                customers,
                codes,
                disable_all_others=bool(data.get("disableAllOthers", False)),
            )

        for patch in data.get("ruleUpdates") or []:
            keys = set(patch)
            # 纯数值字段更新（setPrice/setFoldRatio/setThreshold）须在所有 newRules 之后应用，
            # 否则当该文件字母序早于创建规则的 newRules 文件时会被丢弃
            # （如 phase-fold-unitprice-customers-20260820.json 的 setPrice 5.5）。
            # 含 setName/setIsActive/关键词的补丁必须内联，与 Java 保持一致的身份/启用态顺序。
            if ("setPrice" in keys or "setFoldRatio" in keys or "setThreshold" in keys) and not (
                keys & {"setName", "setIsActive", "setKeywords", "addKeywords", "removeKeywords", "setExcludeKeywords", "addExcludeKeywords"}
            ):
                pending_price_updates.append(patch)
                continue
            code = _text(patch, "code")
            if not code or code not in customers:
                continue
            rules = customers[code].setdefault("productRules", {})
            _apply_rule_update(rules, patch)

        for raw in data.get("newRules") or []:
            code = _text(raw, "code")
            if not code:
                continue
            entry = customers.setdefault(code, {"code": code, "productRules": {}})
            rule = _normalize_rule(raw)
            rule.pop("code", None)
            name = _rule_name(rule)
            if not name:
                continue
            rule["name"] = name
            if (code, name) in deactivated:
                rule["isActive"] = False
            entry.setdefault("productRules", {})[name] = rule

        for deact in data.get("deactivateRules") or []:
            code = _text(deact, "code")
            rule_name = _text(deact, "ruleName")
            if not code or not rule_name:
                continue
            deactivated.add((code, rule_name))
            entry = customers.setdefault(code, {"code": code, "productRules": {}})
            rules = entry.setdefault("productRules", {})
            if rule_name in rules:
                rules[rule_name]["isActive"] = False

        for act in data.get("activateRules") or []:
            code = _text(act, "code")
            rule_name = _text(act, "ruleName")
            if not code or not rule_name:
                continue
            deactivated.discard((code, rule_name))
            entry = customers.setdefault(code, {"code": code, "productRules": {}})
            rules = entry.setdefault("productRules", {})
            if rule_name in rules:
                rules[rule_name]["isActive"] = True

    # 二遍处理：仅纯数值字段更新（setPrice/setFoldRatio/setThreshold）。
    # 这些更新可能先于 newRules 执行（字母序靠前），须在所有规则创建后统一应用。
    for patch in pending_price_updates:
        code = _text(patch, "code")
        if not code or code not in customers:
            continue
        rules = customers[code].setdefault("productRules", {})
        _apply_rule_update(rules, patch)

    for code in INACTIVE_EXTRA_CODES:
        if code in customers:
            customers[code]["status"] = "inactive"

    # 补录 Java HardcodedRulesMigrationRunner 硬编码规则（非 seed 文件）。
    # 仅当同名规则不存在时插入（对应 Java ensureRule 的 countByCustomerIdAndName 跳过逻辑）。
    for code, hardcoded in HARDCODED_RULES.items():
        if code not in customers:
            continue
        rules = customers[code].setdefault("productRules", {})
        for raw in hardcoded:
            rule = _normalize_rule(raw)
            name = _rule_name(rule)
            if not name or name in rules:
                continue
            rule["name"] = name
            rule["isActive"] = True
            rules[name] = rule

    # 最终仅保留 22 家特殊计价客户：删除非严格测试口径的客户。
    customers = {code: entry for code, entry in customers.items() if code in STRICT_KEEP_CODES}

    manifest_customers: dict[str, Any] = {}
    for code in sorted(customers):
        entry = customers[code]
        rules_map: dict[str, dict[str, Any]] = entry.get("productRules") or {}
        rules_list = [rules_map[name] for name in sorted(rules_map)]
        active_count = sum(1 for r in rules_list if r.get("isActive", True))
        billing_enabled = bool(entry.get("billingEnabled", False))
        status = entry.get("status")
        manifest_customers[code] = {
            "code": code,
            "name": entry.get("name") or CUSTOMER_CANONICAL_NAMES.get(code, code),
            "status": status,
            "billingPricingMode": entry.get("billingPricingMode"),
            "standardPricingOverride": entry.get("standardPricingOverride"),
            "billingEnabled": billing_enabled,
            "productRules": rules_list,
            "rule_count": len(rules_list),
            "active_rule_count": active_count,
        }

    enabled_count = sum(1 for c in manifest_customers.values() if c.get("billingEnabled"))
    active_enabled_count = sum(
        1
        for c in manifest_customers.values()
        if c.get("billingEnabled") and c.get("status") != "inactive"
    )
    canonical = json.dumps(manifest_customers, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    manifest_hash = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return {
        "version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "manifest_hash": manifest_hash,
        "billing_enabled_count": enabled_count,
        "active_billing_enabled_count": active_enabled_count,
        "customers": manifest_customers,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Build billing rules manifest from billing-seeds")
    parser.add_argument("--write", action="store_true", help="Write manifest JSON files")
    parser.add_argument("--code", help="Print summary for one customer code")
    args = parser.parse_args()

    manifest = build_manifest()
    if args.code:
        code = args.code.strip().upper()
        entry = manifest["customers"].get(code)
        if not entry:
            print(f"{code}: not in manifest")
            return 1
        print(
            f"{code}: rules={entry['rule_count']} active={entry['active_rule_count']} "
            f"mode={entry.get('billingPricingMode')} billingEnabled={entry.get('billingEnabled')}"
        )
        return 0

    if args.write:
        payload = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
        BACKEND_MANIFEST.write_text(payload, encoding="utf-8")
        TEST_MANIFEST.write_text(payload, encoding="utf-8")
        LEGACY_TEST_MANIFEST.write_text(payload, encoding="utf-8")
        print(f"wrote {BACKEND_MANIFEST}")
        print(f"wrote {TEST_MANIFEST}")
        print(f"wrote {LEGACY_TEST_MANIFEST}")
        print(
            f"customers={len(manifest['customers'])} "
            f"billing_enabled={manifest['billing_enabled_count']} "
            f"active_billing_enabled={manifest['active_billing_enabled_count']} "
            f"hash={manifest['manifest_hash'][:16]}..."
        )
        return 0

    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
