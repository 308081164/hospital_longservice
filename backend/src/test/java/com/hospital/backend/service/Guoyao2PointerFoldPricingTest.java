package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 电机厂（GUOYAO-2）指针 FOLD 计价修复验收：
 * 客户规则③ ceil(件数/5)×5.5 +（≤10 件时）标准袋费。
 */
class Guoyao2PointerFoldPricingTest {

    private static final String HOSPITAL = "国药总医院第二院区";
    private static final String DEPT = "手术室";
    private static final String TYPE = "额外包(纸塑袋)";
    private static final String MATERIAL = "高温纸塑袋75*370";

    @Test
    void screenshotCase_pointerTen_matchesBill13_5_not55() throws Exception {
        PricingEngine engine = engineForGuoyao2();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "指针-10/z7537", 10, 13.5, 13.5));

        assertThat(result.expectedUnitPrice)
                .as("10 件应 FOLD 为 2 折算件×5.5+10cm 袋费，而非 10×5.5=55")
                .isEqualTo(13.5);
        assertThat(result.expectedUnitPrice).isNotEqualTo(55.0);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.difference).isNotNull();
        assertThat(Math.abs(result.difference)).isLessThan(0.001);
        assertThat(result.pricingRule).isEqualTo("电机厂指针5合1含包材");
        assertThat(result.pricingRule).doesNotContain("高温纸塑袋10cm计费");
        assertThat(result.notes).anyMatch(n -> n.contains("折算为 2 件"));
        assertThat(result.notes).noneMatch(n -> n.contains("混合模式未命中特色规则，走标准灭菌计价"));
        assertThat(result.notes).noneMatch(n -> n.contains("大于等于 3 件，按 5.50 元/件 × 10"));
    }

    @Test
    void pointerTwelve_matchesBill16_5_withoutBagFee() throws Exception {
        PricingEngine engine = engineForGuoyao2();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "指针-12/z7537", 12, 16.5, 16.5));

        assertThat(result.expectedUnitPrice).isEqualTo(16.5);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.pricingRule).isEqualTo("电机厂指针5合1免包材");
        assertThat(result.notes).anyMatch(n -> n.contains("折算为 3 件"));
        assertThat(result.notes).noneMatch(n -> n.contains("混合模式未命中特色规则，走标准灭菌计价"));
    }

    @Test
    void pointerTwenty_matchesBill22_foldOnlyNoBag() throws Exception {
        PricingEngine engine = engineForGuoyao2();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "指针-20/Z7520", 20, 22.0, 22.0));

        assertThat(result.expectedUnitPrice).isEqualTo(22.0);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.pricingRule).isEqualTo("电机厂指针5合1免包材");
        assertThat(result.notes).anyMatch(n -> n.contains("折算为 4 件"));
    }

    @Test
    void withoutPointerFoldRules_fallsBackToStandard55_andHybridNote() throws Exception {
        PricingEngine engine = engineWithoutPointerFoldRules();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "指针-10/z7537", 10, 13.5, 13.5));

        assertThat(result.expectedUnitPrice)
                .as("无 FOLD 规则时应走高温纸塑≥3 件标准路径")
                .isEqualTo(55.0);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.pricingRule).contains("高温纸塑袋");
        assertThat(result.notes).anyMatch(n -> n.contains("混合模式未命中特色规则，走标准灭菌计价"));
        assertThat(result.notes).anyMatch(n -> n.contains("大于等于 3 件，按 5.50 元/件 × 10"));
    }

    @Test
    void unrelatedPackOnHybrid_stillShowsHybridMissNote() throws Exception {
        PricingEngine engine = engineForGuoyao2();
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", HOSPITAL,
                "department", DEPT,
                "type", "高温纸塑袋75*200",
                "packName", "咬针器-1/W6050",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 16.5,
                "totalPrice", 16.5
        ));

        assertThat(result.notes).anyMatch(n -> n.contains("混合模式未命中特色规则，走标准灭菌计价"));
        assertThat(result.pricingRule).contains("高温纸塑袋");
    }

    private static Map<String, Object> row(String packName, int instrumentCount, double unitPrice, double totalPrice) {
        return Map.of(
                "hospitalName", HOSPITAL,
                "department", DEPT,
                "type", TYPE,
                "packName", packName,
                "packageMaterial", MATERIAL,
                "instrumentCount", instrumentCount,
                "packCount", 1,
                "unitPrice", unitPrice,
                "totalPrice", totalPrice
        );
    }

    private static PricingEngine engineForGuoyao2() throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("GUOYAO-2");
        return new PricingEngine(rules);
    }

    /** 模拟生产 FOLD 规则 inactive：从编译结果中移除指针 FOLD 规则。 */
    private static PricingEngine engineWithoutPointerFoldRules() throws Exception {
        ObjectNode compiled = RuleFidelityTestSupport.compileForCustomerCode("GUOYAO-2").deepCopy();
        JsonNode specialRules = compiled.path("specialRules");
        if (!(specialRules instanceof ObjectNode specialRulesNode)) {
            return new PricingEngine(compiled);
        }
        JsonNode foldRules = specialRules.path("foldRules");
        if (!foldRules.isArray()) {
            return new PricingEngine(compiled);
        }
        ArrayNode filtered = JsonUtils.getObjectMapper().createArrayNode();
        for (JsonNode rule : foldRules) {
            String name = rule.path("name").asText("");
            if (!name.contains("电机厂指针5合1")) {
                filtered.add(rule);
            }
        }
        specialRulesNode.set("foldRules", filtered);
        return new PricingEngine(compiled);
    }
}
