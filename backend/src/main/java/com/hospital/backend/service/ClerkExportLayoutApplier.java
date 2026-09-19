package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.dto.request.hospital.HospitalBillTemplateExportRequest;
import org.springframework.stereotype.Component;

/**
 * 应用内勤 EXPORT_LAYOUT 规则（列裁剪/保留）。
 */
@Component
public class ClerkExportLayoutApplier {

    public void apply(HospitalBillTemplateExportRequest request, JsonNode compiledClerk) {
        if (request == null || compiledClerk == null) {
            return;
        }
        JsonNode layouts = compiledClerk.path("exportLayouts");
        if (!layouts.isArray() || layouts.isEmpty()) {
            return;
        }
        for (JsonNode layout : layouts) {
            JsonNode params = layout.path("params");
            if (params.has("removeColumns") && params.get("removeColumns").isArray()) {
                for (JsonNode col : params.get("removeColumns")) {
                    request.addClerkRemoveColumn(col.asText());
                }
            }
        }
    }
}
