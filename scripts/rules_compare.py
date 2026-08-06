#!/usr/bin/env python3
"""Compare local billing-rules manifest vs prod/local API productRules."""

from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path
from typing import Any

from lib.api_client import ApiClient

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "backend/src/main/resources/billing-seeds/billing-rules-manifest.json"
FALLBACK_MANIFEST = ROOT / "测试用例/billing_rules_manifest.json"
PARITY_REPORT = ROOT / "测试用例/billing_rules_parity_report.json"

COMPARE_FIELDS = (
    "ruleType",
    "price",
    "keywords",
    "priority",
    "foldRatio",
    "threshold",
    "isActive",
    "conditionsJson",
    "billingMode",
)


def load_manifest(path: Path | None = None) -> dict[str, Any]:
    manifest_path = path or MANIFEST_PATH
    if not manifest_path.is_file():
        manifest_path = FALLBACK_MANIFEST
    if not manifest_path.is_file():
        raise FileNotFoundError("billing-rules manifest not found; run billing_rules_manifest.py --write")
    return json.loads(manifest_path.read_text(encoding="utf-8"))


def _as_float(val: Any) -> float | None:
    if val is None:
        return None
    try:
        return float(val)
    except (TypeError, ValueError):
        return None


def _normalize_keywords(val: Any) -> list[str]:
    if val is None:
        return []
    if isinstance(val, list):
        return sorted(str(x) for x in val if x is not None)
    return []


def _normalize_conditions_json(val: Any) -> str | None:
    if val is None or val == "":
        return None
    if isinstance(val, (dict, list)):
        return json.dumps(val, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    text = str(val).strip()
    if not text:
        return None
    try:
        parsed = json.loads(text)
        return json.dumps(parsed, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    except json.JSONDecodeError:
        return text


def normalize_rule(rule: dict[str, Any]) -> dict[str, Any]:
    price = rule.get("price")
    if price is None:
        price = rule.get("fixed_price") or rule.get("fixedPrice")
    fold = rule.get("foldRatio")
    if fold is None:
        fold = rule.get("fold_ratio")
    threshold = rule.get("threshold")
    is_active = rule.get("isActive")
    if is_active is None:
        is_active = rule.get("is_active")
    if is_active is None:
        is_active = True
    rule_type = rule.get("ruleType") or rule.get("rule_type") or "FIXED_PRICE"
    keywords = rule.get("keywords")
    billing_mode = rule.get("billingMode") or rule.get("billing_mode")
    conditions = rule.get("conditionsJson") or rule.get("conditions_json")
    return {
        "name": str(rule.get("name") or "").strip(),
        "ruleType": str(rule_type),
        "price": _as_float(price),
        "keywords": _normalize_keywords(keywords),
        "priority": int(rule.get("priority") if rule.get("priority") is not None else 100),
        "foldRatio": _as_float(fold),
        "threshold": int(threshold) if threshold is not None else None,
        "isActive": bool(is_active),
        "conditionsJson": _normalize_conditions_json(conditions),
        "billingMode": str(billing_mode) if billing_mode else None,
    }


def _price_equal(a: float | None, b: float | None) -> bool:
    if a is None and b is None:
        return True
    if a is None or b is None:
        return False
    return math.isclose(a, b, abs_tol=0.01)


def _fold_equal(a: float | None, b: float | None) -> bool:
    if a is None and b is None:
        return True
    if a is None or b is None:
        return False
    return math.isclose(a, b, abs_tol=0.0001)


def canonical_json_hash(val: Any) -> str | None:
    if val is None:
        return None
    if isinstance(val, str):
        text = val.strip()
        if not text:
            return None
        try:
            val = json.loads(text)
        except json.JSONDecodeError:
            return hashlib.sha256(text.encode("utf-8")).hexdigest()
    if isinstance(val, (dict, list)):
        canonical = json.dumps(val, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return hashlib.sha256(str(val).encode("utf-8")).hexdigest()


def diff_rule(expected: dict[str, Any], actual: dict[str, Any]) -> list[str]:
    diffs: list[str] = []
    exp = normalize_rule(expected)
    act = normalize_rule(actual)
    if exp["ruleType"] != act["ruleType"]:
        diffs.append(f"ruleType {act['ruleType']}!={exp['ruleType']}")
    if not _price_equal(exp["price"], act["price"]):
        diffs.append(f"price {act['price']}!={exp['price']}")
    if exp["keywords"] != act["keywords"]:
        diffs.append(f"keywords {act['keywords']}!={exp['keywords']}")
    if exp["priority"] != act["priority"]:
        diffs.append(f"priority {act['priority']}!={exp['priority']}")
    if not _fold_equal(exp["foldRatio"], act["foldRatio"]):
        diffs.append(f"foldRatio {act['foldRatio']}!={exp['foldRatio']}")
    if exp["threshold"] != act["threshold"]:
        diffs.append(f"threshold {act['threshold']}!={exp['threshold']}")
    if exp["isActive"] != act["isActive"]:
        diffs.append(f"isActive {act['isActive']}!={exp['isActive']}")
    if exp["conditionsJson"] != act["conditionsJson"]:
        diffs.append(f"conditionsJson {act['conditionsJson']!r}!={exp['conditionsJson']!r}")
    if exp["billingMode"] is not None and exp["billingMode"] != act["billingMode"]:
        diffs.append(f"billingMode {act['billingMode']!r}!={exp['billingMode']!r}")
    return diffs


def active_rules(rules: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [r for r in rules if normalize_rule(r)["isActive"]]


def compare_customer(
    *,
    code: str,
    expected_rules: list[dict[str, Any]],
    prod_rules: list[dict[str, Any]],
    expected_mode: str | None = None,
    prod_mode: str | None = None,
    expected_override: Any = None,
    prod_override: Any = None,
    expected_billing_enabled: bool | None = None,
    prod_billing_enabled: bool | None = None,
) -> dict[str, Any]:
    exp_by_name = {normalize_rule(r)["name"]: r for r in active_rules(expected_rules) if normalize_rule(r)["name"]}
    prod_by_name = {normalize_rule(r)["name"]: r for r in active_rules(prod_rules) if normalize_rule(r)["name"]}
    missing = sorted(set(exp_by_name) - set(prod_by_name))
    extra = sorted(set(prod_by_name) - set(exp_by_name))
    changed: list[dict[str, Any]] = []
    for name in sorted(set(exp_by_name) & set(prod_by_name)):
        diffs = diff_rule(exp_by_name[name], prod_by_name[name])
        if diffs:
            changed.append({"name": name, "diffs": diffs})
    active_expected = sum(1 for r in expected_rules if normalize_rule(r)["isActive"])
    active_prod = sum(1 for r in prod_rules if normalize_rule(r)["isActive"])
    mode_expected = (expected_mode or "standard").strip().lower()
    mode_prod = (prod_mode or "standard").strip().lower()
    mode_drift = mode_expected != mode_prod
    exp_override_hash = canonical_json_hash(expected_override)
    prod_override_hash = canonical_json_hash(prod_override)
    override_drift = False
    if exp_override_hash is not None:
        override_drift = exp_override_hash != prod_override_hash
    billing_enabled_drift = False
    if expected_billing_enabled is not None and prod_billing_enabled is not None:
        billing_enabled_drift = bool(expected_billing_enabled) != bool(prod_billing_enabled)
    ok = (
        not missing
        and not extra
        and not changed
        and not mode_drift
        and not override_drift
        and not billing_enabled_drift
    )
    return {
        "code": code,
        "expected_count": len(expected_rules),
        "prod_count": len(prod_rules),
        "active_expected": active_expected,
        "active_prod": active_prod,
        "mode_expected": mode_expected,
        "mode_prod": mode_prod,
        "mode_drift": mode_drift,
        "override_drift": override_drift,
        "override_hash_expected": exp_override_hash,
        "override_hash_prod": prod_override_hash,
        "billing_enabled_expected": expected_billing_enabled,
        "billing_enabled_prod": prod_billing_enabled,
        "billing_enabled_drift": billing_enabled_drift,
        "missing": missing,
        "extra": extra,
        "changed": changed,
        "missing_count": len(missing),
        "extra_count": len(extra),
        "changed_count": len(changed),
        "ok": ok,
    }


def run_rules_compare(
    client: ApiClient,
    *,
    code: str | None,
    compare_all: bool,
    manifest_path: Path | None = None,
) -> dict[str, Any]:
    manifest = load_manifest(manifest_path)
    customers_manifest: dict[str, Any] = manifest.get("customers") or {}
    client.login(force=True)
    api_customers = client.customers()
    billing_enabled = {
        str(c.get("code") or "").strip().upper(): c
        for c in api_customers
        if c.get("billingEnabled") or c.get("billing_enabled")
    }

    targets: list[str] = []
    if code:
        targets = [code.strip().upper()]
    elif compare_all:
        targets = sorted(set(customers_manifest) & set(billing_enabled))
    else:
        raise ValueError("需要 --code 或 --all")

    results: list[dict[str, Any]] = []
    for target in targets:
        entry = customers_manifest.get(target)
        if not entry:
            results.append(
                {
                    "code": target,
                    "ok": False,
                    "error": "not in manifest",
                    "missing_count": 0,
                    "extra_count": 0,
                    "changed_count": 0,
                }
            )
            continue
        customer = billing_enabled.get(target) or client.customer_by_code(target)
        if customer is None:
            results.append(
                {
                    "code": target,
                    "ok": False,
                    "error": "customer not found in API",
                    "missing_count": 0,
                    "extra_count": 0,
                    "changed_count": 0,
                }
            )
            continue
        customer_id = int(customer.get("id") or customer.get("customerId") or 0)
        prod_rules = client.product_rules(customer_id)
        expected_rules = list(entry.get("productRules") or [])
        prod_mode = (
            customer.get("billingPricingMode")
            or customer.get("billing_pricing_mode")
            or "standard"
        )
        prod_override = customer.get("standardPricingOverride") or customer.get(
            "standard_pricing_override"
        )
        prod_billing_enabled = customer.get("billingEnabled")
        if prod_billing_enabled is None:
            prod_billing_enabled = customer.get("billing_enabled")
        expected_billing_enabled = entry.get("billingEnabled")
        results.append(
            compare_customer(
                code=target,
                expected_rules=expected_rules,
                prod_rules=prod_rules,
                expected_mode=entry.get("billingPricingMode"),
                prod_mode=str(prod_mode),
                expected_override=entry.get("standardPricingOverride"),
                prod_override=prod_override,
                expected_billing_enabled=expected_billing_enabled,
                prod_billing_enabled=prod_billing_enabled,
            )
        )

    ok = all(r.get("ok") for r in results)
    summary = {
        "customers": len(results),
        "ok_count": sum(1 for r in results if r.get("ok")),
        "drift_count": sum(1 for r in results if not r.get("ok")),
        "total_missing": sum(int(r.get("missing_count") or 0) for r in results),
        "total_extra": sum(int(r.get("extra_count") or 0) for r in results),
        "total_changed": sum(int(r.get("changed_count") or 0) for r in results),
        "mode_drift_count": sum(1 for r in results if r.get("mode_drift")),
        "override_drift_count": sum(1 for r in results if r.get("override_drift")),
        "billing_enabled_drift_count": sum(1 for r in results if r.get("billing_enabled_drift")),
    }
    return {
        "command": "rules compare",
        "manifest_hash": manifest.get("manifest_hash"),
        "api_base": client.api_base,
        "ok": ok,
        "summary": summary,
        "results": results,
    }


def format_human(report: dict[str, Any]) -> str:
    lines = [
        f"manifest hash: {(report.get('manifest_hash') or '')[:16]}…",
        f"customers: {report['summary']['customers']} · "
        f"OK {report['summary']['ok_count']} · drift {report['summary']['drift_count']}",
        f"missing {report['summary']['total_missing']} · "
        f"extra {report['summary']['total_extra']} · "
        f"changed {report['summary']['total_changed']} · "
        f"mode_drift {report['summary'].get('mode_drift_count', 0)} · "
        f"override_drift {report['summary'].get('override_drift_count', 0)} · "
        f"billing_enabled_drift {report['summary'].get('billing_enabled_drift_count', 0)}",
    ]
    for row in report.get("results") or []:
        code = row.get("code")
        if row.get("error"):
            lines.append(f"  {code}: ERROR {row['error']}")
            continue
        status = "OK" if row.get("ok") else "DRIFT"
        mode_note = ""
        if row.get("mode_drift"):
            mode_note = f" · mode {row.get('mode_prod')}!={row.get('mode_expected')}"
        if row.get("override_drift"):
            mode_note += " · override drift"
        if row.get("billing_enabled_drift"):
            mode_note += (
                f" · billingEnabled {row.get('billing_enabled_prod')}"
                f"!={row.get('billing_enabled_expected')}"
            )
        lines.append(
            f"  {code}: {status} · expected {row.get('expected_count')} · "
            f"prod {row.get('prod_count')} · "
            f"missing {row.get('missing_count')} · extra {row.get('extra_count')} · "
            f"changed {row.get('changed_count')}{mode_note}"
        )
        if row.get("missing"):
            lines.append(f"    missing: {', '.join(row['missing'][:8])}"
                         + ("…" if len(row["missing"]) > 8 else ""))
    return "\n".join(lines)
