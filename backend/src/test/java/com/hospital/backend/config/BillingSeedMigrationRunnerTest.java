package com.hospital.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BillingSeedMigrationRunnerTest {

    @Test
    void clerkSettlementCustomersUseExistingCodesWithoutPriceParams() throws Exception {
        JsonNode root = JsonUtils.getObjectMapper().readTree(
                new ClassPathResource("billing-seeds/clerk-settlement-customers-20261020.json").getInputStream());
        assertThat(root.path("profiles")).hasSize(6);
        assertThat(root.toString()).doesNotContain("feePerTrip", "minCharge", "productRules", "policies");
        assertThat(profile(root, "SHANTUN-RM").path("name").asText()).isEqualTo("黑龙江省山河屯林区人民医院");
        assertThat(profile(root, "XZX-YY").path("name").asText()).isEqualTo("新中新医院");
        assertThat(profile(root, "LZL-KQ").path("name").asText()).isEqualTo("哈尔滨市南岗区黎增亮口腔科诊所");
        assertThat(profile(root, "YUEMEI-FH").path("name").asText()).isEqualTo("悦美芳华医疗门诊医院");
        assertThat(profile(root, "HRB-HEU").path("name").asText()).isEqualTo("哈尔滨工程大学医院");
        assertThat(profile(root, "DONGDA-GC").path("name").asText()).isEqualTo("黑龙江东大肛肠医院");
        assertThat(profile(root, "LZL-KQ").path("aliases"))
                .extracting(JsonNode::asText)
                .contains("哈尔滨市南岗区黎增亮口腔科诊所", "黎增亮口腔");
    }

    private static JsonNode profile(JsonNode root, String code) {
        for (JsonNode profile : root.path("profiles")) {
            if (code.equals(profile.path("code").asText())) {
                return profile;
            }
        }
        throw new AssertionError("missing profile " + code);
    }

    @Test
    void renshengSettlementCustomerIsIdentityOnlyAndKeptOutOfStrictList() throws Exception {
        JsonNode root = JsonUtils.getObjectMapper().readTree(
                new ClassPathResource("billing-seeds/clerk-settlement-customer-rensheng-20261026.json")
                        .getInputStream());
        assertThat(root.path("profiles")).hasSize(1);
        assertThat(root.toString()).doesNotContain(
                "feePerTrip", "minCharge", "rate", "productRules", "policies", "billingEnabled");
        JsonNode profile = profile(root, "RENSHENG");
        assertThat(profile.path("name").asText()).isEqualTo("哈尔滨仁胜医院");
        assertThat(profile.path("aliases")).extracting(JsonNode::asText)
                .contains("哈尔滨仁胜医院", "仁胜");

        assertThat(stringList("CLERK_SETTLEMENT_CUSTOMER_CODES")).contains("RENSHENG");
        assertThat(stringList("STRICT_KEEP_CODES")).doesNotContain("RENSHENG");
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(String fieldName) throws Exception {
        Field field = BillingSeedMigrationRunner.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (List<String>) field.get(null);
    }

    @Test
    void genericSmallItemFoldsInStoredRuleAreLimitedToPaperPlastic() {
        ObjectNode rules = JsonUtils.getObjectMapper().createObjectNode();
        ArrayNode folds = rules.putObject("specialRules").putArray("foldRules");
        ObjectNode withBag = folds.addObject();
        withBag.put("name", "通用小件5合1含包材");
        withBag.putArray("keywords").add("克氏针");
        ObjectNode noBag = folds.addObject();
        noBag.put("name", "通用小件5合1免包材");
        ObjectNode suture = folds.addObject();
        suture.put("name", "通用缝合针按1件含包材");

        assertThat(BillingSeedMigrationRunner.scopeGenericSmallItemFoldsToPaperPlastic(rules)).isTrue();
        assertThat(withBag.path("acceptedTypes")).extracting(JsonNode::asText)
                .containsExactly("额外包（纸塑袋）");
        assertThat(noBag.path("acceptedTypes")).extracting(JsonNode::asText)
                .containsExactly("额外包（纸塑袋）");
        assertThat(suture.has("acceptedTypes")).isFalse();
        assertThat(withBag.path("keywords").get(0).asText()).isEqualTo("克氏针");
        assertThat(BillingSeedMigrationRunner.scopeGenericSmallItemFoldsToPaperPlastic(rules)).isFalse();
    }

    @Test
    void phase2SeedFileHasThreeProfiles() throws Exception {
        JsonNode root = JsonUtils.getObjectMapper().readTree(
                new ClassPathResource("billing-seeds/phase2-policies.json").getInputStream());
        assertThat(root.path("profiles")).hasSize(3);
        assertThat(root.path("profiles").get(0).path("code").asText()).isEqualTo("VICTORIA");
    }

    @Test
    void phase7BatchDHasWuyuanGroup() throws Exception {
        JsonNode root = JsonUtils.getObjectMapper().readTree(
                new ClassPathResource("billing-seeds/phase7-batch-d.json").getInputStream());
        assertThat(root.path("customerGroups")).hasSize(1);
        assertThat(root.path("customerGroups").get(0).path("memberCodes")).hasSize(2);
    }

    @Test
    void allSeedFilesExist() throws Exception {
        final String archive = "billing-seeds/archive/legacy-2026/";
        for (String file : List.of(
                "billing-seeds/phase1-batch-a-extra.json",
                "billing-seeds/phase2-policies.json",
                "billing-seeds/phase5-batch-c.json",
                "billing-seeds/phase7-batch-d.json",
                "billing-seeds/phase7-batch-e.json",
                archive + "phase-zyy-d1-fuyi.json",
                archive + "phase-zyy-d1-standard-pricing-20260723.json",
                archive + "phase-hulan-heu-hit-20260722.json",
                archive + "phase-hrb-hx-eye-20260723.json",
                archive + "phase-hrb-hx-eye-fix-20260724.json",
                archive + "phase-hrb-hx-eye-fix-20260724-v2.json",
                archive + "phase-ng-fuchan-gongqiangjing-20260723.json",
                archive + "phase-ng-fuchan-pdf-ocr-20260723.json",
                archive + "phase-ng-fuchan-renliubao-fix-20260724.json",
                archive + "phase-hrb-bc-med-beauty-20260723.json",
                archive + "phase-hrb-bc-med-beauty-fix-20260724.json",
                archive + "phase-hrb-bc-med-beauty-fix-20260724-v2.json",
                archive + "phase-hrb-cj-standard-billing-20260723.json",
                archive + "phase-hrb-cj-fix-20260724.json",
                archive + "phase-hrb-cj-surgical-pack-fix-20260724.json",
                archive + "phase-hrb-mhm-xizhizhen-20260723.json",
                archive + "phase-hrb-sd-neau-kouqiang-fold-20260723.json",
                archive + "phase-hrb-sd-neau-kouqiang-fold-fix-20260724-v2.json",
                archive + "phase-hlfb-sf-chezhen-20260724.json",
                archive + "phase-s7-bokang-pdf-ocr-20260723.json",
                archive + "phase-daowai-path-override-20260723.json",
                archive + "phase-wcsrm-yy-or-pricing-20260724.json",
                archive + "phase-wcsrm-yy-or-consolidate-20260724.json",
                archive + "phase-wcsrm-yy-or-conditions-fix-20260724-v2.json",
                archive + "phase-wcsrm-yy-extra-bag-fix-20260724.json",
                archive + "phase-wcsrm-yy-pack-price-fix-20260724.json",
                archive + "phase-hrb-2nd-fix-20260724.json",
                archive + "phase-hrb-sh-pricing-20260724.json",
                archive + "phase-hrb-ngjy-fix-20260724.json",
                archive + "phase-zuyan-ng-export-pricing-20260724.json",
                archive + "phase-sanjing-neilou-instrument-count-fix-20260727.json",
                archive + "phase-sheng-yy-xf-dept-pricing-20260727.json",
                archive + "phase-sheng-yy-xf-shenwai-goudao-20260728.json",
                archive + "phase-jzsw-bio-yanhdao-20260729.json",
                archive + "phase-bill-wave4c-close-20260729.json",
                archive + "phase-bill-wave4c-close-v2-20260729.json")) {
            assertThat(new ClassPathResource(file).exists())
                    .as("seed file %s", file)
                    .isTrue();
        }
    }

    @Test
    void incrementalSeedsFrozen() {
        assertThat(BillingSeedMigrationRunner.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("INCREMENTAL_SEEDS"));
    }
}
