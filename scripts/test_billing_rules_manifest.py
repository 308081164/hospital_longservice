#!/usr/bin/env python3
"""Tests for billing_rules_manifest merge logic."""

from __future__ import annotations

import sys
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPTS))

from billing_rules_manifest import build_manifest  # noqa: E402


def test_hrb_2nd_special_only_and_deactivated_rule() -> None:
    manifest = build_manifest()
    entry = manifest["customers"]["HRB-2ND"]
    assert entry["billingPricingMode"] == "special_only", entry["billingPricingMode"]
    rules = {r["name"]: r for r in entry["productRules"]}
    assert rules["校正价5.5"]["isActive"] is False
    assert rules["口腔科正畸车针8元"]["isActive"] is True
    oral_depts = rules["口腔科调刀8元"].get("conditionsJson") or ""
    assert "口腔修复诊室" in oral_depts
    assert entry["active_rule_count"] == 7


def test_changjian_dedup_and_rule_migrate() -> None:
    manifest = build_manifest()
    customers = manifest["customers"]
    changjian = customers.get("CHANGJIAN") or {}
    hrb_cj = customers.get("HRB-CJ") or {}
    assert changjian.get("name") != hrb_cj.get("name")
    assert str(changjian.get("status") or "").lower() == "inactive"
    hrb_rules = {r["name"]: r for r in hrb_cj.get("productRules") or []}
    assert "长健结款单行对齐" in hrb_rules
    assert hrb_rules["长健结款单行对齐"]["price"] == 50
    cj_rules = {r["name"]: r for r in changjian.get("productRules") or []}
    if "长健结款单行对齐" in cj_rules:
        assert cj_rules["长健结款单行对齐"]["isActive"] is False


def test_active_customers_no_duplicate_names() -> None:
    manifest = build_manifest()
    from collections import defaultdict

    by_name: dict[str, list[str]] = defaultdict(list)
    for code, entry in manifest["customers"].items():
        if str(entry.get("status") or "").lower() == "inactive":
            continue
        name = (entry.get("name") or code).strip()
        by_name[name].append(code)
    dupes = {n: codes for n, codes in by_name.items() if len(codes) > 1}
    assert not dupes, dupes


def test_catalog_skips_inactive_main_sections() -> None:
    from billing_rules_catalog import build_catalog_md  # noqa: E402

    md = build_catalog_md()
    assert "## 哈尔滨长健医院（`CHANGJIAN`）" not in md
    assert "## 哈尔滨长健医院（`HRB-CJ`）" in md
    assert "规范名重复审计**：无" in md


if __name__ == "__main__":
    test_hrb_2nd_special_only_and_deactivated_rule()
    test_changjian_dedup_and_rule_migrate()
    test_active_customers_no_duplicate_names()
    test_catalog_skips_inactive_main_sections()
    print("ok")
