package com.hospital.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.export.ExportStageDiscountApplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.assertj.core.data.Offset;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * 2026-10-16 客户反馈：种植盒不折小件、华夏按单包满 3 把、南岗妇产固定价。
 */
class CustomerFeedback20261016PricingTest {

    private static final Offset<Double> CENT = offset(0.001);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReconciliationPricingOrchestrator orchestrator;
    private PricingEngine standardEngine;
    private ClerkRuleCompiler compiler;

    @BeforeEach
    void setUp() {
        orchestrator = new ReconciliationPricingOrchestrator(
                new ClerkBillPriceRuleApplier(),
                new ExportStageDiscountApplier());
        standardEngine = new PricingEngine(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()));
        compiler = new ClerkRuleCompiler(new ClerkRuleIndex());
    }

    @Test
    void genericFoldWordsStayEightAndPlantingBoxIsExcluded() {
        assertThat(DefaultPricingTemplate.genericSmallItemFoldKeywords())
                .containsExactly("克氏针", "银质针", "内热针", "车针", "拔髓针", "扩大针", "根扩针", "卷棉子");

        ObjectNode needle = MAPPER.createObjectNode();
        needle.putArray("keywords").add("克氏针").add("种植盒").add("车针");
        ArrayNode configs = needle.putArray("keywordConfigs");
        ObjectNode planting = configs.addObject();
        planting.put("keyword", "种植盒");
        planting.put("matchMode", "contains");
        ArrayNode effective = PricingEngine.effectiveNeedleKeywords(needle);
        assertThat(effective).extracting(node -> node.asText()).contains("克氏针", "车针").doesNotContain("种植盒");
    }

    @Test
    void plantingBoxBillsByActualPiecesEvenIfFoldKeywordIsPresent() {
        ObjectNode rules = (ObjectNode) MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        ObjectNode needle = (ObjectNode) rules.path("needle");
        ((ArrayNode) needle.path("keywords")).add("种植盒");
        ObjectNode cfg = needle.putArray("keywordConfigs").addObject();
        cfg.put("keyword", "种植盒");
        cfg.put("matchMode", "contains");
        PricingEngine engine = new PricingEngine(rules);

        PricingEngine.ProcessedResult twoPacks = engine.processRow(nonWoven(
                "ITI种植盒-31件", 64, 2, 176.0, 352.0));
        assertThat(twoPacks.expectedUnitPrice).isCloseTo(176.0, CENT);
        assertThat(twoPacks.correctedTotalPrice).isCloseTo(352.0, CENT);
        assertThat(joined(twoPacks)).doesNotContain("折算");

        PricingEngine.ProcessedResult thirtyOne = engine.processRow(nonWoven(
                "DT种植盒-30件", 31, 1, 170.5, 170.5));
        assertThat(thirtyOne.expectedUnitPrice).isCloseTo(170.5, CENT);

        PricingEngine.ProcessedResult thirtyTwo = engine.processRow(nonWoven(
                "DT种植盒-31件", 32, 1, 176.0, 176.0));
        assertThat(thirtyTwo.expectedUnitPrice).isCloseTo(176.0, CENT);
        assertThat(thirtyTwo.correctedTotalPrice).isCloseTo(176.0, CENT);
    }

    @Test
    void huaxiaThresholdUsesPiecesPerPack() {
        ObjectNode clerk = compiler.compileForCustomer("HUAXIA-EYE");

        PricingEngine.ProcessedResult tray = orchestrator.processRow(
                paper("弯盘-2", "高温纸塑袋200*250", 8, 4, 16.5, 66.0),
                standardEngine, clerk, "HUAXIA-EYE", Set.of());
        assertThat(tray.expectedUnitPrice).isCloseTo(16.5, CENT);
        assertThat(tray.correctedTotalPrice).isCloseTo(66.0, CENT);
        assertThat(tray.status).isEqualTo("unchanged");
        assertThat(tray.pricingRule).doesNotContain("内勤计价");

        PricingEngine.ProcessedResult sleeve = orchestrator.processRow(
                paper("小钢套-1", "高温纸塑袋100*200", 5, 5, 8.0, 40.0),
                standardEngine, clerk, "HUAXIA-EYE", Set.of());
        assertThat(sleeve.expectedUnitPrice).isCloseTo(8.0, CENT);
        assertThat(sleeve.correctedTotalPrice).isCloseTo(40.0, CENT);
        assertThat(sleeve.status).isEqualTo("unchanged");

        PricingEngine.ProcessedResult threePerPack = orchestrator.processRow(
                paper("小钢杯-3", "高温纸塑袋200*250", 6, 2, 16.5, 33.0),
                standardEngine, clerk, "HUAXIA-EYE", Set.of());
        assertThat(threePerPack.expectedUnitPrice).isCloseTo(8.25, CENT);
        assertThat(threePerPack.correctedTotalPrice).isCloseTo(16.5, CENT);
        assertThat(threePerPack.difference).isCloseTo(-16.5, CENT);
        assertThat(threePerPack.status).isEqualTo("warning");

        PricingEngine.ProcessedResult alreadySpecial = orchestrator.processRow(
                paper("小钢杯-3", "高温纸塑袋200*250", 6, 2, 8.25, 16.5),
                standardEngine, clerk, "HUAXIA-EYE", Set.of());
        assertThat(alreadySpecial.expectedUnitPrice).isCloseTo(8.25, CENT);
        assertThat(alreadySpecial.status).isEqualTo("unchanged");
    }

    @Test
    void huaxiaHalfWidthNonwovenTypeMatchesFullWidthRule() {
        ObjectNode clerk = compiler.compileForCustomer("HUAXIA-EYE");

        // 规则库存全角「额外包（无纺布）」，账单半角。单包 18/2=9 件，满足 N≥3。
        // PER_PIECE 用整行 18 把：2.75×18=49.50，再除以 2 包，规则单价 24.75。
        Map<String, Object> halfWidth = nonWoven("ICL器械-8件（盒1）/W6050", 18, 2, 49.50, 99.0);
        halfWidth.put("type", "额外包(无纺布)");
        halfWidth.put("packageMaterial", "无纺布-60×60-50g");
        halfWidth.put("hospitalName", "哈尔滨华夏眼科医院");
        PricingEngine.ProcessedResult icl = orchestrator.processRow(
                halfWidth, standardEngine, clerk, "HUAXIA-EYE", Set.of());
        assertThat(icl.expectedUnitPrice).isCloseTo(24.75, CENT);
        assertThat(icl.correctedTotalPrice).isCloseTo(49.50, CENT);
        assertThat(icl.difference).isCloseTo(-49.50, CENT);
        assertThat(icl.status).isEqualTo("warning");
        assertThat(icl.pricingRule).contains("内勤计价");
        assertThat(icl.billingNotes.get("pricingLayer")).isEqualTo("clerk");

        // 反过来：账单全角，规则半角「额外包(纸塑袋)」。单包 3 件，单价仍是 2.75×6/2=8.25。
        Map<String, Object> fullWidthPaper = paper("小钢杯-3", "高温纸塑袋200*250", 6, 2, 16.5, 33.0);
        fullWidthPaper.put("type", "额外包（纸塑袋）");
        PricingEngine.ProcessedResult paperPack = orchestrator.processRow(
                fullWidthPaper, standardEngine, clerk, "HUAXIA-EYE", Set.of());
        assertThat(paperPack.expectedUnitPrice).isCloseTo(8.25, CENT);
        assertThat(paperPack.pricingRule).contains("内勤计价");
        assertThat(paperPack.billingNotes.get("pricingLayer")).isEqualTo("clerk");
    }

    @Test
    void nangangShortNameUsesExistingFixedPrices() {
        ClerkRuleIndex index = new ClerkRuleIndex();
        assertThat(index.resolveCustomerCodeByHospitalName("南岗区妇产医院")).isEqualTo("NG-FUCHAN");
        assertThat(index.resolveCustomerCodeByHospitalName("哈尔滨市南岗区妇产医院")).isEqualTo("NG-FUCHAN");
        assertThat(index.resolveCustomerCodeByHospitalName("道外区人民医院")).isEqualTo("DAOWAI-RM");
        assertThat(index.resolveCustomerCodeByHospitalName("太平人民医院")).isEqualTo("TAIPING-RM");
        assertThat(index.resolveCustomerCodeByHospitalName("三精肾病医院")).isEqualTo("SANJING-SB");

        ObjectNode clerk = compiler.compileForCustomer("NG-FUCHAN");

        PricingEngine.ProcessedResult vaginal = orchestrator.processRow(
                nonWoven("阴式包", 43, 1, 236.5, 236.5),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(vaginal.expectedUnitPrice).isCloseTo(170.5, CENT);
        assertThat(vaginal.correctedTotalPrice).isCloseTo(170.5, CENT);
        assertThat(vaginal.difference).isCloseTo(-66.0, CENT);
        assertThat(vaginal.status).isEqualTo("warning");

        PricingEngine.ProcessedResult induction = orchestrator.processRow(
                nonWoven("引产包", 33, 1, 181.5, 181.5),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(induction.expectedUnitPrice).isCloseTo(160.0, CENT);
        assertThat(induction.correctedTotalPrice).isCloseTo(160.0, CENT);
        assertThat(induction.difference).isCloseTo(-21.5, CENT);
        assertThat(induction.status).isEqualTo("warning");

        PricingEngine.ProcessedResult embedded = orchestrator.processRow(
                nonWoven("妇科阴式包", 12, 1, 66.0, 66.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(embedded.expectedUnitPrice).isNotCloseTo(170.5, CENT);
        assertThat(embedded.pricingRule).doesNotContain("阴式包");

        PricingEngine.ProcessedResult smallBag = orchestrator.processRow(
                dressing("敷料包（纸塑袋）", "高温纸塑袋100*200", 2.5),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(smallBag.pricingRule).doesNotContain("纸塑袋＜20cm");

        PricingEngine.ProcessedResult largeBag = orchestrator.processRow(
                dressing("小敷料包（纸塑袋）", "高温纸塑袋200*250", 8.25),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(largeBag.pricingRule).doesNotContain("纸塑袋≥20cm");
    }

    @Test
    void nangangGauzeAndCottonBallUsePerPackNamePrices() {
        ObjectNode clerk = compiler.compileForCustomer("NG-FUCHAN");

        PricingEngine.ProcessedResult gauzeCode = orchestrator.processRow(
                nangangRow("纱布/Z1526", "", 5, 1, 2.5, 2.5),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(gauzeCode.expectedUnitPrice).isCloseTo(2.3, CENT);
        assertThat(gauzeCode.correctedTotalPrice).isCloseTo(2.3, CENT);
        assertThat(gauzeCode.pricingRule).contains("纱布");
        assertThat(gauzeCode.pricingRule).doesNotContain("散包");
        assertThat(gauzeCode.billingNotes.get("pricingLayer")).isEqualTo("clerk");

        PricingEngine.ProcessedResult gauzeLargeBag = orchestrator.processRow(
                nangangRow("纱布", "高温纸塑袋200*250", 4, 2, 4.0, 8.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(gauzeLargeBag.expectedUnitPrice).isCloseTo(2.3, CENT);
        assertThat(gauzeLargeBag.correctedTotalPrice).isCloseTo(4.6, CENT);
        assertThat(gauzeLargeBag.expectedUnitPrice).isNotCloseTo(1.15, CENT);
        assertThat(gauzeLargeBag.pricingRule).contains("纱布");
        assertThat(gauzeLargeBag.pricingRule).doesNotContain("纸塑袋");

        PricingEngine.ProcessedResult cotton = orchestrator.processRow(
                nangangRow("棉球", "", 3, 1, 8.0, 8.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(cotton.expectedUnitPrice).isCloseTo(4.0, CENT);
        assertThat(cotton.correctedTotalPrice).isCloseTo(4.0, CENT);
        assertThat(cotton.pricingRule).isEqualTo("内勤计价：棉球");

        PricingEngine.ProcessedResult cottonCode = orchestrator.processRow(
                nangangRow("棉球/W6050", "无纺布", 6, 2, 8.0, 16.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(cottonCode.expectedUnitPrice).isCloseTo(4.0, CENT);
        assertThat(cottonCode.correctedTotalPrice).isCloseTo(8.0, CENT);
        assertThat(cottonCode.pricingRule).isEqualTo("内勤计价：棉球");

        PricingEngine.ProcessedResult jar = orchestrator.processRow(
                nangangRow("棉球缸", "高温纸塑袋200*250", 1, 1, 16.0, 16.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(jar.expectedUnitPrice).isCloseTo(16.0, CENT);
        assertThat(jar.correctedTotalPrice).isCloseTo(16.0, CENT);
        assertThat(jar.pricingRule).contains("棉球缸");
        assertThat(jar.pricingRule).isNotEqualTo("内勤计价：棉球");

        PricingEngine.ProcessedResult scatter = orchestrator.processRow(
                nangangRow("弯盘", "高温无纺布", 1, 1, 5.0, 5.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(scatter.expectedUnitPrice).isCloseTo(8.0, CENT);
        assertThat(scatter.correctedTotalPrice).isCloseTo(8.0, CENT);
        assertThat(scatter.pricingRule).contains("散包按把");
    }

    @Test
    void shortBillNamesUseExistingClerkPriceTables() {
        ObjectNode daowai = compiler.compileForCustomer("DAOWAI-RM");
        Map<String, Object> extra = nangangRow("持针器-1/z1029", "高温纸塑袋", 1, 1, 8.0, 8.0);
        extra.put("hospitalName", "道外区人民医院");
        extra.put("type", "额外包(纸塑袋)");
        PricingEngine.ProcessedResult piece = orchestrator.processRow(
                extra, standardEngine, daowai, "DAOWAI-RM", Set.of());
        assertThat(piece.expectedUnitPrice).isCloseTo(3.0, CENT);
        assertThat(piece.pricingRule).contains("额外包(纸塑袋)");
        assertThat(piece.billingNotes.get("pricingLayer")).isEqualTo("clerk");

        ObjectNode sanjing = compiler.compileForCustomer("SANJING-SB");
        Map<String, Object> three = nangangRow("普通器械", "高温纸塑袋", 3, 1, 16.5, 16.5);
        three.put("hospitalName", "三精肾病医院");
        three.put("type", "额外包(纸塑袋)");
        PricingEngine.ProcessedResult perPiece = orchestrator.processRow(
                three, standardEngine, sanjing, "SANJING-SB", Set.of());
        assertThat(perPiece.expectedUnitPrice).isCloseTo(9.0, CENT);
        assertThat(perPiece.billingNotes.get("pricingLayer")).isEqualTo("clerk");

        ObjectNode taiping = compiler.compileForCustomer("TAIPING-RM");
        Map<String, Object> paper = nangangRow("剪刀-1", "高温纸塑袋200*250", 1, 1, 8.0, 8.0);
        paper.put("hospitalName", "太平人民医院");
        paper.put("type", "额外包(纸塑袋)");
        PricingEngine.ProcessedResult fixed = orchestrator.processRow(
                paper, standardEngine, taiping, "TAIPING-RM", Set.of());
        assertThat(fixed.billingNotes.get("pricingLayer")).isEqualTo("clerk");
        assertThat(fixed.pricingRule).contains("额外包(纸塑袋)");
        assertThat(fixed.expectedUnitPrice).isCloseTo(9.8, CENT);
    }

    @Test
    void nangangRingRemovalPackIsUnitPriceNotRowTotal() {
        ObjectNode clerk = compiler.compileForCustomer("NG-FUCHAN");
        PricingEngine.ProcessedResult ring = orchestrator.processRow(
                nonWoven("取环包（90布）", 12, 2, 33.0, 66.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(ring.expectedUnitPrice).isCloseTo(33.0, CENT);
        assertThat(ring.correctedTotalPrice).isCloseTo(66.0, CENT);
        assertThat(ring.difference).isCloseTo(0.0, CENT);
        assertThat(ring.pricingRule).contains("取环包");
        assertThat(ring.expectedUnitPrice).isNotCloseTo(16.50, CENT);

        PricingEngine.ProcessedResult halfWidth = orchestrator.processRow(
                nonWoven("取环包(90布)", 12, 2, 33.0, 66.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(halfWidth.expectedUnitPrice).isCloseTo(33.0, CENT);
        assertThat(halfWidth.correctedTotalPrice).isCloseTo(66.0, CENT);
        assertThat(halfWidth.pricingRule).contains("取环包");
    }

    @Test
    void nangangParentheticalLaparoscopyUsesPerPiecePrice() {
        ObjectNode clerk = compiler.compileForCustomer("NG-FUCHAN");
        PricingEngine.ProcessedResult inside = orchestrator.processRow(
                nonWoven("盆2盘1杯1(腹腔镜里的)/w9050", 4, 1, 22.0, 22.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(inside.expectedUnitPrice).isCloseTo(32.0, CENT);
        assertThat(inside.pricingRule).contains("散包按把");
        assertThat(inside.pricingRule).doesNotContain("腹腔镜");
        assertThat(inside.billingNotes.get("pricingLayer")).isEqualTo("clerk");

        PricingEngine.ProcessedResult fullWidth = orchestrator.processRow(
                nonWoven("盆2盘1杯1（腹腔镜里的）/w9050", 4, 1, 22.0, 22.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(fullWidth.expectedUnitPrice).isCloseTo(32.0, CENT);
        assertThat(fullWidth.pricingRule).contains("散包按把");
        assertThat(fullWidth.billingNotes.get("pricingLayer")).isEqualTo("clerk");
    }

    @Test
    void nangangLaparoscopyOutsideParenthesesAndNamedPacksStayPerPack() {
        ObjectNode clerk = compiler.compileForCustomer("NG-FUCHAN");

        PricingEngine.ProcessedResult exact = orchestrator.processRow(
                nonWoven("腹腔镜", 10, 1, 200.0, 200.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(exact.expectedUnitPrice).isCloseTo(170.5, CENT);
        assertThat(exact.pricingRule).contains("腹腔镜");

        PricingEngine.ProcessedResult outside = orchestrator.processRow(
                nonWoven("腹腔镜包", 8, 2, 170.5, 341.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(outside.expectedUnitPrice).isCloseTo(32.0, CENT);
        assertThat(outside.pricingRule).contains("散包按把");
        assertThat(outside.pricingRule).doesNotContain("腹腔镜");

        PricingEngine.ProcessedResult prefixed = orchestrator.processRow(
                nonWoven("妇科腹腔镜（备注）", 6, 1, 200.0, 200.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(prefixed.expectedUnitPrice).isCloseTo(48.0, CENT);
        assertThat(prefixed.pricingRule).contains("散包按把");
        assertThat(prefixed.pricingRule).doesNotContain("腹腔镜");

        PricingEngine.ProcessedResult vaginal = orchestrator.processRow(
                nonWoven("阴式包", 12, 2, 170.5, 341.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(vaginal.expectedUnitPrice).isCloseTo(170.5, CENT);
        assertThat(vaginal.correctedTotalPrice).isCloseTo(341.0, CENT);
        assertThat(vaginal.pricingRule).contains("阴式包");

        PricingEngine.ProcessedResult induction = orchestrator.processRow(
                nonWoven("引产包", 8, 2, 160.0, 320.0),
                standardEngine, clerk, "NG-FUCHAN", Set.of());
        assertThat(induction.expectedUnitPrice).isCloseTo(160.0, CENT);
        assertThat(induction.correctedTotalPrice).isCloseTo(320.0, CENT);
        assertThat(induction.pricingRule).contains("引产包");
    }

    private static Map<String, Object> nonWoven(
            String packName, int instrumentCount, int packCount, double unitPrice, double totalPrice) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", "哈尔滨工业大学医院");
        row.put("packName", packName);
        row.put("type", "器械包");
        row.put("packageMaterial", "高温无纺布");
        row.put("instrumentCount", instrumentCount);
        row.put("packCount", packCount);
        row.put("unitPrice", unitPrice);
        row.put("totalPrice", totalPrice);
        return row;
    }

    private static Map<String, Object> paper(
            String packName, String material, int instrumentCount, int packCount,
            double unitPrice, double totalPrice) {
        Map<String, Object> row = nonWoven(packName, instrumentCount, packCount, unitPrice, totalPrice);
        row.put("type", "额外包(纸塑袋)");
        row.put("packageMaterial", material);
        row.put("hospitalName", "哈尔滨华夏眼科医院");
        return row;
    }

    private static Map<String, Object> nangangRow(
            String packName, String material, int instrumentCount, int packCount,
            double unitPrice, double totalPrice) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", "南岗区妇产医院");
        row.put("packName", packName);
        row.put("type", "器械包");
        row.put("packageMaterial", material);
        row.put("instrumentCount", instrumentCount);
        row.put("packCount", packCount);
        row.put("unitPrice", unitPrice);
        row.put("totalPrice", totalPrice);
        return row;
    }

    private static Map<String, Object> dressing(String packName, String material, double billUnit) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", "南岗区妇产医院");
        row.put("packName", packName);
        row.put("type", "敷料包(纸塑袋)");
        row.put("packageMaterial", material);
        row.put("instrumentCount", 1);
        row.put("packCount", 1);
        row.put("unitPrice", billUnit);
        row.put("totalPrice", billUnit);
        return row;
    }

    private static String joined(PricingEngine.ProcessedResult result) {
        return result.notes == null ? "" : String.join("\n", result.notes);
    }
}
