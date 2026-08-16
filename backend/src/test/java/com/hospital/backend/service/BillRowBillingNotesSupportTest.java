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
    }
}
