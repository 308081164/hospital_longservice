#!/usr/bin/env python3
"""rules_compare 与 Java baseline import/verify 路径对齐测试。"""

import json
import unittest

from rules_compare import diff_rule, merge_accepted_types_into_conditions, normalize_rule, resolve_conditions_json


class RulesCompareAcceptedTypesTest(unittest.TestCase):
    def test_merge_accepted_types_appends_type_condition(self) -> None:
        merged = merge_accepted_types_into_conditions(None, ["器械包"])
        self.assertEqual(
            json.loads(merged or "[]"),
            [{"field": "type", "operator": "in", "value": ["器械包"]}],
        )

    def test_merge_preserves_manual_review_and_replaces_type(self) -> None:
        base = '[{"field":"manualReview","value":true},{"field":"type","operator":"in","value":["旧"]}]'
        merged = merge_accepted_types_into_conditions(base, ["额外包（纸塑袋）"])
        parsed = json.loads(merged or "[]")
        self.assertEqual(parsed[0], {"field": "manualReview", "value": True})
        self.assertEqual(parsed[-1]["field"], "type")
        self.assertEqual(parsed[-1]["value"], ["额外包（纸塑袋）"])

    def test_baseline_and_prod_rule_match_after_accepted_types_merge(self) -> None:
        expected = {
            "ruleType": "PRICE_PER_INSTRUMENT",
            "name": "冰城整形手术包按件5.5",
            "price": 5.5,
            "acceptedTypes": ["器械包"],
            "isActive": True,
        }
        actual = {
            "ruleType": "PRICE_PER_INSTRUMENT",
            "name": "冰城整形手术包按件5.5",
            "price": 5.5,
            "isActive": True,
            "conditionsJson": '[{"field":"type","operator":"in","value":["器械包"]}]',
        }
        self.assertEqual(diff_rule(expected, actual), [])

    def test_keyword_match_mode_exact_token_equivalent_to_missing(self) -> None:
        expected = {"name": "r", "ruleType": "FIXED_PRICE", "price": 1.0, "isActive": True}
        actual = {
            "name": "r",
            "ruleType": "FIXED_PRICE",
            "price": 1.0,
            "isActive": True,
            "keywordMatchMode": "exact_token",
        }
        self.assertEqual(diff_rule(expected, actual), [])

    def test_resolve_conditions_json_canonicalizes_key_order(self) -> None:
        left = resolve_conditions_json(
            {
                "acceptedTypes": ["额外包（纸塑袋）"],
                "conditionsJson": '[{"field":"manualReview","value":true}]',
            }
        )
        right = resolve_conditions_json(
            {
                "conditionsJson": (
                    '[{"field": "manualReview", "value": true}, '
                    '{"field": "type", "value": ["额外包（纸塑袋）"], "operator": "in"}]'
                )
            }
        )
        self.assertEqual(left, right)


if __name__ == "__main__":
    unittest.main()
