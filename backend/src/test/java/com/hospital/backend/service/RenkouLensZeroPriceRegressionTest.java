package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 人口医院（HLJ-FY-RK）镜头 0 元锁价 35 + 镜补包器械数补缺回归（2026-09-07）。
 *
 * <p>背景：客户在生产 UI 手工配置的「腹腔镜镜头/宫腔镜镜头 0元导入→35元」规则不在 manifest，
 * 被 BillingRulesManifestReconciler 启动清除；补录为 ZERO_PRICE_OVERRIDE（完整词关键词，
 * 绝不用宽泛「镜/镜头」），并把「妇幼人口新腹腔镜镜头加收8」关键词收窄为「新腹腔镜镜头」，
 * 避免高温 0 元腹腔镜镜头行在 35 锁价上重复 +8（43 元，即「重复收费 8 元」事故）。
 */
class RenkouLensZeroPriceRegressionTest {

    private static final String RENKOU = "黑龙江省妇幼保健院（人口）";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode renkouRules() {
        ObjectNode rules = (ObjectNode) MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");

        ArrayNode zeroOverrides = specialRules.withArray("zeroPriceOverrides");
        ObjectNode fuqiang = zeroOverrides.addObject();
        fuqiang.put("name", "妇幼人口腹腔镜镜头0元锁价35");
        fuqiang.putArray("hospitals").add(RENKOU);
        fuqiang.putArray("keywords").add("腹腔镜镜头");
        fuqiang.put("price", 35.0);
        fuqiang.put("skipPackaging", true);
        ObjectNode gongqiang = zeroOverrides.addObject();
        gongqiang.put("name", "妇幼人口宫腔镜镜头0元锁价35");
        gongqiang.putArray("hospitals").add(RENKOU);
        gongqiang.putArray("keywords").add("宫腔镜镜头");
        gongqiang.put("price", 35.0);
        gongqiang.put("skipPackaging", true);

        ArrayNode extraFees = specialRules.withArray("extraFees");
        ObjectNode extraFee = extraFees.addObject();
        extraFee.put("name", "妇幼人口新腹腔镜镜头加收8");
        extraFee.putArray("hospitals").add(RENKOU);
        extraFee.putArray("keywords").add("新腹腔镜镜头");
        extraFee.put("fee", 8.0);
        return rules;
    }

    private static Map<String, Object> row(
            String type, String packName, String packageMaterial,
            int instrumentCount, int packCount, double unitPrice) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", RENKOU);
        row.put("type", type);
        row.put("packName", packName);
        row.put("packageMaterial", packageMaterial);
        row.put("instrumentCount", instrumentCount);
        row.put("packCount", packCount);
        row.put("unitPrice", unitPrice);
        row.put("totalPrice", unitPrice * Math.max(1, packCount));
        return row;
    }

    @Test
    void fuqiangjingLensZeroPriceLocks35WithoutExtraFee() {
        PricingEngine engine = new PricingEngine(renkouRules());
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "器械包(纸塑袋)", "腹腔镜镜头", "高温纸塑袋75*200", 1, 1, 0.0));

        assertThat(result.expectedUnitPrice).isEqualTo(35.0);
        assertThat(result.pricingRule).contains("腹腔镜镜头0元锁价35");
        assertThat(result.pricingRule).doesNotContain("加收8");
        assertThat(result.notes).noneMatch(n -> n.contains("加收 8"));
    }

    @Test
    void gongqiangjingLensZeroPriceLocks35() {
        PricingEngine engine = new PricingEngine(renkouRules());
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "器械包(纸塑袋)", "宫腔镜镜头", "高温纸塑袋75*200", 1, 1, 0.0));

        assertThat(result.expectedUnitPrice).isEqualTo(35.0);
        assertThat(result.pricingRule).contains("宫腔镜镜头0元锁价35");
    }

    @Test
    void pricedLensRowsAreNotLocked() {
        PricingEngine engine = new PricingEngine(renkouRules());
        // 关节镜镜头 / 镜头 单价 8 元：不命中 0 元锁价，走通用高温纸塑袋阶梯（1 件 10cm = 5.5+2.5 = 8）
        for (String packName : new String[]{"关节镜镜头", "镜头"}) {
            PricingEngine.ProcessedResult result = engine.processRow(row(
                    "器械包(纸塑袋)", packName, "高温纸塑袋75*200", 1, 1, 8.0));
            assertThat(result.expectedUnitPrice).isEqualTo(8.0);
            assertThat(result.pricingRule).doesNotContain("锁价35");
            assertThat(result.status).isEqualTo("unchanged");
        }
    }

    @Test
    void jingbubaoIsNotLockedAt35() {
        PricingEngine engine = new PricingEngine(renkouRules());
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "器械包(纸塑袋)", "镜补包-1剪刀-1/Z2032", "高温纸塑袋75*200", 2, 2, 0.0));

        assertThat(result.pricingRule).doesNotContain("锁价35");
        assertThat(result.expectedUnitPrice).isNotEqualTo(35.0);
    }

    @Test
    void jingbubaoMissingInstrumentCountIsFilledFromPackName() {
        PricingEngine engine = new PricingEngine(renkouRules());
        // 账单器械数列缺失(0)：包名 1+1=2 件/包 × 2 包 = 4，不再报「器械数为0」，
        // 单包按 2 件计价（10cm 高温纸塑袋：5.5×2+2.5=13.5/包，×2 包=27）
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "器械包(纸塑袋)", "镜补包-1剪刀-1/Z2032", "高温纸塑袋75*200", 0, 2, 0.0));

        assertThat(result.notes).noneMatch(n -> n.contains("器械数为0"));
        assertThat(result.notes).anyMatch(n -> n.contains("器械数列缺失") && n.contains("补齐为 4"));
        assertThat(result.notes).noneMatch(n -> n.contains("与器械数列"));
        assertThat(result.expectedUnitPrice).isEqualTo(13.5);
        assertThat(result.correctedTotalPrice).isEqualTo(27.0);
    }

    @Test
    void lowTempNewFuqiangjingKeepsExtraFee8() {
        PricingEngine engine = new PricingEngine(renkouRules());
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "单包装包(老肯低温)", "新腹腔镜镜头", "低温纸塑袋200*600", 1, 1, 36.0));

        assertThat(result.expectedUnitPrice).isEqualTo(36.0);
        assertThat(result.pricingRule).contains("加收8");
    }

    @Test
    void lowTempFuqiangjingVariantDropsExtraFeeAfterKeywordNarrowing() {
        PricingEngine engine = new PricingEngine(renkouRules());
        // 「腹腔镜镜头-1/z2060」不含完整词「新腹腔镜镜头」，收窄后不再 +8，回标准低温单件 28
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "单包装包(老肯低温)", "腹腔镜镜头-1/z2060", "低温纸塑袋200*600", 1, 1, 28.0));

        assertThat(result.expectedUnitPrice).isEqualTo(28.0);
        assertThat(result.pricingRule).doesNotContain("加收8");
        // 低温非 0 元行不触发 0 元锁价
        assertThat(result.pricingRule).doesNotContain("锁价35");
    }
}
