package com.hospital.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BillingSeedMigrationRunnerTest {

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
        for (String file : List.of(
                "billing-seeds/phase1-batch-a-extra.json",
                "billing-seeds/phase2-policies.json",
                "billing-seeds/phase5-batch-c.json",
                "billing-seeds/phase7-batch-d.json",
                "billing-seeds/phase7-batch-e.json",
                "billing-seeds/phase-zyy-d1-fuyi.json",
                "billing-seeds/phase-zyy-d1-standard-pricing-20260723.json",
                "billing-seeds/phase-hulan-heu-hit-20260722.json",
                "billing-seeds/phase-hrb-hx-eye-20260723.json",
                "billing-seeds/phase-ng-fuchan-gongqiangjing-20260723.json",
                "billing-seeds/phase-ng-fuchan-pdf-ocr-20260723.json",
                "billing-seeds/phase-hrb-bc-med-beauty-20260723.json",
                "billing-seeds/phase-hrb-cj-standard-billing-20260723.json",
                "billing-seeds/phase-hrb-mhm-xizhizhen-20260723.json",
                "billing-seeds/phase-hrb-sd-neau-kouqiang-fold-20260723.json",
                "billing-seeds/phase-hlfb-sf-chezhen-20260724.json",
                "billing-seeds/phase-s7-bokang-pdf-ocr-20260723.json",
                "billing-seeds/phase-daowai-path-override-20260723.json")) {
            assertThat(new ClassPathResource(file).exists())
                    .as("seed file %s", file)
                    .isTrue();
        }
    }
}
