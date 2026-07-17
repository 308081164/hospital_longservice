package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regenerates hospital-billing-golden-rows.json from PricingEngine output.
 * Run: mvn test -Dtest=GoldenRowsGeneratorTest#writeGoldenRowsFile
 */
class GoldenRowsGeneratorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record CaseDef(String id, String description, Map<String, Object> input, JsonNode rulesOverlay) {}

    @Test
    @Disabled("manual: mvn test -Dtest=GoldenRowsGeneratorTest#writeGoldenRowsFile")
    void writeGoldenRowsFile() throws Exception {
        ObjectNode root = buildDocument();
        Path out = Path.of("src/test/resources/hospital-billing-golden-rows.json");
        Files.writeString(out, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + System.lineSeparator());
    }

    static ObjectNode buildDocument() throws Exception {
        List<CaseDef> defs = new ArrayList<>();
        defs.addAll(hospitalBlocks());
        defs.addAll(overlayBlocks());

        ArrayNode cases = MAPPER.createArrayNode();
        for (CaseDef def : defs) {
            JsonNode rules = buildRulesForCase(def.rulesOverlay());
            PricingEngine caseEngine = new PricingEngine(rules);
            PricingEngine.ProcessedResult result = caseEngine.processRow(def.input());

            ObjectNode caseNode = MAPPER.createObjectNode();
            caseNode.put("id", def.id());
            caseNode.put("description", def.description());
            ObjectNode input = MAPPER.createObjectNode();
            def.input().forEach((k, v) -> {
                if (!"hospitalName".equals(k)) {
                    input.set(k, MAPPER.valueToTree(v));
                }
            });
            input.put("hospitalName", String.valueOf(def.input().get("hospitalName")));
            caseNode.set("input", input);

            ObjectNode expected = MAPPER.createObjectNode();
            if (result.expectedUnitPrice != null) {
                expected.put("expectedUnitPrice", result.expectedUnitPrice);
            }
            if (result.correctedTotalPrice != null) {
                expected.put("correctedTotalPrice", result.correctedTotalPrice);
            }
            expected.put("status", result.status);
            if (result.matchedRuleId != null) {
                expected.put("matchedRuleId", result.matchedRuleId);
            }
            if (result.matchedPriceOption != null) {
                expected.put("matchedPriceOption", result.matchedPriceOption);
            }
            appendNotesExpectations(expected, def.id());
            if (result.billingNotes != null && !result.billingNotes.isEmpty()) {
                expected.set("billingNotes", MAPPER.valueToTree(result.billingNotes));
            }
            caseNode.set("expected", expected);
            if (def.rulesOverlay() != null && !def.rulesOverlay().isEmpty()) {
                caseNode.set("rulesOverlay", def.rulesOverlay());
            }
            cases.add(caseNode);
        }

        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", "phase0-v2");
        root.put("description", "Phase 0 golden rows — >=20 hospitals x >=5 rows regression skeleton");
        root.put("hospitalCount", 20);
        root.put("caseCount", cases.size());
        root.set("cases", cases);
        return root;
    }

    private static JsonNode buildRulesForCase(JsonNode overlay) throws Exception {
        ObjectNode rules = (ObjectNode) defaultRules().deepCopy();
        if (overlay == null || overlay.isEmpty()) {
            return rules;
        }
        if (overlay.has("fixedPrices")) {
            ((ObjectNode) rules.path("specialRules")).set("fixedPrices", overlay.path("fixedPrices").deepCopy());
        }
        if (overlay.has("billingPolicies")) {
            rules.set("billingPolicies", overlay.path("billingPolicies").deepCopy());
        }
        if (overlay.has("billingProfile")) {
            rules.set("billingProfile", overlay.path("billingProfile").deepCopy());
        }
        return rules;
    }

    private static JsonNode defaultRules() throws Exception {
        var method = PricingEngineTest.class.getDeclaredMethod("defaultRules");
        method.setAccessible(true);
        return (JsonNode) method.invoke(null);
    }

    private static void appendNotesExpectations(ObjectNode expected, String id) {
        if (id.contains("fold") || id.contains("jikuozhen")) {
            expected.putArray("notesContains").add("机扩针");
        } else if (id.contains("lens") || id.contains("jintou")) {
            expected.putArray("notesContains").add("镜头");
        } else if (id.contains("path-override")) {
            expected.putArray("notesContains").add("路径覆盖");
        } else if (id.contains("special-only")) {
            expected.putArray("notesContains").add("仅特色规则");
        } else if (id.contains("exclude")) {
            expected.putArray("notesNotContains").add("xx钉");
        } else if (id.contains("any-price")) {
            expected.putArray("notesContains").add("多报价");
        }
    }

    private static List<CaseDef> hospitalBlocks() {
        List<CaseDef> defs = new ArrayList<>();
        addHospital(defs, "dongbei-nongda", "东北农业大学医院", List.of(
                row("per-instrument-jieya", "洁牙机尖按件固定价", "东北农业大学医院",
                        "额外包(纸塑袋)", "洁牙机尖-4/Z7526", "高温纸塑袋75*200", 4, 1, 22, 22),
                stdHt("ht-1", "东北农业大学医院", 1), stdHt("ht-2", "东北农业大学医院", 2),
                stdHt("ht-3", "东北农业大学医院", 3), stdHt("ht-4", "东北农业大学医院", 4)
        ));
        addHospital(defs, "hangtian-fenghua", "哈尔滨航天风华医院", List.of(
                row("per-instrument-spoon", "挖勺按件计价", "哈尔滨航天风华医院",
                        "额外包(纸塑袋)", "挖勺-2/z7530", "高温纸塑袋75*300", 8, 4, 13.5, 54),
                stdHt("ht-1", "哈尔滨航天风华医院", 1), stdHt("ht-2", "哈尔滨航天风华医院", 2),
                stdHt("ht-3", "哈尔滨航天风华医院", 3), stdHt("ht-4", "哈尔滨航天风华医院", 4)
        ));
        addHospital(defs, "songdian", "哈尔滨道外区松电慢性病专科门诊部", List.of(
                row("fold-jikuozhen", "机扩针FOLD折算", "哈尔滨道外区松电慢性病专科门诊部",
                        "额外包(纸塑袋)", "机扩针-20/Z7520", "高温纸塑袋75*200", 20, 1, 22, 22),
                stdHt("ht-1", "哈尔滨道外区松电慢性病专科门诊部", 1),
                stdHt("ht-2", "哈尔滨道外区松电慢性病专科门诊部", 2),
                stdHt("ht-3", "哈尔滨道外区松电慢性病专科门诊部", 3),
                stdHt("ht-4", "哈尔滨道外区松电慢性病专科门诊部", 4)
        ));
        addHospital(defs, "hl-zgh", "黑龙江总工会医院", List.of(
                row("extra-fee-lens", "镜头低温阶梯+加收", "黑龙江总工会医院",
                        "单包装包(老肯低温)", "30°镜头，镜鞘-2（带转换帽）/Z2060", "低温纸塑袋200*600", 4, 2, 52, 104),
                stdHt("ht-1", "黑龙江总工会医院", 1), stdHt("ht-2", "黑龙江总工会医院", 2),
                stdHt("ht-3", "黑龙江总工会医院", 3), stdHt("ht-4", "黑龙江总工会医院", 4)
        ));
        addHospital(defs, "er-ng", "黑龙江省第二医院（南岗区）", List.of(
                row("fixed-xiaqiang", "小腔包固定价", "黑龙江省第二医院（南岗区）",
                        "额外包(纸塑袋)", "小腔包/Z7526", "高温纸塑袋20cm", 1, 1, 49.7, 49.7),
                row("fixed-ruanjing", "软镜固定价", "黑龙江省第二医院（南岗区）",
                        "额外包(纸塑袋)", "软镜/Z7526", "高温纸塑袋20cm", 1, 1, 210, 210),
                row("fixed-ding", "钉固定价", "黑龙江省第二医院（南岗区）",
                        "额外包(纸塑袋)", "xx钉/Z7526", "高温纸塑袋20cm", 1, 1, 140, 140),
                row("fixed-hollow", "3.6空心钉固定价", "黑龙江省第二医院（南岗区）",
                        "额外包(纸塑袋)", "3.6空心钉-2", "高温纸塑袋20cm", 1, 1, 13.3, 13.3),
                stdHt("ht-4", "黑龙江省第二医院（南岗区）", 4)
        ));
        addHospital(defs, "er-sb", "黑龙江省第二医院（松北区）", List.of(
                row("fixed-xiaqiang", "小腔包固定价", "黑龙江省第二医院（松北区）",
                        "额外包(纸塑袋)", "小腔包/Z7526", "高温纸塑袋20cm", 1, 1, 53.55, 53.55),
                row("fixed-ding", "钉固定价", "黑龙江省第二医院（松北区）",
                        "额外包(纸塑袋)", "xx钉/Z7526", "高温纸塑袋20cm", 1, 1, 35, 35),
                stdHt("ht-1", "黑龙江省第二医院（松北区）", 1),
                stdHt("ht-2", "黑龙江省第二医院（松北区）", 2),
                stdHt("ht-3", "黑龙江省第二医院（松北区）", 3)
        ));

        List<String> extraHospitals = List.of(
                "呼兰区第一人民医院", "显著医生集团中西医结合门诊", "哈尔滨美涵美医疗美容有限公司",
                "黑龙江省海员总医院（松北）", "黑龙江省中医药大学附属第四医院", "哈尔滨市道里区妇幼保健院",
                "黑龙江省妇幼保健院（人口）", "哈尔滨市道外区人民医院", "黑龙江维多利亚妇产医院",
                "哈尔滨市红十字妇产医院", "黑龙江中医药大学附属第三医院", "哈尔滨冰城医疗美容医院",
                "五常市人民医院", "予美医疗整形医院"
        );
        int idx = 1;
        for (String hospital : extraHospitals) {
            String slug = String.format("std-%02d", idx++);
            addHospital(defs, slug, hospital, List.of(
                    stdHt("ht-1", hospital, 1), stdHt("ht-2", hospital, 2),
                    stdHt("ht-3", hospital, 3), stdHt("ht-4", hospital, 4),
                    stdHt("ht-8", hospital, 8)
            ));
        }
        return defs;
    }

    private static List<CaseDef> overlayBlocks() {
        ObjectMapper mapper = MAPPER;
        List<CaseDef> defs = new ArrayList<>();

        ObjectNode excludeOverlay = mapper.createObjectNode();
        ArrayNode fixedPrices = excludeOverlay.putArray("fixedPrices");
        ObjectNode nail = fixedPrices.addObject();
        nail.put("name", "xx钉");
        nail.put("price", 200.0);
        nail.put("skipPackaging", true);
        nail.putArray("hospitals").add("省二院");
        nail.putArray("keywords").add("钉");
        nail.putArray("excludeKeywords").add("空心钉");
        defs.add(new CaseDef("exclude-keywords-hollow-nail-skipped",
                "excludeKeywords：空心钉不匹配「xx钉」",
                rowMap("省二院", "额外包(纸塑袋)", "3.6空心钉-2", "高温纸塑袋75*200", 2, 1, 19, 19),
                excludeOverlay));

        ObjectNode anyPriceOverlay = mapper.createObjectNode();
        ArrayNode anyFixed = anyPriceOverlay.putArray("fixedPrices");
        ObjectNode cavity = anyFixed.addObject();
        cavity.put("ruleId", 100);
        cavity.put("name", "小腔包");
        cavity.put("price", 71.0);
        cavity.put("matchMode", "any_price");
        cavity.put("skipPackaging", true);
        cavity.putArray("hospitals").add("省二院");
        cavity.putArray("keywords").add("小腔包");
        cavity.putArray("acceptedPrices").add(71.0).add(76.5);
        defs.add(new CaseDef("any-price-lower-tier", "matchMode=any_price：较低候选价",
                rowMap("省二院", "额外包(纸塑袋)", "小腔包A", "高温纸塑袋75*200", 1, 1, 71, 71),
                anyPriceOverlay));

        ObjectNode pathOverlay = mapper.createObjectNode();
        ObjectNode pathOverride = pathOverlay.putObject("billingProfile").putObject("pathOverride");
        pathOverride.put("disableLowTemp", true);
        pathOverride.put("forceHighTempUnitPrice", 3.0);
        defs.add(new CaseDef("path-override-daowai", "pathOverride：道外人民",
                rowMap("道外人民", "单包装包(老肯低温)", "普通器械-4/Z7526", "低温纸塑袋200*600", 4, 1, 12, 12),
                pathOverlay));

        ObjectNode specialOnlyOverlay = mapper.createObjectNode();
        specialOnlyOverlay.putObject("billingProfile").put("pricingMode", "special_only");
        defs.add(new CaseDef("special-only-unmatched", "special_only 未命中特色规则",
                rowMap("某院", "额外包(纸塑袋)", "普通器械-4/Z7526", "高温纸塑袋20cm", 4, 1, 22, 22),
                specialOnlyOverlay));

        return defs;
    }

    private static void addHospital(List<CaseDef> defs, String slug, String hospital, List<CaseDef> rows) {
        for (CaseDef row : rows) {
            defs.add(new CaseDef(slug + "-" + row.id(), hospital + "：" + row.description(), row.input(), null));
        }
    }

    private static CaseDef row(String id, String desc, String hospital, String type, String packName,
                               String material, int inst, int packs, double unit, double total) {
        return new CaseDef(id, desc, rowMap(hospital, type, packName, material, inst, packs, unit, total), null);
    }

    private static CaseDef stdHt(String id, String hospital, int inst) {
        return row(id, "高温标准" + inst + "件", hospital, "额外包(纸塑袋)", "普通器械-" + inst + "/Z7526",
                "高温纸塑袋20cm", inst, 1, 0, 0);
    }

    private static Map<String, Object> rowMap(String hospital, String type, String packName, String material,
                                              int inst, int packs, double unit, double total) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hospitalName", hospital);
        row.put("type", type);
        row.put("packName", packName);
        row.put("packageMaterial", material);
        row.put("instrumentCount", inst);
        row.put("packCount", packs);
        row.put("unitPrice", unit);
        row.put("totalPrice", total);
        return row;
    }
}
