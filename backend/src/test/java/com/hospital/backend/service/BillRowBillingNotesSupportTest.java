package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BillRowBillingNotesSupportTest {

    @Test
    void extractsNestedFieldConsistencyViolations() {
        String json = """
                {
                  "policyTraces": [{"label": "未命中折扣策略"}],
                  "fieldConsistency": {
                    "type": "field_consistency",
                    "violations": [
                      {"code": "INSTRUMENT_COUNT_MISMATCH", "message": "包名件数 2 与器械数列 4 不一致"}
                    ]
                  },
                  "consistencyViolations": [
                    {"code": "INSTRUMENT_COUNT_MISMATCH", "message": "包名件数 2 与器械数列 4 不一致"}
                  ]
                }
                """;

        List<Map<String, Object>> violations = BillRowBillingNotesSupport.extractFieldConsistencyViolations(json);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).get("code")).isEqualTo("INSTRUMENT_COUNT_MISMATCH");
        assertThat(BillRowBillingNotesSupport.hasFieldConsistencyViolations(json)).isTrue();
        assertThat(BillRowBillingNotesSupport.summarizeFieldConsistencyViolations(json))
                .contains("包名件数 2 与器械数列 4 不一致");
    }

    @Test
    void returnsEmptyWhenNoViolations() {
        assertThat(BillRowBillingNotesSupport.extractFieldConsistencyViolations(null)).isEmpty();
        assertThat(BillRowBillingNotesSupport.hasFieldConsistencyViolations("{}")).isFalse();
        assertThat(BillRowBillingNotesSupport.extractBillingValidationViolations(null)).isEmpty();
        assertThat(BillRowBillingNotesSupport.hasAnyFieldCheckViolations("{}")).isFalse();
    }

    @Test
    void extractsNestedBillingValidationViolations() {
        String json = """
                {
                  "ruleName": "高温纸塑袋",
                  "billingValidation": {
                    "type": "billing_validation",
                    "violations": [
                      {"code": "BLANK_PACKAGE_MATERIAL", "message": "包装材料为空", "severity": "error"},
                      {"code": "ZERO_INSTRUMENT_COUNT", "message": "器械数为0", "severity": "error"}
                    ]
                  }
                }
                """;

        List<Map<String, Object>> violations =
                BillRowBillingNotesSupport.extractBillingValidationViolations(json);

        assertThat(violations).hasSize(2);
        assertThat(violations.get(0).get("code")).isEqualTo("BLANK_PACKAGE_MATERIAL");
        assertThat(BillRowBillingNotesSupport.hasFieldConsistencyViolations(json)).isFalse();
        assertThat(BillRowBillingNotesSupport.hasAnyFieldCheckViolations(json)).isTrue();
        assertThat(BillRowBillingNotesSupport.summarizeAllFieldCheckViolations(json))
                .contains("包装材料为空")
                .contains("器械数为0");
    }

    @Test
    void extractsTopLevelBillingValidationPayload() {
        String json = """
                {
                  "type": "billing_validation",
                  "violations": [
                    {"code": "ZERO_INSTRUMENT_COUNT", "message": "器械数为0", "severity": "error"}
                  ]
                }
                """;

        assertThat(BillRowBillingNotesSupport.extractBillingValidationViolations(json)).hasSize(1);
        assertThat(BillRowBillingNotesSupport.hasAnyFieldCheckViolations(json)).isTrue();
    }

    @Test
    void combinedSummaryMergesConsistencyAndValidationMessages() {
        String json = """
                {
                  "fieldConsistency": {
                    "type": "field_consistency",
                    "violations": [
                      {"code": "INSTRUMENT_COUNT_MISMATCH", "message": "包名件数 2 与器械数列 4 不一致"}
                    ]
                  },
                  "billingValidation": {
                    "type": "billing_validation",
                    "violations": [
                      {"code": "BLANK_PACKAGE_MATERIAL", "message": "包装材料为空", "severity": "error"}
                    ]
                  }
                }
                """;

        assertThat(BillRowBillingNotesSupport.hasAnyFieldCheckViolations(json)).isTrue();
        assertThat(BillRowBillingNotesSupport.summarizeAllFieldCheckViolations(json))
                .isEqualTo("包名件数 2 与器械数列 4 不一致；包装材料为空");
    }
}
