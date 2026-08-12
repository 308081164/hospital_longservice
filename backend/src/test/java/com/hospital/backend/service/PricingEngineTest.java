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
    void hrbCjZsdInstrumentPackUsesHighTempNonWovenTierWhenBillingEnabled() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("enabled", true);
        billingProfile.put("pricingMode", "standard");
        ((ObjectNode) rules.path("cleaning")).put("recomputeTotalsWhenPriceChanges", true);

        PricingEngine pricingEngine = new PricingEngine(rules);

        PricingEngine.ProcessedResult paperPlastic = pricingEngine.processRow(row(
                "哈尔滨长健医院",
                "额外包(纸塑袋)",
                "尿道探子-14/w6050",
                "高温纸塑袋75*300",
                14,
                1,
                77,
                77));
        assertThat(paperPlastic.expectedUnitPrice).isEqualTo(77.0);
        assertThat(paperPlastic.status).isEqualTo("unchanged");

        PricingEngine.ProcessedResult zsdPack = pricingEngine.processRow(row(
                "哈尔滨长健医院",
                "器械包(ZSD)",
                "手术包（二）",
                "",
                43,
                1,
                231,
                231));
        assertThat(zsdPack.expectedUnitPrice).isEqualTo(236.5);
        assertThat(zsdPack.status).isEqualTo("warning");
        assertThat(zsdPack.notes).anyMatch(note -> note.contains("器械包(ZSD)"));
    }

    @Test
    void zsdKirschnerWirePliersPackSkipsSmallItemFoldAndUsesFullInstrumentCount() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ((ObjectNode) rules.path("cleaning")).put("recomputeTotalsWhenPriceChanges", true);

        PricingEngine pricingEngine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = pricingEngine.processRow(row(
                "哈尔滨市平房区人民医院",
                "器械包(ZSD)",
                "克氏针.钳",
                "无纺布-90×90-50g",
                15,
                1,
                82.5,
                82.5));

        assertThat(result.expectedUnitPrice).isEqualTo(82.5);
        assertThat(result.correctedTotalPrice).isEqualTo(82.5);
        assertThat(result.notes).noneMatch(n -> n.contains("小件关键词") && n.contains("折算"));
    }

    @Test
    void yuemeiYanBaoMultiPackZsdUsesPerPackInstrumentCountForHighTempNonWoven() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ArrayNode fixedPrices = (ArrayNode) rules.path("specialRules").path("fixedPrices");
        ObjectNode rule = fixedPrices.addObject();
        rule.put("name", "眼包5.5元/件");
        rule.putArray("hospitals").add("悦美芳华医疗门诊医院");
        rule.putArray("keywords").add("眼包");
        rule.put("price", 5.5);
        rule.put("pricePerInstrument", true);
        rule.put("skipPackaging", true);
        rule.put("skipHospitalDiscount", true);
        PricingEngine pricingEngine = new PricingEngine(rules);

        PricingEngine.ProcessedResult twoPacks = pricingEngine.processRow(row(
                "悦美芳华医疗门诊医院",
                "器械包(ZSD)",
                "眼包",
                "",
                28,
                2,
                77,
                154));
        assertThat(twoPacks.expectedUnitPrice).isEqualTo(77.0);
        assertThat(twoPacks.correctedTotalPrice).isEqualTo(154.0);
        assertThat(twoPacks.status).isEqualTo("unchanged");
        assertThat(twoPacks.pricingRule).contains("眼包5.5元/件");

        PricingEngine.ProcessedResult sixPacks = pricingEngine.processRow(row(
                "悦美芳华医疗门诊医院",
                "器械包(ZSD)",
                "眼包",
                "",
                84,
                6,
                77,
                462));
        assertThat(sixPacks.expectedUnitPrice).isEqualTo(77.0);
        assertThat(sixPacks.correctedTotalPrice).isEqualTo(462.0);
        assertThat(sixPacks.status).isEqualTo("unchanged");

        PricingEngine.ProcessedResult wrongUnit = pricingEngine.processRow(row(
                "悦美芳华医疗门诊医院",
                "器械包(ZSD)",
                "眼包",
                "",
                28,
                2,
                80,
                160));
        assertThat(wrongUnit.status).isEqualTo("warning");
        assertThat(wrongUnit.expectedUnitPrice).isEqualTo(77.0);
    }

    @Test
    void shkfDaCheZhenHeFixedPrice22() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ArrayNode fixedPrices = (ArrayNode) rules.path("specialRules").path("fixedPrices");
        ObjectNode rule = fixedPrices.addObject();
        rule.put("name", "社会康复大车针盒22");
        rule.putArray("hospitals").add("黑龙江省社会康复医院");
        rule.putArray("keywords").add("大车针盒");
        rule.put("price", 22);
        rule.put("skipPackaging", true);
        rule.put("skipHospitalDiscount", true);
        PricingEngine pricingEngine = new PricingEngine(rules);

        PricingEngine.ProcessedResult ok = pricingEngine.processRow(row(
                "黑龙江省社会康复医院",
                "额外包(纸塑袋)",
                "大车针盒-1/Z1526",
                "高温纸塑袋150*260",
                1, 1, 22, 22));
        assertThat(ok.expectedUnitPrice).isEqualTo(22.0);
        assertThat(ok.status).isEqualTo("unchanged");
        assertThat(ok.pricingRule).contains("社会康复大车针盒22");

        PricingEngine.ProcessedResult wrong = pricingEngine.processRow(row(
                "黑龙江省社会康复医院",
                "额外包(纸塑袋)",
                "大车针盒-1/Z1526",
                "高温纸塑袋150*260",
                1, 1, 11, 11));
        assertThat(wrong.status).isEqualTo("warning");
        assertThat(wrong.expectedUnitPrice).isEqualTo(22.0);
    }

    @Test
    void jzswBioArgonHeliumKnifeEoFixedPrice150() throws Exception {
        ObjectNode rules = MAPPER.createObjectNode();
        rules.setAll((ObjectNode) defaultRules());
        ArrayNode fixedPrices = (ArrayNode) rules.path("specialRules").path("fixedPrices");
        ObjectNode rule = fixedPrices.addObject();
        rule.put("name", "氩氦刀（EO）150元");
        rule.putArray("hospitals").add("哈尔滨基准生物科技有限公司");
        rule.putArray("keywords").add("氩氦刀");
        rule.put("price", 150);
        rule.put("temperature", "LT");
        rule.put("skipPackaging", true);
        rule.put("skipHospitalDiscount", true);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "哈尔滨基准生物科技有限公司",
                "额外包(低温等离子)",
                "氩氦刀-1件/W6050",
                "低温纸塑袋200*300",
                1,
                1,
                150,
                150));
        assertThat(result.expectedUnitPrice).isEqualTo(150.0);
        assertThat(result.correctedTotalPrice).isEqualTo(150.0);
        assertThat(result.status).isEqualTo("unchanged");
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
    void hrbHxEyeAppliesFixed275WhenHighTempInstrumentCountAtLeastThree() throws Exception {
        ObjectNode rules = MAPPER.createObjectNode();
        rules.setAll((ObjectNode) defaultRules());
        ArrayNode fixedPrices = (ArrayNode) rules.path("specialRules").path("fixedPrices");
        ObjectNode tierRule = fixedPrices.addObject();
        tierRule.put("name", "≥3件器械包单价2.75");
        tierRule.putArray("hospitals").add("哈尔滨华夏眼科医院");
        tierRule.put("price", 2.75);
        tierRule.put("pricePerInstrument", true);
        tierRule.put("minInstrumentCount", 3);
        tierRule.put("temperature", "HT");
        tierRule.put("skipPackaging", true);

        PricingEngine hxEngine = new PricingEngine(rules);

        PricingEngine.ProcessedResult atThree = hxEngine.processRow(row(
                "哈尔滨华夏眼科医院",
                "额外包(纸塑袋)",
                "持物钳-3/Z7520",
                "高温纸塑袋75*200",
                3,
                1,
                8.25,
                8.25
        ));
        assertThat(atThree.expectedUnitPrice).isEqualTo(8.25);
        assertThat(atThree.correctedTotalPrice).isEqualTo(8.25);

        PricingEngine.ProcessedResult iclBox = hxEngine.processRow(row(
                "哈尔滨华夏眼科医院",
                "额外包(纸塑袋)",
                "ICL器械-8件（盒1）/W6050",
                "高温纸塑袋75*200",
                18,
                2,
                24.75,
                49.5
        ));
        assertThat(iclBox.expectedUnitPrice).isEqualTo(24.75);
        assertThat(iclBox.correctedTotalPrice).isEqualTo(49.5);

        PricingEngine.ProcessedResult belowThree = hxEngine.processRow(row(
                "哈尔滨华夏眼科医院",
                "额外包(纸塑袋)",
                "持物钳-2/Z7520",
                "高温纸塑袋75*200",
                2,
                1,
                6.39,
                6.39
        ));
        assertThat(belowThree.expectedUnitPrice).isNotEqualTo(8.25);
        assertThat(belowThree.expectedUnitPrice).isNotEqualTo(5.5);

        PricingEngine.ProcessedResult lowTemp = hxEngine.processRow(row(
                "哈尔滨华夏眼科医院",
                "额外包(纸塑袋)",
                "低温器械-5/Z7520",
                "低温灭菌纸塑袋20cm",
                5,
                1,
                70.33,
                70.33
        ));
        assertThat(lowTemp.expectedUnitPrice).isNotEqualTo(8.25);
        assertThat(lowTemp.expectedUnitPrice).isNotEqualTo(13.75);
    }

    @Test
    void bingchengYmAppliesZhengxing58Zichong54_5AndPerPiece55FromThreeInstruments() throws Exception {
        ObjectNode rules = MAPPER.createObjectNode();
        rules.setAll((ObjectNode) defaultRules());
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("pricingMode", "special_only");
        ArrayNode fixedPrices = (ArrayNode) rules.path("specialRules").path("fixedPrices");
        String hospital = "哈尔滨冰城医疗美容医院";

        ObjectNode zhengxing = fixedPrices.addObject();
        zhengxing.put("name", "整形包58");
        zhengxing.put("price", 58);
        zhengxing.put("skipPackaging", true);
        zhengxing.put("skipHospitalDiscount", true);
        zhengxing.putArray("hospitals").add(hospital);
        zhengxing.putArray("keywords").add("整形包").add("整形手术包");
        zhengxing.putArray("excludeKeywords").add("脂充包");

        fixedPrices.add(MAPPER.valueToTree(fixedPrice(
                "脂充包54.5",
                List.of(hospital),
                List.of("脂充包"),
                54.5,
                false,
                false,
                true)));

        ObjectNode tierRule = fixedPrices.addObject();
        tierRule.put("name", "≥3件按件5.5元");
        tierRule.putArray("hospitals").add(hospital);
        tierRule.put("price", 5.5);
        tierRule.put("pricePerInstrument", true);
        tierRule.put("minInstrumentCount", 3);
        tierRule.put("temperature", "HT");
        tierRule.put("skipPackaging", true);
        tierRule.put("skipHospitalDiscount", true);

        PricingEngine bcEngine = new PricingEngine(rules);

        PricingEngine.ProcessedResult zhengxingPack = bcEngine.processRow(row(
                hospital,
                "额外包(纸塑袋)",
                "整形包-3/Z7526",
                "高温纸塑袋75*200",
                3,
                1,
                54.5,
                54.5
        ));
        assertThat(zhengxingPack.expectedUnitPrice).isEqualTo(58.0);
        assertThat(zhengxingPack.correctedTotalPrice).isEqualTo(58.0);

        PricingEngine.ProcessedResult zhengxingSurgeryPack = bcEngine.processRow(row(
                hospital,
                "器械包(ZSD)",
                "整形手术包",
                "高温纸塑袋75*200",
                10,
                2,
                58.0,
                116.0
        ));
        assertThat(zhengxingSurgeryPack.expectedUnitPrice).isEqualTo(58.0);
        assertThat(zhengxingSurgeryPack.correctedTotalPrice).isEqualTo(116.0);
        assertThat(zhengxingSurgeryPack.status).isEqualTo("unchanged");

        PricingEngine.ProcessedResult zichongPack = bcEngine.processRow(row(
                hospital,
                "额外包(纸塑袋)",
                "脂充包/Z7526",
                "高温纸塑袋75*200",
                2,
                1,
                54.5,
                54.5
        ));
        assertThat(zichongPack.expectedUnitPrice).isEqualTo(54.5);

        PricingEngine.ProcessedResult threePieces = bcEngine.processRow(row(
                hospital,
                "额外包(纸塑袋)",
                "普通器械-3/Z7526",
                "高温纸塑袋75*200",
                3,
                1,
                16.5,
                16.5
        ));
        assertThat(threePieces.expectedUnitPrice).isEqualTo(16.5);
        assertThat(threePieces.correctedTotalPrice).isEqualTo(16.5);
    }

    @Test
    void ngFuchanGongqiangjingAppliesFixed170_5() throws Exception {
        ObjectNode rules = MAPPER.createObjectNode();
        rules.setAll((ObjectNode) defaultRules());
        ArrayNode fixedPrices = (ArrayNode) rules.path("specialRules").path("fixedPrices");
        fixedPrices.add(MAPPER.valueToTree(fixedPrice(
                "南岗妇产宫腔镜包",
                List.of("南岗区妇产医院"),
                List.of("宫腔镜"),
                170.5,
                false,
                false,
                true)));

        PricingEngine ngEngine = new PricingEngine(rules);

        PricingEngine.ProcessedResult result = ngEngine.processRow(row(
                "南岗区妇产医院",
                "额外包(纸塑袋)",
                "宫腔镜",
                "高温纸塑袋75*300",
                13,
                1,
                192.5,
                192.5
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(170.5);
        assertThat(result.correctedTotalPrice).isEqualTo(170.5);
        assertThat(result.status).isEqualTo("warning");
    }

    @Test
    void hrbShJiaomaoAndGongshaApplySpecialPricing() throws Exception {
        ObjectNode rules = MAPPER.createObjectNode();
        rules.setAll((ObjectNode) defaultRules());
        ArrayNode fixedPrices = (ArrayNode) rules.path("specialRules").path("fixedPrices");
        ObjectNode jiaomao = fixedPrices.addObject();
        jiaomao.put("name", "胶帽22元/件");
        jiaomao.putArray("hospitals").add("哈尔滨森海医院");
        jiaomao.putArray("keywords").add("胶帽");
        jiaomao.put("price", 22);
        jiaomao.put("pricePerInstrument", true);
        jiaomao.put("skipPackaging", true);
        jiaomao.put("skipHospitalDiscount", true);
        fixedPrices.add(MAPPER.valueToTree(fixedPrice(
                "纱布4元",
                List.of("哈尔滨森海医院"),
                List.of("纱布"),
                4.0,
                false,
                false,
                true)));

        PricingEngine shEngine = new PricingEngine(rules);
        String hospital = "哈尔滨森海医院";

        PricingEngine.ProcessedResult jiaomaoTwo = shEngine.processRow(row(
                hospital,
                "额外包(低温等离子)",
                "胶帽-2/Z7520",
                "",
                2,
                1,
                22.0,
                22.0
        ));
        assertThat(jiaomaoTwo.expectedUnitPrice).isEqualTo(44.0);
        assertThat(jiaomaoTwo.correctedTotalPrice).isEqualTo(44.0);
        assertThat(jiaomaoTwo.status).isEqualTo("warning");

        PricingEngine.ProcessedResult jiaomaoOne = shEngine.processRow(row(
                hospital,
                "额外包(低温等离子)",
                "胶帽-1/z7520",
                "",
                1,
                1,
                22.0,
                22.0
        ));
        assertThat(jiaomaoOne.expectedUnitPrice).isEqualTo(22.0);
        assertThat(jiaomaoOne.status).isEqualTo("unchanged");

        PricingEngine.ProcessedResult gongsha = shEngine.processRow(row(
                hospital,
                "敷料包(无纺布包)",
                "纱布/z2032",
                "",
                1,
                1,
                7.5,
                7.5
        ));
        assertThat(gongsha.expectedUnitPrice).isEqualTo(4.0);
        assertThat(gongsha.correctedTotalPrice).isEqualTo(4.0);
        assertThat(gongsha.status).isEqualTo("warning");
    }

    @Test
    void ngFuchanQuhuanqiAndGongshaApplyFixedPrice() throws Exception {
        ObjectNode rules = MAPPER.createObjectNode();
        rules.setAll((ObjectNode) defaultRules());
        ArrayNode fixedPrices = (ArrayNode) rules.path("specialRules").path("fixedPrices");
        fixedPrices.add(MAPPER.valueToTree(fixedPrice(
                "校正价8.0",
                List.of("南岗区妇产医院"),
                List.of("取环器-1", "宫颈钳-1"),
                8.0,
                false,
                false,
                true)));
        fixedPrices.add(MAPPER.valueToTree(fixedPrice(
                "校正价2.3",
                List.of("南岗区妇产医院"),
                List.of("纱布"),
                2.3,
                true,
                false,
                true)));

        PricingEngine ngEngine = new PricingEngine(rules);

        PricingEngine.ProcessedResult quhuan = ngEngine.processRow(row(
                "南岗区妇产医院",
                "额外包(纸塑袋)",
                "取环器-1/z1535",
                "高温纸塑袋75*370",
                1,
                1,
                11.0,
                11.0
        ));
        assertThat(quhuan.expectedUnitPrice).isEqualTo(8.0);
        assertThat(quhuan.correctedTotalPrice).isEqualTo(8.0);

        PricingEngine.ProcessedResult gongsha = ngEngine.processRow(row(
                "南岗区妇产医院",
                "额外包(纸塑袋)",
                "纱布/Z1526",
                "高温纸塑袋200*250",
                15,
                1,
                2.5,
                37.5
        ));
        assertThat(gongsha.expectedUnitPrice).isEqualTo(34.5);
        assertThat(gongsha.correctedTotalPrice).isEqualTo(34.5);
    }

    @Test
    void ngFuchanKuobangAndWanpanScatterApplyFixedPriceEight() throws Exception {
        ObjectNode rules = MAPPER.createObjectNode();
        rules.setAll((ObjectNode) defaultRules());
        ArrayNode fixedPrices = (ArrayNode) rules.path("specialRules").path("fixedPrices");
        fixedPrices.add(MAPPER.valueToTree(fixedPrice(
                "校正价16.0",
                List.of("南岗区妇产医院"),
                List.of("弯盘-2"),
                16.0,
                false,
                false,
                true)));
        fixedPrices.add(MAPPER.valueToTree(fixedPrice(
                "校正价24.0",
                List.of("南岗区妇产医院"),
                List.of("扩棒（4 4.5 5）-3"),
                24.0,
                false,
                false,
                true)));
        fixedPrices.add(MAPPER.valueToTree(fixedPrice(
                "校正价扩棒8",
                List.of("南岗区妇产医院"),
                List.of("扩棒"),
                8.0,
                false,
                false,
                true)));
        fixedPrices.add(MAPPER.valueToTree(fixedPrice(
                "校正价弯盘8",
                List.of("南岗区妇产医院"),
                List.of("弯盘"),
                8.0,
                false,
                false,
                true)));

        PricingEngine ngEngine = new PricingEngine(rules);

        PricingEngine.ProcessedResult kuobangScatter = ngEngine.processRow(row(
                "南岗区妇产医院",
                "额外包(纸塑袋)",
                "扩棒-1/z1026",
                "高温纸塑袋75*300",
                1,
                1,
                11.0,
                11.0
        ));
        assertThat(kuobangScatter.expectedUnitPrice).isEqualTo(8.0);
        assertThat(kuobangScatter.correctedTotalPrice).isEqualTo(8.0);

        PricingEngine.ProcessedResult kuobangBundle = ngEngine.processRow(row(
                "南岗区妇产医院",
                "额外包(纸塑袋)",
                "扩棒（4 4.5 5）-3/z7526",
                "高温纸塑袋75*300",
                3,
                1,
                16.5,
                16.5
        ));
        assertThat(kuobangBundle.expectedUnitPrice).isEqualTo(24.0);
        assertThat(kuobangBundle.correctedTotalPrice).isEqualTo(24.0);

        PricingEngine.ProcessedResult wanpanScatter = ngEngine.processRow(row(
                "南岗区妇产医院",
                "额外包(纸塑袋)",
                "弯盘-1/z2032",
                "高温纸塑袋75*300",
                1,
                1,
                9.1,
                9.1
        ));
        assertThat(wanpanScatter.expectedUnitPrice).isEqualTo(8.0);
        assertThat(wanpanScatter.correctedTotalPrice).isEqualTo(8.0);

        PricingEngine.ProcessedResult wanpanTwo = ngEngine.processRow(row(
                "南岗区妇产医院",
                "额外包(纸塑袋)",
                "弯盘-2/Z2032",
                "高温纸塑袋75*300",
                2,
                1,
                16.5,
                16.5
        ));
        assertThat(wanpanTwo.expectedUnitPrice).isEqualTo(16.0);
        assertThat(wanpanTwo.correctedTotalPrice).isEqualTo(16.0);
    }

    @Test
    void ngFuchanKuobangBundleForcesTwentyFourDespiteOriginalBillSixteenFive() throws Exception {
        ObjectNode rules = MAPPER.createObjectNode();
        rules.setAll((ObjectNode) defaultRules());
        ArrayNode fixedPrices = (ArrayNode) rules.path("specialRules").path("fixedPrices");
        Map<String, Object> bundle24 = new LinkedHashMap<>(fixedPrice(
                "校正价24.0",
                List.of("南岗区妇产医院"),
                List.of("扩棒（4 4.5 5）-3"),
                24.0,
                false,
                false,
                true));
        bundle24.put("matchMode", "first");
        bundle24.put("acceptedPrices", List.of(24.0));
        fixedPrices.add(MAPPER.valueToTree(bundle24));
        Map<String, Object> scatter8 = new LinkedHashMap<>(fixedPrice(
                "校正价扩棒8",
                List.of("南岗区妇产医院"),
                List.of("扩棒"),
                8.0,
                false,
                false,
                true));
        scatter8.put("excludeKeywords", List.of("扩棒（4 4.5 5）-3", "（4 4.5 5）-3"));
        fixedPrices.add(MAPPER.valueToTree(scatter8));

        PricingEngine ngEngine = new PricingEngine(rules);
        PricingEngine.ProcessedResult bundle = ngEngine.processRow(row(
                "南岗区妇产医院",
                "额外包(纸塑袋)",
                "扩棒（4 4.5 5）-3/z7526",
                "高温纸塑袋75*300",
                3,
                1,
                16.5,
                16.5
        ));
        assertThat(bundle.expectedUnitPrice).isEqualTo(24.0);
        assertThat(bundle.correctedTotalPrice).isEqualTo(24.0);
        assertThat(bundle.pricingRule).contains("校正价24.0");
    }

    @Test
    void ngFuchanPdfQuhuanbaoAndDaijiaZhiliaopanApplyFixedPrice() throws Exception {
        ObjectNode rules = MAPPER.createObjectNode();
        rules.setAll((ObjectNode) defaultRules());
        ArrayNode fixedPrices = (ArrayNode) rules.path("specialRules").path("fixedPrices");
        fixedPrices.add(MAPPER.valueToTree(fixedPrice(
                "PDF取环包33",
                List.of("南岗区妇产医院"),
                List.of("取环包"),
                33.0,
                false,
                false,
                true)));
        fixedPrices.add(MAPPER.valueToTree(fixedPrice(
                "PDF人流包33",
                List.of("南岗区妇产医院"),
                List.of("人流包"),
                33.0,
                false,
                false,
                true)));
        fixedPrices.add(MAPPER.valueToTree(fixedPrice(
                "PDF带架治疗盘16.5",
                List.of("南岗区妇产医院"),
                List.of("带架治疗"),
                16.5,
                false,
                false,
                true)));

        PricingEngine ngEngine = new PricingEngine(rules);

        PricingEngine.ProcessedResult quhuanbao = ngEngine.processRow(row(
                "南岗区妇产医院",
                "器械包",
                "取环包",
                "无纺布-60×60-50g",
                6,
                1,
                16.5,
                16.5
        ));
        assertThat(quhuanbao.expectedUnitPrice).isEqualTo(33.0);
        assertThat(quhuanbao.correctedTotalPrice).isEqualTo(33.0);

        PricingEngine.ProcessedResult renliubao = ngEngine.processRow(row(
                "南岗区妇产医院",
                "器械包",
                "人流包（90布）",
                "无纺布-90×90-50g",
                6,
                1,
                33.0,
                33.0
        ));
        assertThat(renliubao.expectedUnitPrice).isEqualTo(33.0);
        assertThat(renliubao.correctedTotalPrice).isEqualTo(33.0);

        PricingEngine.ProcessedResult daijia = ngEngine.processRow(row(
                "南岗区妇产医院",
                "额外包(纸塑袋)",
                "带架治疗盘-1",
                "高温纸塑袋75*300",
                2,
                1,
                11.0,
                11.0
        ));
        assertThat(daijia.expectedUnitPrice).isEqualTo(16.5);
        assertThat(daijia.correctedTotalPrice).isEqualTo(16.5);
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
    void duplicateFixedPriceRulesSelectByBillUnitPrice() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode lower = fixedPrices.addObject();
        lower.put("ruleId", 200L);
        lower.put("name", "刮勺探针4-低价");
        lower.put("price", 8.0);
        lower.putArray("hospitals").add("附二南岗");
        lower.putArray("keywords").add("刮勺探针4");
        lower.put("skipPackaging", true);

        ObjectNode higher = fixedPrices.addObject();
        higher.put("ruleId", 201L);
        higher.put("name", "刮勺探针4-高价");
        higher.put("price", 22.0);
        higher.putArray("hospitals").add("附二南岗");
        higher.putArray("keywords").add("刮勺探针4");
        higher.put("skipPackaging", true);

        PricingEngine engine = new PricingEngine(rules);

        PricingEngine.ProcessedResult atLower = engine.processRow(row(
                "附二南岗", "额外包(纸塑袋)", "刮勺探针4", "高温纸塑袋75*200",
                1, 1, 8, 8));
        assertThat(atLower.status).isEqualTo("unchanged");
        assertThat(atLower.matchedRuleId).isEqualTo(200L);

        PricingEngine.ProcessedResult atHigher = engine.processRow(row(
                "附二南岗", "额外包(纸塑袋)", "刮勺探针4", "高温纸塑袋75*200",
                1, 1, 22, 22));
        assertThat(atHigher.status).isEqualTo("unchanged");
        assertThat(atHigher.matchedRuleId).isEqualTo(201L);
    }

    @Test
    void anyPriceRulePreferredWhenListedAfterSinglePriceRule() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode single = fixedPrices.addObject();
        single.put("ruleId", 210L);
        single.put("name", "弯针-2默认");
        single.put("price", 8.0);
        single.putArray("hospitals").add("附二南岗");
        single.putArray("keywords").add("弯针-2");
        single.put("skipPackaging", true);

        ObjectNode multi = fixedPrices.addObject();
        multi.put("ruleId", 211L);
        multi.put("name", "弯针-2多报价");
        multi.put("price", 8.0);
        multi.put("matchMode", "any_price");
        multi.putArray("hospitals").add("附二南岗");
        multi.putArray("keywords").add("弯针-2");
        multi.putArray("acceptedPrices").add(8.0).add(13.5);
        multi.put("skipPackaging", true);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult upper = engine.processRow(row(
                "附二南岗", "额外包(纸塑袋)", "弯针-2", "高温纸塑袋75*200",
                1, 1, 13.5, 13.5));

        assertThat(upper.status).isEqualTo("unchanged");
        assertThat(upper.matchedRuleId).isEqualTo(211L);
        assertThat(upper.matchedPriceOption).isEqualTo(13.5);
        assertThat(upper.billingNotes.get("type")).isEqualTo("any_price_match");
    }

    @Test
    void zyyD2NgSpecialPricingForcesTargetDespiteLowOriginalBill() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ArrayNode fixedPrices = ((ObjectNode) rules.path("specialRules")).withArray("fixedPrices");
        fixedPrices.add(MAPPER.valueToTree(perPieceRule(
                301L, "附二南岗刮勺探针5.5元/把", "附二南岗", "刮勺探针", 5.5)));
        fixedPrices.add(MAPPER.valueToTree(fixedPriceRule(
                302L, "附二南岗弯针2", "附二南岗", "弯针-2", 13.5)));
        fixedPrices.add(MAPPER.valueToTree(fixedPriceRule(
                303L, "校正价35.0", "附二南岗", "小单", 35.0)));
        fixedPrices.add(MAPPER.valueToTree(fixedPriceRule(
                304L, "附二南岗椎间孔镜镜头", "附二南岗", "椎间孔镜镜头", 35.0)));
        fixedPrices.add(MAPPER.valueToTree(fixedPriceRule(
                305L, "附二南岗关节镜70度", "附二南岗", "关节镜70°", 28.0)));
        fixedPrices.add(MAPPER.valueToTree(fixedPriceRule(
                306L, "附二南岗关节镜30度", "附二南岗", "关节镜30°", 36.0)));

        PricingEngine engine = new PricingEngine(rules);
        assertSpecialPriceWarning(engine, row(
                "附二南岗", "额外包(纸塑袋)", "刮勺探针4/z1035", "高温纸塑袋75*200",
                1, 1, 8, 8), 22.0, "附二南岗刮勺探针5.5元/把");
        assertSpecialPriceWarning(engine, row(
                "附二南岗", "额外包(纸塑袋)", "刮勺探针3/z1035", "高温纸塑袋75*200",
                1, 1, 8, 8), 16.5, "附二南岗刮勺探针5.5元/把");
        assertSpecialPriceWarning(engine, row(
                "附二南岗", "额外包(纸塑袋)", "弯针-2/z1035", "高温纸塑袋75*200",
                1, 1, 8, 8), 13.5, "附二南岗弯针2");
        assertSpecialPriceWarning(engine, row(
                "附二南岗", "敷料包(无纺布)", "小单/w1250", "无纺布-125×125-50g",
                2, 2, 25, 50), 35.0, "校正价35.0");
        assertSpecialPriceWarning(engine, row(
                "附二南岗", "额外包(低温等离子)", "椎间孔镜镜头-1（双层）/z2060", "低温纸塑袋200*300",
                1, 1, 30, 30), 35.0, "附二南岗椎间孔镜镜头");
        assertSpecialPriceWarning(engine, row(
                "附二南岗", "额外包(低温等离子)", "关节镜70°镜头/z2060", "低温纸塑袋200*300",
                1, 1, 44, 44), 28.0, "附二南岗关节镜70度");
        assertSpecialPriceWarning(engine, row(
                "附二南岗", "额外包(低温等离子)", "关节镜30°镜头/z2060", "低温纸塑袋200*300",
                1, 1, 44, 44), 36.0, "附二南岗关节镜30度");
    }

    private static Map<String, Object> fixedPriceRule(long id, String name, String hospital,
                                                      String keyword, double price) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("ruleId", id);
        rule.put("name", name);
        rule.put("price", price);
        rule.put("matchMode", "first");
        rule.put("hospitals", List.of(hospital));
        rule.put("keywords", List.of(keyword));
        rule.put("skipPackaging", true);
        rule.put("skipDiscount", true);
        return rule;
    }

    private static Map<String, Object> perPieceRule(long id, String name, String hospital,
                                                    String keyword, double unitPrice) {
        Map<String, Object> rule = fixedPriceRule(id, name, hospital, keyword, unitPrice);
        rule.put("pricePerInstrument", true);
        return rule;
    }

    private static void assertSpecialPriceWarning(PricingEngine engine, Map<String, Object> row,
                                                    double expectedUnit, String ruleFragment) {
        PricingEngine.ProcessedResult result = engine.processRow(row);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.expectedUnitPrice).isEqualTo(expectedUnit);
        assertThat(result.pricingRule).contains(ruleFragment);
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
    void ngjyZeroDressingPackWithoutMaterialFlagsWarning() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode zeroOverrides = specialRules.withArray("zeroPriceOverrides");
        ObjectNode zeroRule = zeroOverrides.addObject();
        zeroRule.put("name", "九院敷料包0元覆盖15");
        zeroRule.putArray("hospitals").add("哈尔滨市南岗区人民医院（九院）");
        zeroRule.putArray("keywords").add("敷料包");
        zeroRule.put("price", 15.0);
        zeroRule.put("skipPackaging", true);

        PricingEngine ngjyEngine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = ngjyEngine.processRow(ngjyDressingPackRow(
                "手术室", 0, 1, 0, 0));

        assertThat(result.status).isEqualTo("warning");
        assertThat(result.expectedUnitPrice).isEqualTo(15.0);
        assertThat(result.difference).isEqualTo(15.0);
        assertThat(result.notes).anyMatch(n -> n.contains("0 元导入"));
    }

    @Test
    void wjZeroPricePaperPlasticDressingPackUsesEightYuan() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        ObjectNode pathOverride = billingProfile.putObject("pathOverride");
        pathOverride.put("zeroPriceMode", "packaging_type");
        ArrayNode fixedPrices = ((ObjectNode) rules.path("specialRules")).withArray("fixedPrices");
        ObjectNode fixed = fixedPrices.addObject();
        fixed.put("name", "校正价8.0");
        fixed.putArray("hospitals").add("武警黑龙江省总队医院");
        fixed.putArray("keywords").add("器械包（纸塑袋）").add("敷料包（纸塑袋）");
        fixed.put("price", 8.0);
        fixed.put("skipPackaging", true);

        PricingEngine wjEngine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = wjEngine.processRow(row(
                "武警黑龙江省总队医院",
                "敷料包(纸塑袋)",
                "敷料包(纸塑袋)",
                "",
                1,
                1,
                0,
                0));

        assertThat(result.expectedUnitPrice).isEqualTo(8.0);
        assertThat(result.notes).anyMatch(n -> n.contains("校正价8.0") || n.contains("纸塑袋"));
    }

    @Test
    void wjZeroPriceNonWovenInstrumentPackUsesTwentyYuan() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.putObject("pathOverride").put("zeroPriceMode", "packaging_type");
        ArrayNode fixedPrices = ((ObjectNode) rules.path("specialRules")).withArray("fixedPrices");
        ObjectNode fixed = fixedPrices.addObject();
        fixed.put("name", "校正价20.0");
        fixed.putArray("hospitals").add("武警黑龙江省总队医院");
        fixed.putArray("keywords").add("器械包（无纺布）").add("敷料包（无纺布）");
        fixed.put("price", 20.0);
        fixed.put("skipPackaging", true);

        PricingEngine wjEngine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = wjEngine.processRow(row(
                "武警黑龙江省总队医院",
                "敷料包(无纺布包)",
                "器械包 (无纺布)",
                "",
                2,
                1,
                0,
                0));

        assertThat(result.expectedUnitPrice).isEqualTo(20.0);
    }

    @Test
    void songdianChezhenFoldsWithoutDepartmentRestriction() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ArrayNode foldRules = ((ObjectNode) rules.path("specialRules")).withArray("foldRules");
        ObjectNode fold = foldRules.addObject();
        fold.put("name", "松电车针5件算1件");
        fold.putArray("hospitals").add("哈尔滨道外区松电慢性病专科门诊部");
        fold.putArray("keywords").add("车针");
        fold.put("threshold", 5);
        fold.put("foldRatio", 5);

        PricingEngine sdEngine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = sdEngine.processRow(row(
                "哈尔滨道外区松电慢性病专科门诊部",
                "额外包(纸塑袋)",
                "车针-32/Z7520",
                "高温纸塑袋75*200",
                32,
                1,
                66,
                66));

        assertThat(result.notes).anyMatch(n -> n.contains("松电车针5件算1件") && n.contains("折算为 7 件"));
    }

    @Test
    void ngjyZeroDressingPackWithBillingDisabledStaysUnchanged() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("enabled", false);

        PricingEngine disabledEngine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = disabledEngine.processRow(ngjyDressingPackRow(
                "手术室", 0, 1, 0, 0));

        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.expectedUnitPrice).isEqualTo(0.0);
        assertThat(result.difference).isEqualTo(0.0);
        assertThat(result.pricingRule).isEqualTo("特色账单已关闭");
    }

    private static Map<String, Object> ngjyDressingPackRow(
            String department,
            int instrumentCount,
            int packCount,
            double unitPrice,
            double totalPrice) {
        Map<String, Object> row = row(
                "哈尔滨市南岗区人民医院（九院）",
                "敷料包",
                "敷料包",
                "",
                instrumentCount,
                packCount,
                unitPrice,
                totalPrice);
        row.put("department", department);
        return row;
    }

    @Test
    void dressingPaperPlasticPackKeepsOriginalUnitPrice() {
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "南岗区先锋路社区卫生服务中心",
                "敷料包(纸塑袋)",
                "引流条/Z7520",
                "高温纸塑袋75*200",
                0,
                2,
                2.5,
                5.0));

        assertThat(result.expectedUnitPrice).isEqualTo(2.5);
        assertThat(result.correctedTotalPrice).isEqualTo(5.0);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.pricingRule).contains("敷料包(纸塑袋)");
        assertThat(result.notes).anyMatch(n -> n.contains("原单价"));
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
    void fuyiCapModePricesHighTempPaperPlastic() throws Exception {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode htPaper = (ObjectNode) rules.path("highTemperature").path("paperPlastic");
        htPaper.put("capMode", "fuyi");
        htPaper.put("perPackagePrice", 4.4);
        ArrayNode bagSizes = htPaper.withArray("bagSizes");
        bagSizes.removeAll();
        bagSizes.addObject().put("size", 15).put("price", 8.79)
                .set("keywords", MAPPER.createArrayNode().add("15cm"));

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult one = engine.processRow(row(
                "附一", "额外包(纸塑袋)", "包", "高温纸塑袋150*260", 1, 1, 8.79, 8.79));
        assertThat(one.expectedUnitPrice).isEqualTo(8.79);

        PricingEngine.ProcessedResult eight = engine.processRow(row(
                "附一", "额外包(纸塑袋)", "包", "高温纸塑袋150*260", 8, 1, 10.4, 83.2));
        assertThat(eight.expectedUnitPrice).isEqualTo(35.2);
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
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.expectedUnitPrice).isEqualTo(22.0);
    }

    @Test
    void hrbCjDressingAndSiliconeFixedPriceAtPricingStage() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ArrayNode fixedPrices = (ArrayNode) rules.path("specialRules").path("fixedPrices");
        ObjectNode dressing = fixedPrices.addObject();
        dressing.put("name", "长健敷料包W12050");
        dressing.putArray("hospitals").add("哈尔滨长健医院");
        dressing.putArray("keywords").add("敷料包/W12050");
        dressing.put("price", 35);
        dressing.put("skipPackaging", true);
        ObjectNode silicone = fixedPrices.addObject();
        silicone.put("name", "长健硅胶珠子22");
        silicone.putArray("hospitals").add("哈尔滨长健医院");
        silicone.putArray("keywords").add("硅胶珠子7号");
        silicone.put("price", 22);
        silicone.put("skipPackaging", true);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult dressingResult = engine.processRow(row(
                "哈尔滨长健医院", "敷料包(无纺布包)", "包", "W12050", 1, 1, 35, 35));
        assertThat(dressingResult.expectedUnitPrice).isEqualTo(35.0);
        assertThat(dressingResult.status).isEqualTo("unchanged");

        PricingEngine.ProcessedResult siliconeResult = engine.processRow(row(
                "哈尔滨长健医院", "器械包(ZSD)", "硅胶珠子7号-1/z1026", "低温纸塑袋20cm", 10, 10, 22, 220));
        assertThat(siliconeResult.expectedUnitPrice).isEqualTo(22.0);
        assertThat(siliconeResult.status).isEqualTo("unchanged");
    }

    @Test
    void cottonBallPaperPlastic15cmShouldBe2_5() {
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "道外区人民医院",
                "敷料包(纸塑袋)",
                "棉球/Z1526",
                "高温纸塑袋150*260",
                0, 1, 2.5, 2.5));

        assertThat(result.expectedUnitPrice).isEqualTo(2.5);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.pricingRule).contains("15cm");
    }

    @Test
    void cottonBallJar25cmShouldFallbackTo10_5() {
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "道外区人民医院",
                "额外包(纸塑袋)",
                "棉球缸-1/z2530",
                "高温纸塑袋250*300",
                1, 1, 16.0, 16.0));

        assertThat(result.expectedUnitPrice).isEqualTo(10.5);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.pricingRule).contains("25cm");
    }

    @Test
    void cottonBallUnrecognizedSpecWithMatchingPriceIsUnchanged() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "南岗区妇产医院",
                "敷料包(纸塑袋)",
                "棉球",
                "",
                3, 3, 4, 12));

        assertThat(result.expectedUnitPrice).isEqualTo(4.0);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.pricingRule).contains("未识别规格");
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

    @Test
    void meihanmeiLiposuctionNeedleAbove20cmBillsByInstrumentCount() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode foldRules = specialRules.withArray("foldRules");
        ObjectNode fold = foldRules.addObject();
        fold.put("name", "美涵吸脂针20cm以下5件算1件");
        fold.putArray("hospitals").add("哈尔滨美涵美医疗美容有限公司");
        fold.putArray("keywords").add("型号20cm以下").add("20cm以下");
        fold.put("threshold", 5);
        fold.put("foldRatio", 5);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "哈尔滨美涵美医疗美容有限公司",
                "额外包(纸塑袋)",
                "吸脂针(型号20cm以上)-7件/z1035",
                "高温纸塑袋75*200",
                7,
                1,
                38.5,
                38.5
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(38.5);
        assertThat(result.correctedTotalPrice).isEqualTo(38.5);
        assertThat(result.notes).noneMatch(n -> n.contains("小件关键词") && n.contains("折算"));
    }

    @Test
    void meihanmeiLiposuctionNeedleBelow20cmFoldsFiveToOne() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode foldRules = specialRules.withArray("foldRules");
        ObjectNode fold = foldRules.addObject();
        fold.put("name", "美涵吸脂针20cm以下5件算1件");
        fold.putArray("hospitals").add("哈尔滨美涵美医疗美容有限公司");
        fold.putArray("keywords").add("型号20cm以下").add("20cm以下");
        fold.put("threshold", 5);
        fold.put("foldRatio", 5);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "哈尔滨美涵美医疗美容有限公司",
                "额外包(纸塑袋)",
                "吸脂针(型号20cm以下)-5件/z1029",
                "高温纸塑袋75*200",
                5,
                1,
                8,
                8
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(8.0);
        assertThat(result.correctedTotalPrice).isEqualTo(8.0);
        assertThat(result.notes).anyMatch(n -> n.contains("美涵吸脂针20cm以下5件算1件"));
    }

    @Test
    void hrbCjHighTempPaperPlasticChargesFivePointFivePerItemFromThreePieces() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("enabled", true);
        billingProfile.put("pricingMode", "standard");

        PricingEngine pricingEngine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = pricingEngine.processRow(row(
                "哈尔滨长健医院",
                "额外包(纸塑袋)",
                "尿道探子-14/w6050",
                "高温纸塑袋75*300",
                14,
                1,
                77,
                77));

        assertThat(result.expectedUnitPrice).isEqualTo(77.0);
        assertThat(result.notes).anyMatch(n -> n.contains("5.5"));
        assertThat(result.status).isEqualTo("unchanged");
    }

    @Test
    void hrbCjSurgicalPackUsesExplicitFivePointFivePerInstrumentRule() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("enabled", true);
        billingProfile.put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode tierRule = fixedPrices.addObject();
        tierRule.put("name", "手术包5.5元/件");
        tierRule.putArray("hospitals").add("哈尔滨长健医院");
        tierRule.putArray("keywords").add("手术包");
        tierRule.put("price", 5.5);
        tierRule.put("pricePerInstrument", true);
        tierRule.put("temperature", "HT");
        tierRule.put("skipPackaging", true);
        tierRule.put("skipHospitalDiscount", true);

        PricingEngine pricingEngine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = pricingEngine.processRow(row(
                "哈尔滨长健医院",
                "器械包(ZSD)",
                "手术包（二）",
                "",
                43,
                1,
                231,
                231));

        assertThat(result.expectedUnitPrice).isEqualTo(236.5);
        assertThat(result.notes).anyMatch(n -> n.contains("手术包5.5元/件"));
        assertThat(result.status).isEqualTo("warning");
    }

    @Test
    void hrbCjSurgicalPackChargesFivePointFivePerPieceWithoutMinCount() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("enabled", true);
        billingProfile.put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode tierRule = fixedPrices.addObject();
        tierRule.put("name", "手术包5.5元/件");
        tierRule.putArray("hospitals").add("哈尔滨长健医院");
        tierRule.putArray("keywords").add("手术包");
        tierRule.put("price", 5.5);
        tierRule.put("pricePerInstrument", true);
        tierRule.put("temperature", "HT");
        tierRule.put("skipPackaging", true);
        tierRule.put("skipHospitalDiscount", true);

        PricingEngine pricingEngine = new PricingEngine(rules);

        PricingEngine.ProcessedResult onePiece = pricingEngine.processRow(row(
                "哈尔滨长健医院",
                "器械包(ZSD)",
                "手术包-1件",
                "",
                1,
                1,
                5.5,
                5.5));
        assertThat(onePiece.expectedUnitPrice).isEqualTo(5.5);
        assertThat(onePiece.status).isEqualTo("unchanged");

        PricingEngine.ProcessedResult twoPieces = pricingEngine.processRow(row(
                "哈尔滨长健医院",
                "器械包(ZSD)",
                "手术包-2件",
                "",
                2,
                1,
                11,
                11));
        assertThat(twoPieces.expectedUnitPrice).isEqualTo(11.0);
        assertThat(twoPieces.status).isEqualTo("unchanged");
    }

    @Test
    void hlfbSfChezhenFiveNeedlesFixedAtThirteenPointFive() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("enabled", true);
        billingProfile.put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode fixed = fixedPrices.addObject();
        fixed.put("name", "校正价13.5");
        fixed.putArray("hospitals").add("黑龙江省妇幼保健院（人口）");
        fixed.putArray("keywords").add("车针");
        fixed.put("price", 13.5);
        fixed.put("skipPackaging", true);
        fixed.put("skipDiscount", true);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "黑龙江省妇幼保健院（人口）",
                "额外包(纸塑袋)",
                "车针-5/Z7520",
                "高温纸塑袋75*200",
                5,
                1,
                8,
                8));

        assertThat(result.expectedUnitPrice).isEqualTo(13.5);
        assertThat(result.correctedTotalPrice).isEqualTo(13.5);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.notes).anyMatch(n -> n.contains("校正价13.5"));
    }

    @Test
    void hljJygljWeikeJiaqianFixedAtThirtyFive() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode fixed = fixedPrices.addObject();
        fixed.put("name", "微克夹钳35元");
        fixed.putArray("hospitals").add("省监狱管理局医院");
        fixed.putArray("keywords").add("微克夹钳");
        fixed.put("price", 35.0);
        fixed.put("skipPackaging", true);
        fixed.put("skipHospitalDiscount", true);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "省监狱管理局医院",
                "额外包(ETO)",
                "微克夹钳-1/双z2060",
                "",
                1,
                1,
                28,
                28));

        assertThat(result.expectedUnitPrice).isEqualTo(35.0);
        assertThat(result.correctedTotalPrice).isEqualTo(35.0);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.notes).anyMatch(n -> n.contains("微克夹钳35元"));
    }

    @Test
    void songdianOralChezhen32FoldsToSevenBillableUnits() {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("enabled", true);
        billingProfile.put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode foldRules = specialRules.withArray("foldRules");
        ObjectNode fold = foldRules.addObject();
        fold.put("name", "松电口腔科针类5件算1件");
        fold.putArray("hospitals").add("哈尔滨道外区松电慢性病专科门诊部");
        fold.putArray("keywords").add("车针");
        fold.put("threshold", 5);
        fold.put("foldRatio", 5);
        fold.putArray("departments").add("口腔科");

        PricingEngine engine = new PricingEngine(rules);
        Map<String, Object> data = row(
                "哈尔滨道外区松电慢性病专科门诊部",
                "额外包(纸塑袋)",
                "车针-32/Z7520",
                "高温纸塑袋75*200",
                32,
                1,
                66,
                66
        );
        data.put("department", "口腔科");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.notes).anyMatch(n -> n.contains("松电口腔科针类5件算1件") && n.contains("折算为 7 件"));
        assertThat(result.expectedUnitPrice).isEqualTo(38.5);
        assertThat(result.correctedTotalPrice).isEqualTo(38.5);
        assertThat(result.status).isEqualTo("warning");
    }

    @Test
    void songdianOralKuodazhen17FoldsToFourBillableUnits() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode foldRules = specialRules.withArray("foldRules");
        ObjectNode fold = foldRules.addObject();
        fold.put("name", "松电口腔科针类5件算1件");
        fold.putArray("hospitals").add("哈尔滨道外区松电慢性病专科门诊部");
        fold.putArray("keywords").add("扩大针");
        fold.put("threshold", 5);
        fold.put("foldRatio", 5);
        fold.putArray("departments").add("口腔科");

        PricingEngine engine = new PricingEngine(rules);
        Map<String, Object> data = row(
                "哈尔滨道外区松电慢性病专科门诊部",
                "额外包(纸塑袋)",
                "扩大针-17/Z7520",
                "高温纸塑袋75*200",
                17,
                1,
                13.5,
                13.5
        );
        data.put("department", "口腔科");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.notes).anyMatch(n -> n.contains("松电口腔科针类5件算1件") && n.contains("折算为 4 件"));
        assertThat(result.expectedUnitPrice).isEqualTo(22.0);
        assertThat(result.correctedTotalPrice).isEqualTo(22.0);
        assertThat(result.status).isEqualTo("warning");
    }

    @Test
    void neauOralChezhen62FoldsToThirteenBillableUnits() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode foldRules = specialRules.withArray("foldRules");
        ObjectNode fold = foldRules.addObject();
        fold.put("name", "东北农大口腔科针类5件算1件");
        fold.putArray("hospitals").add("东北农业大学医院");
        fold.putArray("keywords").add("车针");
        fold.put("threshold", 5);
        fold.put("foldRatio", 5);
        fold.putArray("departments").add("口腔科");

        PricingEngine engine = new PricingEngine(rules);
        Map<String, Object> data = row(
                "东北农业大学医院",
                "额外包(纸塑袋)",
                "车针-62/Z7520",
                "高温纸塑袋75*200",
                62,
                1,
                27.5,
                27.5
        );
        data.put("department", "口腔科");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.notes).anyMatch(n -> n.contains("折算为 13 件"));
        assertThat(result.expectedUnitPrice).isEqualTo(71.5);
        assertThat(result.status).isEqualTo("warning");
    }

    @Test
    void oralNeedleFoldSkipsWhenDepartmentMismatch() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode foldRules = specialRules.withArray("foldRules");
        ObjectNode fold = foldRules.addObject();
        fold.put("name", "松电口腔科针类5件算1件");
        fold.putArray("hospitals").add("哈尔滨道外区松电慢性病专科门诊部");
        fold.putArray("keywords").add("车针");
        fold.put("threshold", 5);
        fold.put("foldRatio", 5);
        fold.putArray("departments").add("口腔科");

        PricingEngine engine = new PricingEngine(rules);
        Map<String, Object> data = row(
                "哈尔滨道外区松电慢性病专科门诊部",
                "额外包(纸塑袋)",
                "车针-32/Z7520",
                "高温纸塑袋75*200",
                32,
                1,
                66,
                66
        );
        data.put("department", "内科");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.notes).noneMatch(n -> n.contains("松电口腔科针类5件算1件"));
        // 非口腔科仍可能命中全局 needle 小件规则，此处只断言客户专属 FOLD 未生效
    }

    @Test
    void wuchangOrCaozuoqiChargesTwentyTwoPerPieceInOperatingRoom() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode tierRule = fixedPrices.addObject();
        tierRule.put("name", "手术室操作器22元/件");
        tierRule.putArray("hospitals").add("五常市人民医院");
        tierRule.putArray("keywords").add("操作器");
        tierRule.put("price", 22);
        tierRule.put("pricePerInstrument", true);
        tierRule.put("skipPackaging", true);
        tierRule.put("skipHospitalDiscount", true);
        tierRule.putArray("departments").add("手术室");

        PricingEngine engine = new PricingEngine(rules);
        Map<String, Object> data = row(
                "五常市人民医院",
                "额外包(纸塑袋)",
                "操作器-3件/z2030",
                "高温纸塑袋75*200",
                3,
                1,
                66,
                66
        );
        data.put("department", "手术室");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.expectedUnitPrice).isEqualTo(66.0);
        assertThat(result.correctedTotalPrice).isEqualTo(66.0);
        assertThat(result.notes).anyMatch(n -> n.contains("手术室操作器22元/件"));
        assertThat(result.status).isEqualTo("unchanged");
    }

    @Test
    void wuchangOrLaparoscopicPackFixedAt187EvenWithTwelveInstruments() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode tierRule = fixedPrices.addObject();
        tierRule.put("name", "手术室腹腔镜器械包187元/包");
        tierRule.putArray("hospitals").add("五常市人民医院");
        tierRule.putArray("keywords").add("腹腔镜器械");
        tierRule.put("price", 187);
        tierRule.put("skipPackaging", true);
        tierRule.put("skipHospitalDiscount", true);
        tierRule.putArray("departments").add("手术室");

        PricingEngine engine = new PricingEngine(rules);
        Map<String, Object> data = row(
                "五常市人民医院",
                "器械包(低温等离子)",
                "腹腔镜器械（三号）-11件/W6050",
                "无纺布-120×120-60g",
                12,
                1,
                187,
                187
        );
        data.put("department", "手术室");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.expectedUnitPrice).isEqualTo(187.0);
        assertThat(result.correctedTotalPrice).isEqualTo(187.0);
        assertThat(result.notes).anyMatch(n -> n.contains("手术室腹腔镜器械包187元/包"));
        assertThat(result.status).isEqualTo("unchanged");
    }

    @Test
    void wuchangOrGuyuanzhenFoldsFiveToOneInOperatingRoom() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode foldRules = specialRules.withArray("foldRules");
        ObjectNode fold = foldRules.addObject();
        fold.put("name", "手术室骨元针5件算1件");
        fold.putArray("hospitals").add("五常市人民医院");
        fold.putArray("keywords").add("骨元针");
        fold.put("threshold", 5);
        fold.put("foldRatio", 5);
        fold.putArray("departments").add("手术室");

        PricingEngine engine = new PricingEngine(rules);
        Map<String, Object> data = row(
                "五常市人民医院",
                "额外包(纸塑袋)",
                "1.8骨元针-24/z7530",
                "高温纸塑袋75*300",
                24,
                1,
                27.5,
                27.5
        );
        data.put("department", "手术室");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.notes).anyMatch(n -> n.contains("手术室骨元针5件算1件") && n.contains("折算为 5 件"));
        assertThat(result.expectedUnitPrice).isEqualTo(27.5);
        assertThat(result.correctedTotalPrice).isEqualTo(27.5);
        assertThat(result.status).isEqualTo("unchanged");
    }

    @Test
    void wuchangExtraBag75PaperPlasticFixedAtTenPointFive() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode rule = fixedPrices.addObject();
        rule.put("name", "额外包75纸塑袋10.5");
        rule.putArray("hospitals").add("五常市人民医院");
        rule.putArray("keywords").add("75/双").add("/z7526").add("/z7530").add("/z7534");
        rule.putArray("excludeKeywords").add("墨希钉");
        rule.put("price", 10.5);
        rule.put("minInstrumentCount", 1);
        rule.put("maxInstrumentCount", 1);
        rule.put("skipPackaging", true);
        rule.put("skipHospitalDiscount", true);

        PricingEngine engine = new PricingEngine(rules);
        for (String packName : List.of(
                "10R墨希T型胫骨平台近侧-1件/双/z7526",
                "11L胫骨平台外侧支持板-1件/双/z7530")) {
            PricingEngine.ProcessedResult result = engine.processRow(row(
                    "五常市人民医院",
                    "额外包(纸塑袋)",
                    packName,
                    "高温纸塑袋75*200",
                    1,
                    1,
                    10.5,
                    10.5));

            assertThat(result.expectedUnitPrice).isEqualTo(10.5);
            assertThat(result.correctedTotalPrice).isEqualTo(10.5);
            assertThat(result.notes).anyMatch(n -> n.contains("额外包75纸塑袋10.5"));
            assertThat(result.status).isEqualTo("unchanged");
        }
    }

    @Test
    void wuchangExtraBag10PaperPlasticFixedAtThirteenPointFive() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode rule = fixedPrices.addObject();
        rule.put("name", "额外包10纸塑袋13.5");
        rule.putArray("hospitals").add("五常市人民医院");
        rule.putArray("keywords").add("10/双").add("/z1026").add("/z1029").add("/z1035");
        rule.put("price", 13.5);
        rule.put("minInstrumentCount", 1);
        rule.put("maxInstrumentCount", 1);
        rule.put("skipPackaging", true);
        rule.put("skipHospitalDiscount", true);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "五常市人民医院",
                "额外包(纸塑袋)",
                "探针-1/Z1026",
                "高温纸塑袋10cm",
                1,
                1,
                13.5,
                13.5));

        assertThat(result.expectedUnitPrice).isEqualTo(13.5);
        assertThat(result.correctedTotalPrice).isEqualTo(13.5);
        assertThat(result.notes).anyMatch(n -> n.contains("额外包10纸塑袋13.5"));
        assertThat(result.status).isEqualTo("unchanged");
    }

    @ParameterizedTest
    @MethodSource("wuchangInstrumentPackRowsThatMustStayUnchanged")
    void wuchangInstrumentPackPricesStayUnchanged(
            String packName, String type, String material, int instrumentCount, double unitPrice) {
        PricingEngine engine = new PricingEngine(wuchangPackPriceFixRules());
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "五常市人民医院",
                type,
                packName,
                material,
                instrumentCount,
                1,
                unitPrice,
                unitPrice));

        assertThat(result.expectedUnitPrice).isEqualTo(unitPrice);
        assertThat(result.correctedTotalPrice).isEqualTo(unitPrice);
        assertThat(result.status).isEqualTo("unchanged");
    }

    static Stream<Object[]> wuchangInstrumentPackRowsThatMustStayUnchanged() {
        return Stream.of(
                new Object[]{"空心钉4.0", "器械包", "无纺布-70×70-60g", 26, 233.0},
                new Object[]{"优贝特空心钉4.0", "器械包", "无纺布-70×70-60g", 26, 233.0},
                new Object[]{"上肢锁定", "器械包", "无纺布-70×70-60g", 190, 332.0},
                new Object[]{"上肢锁定", "器械包", "无纺布-70×70-60g", 196, 332.0},
                new Object[]{"上肢锁定", "器械包", "无纺布-70×70-60g", 178, 332.0},
                new Object[]{"上肢锁定长钉", "器械包", "无纺布-70×70-60g", 202, 370.5},
                new Object[]{"墨希钉-23/双/z7520", "额外包(纸塑袋)", "高温纸塑袋75*200", 23, 50.0}
        );
    }

    @Test
    void wuchangMoixiNailDoesNotMatchBroad75PaperPlasticKeyword() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode broad75Rule = fixedPrices.addObject();
        broad75Rule.put("name", "额外包75纸塑袋10.5");
        broad75Rule.putArray("hospitals").add("五常市人民医院");
        broad75Rule.putArray("keywords").add("/z7520").add("/z75");
        broad75Rule.put("price", 10.5);
        broad75Rule.put("minInstrumentCount", 1);
        broad75Rule.put("maxInstrumentCount", 1);
        broad75Rule.put("skipPackaging", true);
        broad75Rule.put("skipHospitalDiscount", true);

        ObjectNode moixiRule = fixedPrices.addObject();
        moixiRule.put("name", "墨希钉固定50");
        moixiRule.putArray("hospitals").add("五常市人民医院");
        moixiRule.putArray("keywords").add("墨希钉");
        moixiRule.put("price", 50);
        moixiRule.put("skipPackaging", true);
        moixiRule.put("skipHospitalDiscount", true);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "五常市人民医院",
                "额外包(纸塑袋)",
                "墨希钉-23/双/z7520",
                "高温纸塑袋75*200",
                23,
                1,
                50,
                50));

        assertThat(result.expectedUnitPrice).isEqualTo(50.0);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.notes).anyMatch(n -> n.contains("墨希钉固定50"));
        assertThat(result.notes).noneMatch(n -> n.contains("额外包75纸塑袋10.5"));
    }

    private static JsonNode wuchangPackPriceFixRules() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");

        ObjectNode youbeit = fixedPrices.addObject();
        youbeit.put("name", "优贝特空心钉4.0固定233");
        youbeit.putArray("hospitals").add("五常市人民医院");
        youbeit.putArray("keywords").add("优贝特空心钉4.0");
        youbeit.put("price", 233);
        youbeit.put("skipPackaging", true);
        youbeit.put("skipHospitalDiscount", true);

        ObjectNode hollow = fixedPrices.addObject();
        hollow.put("name", "空心钉4.0固定233");
        hollow.putArray("hospitals").add("五常市人民医院");
        hollow.putArray("keywords").add("空心钉4.0");
        hollow.putArray("excludeKeywords").add("优贝特");
        hollow.put("price", 233);
        hollow.put("skipPackaging", true);
        hollow.put("skipHospitalDiscount", true);

        ObjectNode longNail = fixedPrices.addObject();
        longNail.put("name", "上肢锁定长钉固定370.5");
        longNail.putArray("hospitals").add("五常市人民医院");
        longNail.putArray("keywords").add("上肢锁定长钉");
        longNail.put("price", 370.5);
        longNail.put("skipPackaging", true);
        longNail.put("skipHospitalDiscount", true);

        ObjectNode upperLock = fixedPrices.addObject();
        upperLock.put("name", "上肢锁定固定332");
        upperLock.putArray("hospitals").add("五常市人民医院");
        upperLock.putArray("keywords").add("上肢锁定");
        upperLock.putArray("excludeKeywords").add("长钉");
        upperLock.put("price", 332);
        upperLock.put("skipPackaging", true);
        upperLock.put("skipHospitalDiscount", true);

        ObjectNode moixi = fixedPrices.addObject();
        moixi.put("name", "墨希钉固定50");
        moixi.putArray("hospitals").add("五常市人民医院");
        moixi.putArray("keywords").add("墨希钉");
        moixi.put("price", 50);
        moixi.put("skipPackaging", true);
        moixi.put("skipHospitalDiscount", true);

        ObjectNode extra75 = fixedPrices.addObject();
        extra75.put("name", "额外包75纸塑袋10.5");
        extra75.putArray("hospitals").add("五常市人民医院");
        extra75.putArray("keywords").add("75/双").add("/z7526").add("/z7530").add("/z7534");
        extra75.putArray("excludeKeywords").add("墨希钉");
        extra75.put("price", 10.5);
        extra75.put("minInstrumentCount", 1);
        extra75.put("maxInstrumentCount", 1);
        extra75.put("skipPackaging", true);
        extra75.put("skipHospitalDiscount", true);

        return rules;
    }

    @Test
    void hrb2ndOrthodonticBurKeepsEightYuanAfterRemovingWrongRule() {
        PricingEngine engine = new PricingEngine(hrb2ndPricingFixRules());
        Map<String, Object> data = row(
                "哈尔滨市第二医院",
                "额外包(纸塑袋)",
                "正畸去胶车针-1/Z7520",
                "高温纸塑袋75*200",
                1,
                1,
                8,
                8
        );
        data.put("department", "口腔科（正）");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.expectedUnitPrice).isEqualTo(8.0);
        assertThat(result.correctedTotalPrice).isEqualTo(8.0);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.notes).anyMatch(n -> n.contains("口腔科正畸车针8元"));
        assertThat(result.notes).noneMatch(n -> n.contains("校正价5.5"));
    }

    @Test
    void hrb2ndKouqiangTiaodaoChargesEightYuanInDentalDepartment() {
        PricingEngine engine = new PricingEngine(hrb2ndPricingFixRules());
        Map<String, Object> data = row(
                "哈尔滨市第二医院",
                "额外包(纸塑袋)",
                "调刀-1/保z7530",
                "高温纸塑袋75*200",
                1,
                1,
                5.5,
                5.5
        );
        data.put("department", "口腔科(内)");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.expectedUnitPrice).isEqualTo(8.0);
        assertThat(result.correctedTotalPrice).isEqualTo(8.0);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.notes).anyMatch(n -> n.contains("口腔科调刀8元"));
    }

    @Test
    void hrb2ndOperatingRoomHemostaticBandChargesEightYuanPerPack() {
        PricingEngine engine = new PricingEngine(hrb2ndPricingFixRules());
        Map<String, Object> data = row(
                "哈尔滨市第二医院",
                "额外包(仅消毒)",
                "市二院止血带7个（只消毒）",
                "",
                7,
                1,
                0,
                0
        );
        data.put("department", "手术室");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.expectedUnitPrice).isEqualTo(8.0);
        assertThat(result.correctedTotalPrice).isEqualTo(8.0);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.notes).anyMatch(n -> n.contains("手术室止血带8元") || n.contains("校正价8.0"));
    }

    @Test
    void hrb2ndOperatingRoomElectrocauteryHookChargesTwentyTwoPerPiece() {
        PricingEngine engine = new PricingEngine(hrb2ndPricingFixRules());
        Map<String, Object> data = row(
                "哈尔滨市第二医院",
                "额外包(低温等离子)",
                "电凝钩吸引器-1/件双/z1060",
                "低温等离子",
                1,
                1,
                41.5,
                41.5
        );
        data.put("department", "手术室");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.expectedUnitPrice).isEqualTo(22.0);
        assertThat(result.correctedTotalPrice).isEqualTo(22.0);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.notes).anyMatch(n -> n.contains("手术室电凝钩22元/件"));
    }

    private static JsonNode hrb2ndPricingFixRules() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "special_only");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");

        ObjectNode orthoBur = fixedPrices.addObject();
        orthoBur.put("name", "口腔科正畸车针8元");
        orthoBur.putArray("hospitals").add("哈尔滨市第二医院");
        orthoBur.putArray("keywords").add("正畸去胶车针").add("正畸去版车针");
        orthoBur.put("price", 8);
        orthoBur.put("skipPackaging", true);
        orthoBur.put("skipHospitalDiscount", true);
        orthoBur.putArray("departments").add("口腔科");

        ObjectNode tiaodao = fixedPrices.addObject();
        tiaodao.put("name", "口腔科调刀8元");
        tiaodao.putArray("hospitals").add("哈尔滨市第二医院");
        tiaodao.putArray("keywords").add("调刀");
        tiaodao.put("price", 8);
        tiaodao.put("skipPackaging", true);
        tiaodao.put("skipHospitalDiscount", true);
        tiaodao.putArray("departments").add("口腔科");

        ObjectNode hemostatic = fixedPrices.addObject();
        hemostatic.put("name", "手术室止血带8元");
        hemostatic.putArray("hospitals").add("哈尔滨市第二医院");
        hemostatic.putArray("keywords").add("市二院止血带");
        hemostatic.put("price", 8);
        hemostatic.put("skipPackaging", true);
        hemostatic.put("skipHospitalDiscount", true);
        hemostatic.putArray("departments").add("手术室");

        ObjectNode legacyEight = fixedPrices.addObject();
        legacyEight.put("name", "校正价8.0");
        legacyEight.putArray("hospitals").add("哈尔滨市第二医院");
        legacyEight.putArray("keywords").add("市二院止血带14个（只消毒）").add("洁牙尖（保护）1抛光杯1");
        legacyEight.put("price", 8);
        legacyEight.put("skipPackaging", true);
        legacyEight.put("skipHospitalDiscount", true);

        ObjectNode electrocautery = fixedPrices.addObject();
        electrocautery.put("name", "手术室电凝钩22元/件");
        electrocautery.putArray("hospitals").add("哈尔滨市第二医院");
        electrocautery.putArray("keywords").add("电凝钩吸引器");
        electrocautery.put("price", 22);
        electrocautery.put("pricePerInstrument", true);
        electrocautery.put("skipPackaging", true);
        electrocautery.put("skipHospitalDiscount", true);
        electrocautery.putArray("departments").add("手术室");

        ObjectNode legacySixteen = fixedPrices.addObject();
        legacySixteen.put("name", "校正价16.5");
        legacySixteen.putArray("hospitals").add("哈尔滨市第二医院");
        legacySixteen.putArray("keywords").add("骨科拉钩-2");
        legacySixteen.put("price", 16.5);
        legacySixteen.put("skipPackaging", true);
        legacySixteen.put("skipHospitalDiscount", true);

        return rules;
    }

    @ParameterizedTest
    @MethodSource("sanjingNeilouPackRowsByInstrumentCount")
    void sanjingNeilouPackPriceByInstrumentCount(
            String packName, int instrumentCount, double unitPrice, double expectedPrice, String expectedRule) {
        PricingEngine engine = new PricingEngine(sanjingNeilouInstrumentCountRules());
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "三精肾病医院",
                "器械包(ZSD)",
                packName,
                "无纺布-60×60-50g",
                instrumentCount,
                2,
                unitPrice,
                unitPrice * 2));

        assertThat(result.expectedUnitPrice).isEqualTo(expectedPrice);
        assertThat(result.correctedTotalPrice).isEqualTo(expectedPrice * 2);
        assertThat(result.notes).anyMatch(n -> n.contains(expectedRule));
    }

    static Stream<Object[]> sanjingNeilouPackRowsByInstrumentCount() {
        return Stream.of(
                new Object[]{"内瘘器械包（一）", 66, 181.5, 99.0, "校正价99.0"},
                new Object[]{"内瘘器械包（一）", 68, 187.0, 102.0, "校正价102.0-内瘘68件"},
                new Object[]{"腹透包-34件（临时）/W9050", 34, 187.0, 102.0, "校正价102.0"}
        );
    }

    private static ObjectNode sanjingNeilouInstrumentCountRules() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
        ArrayNode fixedPrices = ((ObjectNode) rules.path("specialRules")).withArray("fixedPrices");
        ObjectNode rule99 = fixedPrices.addObject();
        rule99.put("name", "校正价99.0");
        rule99.putArray("hospitals").add("三精肾病医院");
        rule99.putArray("keywords").add("内瘘器械包（一）");
        rule99.put("price", 99.0);
        rule99.put("minInstrumentCount", 66);
        rule99.put("maxInstrumentCount", 66);
        rule99.put("skipPackaging", true);
        rule99.put("skipHospitalDiscount", true);

        ObjectNode rule102Neilou = fixedPrices.addObject();
        rule102Neilou.put("name", "校正价102.0-内瘘68件");
        rule102Neilou.putArray("hospitals").add("三精肾病医院");
        rule102Neilou.putArray("keywords").add("内瘘器械包（一）");
        rule102Neilou.put("price", 102.0);
        rule102Neilou.put("minInstrumentCount", 68);
        rule102Neilou.put("maxInstrumentCount", 68);
        rule102Neilou.put("skipPackaging", true);
        rule102Neilou.put("skipHospitalDiscount", true);

        ObjectNode rule102Futou = fixedPrices.addObject();
        rule102Futou.put("name", "校正价102.0");
        rule102Futou.putArray("hospitals").add("三精肾病医院");
        rule102Futou.putArray("keywords").add("腹透包-34件（临时）");
        rule102Futou.put("price", 102.0);
        rule102Futou.put("skipPackaging", true);
        rule102Futou.put("skipHospitalDiscount", true);
        return rules;
    }

    @Test
    void shengYyXfOperatingRoomThoracoscopy17PieceCharges178Point2() {
        PricingEngine engine = new PricingEngine(shengYyXfDeptPricingRules());
        Map<String, Object> data = row(
                "黑龙江省医院（香坊院区）",
                "器械包(ZSD)",
                "胸腔镜-17件（筐1）/w12050",
                "无纺布-60×60-50g",
                17,
                1,
                180.0,
                180.0
        );
        data.put("department", "手术室");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.expectedUnitPrice).isEqualTo(178.2);
        assertThat(result.correctedTotalPrice).isEqualTo(178.2);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.notes).anyMatch(n -> n.contains("手术室胸腔镜17件178.2"));
    }

    @Test
    void shengYyXfOperatingRoomExtraBagThoracoscopyCharges19Point8() {
        PricingEngine engine = new PricingEngine(shengYyXfDeptPricingRules());
        Map<String, Object> data = row(
                "黑龙江省医院（香坊院区）",
                "额外包(无纺布)",
                "胸腔镜-6件/w6050",
                "无纺布-60×60-50g",
                6,
                1,
                30.0,
                30.0
        );
        data.put("department", "手术室");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.expectedUnitPrice).isEqualTo(19.8);
        assertThat(result.correctedTotalPrice).isEqualTo(19.8);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.notes).anyMatch(n -> n.contains("手术室额外包胸腔镜19.8"));
    }

    @Test
    void shengYyXfReproductiveMedicineScrubSuitCharges18Yuan() {
        PricingEngine engine = new PricingEngine(shengYyXfDeptPricingRules());
        Map<String, Object> data = row(
                "黑龙江省医院（香坊院区）",
                "敷料包",
                "刷手服/W9050",
                "W9050",
                1,
                1,
                30.0,
                30.0
        );
        data.put("department", "生殖医学科");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.expectedUnitPrice).isEqualTo(18.0);
        assertThat(result.correctedTotalPrice).isEqualTo(18.0);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.notes).anyMatch(n -> n.contains("生殖医学科刷手服18元")
                || n.contains("生殖医学科W9050纺织18元"));
    }

    @Test
    void shengYyXfReproductiveMedicineW9050BedSheetCharges18Yuan() {
        PricingEngine engine = new PricingEngine(shengYyXfDeptPricingRules());
        Map<String, Object> data = row(
                "黑龙江省医院（香坊院区）",
                "敷料包",
                "床单/W9050",
                "W9050",
                1,
                2,
                30.0,
                60.0
        );
        data.put("department", "生殖医学科");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.expectedUnitPrice).isEqualTo(18.0);
        assertThat(result.correctedTotalPrice).isEqualTo(36.0);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.notes).anyMatch(n -> n.contains("生殖医学科W9050纺织18元"));
    }

    @Test
    void shengYyXfShenwaiGoudao2PieceCharges26Point4() {
        PricingEngine engine = new PricingEngine(shengYyXfShenwaiGoudaoRules());
        Map<String, Object> data = row(
                "黑龙江省医院（香坊院区）",
                "额外包(纸塑袋)",
                "勾刀-2/Z1030",
                "高温纸塑袋75*200",
                2,
                1,
                16.5,
                16.5
        );
        data.put("department", "神外一");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.expectedUnitPrice).isEqualTo(26.4);
        assertThat(result.correctedTotalPrice).isEqualTo(26.4);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.notes).anyMatch(n -> n.contains("神外一勾刀-2 26.4元"));
    }

    @Test
    void shengYyXfShenwaiGoudaoSinglePieceCharges13Point2() {
        PricingEngine engine = new PricingEngine(shengYyXfShenwaiGoudaoRules());
        Map<String, Object> data = row(
                "黑龙江省医院（香坊院区）",
                "额外包(纸塑袋)",
                "钩刀-1/z1030",
                "高温纸塑袋75*200",
                1,
                1,
                16.5,
                16.5
        );
        data.put("department", "神外一");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.expectedUnitPrice).isEqualTo(13.2);
        assertThat(result.correctedTotalPrice).isEqualTo(13.2);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.notes).anyMatch(n -> n.contains("神外一勾刀13.2元"));
    }

    @Test
    void shengYyXfShenwaiGoudaoRulesDoNotApplyToOtherDepartments() {
        PricingEngine engine = new PricingEngine(shengYyXfShenwaiGoudaoRules());
        Map<String, Object> data = row(
                "黑龙江省医院（香坊院区）",
                "额外包(纸塑袋)",
                "钩刀-2/z7526",
                "高温纸塑袋75*200",
                2,
                1,
                8.1,
                8.1
        );
        data.put("department", "手术室");

        PricingEngine.ProcessedResult result = engine.processRow(data);

        assertThat(result.notes).noneMatch(n -> n.contains("神外一勾刀"));
    }

    private static JsonNode shengYyXfShenwaiGoudaoRules() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "special_only");
        ArrayNode fixedPrices = ((ObjectNode) rules.path("specialRules")).withArray("fixedPrices");

        ObjectNode goudao2 = fixedPrices.addObject();
        goudao2.put("name", "神外一勾刀-2 26.4元");
        goudao2.putArray("hospitals").add("黑龙江省医院（香坊院区）");
        goudao2.putArray("keywords").add("勾刀-2").add("钩刀-2");
        goudao2.put("price", 26.4);
        goudao2.put("skipPackaging", true);
        goudao2.put("skipHospitalDiscount", true);
        goudao2.putArray("departments").add("神外一");

        ObjectNode goudao1 = fixedPrices.addObject();
        goudao1.put("name", "神外一勾刀13.2元");
        goudao1.putArray("hospitals").add("黑龙江省医院（香坊院区）");
        goudao1.putArray("keywords").add("勾刀").add("钩刀");
        goudao1.putArray("excludeKeywords").add("-2");
        goudao1.put("price", 13.2);
        goudao1.put("skipPackaging", true);
        goudao1.put("skipHospitalDiscount", true);
        goudao1.putArray("departments").add("神外一");

        return rules;
    }

    private static JsonNode shengYyXfDeptPricingRules() {
        ObjectNode rules = (ObjectNode) defaultRules();
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "special_only");
        ArrayNode fixedPrices = ((ObjectNode) rules.path("specialRules")).withArray("fixedPrices");

        ObjectNode thor17 = fixedPrices.addObject();
        thor17.put("name", "手术室胸腔镜17件178.2");
        thor17.putArray("hospitals").add("黑龙江省医院（香坊院区）");
        thor17.putArray("keywords").add("胸腔镜-17件");
        thor17.put("price", 178.2);
        thor17.put("minInstrumentCount", 17);
        thor17.put("maxInstrumentCount", 17);
        thor17.put("skipPackaging", true);
        thor17.put("skipHospitalDiscount", true);
        thor17.putArray("departments").add("手术室");

        ObjectNode extraBag = fixedPrices.addObject();
        extraBag.put("name", "手术室额外包胸腔镜19.8");
        extraBag.putArray("hospitals").add("黑龙江省医院（香坊院区）");
        extraBag.putArray("keywords").add("胸腔镜");
        extraBag.putArray("excludeKeywords").add("w12050").add("W12050").add("17件").add("21件").add("22件").add("32件");
        extraBag.put("price", 19.8);
        extraBag.put("skipPackaging", true);
        extraBag.put("skipHospitalDiscount", true);
        extraBag.putArray("departments").add("手术室");

        ObjectNode scrub = fixedPrices.addObject();
        scrub.put("name", "生殖医学科刷手服18元");
        scrub.putArray("hospitals").add("黑龙江省医院（香坊院区）");
        scrub.putArray("keywords").add("刷手服");
        scrub.put("price", 18.0);
        scrub.put("skipPackaging", true);
        scrub.put("skipHospitalDiscount", true);
        scrub.putArray("departments").add("生殖医学科");

        ObjectNode w9050Repro = fixedPrices.addObject();
        w9050Repro.put("name", "生殖医学科W9050纺织18元");
        w9050Repro.putArray("hospitals").add("黑龙江省医院（香坊院区）");
        w9050Repro.putArray("keywords").add("床单/W9050").add("敷料包/W9050").add("敷料包").add("刷手服/W9050");
        w9050Repro.put("price", 18.0);
        w9050Repro.put("skipPackaging", true);
        w9050Repro.put("skipHospitalDiscount", true);
        w9050Repro.putArray("departments").add("生殖医学科");

        ObjectNode legacy180 = fixedPrices.addObject();
        legacy180.put("name", "校正价180.0");
        legacy180.putArray("hospitals").add("黑龙江省医院（香坊院区）");
        legacy180.putArray("keywords").add("胸腔镜-").add("外一27件筐1").add("普镜-");
        legacy180.putArray("excludeKeywords").add("17件");
        legacy180.put("price", 180.0);
        legacy180.put("skipPackaging", true);
        legacy180.put("skipHospitalDiscount", true);

        ObjectNode w9050AnyPrice = fixedPrices.addObject();
        w9050AnyPrice.put("name", "W9050纺织现价");
        w9050AnyPrice.putArray("hospitals").add("黑龙江省医院（香坊院区）");
        w9050AnyPrice.putArray("keywords").add("床单/W9050").add("刷手服/W9050").add("敷料/W9050");
        w9050AnyPrice.put("price", 18.0);
        w9050AnyPrice.put("matchMode", "any_price");
        w9050AnyPrice.putArray("acceptedPrices").add(15).add(18);
        w9050AnyPrice.put("skipPackaging", true);
        w9050AnyPrice.put("skipHospitalDiscount", true);

        return rules;
    }

    @Test
    void xinfaLowTempLensFixedPriceMatchesBill() throws Exception {
        ObjectNode rules = (ObjectNode) defaultRules();
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");
        ObjectNode lens30 = fixedPrices.addObject();
        lens30.put("ruleId", 901L);
        lens30.put("name", "新发镜头30度35");
        lens30.put("price", 35.0);
        lens30.put("temperature", "LT");
        lens30.put("skipPackaging", true);
        lens30.putArray("hospitals").add("新发红十字医院");
        lens30.putArray("keywords").add("30度镜头");

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "新发红十字医院", "低温灭菌（纸塑袋）", "30度镜头-1/双/Z1550", "低温灭菌 20cm",
                1, 1, 35.0, 35.0));
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.pricingRule).contains("新发镜头30度35");
    }
}
