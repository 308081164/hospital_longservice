package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.config.ClerkRuleIndex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 2026-11-06 客户反馈：市二院账单导出需按科室拆分（不再是单一「合计」Sheet）。 */
class Hrb2ndDeptSplitLayoutTest {

    @Test
    void billLayoutIsDeptSplit() {
        JsonNode compiled = new ClerkRuleCompiler(new ClerkRuleIndex()).compileForCustomer("HRB-2ND");
        JsonNode layouts = compiled.path("exportLayouts");
        assertThat(layouts.isArray()).isTrue();
        JsonNode target = null;
        for (JsonNode layout : layouts) {
            if (layout.path("params").hasNonNull("billLayout")) {
                target = layout;
            }
        }
        assertThat(target).as("市二院缺少 EXPORT_LAYOUT 规则").isNotNull();
        assertThat(target.path("params").path("billLayout").asText()).isEqualTo("dept_split");
    }
}
