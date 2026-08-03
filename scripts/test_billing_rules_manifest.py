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


if __name__ == "__main__":
    test_hrb_2nd_special_only_and_deactivated_rule()
    print("ok")
