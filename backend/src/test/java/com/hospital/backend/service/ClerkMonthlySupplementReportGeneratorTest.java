package com.hospital.backend.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.config.ClerkRuleIndex;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClerkMonthlySupplementReportGeneratorTest {

    private final ClerkMonthlySupplementReportGenerator generator = new ClerkMonthlySupplementReportGenerator();
    private final ClerkRuleCompiler compiler = new ClerkRuleCompiler(new ClerkRuleIndex());

    @Test
    void detectsDianliSupplementAttachments() {
        ObjectNode compiled = compiler.compileForCustomer("ZY3-DIANLI");
        assertThat(compiled).isNotNull();
        assertThat(generator.hasSupplementReports(compiled)).isTrue();
    }

    @Test
    void previewsInstrumentCountWorkbook() {
        List<Map<String, Object>> preview = generator.previewAttachment(
                "attachments/5黑龙江中医药大学附属第三医院8月器械把数.xlsx");
        assertThat(preview).isNotEmpty();
        assertThat(preview.get(0).get("rowCount")).isNotNull();
    }
}
