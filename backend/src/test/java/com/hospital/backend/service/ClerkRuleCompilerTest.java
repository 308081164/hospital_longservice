package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.config.ClerkRuleIndex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClerkRuleCompilerTest {

    private final ClerkRuleCompiler compiler = new ClerkRuleCompiler(new ClerkRuleIndex());

    @Test
    void compilesHulanTcmSettlementPackSplit() {
        ObjectNode compiled = compiler.compileForCustomer("HULAN-TCM");
        assertThat(compiled).isNotNull();
        assertThat(compiled.path("settlementPackSplit").path("deptKeyword").asText())
                .isEqualTo("手术室（备包）");
    }

    @Test
    void compilesDaowaiBillExportPriceRules() {
        ObjectNode compiled = compiler.compileForCustomer("DAOWAI-RM");
        assertThat(compiled).isNotNull();
        assertThat(compiler.hasActiveBillExportRules("DAOWAI-RM")).isTrue();
        boolean hasPriceRule = false;
        for (JsonNode rule : compiled.path("clerkRules")) {
            if ("BILL_EXPORT_PRICE_RULE".equals(rule.path("ruleType").asText())) {
                hasPriceRule = true;
            }
        }
        assertThat(hasPriceRule).isTrue();
    }

    @Test
    void compilesEryyBillDiscountOntoExportPolicy() {
        ObjectNode compiled = compiler.compileForCustomer("ERYY-NG");
        assertThat(compiled).isNotNull();
        assertThat(compiler.hasActiveBillExportRules("ERYY-NG")).isTrue();
        boolean hasBillDiscount = false;
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if (!"DISCOUNT".equals(policy.path("policyType").asText())) {
                continue;
            }
            if (!"export_only".equals(policy.path("params").path("applyStage").asText())) {
                continue;
            }
            hasBillDiscount = true;
            assertThat(policy.path("name").asText()).isEqualTo("标准价七折");
            assertThat(policy.path("params").path("rate").asDouble()).isEqualTo(0.7);
            assertThat(policy.path("params").path("validateOnly").asBoolean(false)).isFalse();
        }
        assertThat(hasBillDiscount).isTrue();
    }

    @Test
    void legacyValidateOnlyPriceRuleStillAppliesBillDiscount() {
        ObjectNode baseline = com.hospital.backend.common.JsonUtils.getObjectMapper().createObjectNode();
        baseline.put("customerCode", "LEGACY");
        var rules = baseline.putArray("rules");
        var rule = rules.addObject();
        rule.put("ruleType", "PRICE_VALIDATE_ONLY");
        rule.put("name", "标准价七折校对");
        rule.put("stage", "bill_export");
        rule.put("isActive", true);
        rule.putObject("params").put("rate", 0.7).put("validateOnly", true);

        ObjectNode compiled = compiler.compileBaseline(baseline, "LEGACY");
        assertThat(compiled).isNotNull();
        JsonNode policy = null;
        for (JsonNode candidate : compiled.path("billingPolicies")) {
            if ("export_only".equals(candidate.path("params").path("applyStage").asText())) {
                policy = candidate;
            }
        }
        assertThat(policy).isNotNull();
        assertThat(policy.path("params").path("rate").asDouble()).isEqualTo(0.7);
        assertThat(policy.path("params").has("validateOnly")).isFalse();
    }

    @Test
    void compilesJiuzhouSettlementDiscounts() {
        ObjectNode compiled = compiler.compileForCustomer("JIUZHOU-FK");
        assertThat(compiled).isNotNull();
        JsonNode policies = compiled.path("billingPolicies");
        assertThat(policies.isArray()).isTrue();
        assertThat(policies.size()).isGreaterThanOrEqualTo(2);
        boolean hasHt = false;
        boolean hasLt = false;
        for (JsonNode policy : policies) {
            String temp = policy.path("scope").path("temperature").asText("");
            if ("HT".equals(temp)) {
                hasHt = true;
                assertThat(policy.path("params").path("rate").asDouble()).isEqualTo(0.5);
            }
            if ("LT".equals(temp)) {
                hasLt = true;
                assertThat(policy.path("params").path("rate").asDouble()).isEqualTo(0.7);
            }
        }
        assertThat(hasHt).isTrue();
        assertThat(hasLt).isTrue();
    }

    @Test
    void compilesTaipingPieceTierExportDiscount() {
        ObjectNode compiled = compiler.compileForCustomer("TAIPING-RM");
        assertThat(compiled).isNotNull();
        assertThat(compiler.hasActiveBillExportRules("TAIPING-RM")).isTrue();
        int billPriceRules = 0;
        for (JsonNode rule : compiled.path("clerkRules")) {
            if ("BILL_EXPORT_PRICE_RULE".equals(rule.path("ruleType").asText())) {
                billPriceRules++;
            }
        }
        assertThat(billPriceRules).isGreaterThanOrEqualTo(20);
        boolean hasPieceTier = false;
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if (policy.path("params").path("pieceTierDiscounts").isArray()) {
                hasPieceTier = true;
            }
        }
        assertThat(hasPieceTier).isTrue();
    }

    @Test
    void compilesFuyierThreeSupplementTypes() {
        ObjectNode compiled = compiler.compileForCustomer("ZYY-D2-NG");
        assertThat(compiled.path("exportSupplementTypes"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("dept_summary", "price_summary", "instrument_audit");
    }

    @Test
    void compilesZy3SterilizeFeeDetailType() {
        ObjectNode compiled = compiler.compileForCustomer("ZY3-DIANLI");
        assertThat(compiled.path("exportSupplementTypes"))
                .extracting(JsonNode::asText)
                .contains("instrument_audit", "sterilize_fee_detail");
    }

    @Test
    void jiuzhouMinChargeNotDuplicatedInPolicies() {
        ObjectNode compiled = compiler.compileForCustomer("JIUZHOU-FK");
        int minChargePolicies = 0;
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if ("MONTHLY_SETTLEMENT".equals(policy.path("policyType").asText())
                    && policy.path("params").path("minCharge").asDouble() == 3000.0) {
                minChargePolicies++;
            }
        }
        assertThat(minChargePolicies).isEqualTo(1);
    }

    @Test
    void compilesRenshengLogisticsCard() {
        ObjectNode compiled = compiler.compileForCustomer("RENSHENG");
        assertThat(compiled).isNotNull();
        boolean hasCard = false;
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if ("LOGISTICS".equals(policy.path("policyType").asText())
                    && policy.path("params").path("useLogisticsCard").asBoolean()) {
                hasCard = true;
            }
        }
        assertThat(hasCard).isTrue();
    }

    @Test
    void compilesHulanTcmMinCharge() {
        ObjectNode compiled = compiler.compileForCustomer("HULAN-TCM");
        assertThat(compiled).isNotNull();
        boolean hasMinCharge = false;
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if ("MONTHLY_SETTLEMENT".equals(policy.path("policyType").asText())) {
                hasMinCharge = true;
                assertThat(policy.path("params").path("minCharge").asDouble()).isEqualTo(10000);
            }
        }
        assertThat(hasMinCharge).isTrue();
    }

    @Test
    void compilesZyyD1FuyiClerkRules() {
        ObjectNode compiled = compiler.compileForCustomer("ZYY-D1");
        assertThat(compiled).isNotNull();
        assertThat(compiler.hasActiveBillExportRules("ZYY-D1")).isTrue();

        boolean hasFuyiLayout = false;
        for (JsonNode layout : compiled.path("exportLayouts")) {
            JsonNode params = layout.path("params");
            if ("fuyi_extended_11col".equals(params.path("billColumnLayout").asText())
                    && "dept_split".equals(params.path("billLayout").asText())) {
                hasFuyiLayout = true;
            }
        }
        assertThat(hasFuyiLayout).isTrue();

        boolean hasLogistics = false;
        boolean hasWashing = false;
        boolean hasUrgent = false;
        for (JsonNode policy : compiled.path("billingPolicies")) {
            String type = policy.path("policyType").asText();
            if ("LOGISTICS".equals(type)
                    && policy.path("params").path("feePerTrip").asDouble() == 45.0) {
                hasLogistics = true;
            }
            if ("SETTLEMENT_EXTRA".equals(type)
                    && "手术一区洗涤费用".equals(policy.path("params").path("itemName").asText())) {
                hasWashing = true;
            }
            if ("URGENT".equals(type)
                    && policy.path("params").path("baseMultiplier").asDouble() == 1.25) {
                hasUrgent = true;
            }
        }
        assertThat(hasLogistics).isTrue();
        assertThat(hasWashing).isTrue();
        assertThat(hasUrgent).isTrue();

        assertThat(compiled.path("exportSupplementTypes"))
                .extracting(JsonNode::asText)
                .contains("dept_summary", "logistics_allocation");
    }

    @Test
    void compilesNgFuchanPackNamePriceAndScatterPieceRule() {
        ObjectNode compiled = compiler.compileForCustomer("NG-FUCHAN");
        assertThat(compiled).isNotNull();
        assertThat(compiler.hasActiveBillExportRules("NG-FUCHAN")).isTrue();

        boolean hasGongqiang = false;
        boolean hasScatterPiece = false;
        boolean hasExportLayout = false;
        for (JsonNode rule : compiled.path("clerkRules")) {
            String type = rule.path("ruleType").asText();
            if ("PACK_NAME_PRICE".equals(type) && "宫腔镜".equals(rule.path("name").asText())) {
                hasGongqiang = true;
                assertThat(rule.path("params").path("unitPrice").asDouble()).isEqualTo(170.5);
            }
            if ("BILL_EXPORT_PRICE_RULE".equals(type) && "散包按把".equals(rule.path("name").asText())) {
                hasScatterPiece = true;
                assertThat(rule.path("params").path("unitPriceMode").asText()).isEqualTo("PER_PIECE");
            }
            if ("EXPORT_LAYOUT".equals(type)) {
                hasExportLayout = true;
            }
        }
        assertThat(hasGongqiang).isTrue();
        assertThat(hasScatterPiece).isTrue();
        assertThat(hasExportLayout).isTrue();
    }

    @Test
    void compilesAolanYyPriceTableFromOverflowSheet() {
        ObjectNode compiled = compiler.compileForCustomer("AOLAN-YY");
        assertThat(compiled).isNotNull();
        assertThat(compiler.hasActiveBillExportRules("AOLAN-YY")).isTrue();

        boolean hasZhengxingPack = false;
        for (JsonNode rule : compiled.path("clerkRules")) {
            if ("整形包".equals(rule.path("name").asText())
                    && "PACK_NAME_PRICE".equals(rule.path("ruleType").asText())) {
                hasZhengxingPack = true;
                assertThat(rule.path("params").path("unitPrice").asDouble()).isEqualTo(8.0);
                assertThat(rule.path("params").has("acceptedTypes")).isFalse();
            }
        }
        assertThat(hasZhengxingPack).isTrue();
    }
}
