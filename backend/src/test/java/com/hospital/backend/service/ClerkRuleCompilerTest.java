package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.SettlementTemplateFiller;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
        assertThat(billPriceRules).isGreaterThanOrEqualTo(12);
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

    @Test
    void compilesManualLogisticsBaselines() {
        List<Map<String, Object>> twoDays = List.of(
                Map.of("deliveryDate", "2026-06-01"),
                Map.of("deliveryDate", "2026-06-02"));

        ObjectNode shantun = compiler.compileForCustomer("SHANTUN-RM");
        assertThat(shantun).isNotNull();
        assertThat(logisticsParam(shantun, "feePerTrip")).isEqualTo(0.0);
        assertThat(hasDiscountPolicy(shantun)).isFalse();
        assertThat(LogisticsFeeCalculator.compute(shantun, twoDays))
                .get()
                .extracting(LogisticsFeeCalculator.Result::totalFee)
                .isEqualTo(0.0);

        ObjectNode xinzhongxin = compiler.compileForCustomer("XZX-YY");
        assertThat(xinzhongxin).isNotNull();
        assertThat(logisticsParam(xinzhongxin, "feePerTrip")).isEqualTo(37.5);
        assertThat(hasDiscountPolicy(xinzhongxin)).isFalse();
        assertThat(LogisticsFeeCalculator.compute(xinzhongxin, twoDays))
                .get()
                .extracting(LogisticsFeeCalculator.Result::totalFee)
                .isEqualTo(75.0);

        ObjectNode lizengliang = compiler.compileForCustomer("LZL-KQ");
        assertThat(lizengliang).isNotNull();
        assertThat(logisticsParam(lizengliang, "settlementLogisticsAmountOverride")).isEqualTo(350.0);
        assertThat(hasDiscountPolicy(lizengliang)).isFalse();
        assertThat(LogisticsFeeCalculator.compute(lizengliang, twoDays))
                .get()
                .extracting(LogisticsFeeCalculator.Result::totalFee)
                .isEqualTo(350.0);

        ObjectNode globalDefault = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        globalDefault.putObject("logistics").put("enabled", true);
        assertThat(LogisticsFeeCalculator.compute(globalDefault, twoDays))
                .get()
                .extracting(LogisticsFeeCalculator.Result::feePerTrip, LogisticsFeeCalculator.Result::totalFee)
                .containsExactly(50.0, 100.0);
    }

    @Test
    void settlementLetterDiscountsKeepOriginalRatesAndDoNotChangeRowPrice() {
        assertSettlementSterilizeRate("SHKF-YY", "黑龙江省社会康复医院", 0.75, 750.0);
        assertSettlementSterilizeRate("HRB-HEU", "哈尔滨工程大学医院", 0.9, 900.0);
        assertSettlementSterilizeRate("DONGDA-GC", "黑龙江东大肛肠医院", 0.75, 750.0);

        ObjectNode yuemei = compiler.compileForCustomer("YUEMEI-FH");
        assertThat(yuemei).isNotNull();
        assertThat(hasDiscountPolicy(yuemei)).isFalse();
        JsonNode monthly = monthlyPolicy(yuemei);
        assertThat(monthly.path("params").path("minCharge").asDouble()).isEqualTo(1000.0);
        assertThat(monthly.path("params").path("baseComponents"))
                .extracting(JsonNode::asText)
                .containsExactly("sterilize", "logistics");

        double below = SettlementJobFieldsApplier.settlementMinChargeBase(600, 200.0, yuemei);
        MonthlySettlementCalculator.Result low =
                MonthlySettlementCalculator.compute(yuemei, below).orElseThrow();
        assertThat(below).isEqualTo(800.0);
        assertThat(low.adjustedTotal()).isEqualTo(1000.0);
        assertThat(low.adjustment()).isEqualTo(200.0);

        double above = SettlementJobFieldsApplier.settlementMinChargeBase(900, 200.0, yuemei);
        MonthlySettlementCalculator.Result high =
                MonthlySettlementCalculator.compute(yuemei, above).orElseThrow();
        assertThat(above).isEqualTo(1100.0);
        assertThat(high.adjustment()).isEqualTo(0.0);
        assertThat(BillingPolicyApplier.applyBillDetailDiscounts(
                yuemei, "高温", "治疗包", "无纺布", "悦美芳华医疗门诊医院", 33, 1, false, false))
                .isNull();
    }

    @Test
    void zyyD1October20FormulasStayVisibleAndDoNotStackExportDiscount() {
        JsonNode baseline = new ClerkRuleIndex().baselineForCustomer("ZYY-D1");
        // 2026-10-30 新表：账单规则 91-97 共 7 条公式（原 6 条）
        assertThat(baseline.path("rules")).hasSize(16);
        List<String> formulaRefs = new java.util.ArrayList<>();
        int sealed = 0;
        for (JsonNode rule : baseline.path("rules")) {
            if ("BILL_FORMULA".equals(rule.path("ruleType").asText())) {
                assertThat(rule.path("isActive").asBoolean()).isTrue();
                formulaRefs.add(rule.path("sourceRef").asText());
            }
            if (Boolean.FALSE.equals(rule.path("isActive").asBoolean(true))) {
                sealed++;
            }
        }
        assertThat(formulaRefs).containsExactly(
                "excel:医院内勤规则10.30(1).xlsx#账单规则#91",
                "excel:医院内勤规则10.30(1).xlsx#账单规则#92",
                "excel:医院内勤规则10.30(1).xlsx#账单规则#93",
                "excel:医院内勤规则10.30(1).xlsx#账单规则#94",
                "excel:医院内勤规则10.30(1).xlsx#账单规则#95",
                "excel:医院内勤规则10.30(1).xlsx#账单规则#96",
                "excel:医院内勤规则10.30(1).xlsx#账单规则#97");
        assertThat(sealed).isEqualTo(2);

        ObjectNode compiled = compiler.compileForCustomer("ZYY-D1");
        assertThat(compiled).isNotNull();
        List<String> compiledFormulas = new java.util.ArrayList<>();
        for (JsonNode rule : compiled.path("clerkRules")) {
            if ("BILL_FORMULA".equals(rule.path("ruleType").asText())) {
                compiledFormulas.add(rule.path("name").asText());
            }
            assertThat(rule.path("name").asText()).doesNotContain("封存");
        }
        assertThat(compiledFormulas).containsExactly(
                "附一≥3件标准价八折",
                "附一敷料纸塑及敷料包八折",
                "附一＜3件标准价八折再999折",
                "附一额外无纺布＜3固定13.2",
                "附一换药包120布固定21.99",
                "附一低温八折再999折",
                "附一敷料无纺布八折再999折");
        for (JsonNode policy : compiled.path("billingPolicies")) {
            assertThat(policy.path("name").asText()).doesNotContain("系统价七五折");
            if ("DISCOUNT".equals(policy.path("policyType").asText())) {
                assertThat(policy.path("params").path("applyStage").asText()).isNotEqualTo("export_only");
            }
        }
    }

    @Test
    void songbeiCopiesFuyiDeptSplitWithoutChangingDiscountOrLogistics() {
        JsonNode baseline = new ClerkRuleIndex().baselineForCustomer("ERYY-SB");
        assertThat(baseline.path("rules")).hasSize(5);
        assertThat(ruleParam(baseline, "DISCOUNT_OVERLAY", "rate")).isEqualTo(0.7);
        assertThat(ruleParam(baseline, "LOGISTICS_FEE", "feePerTrip")).isEqualTo(98.0);

        ObjectNode compiled = compiler.compileForCustomer("ERYY-SB");
        assertThat(compiled).isNotNull();
        boolean hasLayout = false;
        for (JsonNode layout : compiled.path("exportLayouts")) {
            JsonNode params = layout.path("params");
            if ("dept_split".equals(params.path("billLayout").asText())
                    && "fuyi_extended_11col".equals(params.path("billColumnLayout").asText())
                    && "hospitalName".equals(params.path("d8DisplaySource").asText())) {
                hasLayout = true;
            }
        }
        assertThat(hasLayout).isTrue();
        boolean hasSevenFold = false;
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if ("DISCOUNT".equals(policy.path("policyType").asText())
                    && policy.path("params").path("rate").asDouble() == 0.7) {
                hasSevenFold = true;
                assertThat(policy.path("params").path("applyStage").asText()).isEqualTo("export_only");
            }
        }
        assertThat(hasSevenFold).isTrue();
        assertThat(logisticsNumbers(compiled)).contains(98.0, 80.5);
    }

    @Test
    void hulanJiuzhouRenshengStylesFollowExistingLettersWithoutNewPrices() {
        ObjectNode hulan = compiler.compileForCustomer("HULAN-TCM");
        JsonNode hulanMin = monthlyPolicy(hulan);
        assertThat(hulanMin.path("params").path("minCharge").asDouble()).isEqualTo(10000.0);
        assertThat(hulanMin.path("params").path("settlementDisplayMode").asText()).isEqualTo("embed");
        assertThat(logisticsParam(hulan, "feePerTrip")).isEqualTo(185.0);
        assertThat(hulan.path("settlementPackSplit").path("deptKeyword").asText()).isEqualTo("手术室（备包）");

        HospitalReconciliationJob hulanJob = new HospitalReconciliationJob();
        hulanJob.setHospitalName("呼兰中医院");
        hulanJob.setLogisticsFee(2405.0);
        hulanJob.setMonthlyBreakdown("{\"adjustment\":764,\"minCharge\":10000,\"rawSterilizeTotal\":6831}");
        SettlementTemplateFiller filler = new SettlementTemplateFiller();
        List<SettlementTemplateFiller.SettlementFeeRow> hulanRows =
                filler.buildFeeRows(hulanJob, 5429.5, hulan, List.of());
        assertThat(hulanRows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains("灭菌费用", "物流费用")
                .doesNotContain("低消补差");
        assertThat(filler.embeddedMinChargeRemark(hulanJob, hulan)).isEqualTo("执行低消10000元");

        ObjectNode jiuzhou = compiler.compileForCustomer("JIUZHOU-FK");
        assertThat(monthlyPolicy(jiuzhou).path("params").path("minCharge").asDouble()).isEqualTo(3000.0);
        assertThat(logisticsParam(jiuzhou, "freeCountPerMonth")).isEqualTo(4.0);
        assertThat(BillingPolicyInspector.settlementOmitZeroRows(jiuzhou)).isTrue();
        HospitalReconciliationJob jiuzhouJob = new HospitalReconciliationJob();
        jiuzhouJob.setHospitalName("黑龙江九洲妇科医院");
        jiuzhouJob.setLogisticsFee(0.0);
        HospitalReconciliationRow ht = new HospitalReconciliationRow();
        ht.setType("器械包");
        ht.setPackName("高温包");
        ht.setCorrectedTotalPrice(1000.0);
        HospitalReconciliationRow lt = new HospitalReconciliationRow();
        lt.setType("器械包（低温等离子）");
        lt.setPackName("低温包");
        lt.setCorrectedTotalPrice(200.0);
        List<SettlementTemplateFiller.SettlementFeeRow> jiuzhouRows =
                filler.buildFeeRows(jiuzhouJob, 1200.0, jiuzhou, List.of(ht, lt));
        assertThat(jiuzhouRows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains("高温灭菌费用", "低温灭菌费用")
                .doesNotContain("敷料");
        assertThat(amountOf(jiuzhouRows, "高温灭菌费用")).isEqualTo(500.0);
        assertThat(amountOf(jiuzhouRows, "低温灭菌费用")).isEqualTo(140.0);
        assertThat(remarkOf(jiuzhouRows, "高温灭菌费用")).isEqualTo("高温5折优惠");
        assertThat(remarkOf(jiuzhouRows, "低温灭菌费用")).isEqualTo("低温7折优惠");

        ObjectNode rensheng = compiler.compileForCustomer("RENSHENG");
        BillingPolicyApplier.BillDetailDiscount discount = BillingPolicyApplier.applySettlementDiscount(
                rensheng, "", "", "", "哈尔滨仁胜医院", 784.0);
        assertThat(discount.price()).isEqualTo(627.2);
        assertThat(discount.note()).isEqualTo("8折优惠");
        boolean hasCard = false;
        for (JsonNode policy : rensheng.path("billingPolicies")) {
            if ("LOGISTICS".equals(policy.path("policyType").asText())
                    && policy.path("params").path("useLogisticsCard").asBoolean()) {
                hasCard = true;
            }
        }
        assertThat(hasCard).isTrue();
    }

    private static double ruleParam(JsonNode baseline, String ruleType, String field) {
        for (JsonNode rule : baseline.path("rules")) {
            if (ruleType.equals(rule.path("ruleType").asText()) && rule.path("params").has(field)) {
                return rule.path("params").path(field).asDouble();
            }
        }
        throw new AssertionError("missing " + ruleType + " " + field);
    }

    private static List<Double> logisticsNumbers(ObjectNode compiled) {
        List<Double> numbers = new java.util.ArrayList<>();
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if (!"LOGISTICS".equals(policy.path("policyType").asText())) {
                continue;
            }
            if (policy.path("params").has("feePerTrip")) {
                numbers.add(policy.path("params").path("feePerTrip").asDouble());
            }
            if (policy.path("params").has("urgentAmount")) {
                numbers.add(policy.path("params").path("urgentAmount").asDouble());
            }
        }
        return numbers;
    }

    private static double amountOf(List<SettlementTemplateFiller.SettlementFeeRow> rows, String name) {
        return rows.stream()
                .filter(row -> name.equals(row.getItemName()))
                .mapToDouble(SettlementTemplateFiller.SettlementFeeRow::getAmount)
                .findFirst()
                .orElseThrow();
    }

    private static String remarkOf(List<SettlementTemplateFiller.SettlementFeeRow> rows, String name) {
        return rows.stream()
                .filter(row -> name.equals(row.getItemName()))
                .map(SettlementTemplateFiller.SettlementFeeRow::getRemark)
                .findFirst()
                .orElseThrow();
    }

    private void assertSettlementSterilizeRate(String code, String hospitalName, double rate, double discounted) {
        ObjectNode compiled = compiler.compileForCustomer(code);
        assertThat(compiled).isNotNull();
        JsonNode discount = null;
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if ("DISCOUNT".equals(policy.path("policyType").asText())) {
                discount = policy;
            }
        }
        assertThat(discount).isNotNull();
        assertThat(discount.path("params").path("rate").asDouble()).isEqualTo(rate);
        assertThat(discount.path("params").path("applyStage").asText()).isEqualTo("settlement_only");
        assertThat(discount.path("params").path("target").asText()).isEqualTo("sterilize");
        assertThat(BillingPolicyApplier.applySettlementDiscount(
                compiled, "高温", "治疗包", "无纺布", hospitalName, 1000).price())
                .isEqualTo(discounted);
        assertThat(BillingPolicyApplier.applyBillDetailDiscounts(
                compiled, "高温", "治疗包", "无纺布", hospitalName, 100, 1, false, false))
                .isNull();
    }

    private static JsonNode monthlyPolicy(ObjectNode compiled) {
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if ("MONTHLY_SETTLEMENT".equals(policy.path("policyType").asText())) {
                return policy;
            }
        }
        throw new AssertionError("missing MONTHLY_SETTLEMENT policy");
    }

    private static double logisticsParam(ObjectNode compiled, String field) {
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if ("LOGISTICS".equals(policy.path("policyType").asText())) {
                return policy.path("params").path(field).asDouble();
            }
        }
        throw new AssertionError("missing LOGISTICS policy");
    }

    private static boolean hasDiscountPolicy(ObjectNode compiled) {
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if ("DISCOUNT".equals(policy.path("policyType").asText())) {
                return true;
            }
        }
        return false;
    }
}
