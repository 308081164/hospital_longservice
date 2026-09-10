package com.hospital.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillingConditionEvaluatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void variantRuleRequiresExactVariantMatch() throws Exception {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("variantId", 42L);
        rule.put("productId", 10L);

        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, 10L, 42L, "测试包", "测试包")).isTrue();
        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, 10L, 41L, "测试包", "测试包")).isFalse();
        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, 10L, null, "测试包", "测试包")).isFalse();
    }

    @Test
    void productRuleMatchesByProductIdOrKeywords() throws Exception {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("productId", 10L);
        rule.putArray("keywords").add("腹腔镜");

        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, 10L, null, "任意文本", "任意文本")).isTrue();
        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, null, null, "腹腔镜", "额外包-腹腔镜-高温")).isTrue();
        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, null, null, "无关包名", "无关包名")).isFalse();
    }

    @Test
    void exactTokenRuleMatchesOnPackNameOnly() throws Exception {
        // 名称严格对应（exact_token）：仅对包名判定，组合文本中袋型紧邻包名不应导致失配
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("keywordMatchMode", "exact_token");
        rule.putArray("keywords").add("大车针盒-1/Z1526");

        assertThat(BillingConditionEvaluator.matchesProductBinding(
                rule, null, null, "大车针盒-1/Z1526", "额外包(纸塑袋) 大车针盒-1/Z1526 高温纸塑袋75*200"))
                .isTrue();
        // 包名不完全对应（右侧邻接中文）则不命中
        assertThat(BillingConditionEvaluator.matchesProductBinding(
                rule, null, null, "大车针盒-1/Z1526改", "额外包(纸塑袋) 大车针盒-1/Z1526改 高温纸塑袋75*200"))
                .isFalse();
    }

    @Test
    void containsRuleMatchesOnCombinedText() throws Exception {
        // 包含（默认）：在组合文本上做子串包含，与 2026-08-27 基线行为一致
        ObjectNode rule = MAPPER.createObjectNode();
        rule.putArray("keywords").add("环钻包");

        assertThat(BillingConditionEvaluator.matchesProductBinding(
                rule, null, null, "环钻包", "器械包 环钻包 无纺布-90×90-50g"))
                .isTrue();
        assertThat(BillingConditionEvaluator.matchesProductBinding(
                rule, null, null, "无关包", "器械包 无关包 无纺布-90×90-50g"))
                .isFalse();
    }

    @Test
    void matchSignatureDetectsDuplicateRules() {
        var rule1 = java.util.Map.<String, Object>of(
                "ruleType", "FIXED_PRICE",
                "productId", 1,
                "keywords", "[\"test\"]");
        var rule2 = java.util.Map.<String, Object>of(
                "ruleType", "FIXED_PRICE",
                "productId", 1,
                "keywords", "[\"test\"]");

        assertThat(BillingConditionEvaluator.matchSignature(rule1))
                .isEqualTo(BillingConditionEvaluator.matchSignature(rule2));
    }

    @Test
    void matchesRuleKeywords_honorsContainsSuffixInKeyword() throws Exception {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.putArray("keywords").add("棉花针@contains");

        assertThat(BillingConditionEvaluator.matchesRuleKeywords(rule, "额外包(纸塑袋) 棉花针 高温纸塑袋75*200"))
                .isTrue();
        assertThat(BillingConditionEvaluator.matchesRuleKeywords(rule, "额外包(纸塑袋) 无关包 高温纸塑袋75*200"))
                .isFalse();
    }

    @Test
    void temperatureScopeMatchesHtLtAny() {
        assertThat(BillingConditionEvaluator.temperatureScopeMatches("ANY", "HT")).isTrue();
        assertThat(BillingConditionEvaluator.temperatureScopeMatches("HT", "HT")).isTrue();
        assertThat(BillingConditionEvaluator.temperatureScopeMatches("HT", "LT")).isFalse();
    }

    @Test
    void packTypeConstraintBlocksWrongBillType() throws Exception {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.putArray("keywords").add("纱布");
        rule.putArray("acceptedTypes").add("敷料包（无纺布）");

        assertThat(BillingConditionEvaluator.packTypeMatches(rule, "敷料包(无纺布包)")).isTrue();
        assertThat(BillingConditionEvaluator.packTypeMatches(rule, "额外包(纸塑袋)")).isFalse();
    }

    @Test
    void packTypeConstraintLimitsKeywordToPackNameOnly() throws Exception {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.putArray("keywords").add("纱布");
        rule.putArray("acceptedTypes").add("敷料包（无纺布）");

        assertThat(BillingConditionEvaluator.matchesRuleKeywords(
                rule, "外科纱布包", "额外包(纸塑袋)外科纱布包高温纸塑袋"))
                .isTrue();
        assertThat(BillingConditionEvaluator.matchesRuleKeywords(
                rule, "开口器4件", "额外包(纸塑袋)开口器4件高温纸塑袋"))
                .isFalse();
    }

    @Test
    void needleBoxMode_matchesParenFormula_notBareNeedleToken() throws Exception {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("keywordMatchMode", "exact_token");
        rule.putArray("keywords").add("针盒针@needle_box");

        assertThat(BillingConditionEvaluator.matchesRuleKeywords(
                rule, "全冠套装(针7盒1)", "额外包(纸塑袋) 全冠套装(针7盒1) 高温纸塑袋75*200"))
                .isTrue();
        assertThat(BillingConditionEvaluator.matchesRuleKeywords(
                rule, "针盒1针58/z1026", "额外包(纸塑袋) 针盒1针58/z1026 高温纸塑袋15cm"))
                .isTrue();
        // 裸词「针」exact_token 会误伤，needle_box 不应命中
        assertThat(BillingConditionEvaluator.matchesRuleKeywords(
                rule, "针-5/z7534", "额外包(纸塑袋) 针-5/z7534 高温纸塑袋75*370"))
                .isFalse();
        assertThat(BillingConditionEvaluator.matchesRuleKeywords(
                rule, "加长根管锉-6/Z7520", "额外包(纸塑袋) 加长根管锉-6/Z7520 高温纸塑袋75*370"))
                .isFalse();
        assertThat(BillingConditionEvaluator.matchesRuleKeywords(
                rule, "缝合针-2件/Z7520", "额外包(纸塑袋) 缝合针-2件/Z7520 高温纸塑袋75*370"))
                .isFalse();
    }

    @Test
    void matchesNeedleBoxFormula_rejectsCarNeedleBoxAndMachineExpandNeedle() {
        assertThat(BillingConditionEvaluator.matchesNeedleBoxFormula("全冠套装(针7盒1)")).isTrue();
        assertThat(BillingConditionEvaluator.matchesNeedleBoxFormula("抛光车针盒6件盒1/Z1026")).isFalse();
        assertThat(BillingConditionEvaluator.matchesNeedleBoxFormula("机扩针-6盒1/Z7520")).isFalse();
        assertThat(BillingConditionEvaluator.matchesNeedleBoxFormula("针-5/z7534")).isFalse();
    }

    @Test
    void packTypeEquivalentNormalizesDressingLabels() {
        assertThat(BillingConditionEvaluator.packTypeEquivalent("敷料包（无纺布）", "敷料包(无纺布包)"))
                .isTrue();
        assertThat(BillingConditionEvaluator.packTypeEquivalent("额外包（纸塑袋）", "额外包(纸塑袋)"))
                .isTrue();
    }

    @Test
    void angleDigitKeywordDoesNotMatchInsideLongerDegreeNumber() throws Exception {
        ObjectNode zeroRule = MAPPER.createObjectNode();
        zeroRule.putArray("keywords").add("0°膀胱镜-1");
        zeroRule.putArray("keywords").add("0°膀胱镜");
        zeroRule.putArray("acceptedTypes").add("单包装（低温老肯）");

        assertThat(BillingConditionEvaluator.matchesRuleKeywords(
                zeroRule, "0°膀胱镜-1/Z7520", "单包装包(老肯低温) 0°膀胱镜-1/Z7520 低温纸塑袋"))
                .isTrue();
        assertThat(BillingConditionEvaluator.matchesRuleKeywords(
                zeroRule, "30°膀胱镜-1/Z7520", "单包装包(老肯低温) 30°膀胱镜-1/Z7520 低温纸塑袋"))
                .isFalse();

        ObjectNode thirtyRule = MAPPER.createObjectNode();
        thirtyRule.putArray("keywords").add("30°膀胱镜");
        assertThat(BillingConditionEvaluator.matchesRuleKeywords(
                thirtyRule, "30°膀胱镜-1", "单包装包(老肯低温) 30°膀胱镜-1 低温纸塑袋"))
                .isTrue();
    }

    @Test
    void packTypeEquivalentAcceptsBaselineAcceptedTypeShorthands() {
        assertThat(BillingConditionEvaluator.packTypeEquivalent("额外包低温等离子", "额外包(低温等离子)"))
                .isTrue();
        assertThat(BillingConditionEvaluator.packTypeEquivalent("单包装（低温老肯）", "单包装包（老肯低温）"))
                .isTrue();
    }
}
