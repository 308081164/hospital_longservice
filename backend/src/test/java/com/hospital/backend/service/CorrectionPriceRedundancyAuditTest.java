package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校正价已彻底删除：baseline 与 manifest 中不得再出现「校正价」规则名。
 */
class CorrectionPriceRedundancyAuditTest {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    @Test
    void baselineAndManifestHaveNoCorrectionPriceRules() throws Exception {
        List<String> violations = new ArrayList<>();

        Path baselineDir = Path.of("src/main/resources/billing-rules/baseline");
        if (Files.isDirectory(baselineDir)) {
            for (Path file : Files.list(baselineDir).filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonNode data = MAPPER.readTree(file.toFile());
                for (JsonNode rule : data.path("productRules")) {
                    String name = rule.path("name").asText("");
                    if (name.contains("校正价")) {
                        violations.add("baseline/" + file.getFileName() + ": " + name);
                    }
                }
            }
        }

        try (InputStream in = CorrectionPriceRedundancyAuditTest.class
                .getResourceAsStream("/billing-rules-manifest.json")) {
            assertThat(in).isNotNull();
            JsonNode manifest = MAPPER.readTree(in);
            manifest.path("customers").fields().forEachRemaining(entry -> {
                for (JsonNode rule : entry.getValue().path("productRules")) {
                    String name = rule.path("name").asText("");
                    if (name.contains("校正价")) {
                        violations.add("manifest/" + entry.getKey() + ": " + name);
                    }
                }
            });
        }

        assertThat(violations)
                .as("校正价规则应已全部删除")
                .isEmpty();
    }
}
