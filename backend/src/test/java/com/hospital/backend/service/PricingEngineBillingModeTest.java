package com.hospital.backend.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PricingEngineBillingModeTest {

    @Test
    void hybridUsesStandardPathWhenSpecialRuleMisses() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("GUOYAO-2");
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "国药总医院第二院区",
                "department", "手术室",
                "type", "高温纸塑袋75*200",
                "packName", "咬针器-1/W6050",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 16.5,
                "totalPrice", 16.5
        ));
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.expectedUnitPrice).isEqualTo(8.0);
        assertThat(result.pricingRule).contains("高温纸塑袋");
        assertThat(result.notes).anyMatch(note -> note.contains("混合模式未命中特色规则，走标准灭菌计价"));
    }

    @Test
    void bingchengHybridFallsBackToStandardForNonSpecialRow() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("BINGCHENG-YM");
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "哈尔滨冰城医疗美容医院",
                "department", "手术室",
                "type", "额外包(纸塑袋)",
                "packName", "普通器械-4/Z7526",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 4,
                "packCount", 1,
                "unitPrice", 22.0,
                "totalPrice", 22.0
        ));
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.pricingRule).contains("高温纸塑袋");
        assertThat(result.expectedUnitPrice).isEqualTo(22.0);
        assertThat(result.notes).anyMatch(note -> note.contains("混合模式未命中特色规则，走标准灭菌计价"));
    }

    @Test
    void billingDisabledFallsThroughToStandardPricing() throws Exception {
        ObjectNode rules = PricingEngineTestSupport.defaultRules();
        rules.putObject("billingProfile").put("enabled", false);
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "黑龙江九洲妇科医院",
                "department", "手术室",
                "type", "器械包(ZSD)",
                "packName", "方盘-1",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 11.0,
                "totalPrice", 11.0
        ));
        assertThat(result.pricingRule).isNotEqualTo("特色账单已关闭");
        assertThat(result.notes).noneMatch(n -> n.contains("无法校验"));
    }

    @Test
    void zuyanHybridUsesStandardPathForBeautyDepartmentRows() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("ZUYAN-NG");
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "祖研-黑龙江省中医医院（南岗院区）",
                "department", "美容科",
                "type", "高温纸塑袋75*300",
                "packName", "剪刀-3/z1530",
                "packageMaterial", "高温纸塑袋75*300",
                "instrumentCount", 3,
                "packCount", 1,
                "unitPrice", 22.0,
                "totalPrice", 22.0
        ));
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.expectedUnitPrice).isEqualTo(16.5);
        assertThat(result.pricingRule).contains("高温纸塑袋");
        assertThat(result.notes).anyMatch(note -> note.contains("混合模式未命中特色规则，走标准灭菌计价"));
    }
}
