package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PricingEngineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PricingEngine engine = new PricingEngine(defaultRules());

    @Test
    void pricesNongdaScalerTipByInstrumentCount() {
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "东北农业大学医院",
                "额外包(纸塑袋)",
                "洁牙机尖-4/Z7526",
                "高温纸塑袋75*200",
                4,
                1,
                22,
                22
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(22.0);
        assertThat(result.correctedTotalPrice).isEqualTo(22.0);
        assertThat(result.status).isEqualTo("unchanged");
    }

    @Test
    void pricesHangtianFenghuaSpoonByInstrumentCount() {
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "哈尔滨航天风华医院",
                "额外包(纸塑袋)",
                "挖勺-2/z7530",
                "高温纸塑袋75*300",
                8,
                4,
                13.5,
                54
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(11.0);
        assertThat(result.correctedTotalPrice).isEqualTo(44.0);
        assertThat(result.status).isEqualTo("warning");
    }

    @Test
    void foldsSongdianMachineExpansionNeedles() {
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "哈尔滨道外区松电慢性病专科门诊部",
                "额外包(纸塑袋)",
                "机扩针-20/Z7520",
                "高温纸塑袋75*200",
                20,
                1,
                22,
                22
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(22.0);
        assertThat(result.correctedTotalPrice).isEqualTo(22.0);
        assertThat(result.status).isEqualTo("unchanged");
    }

    @Test
    void extraFeesConfigIsLoaded() {
        JsonNode rules = defaultRules();
        JsonNode feeRule = rules.path("specialRules").path("extraFees").get(0);
        assertThat(rules.path("specialRules").path("extraFees").isArray()).isTrue();
        assertThat(rules.path("specialRules").path("extraFees")).hasSize(1);
        assertThat(feeRule.path("name").asText()).isEqualTo("镜头租借公司筐加收");
        assertThat(feeRule.path("fee").asDouble()).isEqualTo(8.0);
        assertThat(feeRule.path("keywords").get(0).asText()).isEqualTo("镜头");
    }

    @Test
    void addsLaborUnionLensBasketFee() throws Exception {
        ObjectNode rules = MAPPER.createObjectNode();
        ObjectNode specialRules = rules.putObject("specialRules");
        ArrayNode extraFees = specialRules.putArray("extraFees");
        ObjectNode feeRule = extraFees.addObject();
        feeRule.put("name", "镜头租借公司筐加收");
        feeRule.put("fee", 8.0);
        feeRule.putArray("keywords").add("镜头");
        feeRule.putArray("hospitals").add("黑龙江总工会医院");
        specialRules.putArray("fixedPrices");
        specialRules.putArray("foldRules");

        ObjectNode ltPaper = rules.putObject("lowTemperature").putObject("paperPlastic");
        ArrayNode bagSizes = ltPaper.putArray("bagSizes");
        ObjectNode bag20 = bagSizes.addObject();
        bag20.put("size", 20);
        bag20.put("price", 28);
        bag20.putArray("keywords").add("20");
        ArrayNode tiers = ltPaper.putArray("tierPrices");
        ObjectNode tier5 = tiers.addObject();
        tier5.put("count", 5);
        tier5.put("price", 88);
        ltPaper.put("remainderPerPiecePrice", 22);
        rules.putObject("cleaning").put("recomputeTotalsWhenPriceChanges", true);
        ObjectNode needle = rules.putObject("needle");
        needle.put("threshold", 5);
        needle.put("foldRatio", 5);
        needle.putArray("keywords");
        rules.putObject("packaging").put("enabled", false);

        PricingEngine feeEngine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = feeEngine.processRow(row(
                "黑龙江总工会医院",
                "单包装包(老肯低温)",
                "30°镜头，镜鞘-2（带转换帽）/Z2060",
                "低温纸塑袋200*600",
                4,
                2,
                52,
                104
        ));

        // 低温阶梯基础价 2件×22=44，加收 8 元后单价 52
        assertThat(result.expectedUnitPrice).isEqualTo(52.0);
        assertThat(result.correctedTotalPrice).isEqualTo(104.0);
        assertThat(result.notes).anyMatch(note -> note.contains("镜头"));
    }

    @Test
    void subtractsLowTemperatureBoxAfterPackCountNormalization() {
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "任意医院",
                "单包装包(老肯低温)",
                "30度镜头-1（盒1）/Z2060",
                "低温纸塑袋200*600",
                4,
                2,
                28,
                56
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(28.0);
        assertThat(result.correctedTotalPrice).isEqualTo(56.0);
        assertThat(result.notes).anyMatch(note -> note.contains("单包件数减 1 件"));
    }

    @Test
    void discountsSecondHospitalNangangByBaseRulePrice() {
        PricingEngine discountEngine = engineWithDiscount("黑龙江省第二医院（南岗区）", 0.7);
        PricingEngine.ProcessedResult result = discountEngine.processRow(row(
                "黑龙江省第二医院（南岗区）",
                "额外包(纸塑袋)",
                "普通器械-4/Z7526",
                "高温纸塑袋20cm",
                4,
                1,
                15.4,
                15.4
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(15.4);
        assertThat(result.correctedTotalPrice).isEqualTo(15.4);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.notes).anyMatch(note -> note.contains("0.7"));
    }

    @Test
    void doesNotDiscountSecondHospitalWhenNameIsNotExact() {
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "黑龙江省第二医院(南岗区)",
                "额外包(纸塑袋)",
                "普通器械-4/Z7526",
                "高温纸塑袋20cm",
                4,
                1,
                22,
                22
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(22.0);
        assertThat(result.correctedTotalPrice).isEqualTo(22.0);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.notes).noneMatch(note -> note.contains("0.7"));
    }

    @Test
    void discountsSecondHospitalSongbeiByBaseRulePrice() {
        PricingEngine discountEngine = engineWithDiscount("黑龙江省第二医院（松北区）", 0.7);
        PricingEngine.ProcessedResult result = discountEngine.processRow(row(
                "黑龙江省第二医院（松北区）",
                "额外包(纸塑袋)",
                "普通器械-4/Z7526",
                "高温纸塑袋20cm",
                4,
                1,
                15.4,
                15.4
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(15.4);
        assertThat(result.correctedTotalPrice).isEqualTo(15.4);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.notes).anyMatch(note -> note.contains("0.7"));
    }

    @Test
    void doesNotDiscountSecondHospitalSongbeiWhenNameIsNotExact() {
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "黑龙江省第二医院(松北区)",
                "额外包(纸塑袋)",
                "普通器械-4/Z7526",
                "高温纸塑袋20cm",
                4,
                1,
                22,
                22
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(22.0);
        assertThat(result.correctedTotalPrice).isEqualTo(22.0);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.notes).noneMatch(note -> note.contains("0.7"));
    }

    @Test
    void discountsHulanFirstPeopleHospitalByBaseRulePrice() {
        PricingEngine discountEngine = engineWithDiscount("呼兰区第一人民医院", 0.7);
        PricingEngine.ProcessedResult result = discountEngine.processRow(row(
                "呼兰区第一人民医院",
                "额外包(纸塑袋)",
                "普通器械-4/Z7526",
                "高温纸塑袋20cm",
                4,
                1,
                22,
                22
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(15.4);
        assertThat(result.correctedTotalPrice).isEqualTo(15.4);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.notes).anyMatch(note -> note.contains("0.7"));
    }

    @Test
    void doesNotDiscountHulanHospitalWhenNameDiffers() {
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "呼兰区第一人民医院 ",
                "额外包(纸塑袋)",
                "普通器械-4/Z7526",
                "高温纸塑袋20cm",
                4,
                1,
                22,
                22
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(22.0);
        assertThat(result.correctedTotalPrice).isEqualTo(22.0);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.notes).noneMatch(note -> note.contains("0.7"));
    }

    @Test
    void pricesSecondHospitalNangangSpecialFixedItemsWithoutDiscount() {
        assertSecondHospitalFixedPrice("钉", 140.0);
        assertSecondHospitalFixedPrice("软镜", 210.0);
        assertSecondHospitalFixedPrice("3.6空心钉", 13.3);
        assertSecondHospitalFixedPrice("7.3空心钉", 13.3);
        assertSecondHospitalFixedPrice("泌尿显微镜头", 210.0);
        assertSecondHospitalFixedPrice("小腔包", 49.7);
        assertSecondHospitalFixedPrice("手术衣", "高温无纺布", 26.6);
        assertSecondHospitalFixedPrice("手术衣", "高温纸塑袋20cm", 28.0);
        assertSecondHospitalFixedPrice("3.6空心钉工具包", 205.45);
    }

    @Test
    void pricesSecondHospitalSongbeiSpecialFixedItemsWithoutDiscount() {
        assertSecondHospitalFixedPrice("黑龙江省第二医院（松北区）", "钉", "高温纸塑袋20cm", 35.0);
        assertSecondHospitalFixedPrice("黑龙江省第二医院（松北区）", "软镜", "高温纸塑袋20cm", 210.0);
        assertSecondHospitalFixedPrice("黑龙江省第二医院（松北区）", "3.6空心钉", "高温纸塑袋20cm", 13.3);
        assertSecondHospitalFixedPrice("黑龙江省第二医院（松北区）", "7.3空心钉", "高温纸塑袋20cm", 13.3);
        assertSecondHospitalFixedPrice("黑龙江省第二医院（松北区）", "泌尿显微镜头", "高温纸塑袋20cm", 210.0);
        assertSecondHospitalFixedPrice("黑龙江省第二医院（松北区）", "小腔包", "高温纸塑袋20cm", 53.55);
        assertSecondHospitalFixedPrice("黑龙江省第二医院（松北区）", "手术衣", "高温无纺布", 26.6);
        assertSecondHospitalFixedPrice("黑龙江省第二医院（松北区）", "手术衣", "高温纸塑袋20cm", 28.0);
        assertSecondHospitalFixedPrice("黑龙江省第二医院（松北区）", "3.6空心钉工具包", "高温纸塑袋20cm", 190.05);
    }

    private PricingEngine engineWithDiscount(String hospitalName, double rate) {
        Map<String, Object> rules = new LinkedHashMap<>(DefaultPricingTemplate.buildRulesMap());
        rules.put("specialRules", MAPPER.convertValue(defaultRules().get("specialRules"), Map.class));
        rules.put("customerOverrides", Map.of(
                "discountRate", rate,
                "displayName", hospitalName,
                "discountLabel", "测试折扣"
        ));
        return new PricingEngine(MAPPER.valueToTree(rules));
    }

    private static Map<String, Object> row(
            String hospitalName,
            String type,
            String packName,
            String packageMaterial,
            int instrumentCount,
            int packCount,
            double unitPrice,
            double totalPrice
    ) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", hospitalName);
        row.put("type", type);
        row.put("packName", packName);
        row.put("packageMaterial", packageMaterial);
        row.put("instrumentCount", instrumentCount);
        row.put("packCount", packCount);
        row.put("unitPrice", unitPrice);
        row.put("totalPrice", totalPrice);
        return row;
    }

    private void assertSecondHospitalFixedPrice(String itemName, double expectedPrice) {
        assertSecondHospitalFixedPrice(itemName, "高温纸塑袋20cm", expectedPrice);
    }

    private void assertSecondHospitalFixedPrice(String itemName, String packageMaterial, double expectedPrice) {
        assertSecondHospitalFixedPrice("黑龙江省第二医院（南岗区）", itemName, packageMaterial, expectedPrice);
    }

    private void assertSecondHospitalFixedPrice(String hospitalName, String itemName, String packageMaterial, double expectedPrice) {
        PricingEngine.ProcessedResult result = engine.processRow(row(
                hospitalName,
                "额外包(纸塑袋)",
                itemName + "/Z7526",
                packageMaterial,
                1,
                1,
                expectedPrice,
                expectedPrice
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(expectedPrice);
        assertThat(result.correctedTotalPrice).isEqualTo(expectedPrice);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.notes).anyMatch(note -> note.contains(itemName));
        assertThat(result.notes).noneMatch(note -> note.contains("× 0.7"));
    }

    private static JsonNode defaultRules() {
        Map<String, Object> rules = new LinkedHashMap<>(DefaultPricingTemplate.buildRulesMap());

        List<Map<String, Object>> fixedPrices = new ArrayList<>();
        fixedPrices.add(fixedPrice("东北农业大学医院洁牙机尖每件 5.5 元",
                List.of("东北农业大学医院"), List.of("洁牙机尖"), 5.5, true, false, true));
        fixedPrices.add(fixedPrice("航天风华挖勺每件 5.5 元",
                List.of("哈尔滨航天风华医院"), List.of("挖勺"), 5.5, true, false, true));
        fixedPrices.add(fixedPrice("显著医生集团 30cm 棉球固定单价",
                List.of("显著医生集团中西医结合门诊"), List.of("棉球"), 4.0, false, true, true));

        String ng = "黑龙江省第二医院（南岗区）";
        fixedPrices.add(fixedPrice("省二南岗3.6空心钉工具包", List.of(ng), List.of("3.6空心钉工具包"), 205.45, false, false, true));
        fixedPrices.add(fixedPrice("省二南岗3.6空心钉", List.of(ng), List.of("3.6空心钉"), 13.3, false, false, true));
        fixedPrices.add(fixedPrice("省二南岗7.3空心钉", List.of(ng), List.of("7.3空心钉"), 13.3, false, false, true));
        fixedPrices.add(fixedPrice("省二南岗手术衣无纺布", List.of(ng), List.of("手术衣"), 26.6, false, false, true, List.of("无纺布")));
        fixedPrices.add(fixedPrice("省二南岗手术衣纸塑袋", List.of(ng), List.of("手术衣"), 28.0, false, false, true, List.of("纸塑袋")));
        fixedPrices.add(fixedPrice("省二南岗钉", List.of(ng), List.of("钉"), 140.0, false, false, true));
        fixedPrices.add(fixedPrice("省二南岗软镜", List.of(ng), List.of("软镜"), 210.0, false, false, true));
        fixedPrices.add(fixedPrice("省二南岗泌尿显微镜头", List.of(ng), List.of("泌尿显微镜头"), 210.0, false, false, true));
        fixedPrices.add(fixedPrice("省二南岗小腔包", List.of(ng), List.of("小腔包"), 49.7, false, false, true));

        String sb = "黑龙江省第二医院（松北区）";
        fixedPrices.add(fixedPrice("省二松北3.6空心钉工具包", List.of(sb), List.of("3.6空心钉工具包"), 190.05, false, false, true));
        fixedPrices.add(fixedPrice("省二松北3.6空心钉", List.of(sb), List.of("3.6空心钉"), 13.3, false, false, true));
        fixedPrices.add(fixedPrice("省二松北7.3空心钉", List.of(sb), List.of("7.3空心钉"), 13.3, false, false, true));
        fixedPrices.add(fixedPrice("省二松北手术衣无纺布", List.of(sb), List.of("手术衣"), 26.6, false, false, true, List.of("无纺布")));
        fixedPrices.add(fixedPrice("省二松北手术衣纸塑袋", List.of(sb), List.of("手术衣"), 28.0, false, false, true, List.of("纸塑袋")));
        fixedPrices.add(fixedPrice("省二松北钉", List.of(sb), List.of("钉"), 35.0, false, false, true));
        fixedPrices.add(fixedPrice("省二松北软镜", List.of(sb), List.of("软镜"), 210.0, false, false, true));
        fixedPrices.add(fixedPrice("省二松北泌尿显微镜头", List.of(sb), List.of("泌尿显微镜头"), 210.0, false, false, true));
        fixedPrices.add(fixedPrice("省二松北小腔包", List.of(sb), List.of("小腔包"), 53.55, false, false, true));

        List<Map<String, Object>> foldRules = List.of(
                Map.of("name", "松电机扩针 5 件算 1 件",
                        "hospitals", List.of("哈尔滨道外区松电慢性病专科门诊部"),
                        "keywords", List.of("机扩针"), "threshold", 5, "foldRatio", 5)
        );

        List<Map<String, Object>> extraFees = List.of(
                Map.of("name", "镜头租借公司筐加收",
                        "hospitals", List.of("黑龙江总工会医院"),
                        "keywords", List.of("镜头"), "fee", 8.0)
        );

        rules.put("specialRules", Map.of(
                "fixedPrices", fixedPrices,
                "foldRules", foldRules,
                "extraFees", extraFees
        ));
        return MAPPER.valueToTree(rules);
    }

    private static JsonNode rulesWithExtraFee() {
        Map<String, Object> rules = new LinkedHashMap<>(DefaultPricingTemplate.buildRulesMap());
        rules.put("specialRules", Map.of(
                "fixedPrices", List.of(),
                "foldRules", List.of(),
                "extraFees", List.of(
                        Map.of("name", "镜头租借公司筐加收",
                                "hospitals", List.of("黑龙江总工会医院"),
                                "keywords", List.of("镜头"),
                                "fee", 8.0)
                )
        ));
        rules.put("cleaning", Map.of("recomputeTotalsWhenPriceChanges", true));
        return MAPPER.valueToTree(rules);
    }

    private static Map<String, Object> fixedPrice(String name, List<String> hospitals, List<String> keywords,
                                                  double price, boolean pricePerInstrument,
                                                  boolean bagSizeEquals30, boolean skipHospitalDiscount) {
        return fixedPrice(name, hospitals, keywords, price, pricePerInstrument, bagSizeEquals30, skipHospitalDiscount, null);
    }

    private static Map<String, Object> fixedPrice(String name, List<String> hospitals, List<String> keywords,
                                                  double price, boolean pricePerInstrument,
                                                  boolean bagSizeEquals30, boolean skipHospitalDiscount,
                                                  List<String> materials) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", name);
        rule.put("hospitals", hospitals);
        rule.put("keywords", keywords);
        rule.put("price", price);
        rule.put("skipPackaging", true);
        if (materials != null && !materials.isEmpty()) {
            rule.put("materials", materials);
        }
        if (pricePerInstrument) {
            rule.put("pricePerInstrument", true);
        }
        if (bagSizeEquals30) {
            rule.put("bagSizeEquals", 30);
        }
        if (skipHospitalDiscount) {
            rule.put("skipHospitalDiscount", true);
        }
        return rule;
    }

    @Test
    void excludeKeywordsPreventFixedPriceMatch() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode nailRule = fixedPrices.addObject();
        nailRule.put("name", "xx钉");
        nailRule.put("price", 200.0);
        nailRule.put("skipPackaging", true);
        nailRule.putArray("hospitals").add("省二院");
        nailRule.putArray("keywords").add("钉");
        nailRule.putArray("excludeKeywords").add("空心钉");

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult hollow = engine.processRow(row(
                "省二院", "额外包(纸塑袋)", "3.6空心钉-2", "高温纸塑袋75*200",
                2, 1, 19, 19));
        assertThat(hollow.notes).noneMatch(n -> n.contains("xx钉"));

        PricingEngine.ProcessedResult normal = engine.processRow(row(
                "省二院", "额外包(纸塑袋)", "xx钉工具", "高温纸塑袋75*200",
                2, 1, 200, 200));
        assertThat(normal.expectedUnitPrice).isEqualTo(200.0);
        assertThat(normal.notes).anyMatch(n -> n.contains("xx钉"));
    }

    @Test
    void anyPriceModeAcceptsEitherCandidate() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode cavityRule = fixedPrices.addObject();
        cavityRule.put("ruleId", 100L);
        cavityRule.put("name", "小腔包");
        cavityRule.put("price", 71.0);
        cavityRule.put("matchMode", "any_price");
        cavityRule.put("skipPackaging", true);
        cavityRule.putArray("hospitals").add("省二院");
        cavityRule.putArray("keywords").add("小腔包");
        cavityRule.putArray("acceptedPrices").add(71.0).add(76.5);

        PricingEngine engine = new PricingEngine(rules);

        PricingEngine.ProcessedResult lower = engine.processRow(row(
                "省二院", "额外包(纸塑袋)", "小腔包A", "高温纸塑袋75*200",
                1, 1, 71, 71));
        assertThat(lower.status).isEqualTo("unchanged");
        assertThat(lower.matchedRuleId).isEqualTo(100L);
        assertThat(lower.matchedPriceOption).isEqualTo(71.0);
        assertThat(lower.notes).anyMatch(n -> n.contains("多报价命中"));
        assertThat(lower.billingNotes).isNotNull();
        assertThat(lower.billingNotes.get("type")).isEqualTo("any_price_match");
        assertThat(lower.billingNotes.get("matchedRuleId")).isEqualTo(100L);
        assertThat(lower.billingNotes.get("matched_rule_id")).isEqualTo(100L);
        assertThat(lower.billingNotes.get("matchedPrice")).isEqualTo(71.0);
        assertThat(lower.billingNotes.get("candidatePrices")).isEqualTo(List.of(71.0, 76.5));
        assertThat(lower.billingNotes.get("candidates")).isEqualTo(List.of(71.0, 76.5));

        PricingEngine.ProcessedResult upper = engine.processRow(row(
                "省二院", "额外包(纸塑袋)", "小腔包B", "高温纸塑袋75*200",
                1, 1, 76.5, 76.5));
        assertThat(upper.status).isEqualTo("unchanged");
        assertThat(upper.matchedPriceOption).isEqualTo(76.5);
        assertThat(upper.billingNotes.get("type")).isEqualTo("any_price_match");
        assertThat(upper.billingNotes.get("matchedPriceOption")).isEqualTo(76.5);
    }

    @Test
    void anyPriceMismatchPopulatesCandidatePrices() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode cavityRule = fixedPrices.addObject();
        cavityRule.put("ruleId", 101L);
        cavityRule.put("name", "小腔包");
        cavityRule.put("price", 71.0);
        cavityRule.put("matchMode", "any_price");
        cavityRule.put("skipPackaging", true);
        cavityRule.putArray("hospitals").add("省二院");
        cavityRule.putArray("keywords").add("小腔包");
        cavityRule.putArray("acceptedPrices").add(71.0).add(76.5);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult mismatch = engine.processRow(row(
                "省二院", "额外包(纸塑袋)", "小腔包C", "高温纸塑袋75*200",
                1, 1, 80, 80));

        assertThat(mismatch.status).isEqualTo("warning");
        assertThat(mismatch.matchedRuleId).isEqualTo(101L);
        assertThat(mismatch.matchedPriceOption).isNull();
        assertThat(mismatch.notes).anyMatch(n -> n.contains("多报价未命中"));
        assertThat(mismatch.billingNotes).isNotNull();
        assertThat(mismatch.billingNotes.get("type")).isEqualTo("any_price_mismatch");
        assertThat(mismatch.billingNotes.get("candidatePrices")).isEqualTo(List.of(71.0, 76.5));
        assertThat(mismatch.billingNotes.get("billUnitPrice")).isEqualTo(80.0);
        assertThat(mismatch.billingNotes.get("matchedPrice")).isNull();
    }

    @Test
    void anyPriceMatchIncludesDiscountChainWhenDiscountApplied() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        specialRules.set("fixedPrices", MAPPER.createArrayNode());
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode cavityRule = fixedPrices.addObject();
        cavityRule.put("ruleId", 102L);
        cavityRule.put("name", "小腔包");
        cavityRule.put("price", 100.0);
        cavityRule.put("matchMode", "any_price");
        cavityRule.put("skipPackaging", true);
        cavityRule.putArray("hospitals").add("省二院");
        cavityRule.putArray("keywords").add("小腔包");
        cavityRule.putArray("acceptedPrices").add(100.0).add(110.0);

        ArrayNode billingPolicies = rules.putArray("billingPolicies");
        ObjectNode discountPolicy = billingPolicies.addObject();
        discountPolicy.put("policyType", "DISCOUNT");
        discountPolicy.put("name", "高温5折");
        discountPolicy.put("priority", 10);
        ObjectNode scope = discountPolicy.putObject("scope");
        scope.put("temperature", "HT");
        ObjectNode params = discountPolicy.putObject("params");
        params.put("rate", 0.5);
        params.put("skipWhenFixedPrice", false);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "省二院", "额外包(纸塑袋)", "小腔包D", "高温纸塑袋75*200",
                1, 1, 100, 100));

        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.billingNotes).isNotNull();
        assertThat(result.billingNotes.get("type")).isEqualTo("any_price_match");
        assertThat(result.billingNotes.get("discountChain")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chain = (List<Map<String, Object>>) result.billingNotes.get("discountChain");
        assertThat(chain).isNotEmpty();
        assertThat(chain.get(0).get("label")).asString().contains("5折");
    }

    @Test
    void temperatureScopedFixedPriceMatchesOnlyLowTempRows() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode mirrorRule = fixedPrices.addObject();
        mirrorRule.put("name", "等离子镜低温");
        mirrorRule.put("price", 36.0);
        mirrorRule.put("temperature", "LT");
        mirrorRule.put("skipPackaging", true);
        mirrorRule.putArray("hospitals").add("中医三院");
        mirrorRule.putArray("keywords").add("等离子镜");

        ObjectNode boxRule = fixedPrices.addObject();
        boxRule.put("name", "小件盒高温");
        boxRule.put("price", 25.0);
        boxRule.put("temperature", "HT");
        boxRule.put("skipPackaging", true);
        boxRule.putArray("hospitals").add("中医三院");
        boxRule.putArray("keywords").add("小件盒");

        PricingEngine engine = new PricingEngine(rules);

        PricingEngine.ProcessedResult ltResult = engine.processRow(row(
                "中医三院",
                "单包装包(老肯低温)",
                "等离子镜-1/Z7526",
                "低温纸塑袋200*600",
                1, 1, 36, 36));
        assertThat(ltResult.expectedUnitPrice).isEqualTo(36.0);
        assertThat(ltResult.notes).anyMatch(n -> n.contains("等离子镜低温"));

        PricingEngine.ProcessedResult htResult = engine.processRow(row(
                "中医三院",
                "额外包(纸塑袋)",
                "小件盒-1/Z7526",
                "高温纸塑袋20cm",
                1, 1, 25, 25));
        assertThat(htResult.expectedUnitPrice).isEqualTo(25.0);
        assertThat(htResult.notes).anyMatch(n -> n.contains("小件盒高温"));
    }

    @Test
    void scopedDiscountAppliesDifferentRatesByTemperature() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ArrayNode billingPolicies = rules.putArray("billingPolicies");

        ObjectNode htPolicy = billingPolicies.addObject();
        htPolicy.put("policyType", "DISCOUNT");
        htPolicy.put("name", "高温5折");
        htPolicy.put("priority", 10);
        ObjectNode htScope = htPolicy.putObject("scope");
        htScope.put("temperature", "HT");
        ObjectNode htParams = htPolicy.putObject("params");
        htParams.put("rate", 0.5);
        htParams.put("skipWhenFixedPrice", true);

        ObjectNode ltPolicy = billingPolicies.addObject();
        ltPolicy.put("policyType", "DISCOUNT");
        ltPolicy.put("name", "低温7折");
        ltPolicy.put("priority", 20);
        ObjectNode ltScope = ltPolicy.putObject("scope");
        ltScope.put("temperature", "LT");
        ObjectNode ltParams = ltPolicy.putObject("params");
        ltParams.put("rate", 0.7);
        ltParams.put("skipWhenFixedPrice", true);

        PricingEngine engine = new PricingEngine(rules);

        PricingEngine.ProcessedResult htResult = engine.processRow(row(
                "维多利亚医院",
                "额外包(纸塑袋)",
                "普通器械-4/Z7526",
                "高温纸塑袋20cm",
                4, 1, 11, 11));
        assertThat(htResult.expectedUnitPrice).isEqualTo(11.0);
        assertThat(htResult.notes).anyMatch(n -> n.contains("5折") || n.contains("0.5"));

        PricingEngine.ProcessedResult ltResult = engine.processRow(row(
                "维多利亚医院",
                "单包装包(老肯低温)",
                "普通器械-1/Z7526",
                "低温纸塑袋200*600",
                1, 1, 19.6, 19.6));
        assertThat(ltResult.expectedUnitPrice).isEqualTo(19.6);
        assertThat(ltResult.notes).anyMatch(n -> n.contains("7折") || n.contains("0.7"));
    }

    @Test
    void pathOverrideDisablesLowTempAndForcesHighTempPerItemPrice() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        ObjectNode pathOverride = billingProfile.putObject("pathOverride");
        pathOverride.put("disableLowTemp", true);
        pathOverride.put("forceHighTempUnitPrice", 3.0);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "道外人民",
                "单包装包(老肯低温)",
                "普通器械-4/Z7526",
                "低温纸塑袋200*600",
                4, 1, 12, 12));

        assertThat(result.expectedUnitPrice).isEqualTo(12.0);
        assertThat(result.notes).anyMatch(n -> n.contains("路径覆盖"));
    }

    @Test
    void pathOverrideKeepsDressingPackPricing() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        ObjectNode pathOverride = billingProfile.putObject("pathOverride");
        pathOverride.put("disableLowTemp", true);
        pathOverride.put("forceHighTempUnitPrice", 3.0);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "道外人民",
                "敷料包(无纺布包)",
                "敷料包0.6",
                "无纺布0.6m",
                1, 1, 25, 25));

        assertThat(result.expectedUnitPrice).isEqualTo(25.0);
        assertThat(result.pricingRule).contains("敷料包");
    }

    @Test
    void specialOnlyModeSkipsBaseTierWithoutForcePrice() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("pricingMode", "special_only");

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "某院",
                "额外包(纸塑袋)",
                "普通器械-4/Z7526",
                "高温纸塑袋20cm",
                4, 1, 22, 22));

        assertThat(result.pricingRule).contains("special_only");
        assertThat(result.notes).anyMatch(n -> n.contains("仅特色规则"));
    }

    @ParameterizedTest(name = "golden row: {0}")
    @MethodSource("goldenRowCases")
    void goldenRowsRegression(String caseId, JsonNode caseNode) {
        JsonNode rules = buildRulesForGoldenCase(caseNode);
        PricingEngine caseEngine = new PricingEngine(rules);
        Map<String, Object> input = goldenRowInput(caseNode.path("input"));
        PricingEngine.ProcessedResult result = caseEngine.processRow(input);

        JsonNode expected = caseNode.path("expected");
        if (expected.has("expectedUnitPrice")) {
            assertThat(result.expectedUnitPrice).isEqualTo(expected.path("expectedUnitPrice").asDouble());
        }
        if (expected.has("correctedTotalPrice")) {
            assertThat(result.correctedTotalPrice).isEqualTo(expected.path("correctedTotalPrice").asDouble());
        }
        if (expected.has("status")) {
            assertThat(result.status).isEqualTo(expected.path("status").asText());
        }
        if (expected.has("matchedPriceOption") && !expected.path("matchedPriceOption").isNull()) {
            assertThat(result.matchedPriceOption).isEqualTo(expected.path("matchedPriceOption").asDouble());
        }
        if (expected.has("matchedRuleId") && !expected.path("matchedRuleId").isNull()) {
            assertThat(result.matchedRuleId).isEqualTo(expected.path("matchedRuleId").asLong());
        }
        assertGoldenBillingNotes(result.billingNotes, expected.path("billingNotes"));
        assertGoldenNotes(result.notes, expected.path("notesContains"), true);
        assertGoldenNotes(result.notes, expected.path("notesNotContains"), false);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> goldenRowCases() throws Exception {
        InputStream stream = PricingEngineTest.class.getResourceAsStream("/hospital-billing-golden-rows.json");
        assertThat(stream).as("hospital-billing-golden-rows.json").isNotNull();
        JsonNode root = MAPPER.readTree(stream);
        Iterator<JsonNode> cases = root.path("cases").elements();
        List<org.junit.jupiter.params.provider.Arguments> args = new ArrayList<>();
        while (cases.hasNext()) {
            JsonNode caseNode = cases.next();
            args.add(org.junit.jupiter.params.provider.Arguments.of(
                    caseNode.path("id").asText("unnamed"), caseNode));
        }
        return args.stream();
    }

    private static JsonNode buildRulesForGoldenCase(JsonNode caseNode) {
        ObjectNode rules = (ObjectNode) defaultRules().deepCopy();
        JsonNode overlay = caseNode.path("rulesOverlay");
        if (overlay.isMissingNode() || overlay.isEmpty()) {
            return rules;
        }
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        mergeGoldenArrayOverlay(specialRules, "fixedPrices", overlay.path("fixedPrices"));
        mergeGoldenArrayOverlay(specialRules, "foldRules", overlay.path("foldRules"));
        mergeGoldenArrayOverlay(specialRules, "extraFees", overlay.path("extraFees"));
        mergeGoldenArrayOverlay(specialRules, "priceMultipliers", overlay.path("priceMultipliers"));
        if (overlay.has("billingPolicies")) {
            rules.set("billingPolicies", overlay.path("billingPolicies").deepCopy());
        }
        if (overlay.has("billingProfile")) {
            rules.set("billingProfile", overlay.path("billingProfile").deepCopy());
        }
        return rules;
    }

    private static void mergeGoldenArrayOverlay(ObjectNode specialRules, String field, JsonNode additions) {
        if (!additions.isArray() || additions.isEmpty()) {
            return;
        }
        ArrayNode target = specialRules.withArray(field);
        additions.forEach(target::add);
    }

    private static Map<String, Object> goldenRowInput(JsonNode input) {
        Map<String, Object> row = new HashMap<>();
        input.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isInt()) {
                row.put(entry.getKey(), value.asInt());
            } else if (value.isFloatingPointNumber()) {
                row.put(entry.getKey(), value.asDouble());
            } else {
                row.put(entry.getKey(), value.asText());
            }
        });
        return row;
    }

    private static void assertGoldenBillingNotes(Map<String, Object> billingNotes, JsonNode expected) {
        if (expected.isMissingNode() || expected.isEmpty()) {
            return;
        }
        assertThat(billingNotes).isNotNull();
        if (expected.has("type")) {
            assertThat(billingNotes.get("type")).isEqualTo(expected.path("type").asText());
        }
        if (expected.has("matchedRuleId") && !expected.path("matchedRuleId").isNull()) {
            assertThat(billingNotes.get("matchedRuleId")).isEqualTo(expected.path("matchedRuleId").asLong());
        }
        if (expected.has("matchedPrice") && !expected.path("matchedPrice").isNull()) {
            assertThat(billingNotes.get("matchedPrice")).isEqualTo(expected.path("matchedPrice").asDouble());
        }
        if (expected.has("candidatePrices")) {
            assertThat(billingNotes.get("candidatePrices")).isEqualTo(
                    parseGoldenPriceList(expected.path("candidatePrices")));
        }
    }

    private static List<Double> parseGoldenPriceList(JsonNode array) {
        List<Double> prices = new ArrayList<>();
        if (!array.isArray()) {
            return prices;
        }
        array.forEach(node -> prices.add(node.asDouble()));
        return prices;
    }

    private static void assertGoldenNotes(List<String> notes, JsonNode fragments, boolean shouldContain) {
        if (!fragments.isArray() || fragments.isEmpty()) {
            return;
        }
        for (JsonNode fragment : fragments) {
            String text = fragment.asText();
            if (shouldContain) {
                assertThat(notes).anyMatch(note -> note.contains(text));
            } else {
                assertThat(notes).noneMatch(note -> note.contains(text));
            }
        }
    }
}
