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
TEST_MANIFEST = ROOT / "测试用例/billing_rules_manifest.json"
SKIP_FILES = {"billing-rules-manifest.json"}


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


def build_manifest() -> dict[str, Any]:
    customers: dict[str, dict[str, Any]] = {}
    deactivated: set[tuple[str, str]] = set()

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
                if profile_rules or not entry.get("_mode_from_customer_update"):
                    entry["billingPricingMode"] = profile["billingPricingMode"]
            if "standardPricingOverride" in profile:
                entry["standardPricingOverride"] = profile["standardPricingOverride"]
            rules: dict[str, dict[str, Any]] = entry.setdefault("productRules", {})
            _merge_profile_rules(rules, profile_rules, code=code, deactivated=deactivated)

        for patch in data.get("customerUpdates") or []:
            _apply_customer_update(customers, patch)

        for patch in data.get("ruleUpdates") or []:
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

    manifest_customers: dict[str, Any] = {}
    for code in sorted(customers):
        entry = customers[code]
        rules_map: dict[str, dict[str, Any]] = entry.get("productRules") or {}
        rules_list = [rules_map[name] for name in sorted(rules_map)]
        active_count = sum(1 for r in rules_list if r.get("isActive", True))
        manifest_customers[code] = {
            "code": code,
            "name": entry.get("name", code),
            "status": entry.get("status"),
            "billingPricingMode": entry.get("billingPricingMode"),
            "standardPricingOverride": entry.get("standardPricingOverride"),
            "billingEnabled": entry.get("billingEnabled"),
            "productRules": rules_list,
            "rule_count": len(rules_list),
            "active_rule_count": active_count,
        }

    canonical = json.dumps(manifest_customers, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    manifest_hash = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return {
        "version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "manifest_hash": manifest_hash,
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
            f"mode={entry.get('billingPricingMode')}"
        )
        return 0

    if args.write:
        payload = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
        BACKEND_MANIFEST.write_text(payload, encoding="utf-8")
        TEST_MANIFEST.write_text(payload, encoding="utf-8")
        print(f"wrote {BACKEND_MANIFEST}")
        print(f"wrote {TEST_MANIFEST}")
        print(f"customers={len(manifest['customers'])} hash={manifest['manifest_hash'][:16]}...")
        return 0

    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
