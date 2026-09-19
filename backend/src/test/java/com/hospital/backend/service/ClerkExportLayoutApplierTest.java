package com.hospital.backend.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.dto.request.hospital.HospitalBillTemplateExportRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClerkExportLayoutApplierTest {

    private final ClerkExportLayoutApplier applier = new ClerkExportLayoutApplier();

    @Test
    void appliesRemoveColumnsFromExportLayout() {
        ObjectNode compiled = JsonUtils.getObjectMapper().createObjectNode();
        var layouts = JsonUtils.getObjectMapper().createArrayNode();
        var layout = JsonUtils.getObjectMapper().createObjectNode();
        layout.put("ruleType", "EXPORT_LAYOUT");
        var params = JsonUtils.getObjectMapper().createObjectNode();
        var remove = JsonUtils.getObjectMapper().createArrayNode();
        remove.add("备注");
        remove.add("差额");
        params.set("removeColumns", remove);
        layout.set("params", params);
        layouts.add(layout);
        compiled.set("exportLayouts", layouts);

        HospitalBillTemplateExportRequest request = new HospitalBillTemplateExportRequest();
        applier.apply(request, compiled);

        assertThat(request.getClerkRemoveColumns()).containsExactly("备注", "差额");
    }
}
