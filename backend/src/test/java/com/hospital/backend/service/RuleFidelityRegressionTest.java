package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleFidelityRegressionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void guoyao2PointerTenFoldWithBagMatchesCustomerRule() throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("GUOYAO-2");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "国药总医院第二院区",
                "department", "手术室",
                "type", "额外包(纸塑袋)",
                "packName", "指针-10/z7537",
                "packageMaterial", "高温纸塑袋75*370",
                "instrumentCount", 10,
                "packCount", 1,
                "unitPrice", 13.5,
                "totalPrice", 13.5
        ));
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.expectedUnitPrice).isEqualTo(13.5);
        assertThat(result.pricingRule).contains("电机厂指针5合1含包材");
        assertThat(result.notes).anyMatch(note -> note.contains("电机厂指针5合1含包材"));
        assertThat(result.notes).noneMatch(note -> note.contains("混合模式未命中特色规则，走标准灭菌计价"));
    }

    @Test
    void guoyao2PointerTwelveFoldWithoutBagMatchesCustomerRule() throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("GUOYAO-2");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "国药总医院第二院区",
                "department", "手术室",
                "type", "额外包(纸塑袋)",
                "packName", "指针-12/z7537",
                "packageMaterial", "高温纸塑袋75*370",
                "instrumentCount", 12,
                "packCount", 1,
                "unitPrice", 16.5,
                "totalPrice", 16.5
        ));
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.expectedUnitPrice).isEqualTo(16.5);
        assertThat(result.pricingRule).contains("电机厂指针5合1免包材");
        assertThat(result.notes).anyMatch(note -> note.contains("电机厂指针5合1免包材"));
        assertThat(result.notes).noneMatch(note -> note.contains("混合模式未命中特色规则，走标准灭菌计价"));
    }

    @Test
    void guoyao2HybridUsesStandardPathWhenSpecialRuleMisses() throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("GUOYAO-2");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult bite = engine.processRow(Map.of(
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
        assertThat(bite.status).isEqualTo("warning");
        assertThat(bite.expectedUnitPrice).isEqualTo(8.0);
        assertThat(bite.pricingRule).contains("高温纸塑袋");
        assertThat(bite.notes).anyMatch(note -> note.contains("混合模式未命中特色规则，走标准灭菌计价"));

        PricingEngine.ProcessedResult tourniquetPaper = engine.processRow(Map.of(
                "hospitalName", "国药总医院第二院区",
                "department", "手术室",
                "type", "额外包(纸塑袋)",
                "packName", "驱血带(高温)/Z2032",
                "packageMaterial", "高温纸塑袋200*320",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 13.0,
                "totalPrice", 13.0
        ));
        assertThat(tourniquetPaper.status).isEqualTo("unchanged");
        assertThat(tourniquetPaper.expectedUnitPrice).isEqualTo(13.0);
        assertThat(tourniquetPaper.pricingRule).contains("高温纸塑袋20cm");
        assertThat(tourniquetPaper.notes).anyMatch(note -> note.contains("混合模式未命中特色规则，走标准灭菌计价"));
    }

    @Test
    void guoyao2NonWovenTourniquetUsesDressingWCode() throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("GUOYAO-2");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult tourniquet = engine.processRow(Map.of(
                "hospitalName", "国药总医院第二院区",
                "department", "手术室",
                "type", "敷料包(无纺布包)",
                "packName", "驱血带(高温)/W90",
                "packageMaterial", "无纺布-90×90-50g",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 13.0,
                "totalPrice", 13.0
        ));
        assertThat(tourniquet.status).isEqualTo("warning");
        assertThat(tourniquet.expectedUnitPrice).isEqualTo(30.0);
        assertThat(tourniquet.pricingRule).contains("驱血带");
        assertThat(tourniquet.notes).anyMatch(note -> note.contains("混合模式未命中特色规则，走标准灭菌计价"));
    }

    @Test
    void jiuzhouBillingDisabledKeepsOriginalPrice() throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("JIUZHOU-FK");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "黑龙江九洲妇科医院",
                "department", "手术室",
                "type", "高温无纺布-90×90-50g",
                "packName", "人流包-22件/w9050",
                "packageMaterial", "无纺布-90×90-50g",
                "instrumentCount", 22,
                "packCount", 11,
                "unitPrice", 121.0,
                "totalPrice", 121.0
        ));
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.pricingRule).isEqualTo("特色账单已关闭");
        assertThat(result.expectedUnitPrice).isEqualTo(121.0);
        assertThat(result.notes).anyMatch(note -> note.contains("字段核对"));
    }

    @Test
    void zuyanHybridUsesStandardPathForBeautyRows() throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("ZUYAN-NG");
        PricingEngine engine = new PricingEngine(rules);
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
        assertThat(result.notes).anyMatch(note -> note.contains("混合模式未命中特色规则，走标准灭菌计价"));
    }

    @Test
    void bingchengV8HuanzuanUsesPerPiecePlusExtraFee() throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("BINGCHENG-YM");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "哈尔滨冰城医疗美容医院",
                "department", "手术室",
                "type", "高温无纺布-90×90-50g",
                "packName", "环钻包",
                "packageMaterial", "无纺布-90×90-50g",
                "instrumentCount", 10,
                "packCount", 2,
                "unitPrice", 27.5,
                "totalPrice", 55.0
        ));
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.expectedUnitPrice).isEqualTo(30.5);
        assertThat(result.pricingRule).doesNotContain("环钻27.5");
        assertThat(result.pricingRule).contains("冰城环钻包");
    }

    @Test
    void bingchengV8SevenInstrumentHuanzuanStaysUnchangedWhenAlreadyCorrect() throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("BINGCHENG-YM");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "哈尔滨冰城医疗美容医院",
                "department", "手术室",
                "type", "器械包(ZSD)",
                "packName", "环钻包",
                "packageMaterial", "无纺布-90×90-50g",
                "instrumentCount", 7,
                "packCount", 1,
                "unitPrice", 41.5,
                "totalPrice", 41.5
        ));
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.expectedUnitPrice).isEqualTo(41.5);
    }

    @Test
    void bingchengHuanzuanTouDoesNotMatchHuanzuanPackRules() throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("BINGCHENG-YM");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "哈尔滨冰城医疗美容医院",
                "department", "手术室",
                "type", "额外包(纸塑袋)",
                "packName", "1.2环钻头-1件/Z7520",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 8.0,
                "totalPrice", 8.0
        ));
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.expectedUnitPrice).isEqualTo(8.0);
        assertThat(result.pricingRule).doesNotContain("冰城环钻包");
    }

    @Test
    void unknownHospitalDoesNotApplyDefaultTemplate() {
        ObjectNode compiled = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        compiled.putObject("billingProfile").put("enabled", false);
        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "未建档测试医院",
                "type", "高温纸塑袋75*200",
                "packName", "测试包",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 16.5,
                "totalPrice", 16.5
        ));
        assertThat(result.status).isEqualTo("unchanged");
    }

    @Test
    void shangdeRulesMatchExpectedCorrections() {
        ObjectNode compiled = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        ObjectNode billingProfile = compiled.putObject("billingProfile");
        billingProfile.put("enabled", true);
        billingProfile.put("pricingMode", "special_only");
        ArrayNode fixed = compiled.with("specialRules").withArray("fixedPrices");
        ObjectNode zixian = fixed.addObject();
        zixian.put("name", "上德子痫包16.5");
        zixian.put("price", 16.5);
        zixian.put("skipPackaging", true);
        zixian.putArray("keywords").add("子痫包-3件/Z1526");
        zixian.putArray("hospitals").add("黑龙江菁华上德生殖妇产医院");
        ObjectNode youshi = fixed.addObject();
        youshi.put("name", "上德优视加件187");
        youshi.put("price", 187.0);
        youshi.put("skipPackaging", true);
        youshi.putArray("keywords").add("优视加件-11件/w9050");
        youshi.putArray("hospitals").add("黑龙江菁华上德生殖妇产医院");

        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult zixianResult = engine.processRow(Map.of(
                "hospitalName", "黑龙江菁华上德生殖妇产医院",
                "department", "妇科",
                "type", "高温纸塑袋75*300",
                "packName", "子痫包-3件/Z1526",
                "packageMaterial", "高温纸塑袋75*300",
                "instrumentCount", 3,
                "packCount", 1,
                "unitPrice", 22.0,
                "totalPrice", 22.0
        ));
        assertThat(zixianResult.status).isEqualTo("warning");
        assertThat(zixianResult.expectedUnitPrice).isEqualTo(16.5);

        PricingEngine.ProcessedResult youshiResult = engine.processRow(Map.of(
                "hospitalName", "黑龙江菁华上德生殖妇产医院",
                "department", "手术室",
                "type", "高温无纺布-90×90-50g",
                "packName", "优视加件-11件/w9050",
                "packageMaterial", "无纺布-90×90-50g",
                "instrumentCount", 11,
                "packCount", 1,
                "unitPrice", 209.0,
                "totalPrice", 209.0
        ));
        assertThat(youshiResult.status).isEqualTo("warning");
        assertThat(youshiResult.expectedUnitPrice).isEqualTo(187.0);
    }
}
