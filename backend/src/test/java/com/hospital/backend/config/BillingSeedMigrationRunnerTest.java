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
