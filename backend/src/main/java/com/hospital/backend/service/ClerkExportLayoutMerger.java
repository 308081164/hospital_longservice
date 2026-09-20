package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.dto.request.hospital.HospitalBillTemplateExportRequest;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.export.BillColumnLayout;
import com.hospital.backend.export.BillExportLayoutResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 将内勤 EXPORT_LAYOUT 规则合并进账单导出请求/布局（覆盖 export_template 缺省项）。
 */
@Component
@RequiredArgsConstructor
public class ClerkExportLayoutMerger {

    private final ClerkRuleCompiler clerkRuleCompiler;
    private final CustomerResolver customerResolver;
    private final BillExportLayoutResolver billExportLayoutResolver;

    public record LayoutSettings(
            String billLayout,
            String d8DisplaySource,
            BillColumnLayout billColumnLayout) {}

    public void mergeIntoRequest(HospitalBillTemplateExportRequest request) {
        if (request == null || request.getHospitalName() == null) {
            return;
        }
        resolveCustomerCode(request.getHospitalName()).ifPresent(code -> {
            ObjectNode compiled = clerkRuleCompiler.compileForCustomer(code);
            if (compiled == null) {
                return;
            }
            applyLayoutParamsToRequest(request, compiled);
            applyMaterialAliases(request.getRows(), compiled);
        });
    }

    public LayoutSettings merge(String hospitalName, LayoutSettings base) {
        LayoutSettings resolved = base != null ? base : defaultLayout();
        Optional<String> code = resolveCustomerCode(hospitalName);
        if (code.isEmpty()) {
            return resolved;
        }
        ObjectNode compiled = clerkRuleCompiler.compileForCustomer(code.get());
        if (compiled == null) {
            return resolved;
        }
        String billLayout = resolved.billLayout();
        String d8 = resolved.d8DisplaySource();
        BillColumnLayout columnLayout = resolved.billColumnLayout();
        JsonNode layouts = compiled.path("exportLayouts");
        if (layouts.isArray()) {
            for (JsonNode layout : layouts) {
                JsonNode params = layout.path("params");
                if (params.hasNonNull("billLayout")) {
                    billLayout = billExportLayoutResolver.normalizeBillLayout(params.path("billLayout").asText());
                }
                if (params.hasNonNull("d8DisplaySource")) {
                    d8 = billExportLayoutResolver.normalizeD8DisplaySource(params.path("d8DisplaySource").asText());
                }
                if (params.hasNonNull("billColumnLayout")) {
                    BillColumnLayout fromClerk = BillColumnLayout.fromKey(params.path("billColumnLayout").asText());
                    if (fromClerk != null) {
                        columnLayout = fromClerk;
                    }
                }
            }
        }
        return new LayoutSettings(billLayout, d8, columnLayout);
    }

    public List<String> resolveSupplementExportTypes(String hospitalName) {
        Optional<String> code = resolveCustomerCode(hospitalName);
        if (code.isEmpty()) {
            return List.of();
        }
        ObjectNode compiled = clerkRuleCompiler.compileForCustomer(code.get());
        if (compiled == null || !compiled.has("exportSupplementTypes")) {
            return List.of();
        }
        List<String> types = new ArrayList<>();
        for (JsonNode type : compiled.path("exportSupplementTypes")) {
            String value = type.asText("").trim();
            if (!value.isBlank()) {
                types.add(value);
            }
        }
        return types;
    }

    private void applyLayoutParamsToRequest(HospitalBillTemplateExportRequest request, JsonNode compiled) {
        JsonNode layouts = compiled.path("exportLayouts");
        if (!layouts.isArray()) {
            return;
        }
        for (JsonNode layout : layouts) {
            JsonNode params = layout.path("params");
            if (params.hasNonNull("billLayout")) {
                request.setBillLayout(
                        billExportLayoutResolver.normalizeBillLayout(params.path("billLayout").asText()));
            }
            if (params.hasNonNull("d8DisplaySource")) {
                request.setD8DisplaySource(
                        billExportLayoutResolver.normalizeD8DisplaySource(params.path("d8DisplaySource").asText()));
            }
            if (params.hasNonNull("billColumnLayout")) {
                BillColumnLayout fromClerk = BillColumnLayout.fromKey(params.path("billColumnLayout").asText());
                if (fromClerk != null) {
                    request.setBillColumnLayout(fromClerk.getKey());
                }
            }
            if (params.has("removeColumns") && params.get("removeColumns").isArray()) {
                for (JsonNode col : params.get("removeColumns")) {
                    request.addClerkRemoveColumn(col.asText());
                }
            }
        }
    }

    private void applyMaterialAliases(List<BillRowItem> rows, JsonNode compiled) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        JsonNode layouts = compiled.path("exportLayouts");
        if (!layouts.isArray()) {
            return;
        }
        for (JsonNode layout : layouts) {
            JsonNode aliases = layout.path("params").path("materialAliases");
            if (!aliases.isArray()) {
                continue;
            }
            for (BillRowItem row : rows) {
                String material = row.getPackageMaterial();
                if (material == null || material.isBlank()) {
                    continue;
                }
                for (JsonNode alias : aliases) {
                    String from = alias.path("from").asText("");
                    String toPrefix = alias.path("toPrefix").asText("");
                    if (!from.isBlank() && material.contains(from)) {
                        row.setPackageMaterial(toPrefix + material.substring(material.indexOf(from) + from.length()));
                        break;
                    }
                }
            }
        }
    }

    private Optional<String> resolveCustomerCode(String hospitalName) {
        if (hospitalName == null || hospitalName.isBlank()) {
            return Optional.empty();
        }
        return customerResolver.resolveByName(hospitalName).map(Customer::getCode);
    }

    private static LayoutSettings defaultLayout() {
        return new LayoutSettings(
                BillExportLayoutResolver.LAYOUT_AUTO,
                BillExportLayoutResolver.D8_AUTO,
                BillColumnLayout.STANDARD_8COL);
    }
}
