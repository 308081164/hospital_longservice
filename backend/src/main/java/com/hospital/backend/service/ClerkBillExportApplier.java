package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内勤账单导出专用变换（零元行补价等），在价表/折扣之后执行。
 */
@Component
public class ClerkBillExportApplier {

    public List<BillRowItem> apply(JsonNode compiledClerk, List<BillRowItem> rows) {
        if (compiledClerk == null || rows == null || rows.isEmpty()) {
            return rows;
        }
        JsonNode clerkRules = compiledClerk.path("clerkRules");
        if (!clerkRules.isArray()) {
            return rows;
        }
        for (JsonNode rule : clerkRules) {
            if (!rule.path("isActive").asBoolean(true)) {
                continue;
            }
            String type = rule.path("ruleType").asText("");
            if ("ZERO_ROW_PACKAGING_FEE".equals(type)) {
                rows = applyZeroRowPackaging(rule, rows);
            }
        }
        return rows;
    }

    private List<BillRowItem> applyZeroRowPackaging(JsonNode rule, List<BillRowItem> rows) {
        JsonNode params = rule.path("params");
        double defaultPaper = params.path("paperPlastic").asDouble(8);
        double defaultNonwoven = params.path("nonwoven").asDouble(20);
        double defaultEto = params.path("eto").asDouble(35);
        String packagingMaterial = params.path("packagingMaterial").asText("");
        String packNameKeyword = params.path("packNameKeyword").asText("");
        Double configuredPrice = params.has("unitPrice") && !params.path("unitPrice").isNull()
                ? params.path("unitPrice").asDouble()
                : null;

        for (BillRowItem row : rows) {
            if (row.getTotalPrice() == null || row.getTotalPrice() > 0.001) {
                continue;
            }
            String pack = row.getPackName() != null ? row.getPackName() : "";
            String mat = row.getPackageMaterial() != null ? row.getPackageMaterial() : "";
            if (!packNameKeyword.isBlank() && !pack.contains(packNameKeyword)) {
                continue;
            }
            if (!packagingMaterial.isBlank() && !mat.contains(packagingMaterial) && !pack.contains(packagingMaterial)) {
                continue;
            }
            double fee;
            if (configuredPrice != null) {
                fee = configuredPrice;
            } else if (mat.contains("无纺布") || pack.contains("无纺布")) {
                fee = defaultNonwoven;
            } else if (mat.contains("环氧乙烷") || mat.contains("ETO") || pack.contains("环氧乙烷")) {
                fee = defaultEto;
            } else {
                fee = defaultPaper;
            }
            row.setTotalPrice(fee);
            if (row.getUnitPrice() == null || row.getUnitPrice() <= 0.001) {
                row.setUnitPrice(fee);
            }
        }
        return rows;
    }
}
