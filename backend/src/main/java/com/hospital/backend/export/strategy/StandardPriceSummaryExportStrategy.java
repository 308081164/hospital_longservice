package com.hospital.backend.export.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.ExportType;
import com.hospital.backend.export.SummarySheetWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StandardPriceSummaryExportStrategy implements ExportStrategy {

    private final SummarySheetWriter summarySheetWriter;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.STANDARD_PRICE_SUMMARY;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        AllocationResult allocation = parseAllocationResult(context.getJob().getAllocationResult());
        if (allocation == null || allocation.getPriceSummaryByCategory() == null
                || allocation.getPriceSummaryByCategory().isEmpty()) {
            allocation = buildFallbackAllocation(context);
        }
        String sheetName = resolveSheetName(context, "汇总");
        String title = context.getHospitalName() + " — " + sheetName;
        AllocationResult finalAllocation = allocation;
        byte[] content = summarySheetWriter.buildSingleSheetWorkbook(sheetName, (sheet, headerStyle) ->
                summarySheetWriter.writePriceSummarySheet(sheet, headerStyle, finalAllocation, title));
        String fileName = safeName(context.getHospitalName()) + "_price_summary_v2_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        return ExportResult.builder()
                .content(content)
                .fileName(fileName)
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .strategyKey(strategyKey())
                .templateId(context.getTemplate().getTemplateId())
                .build();
    }

    private AllocationResult buildFallbackAllocation(ExportContext context) {
        AllocationResult allocation = new AllocationResult();
        Map<String, Double> categories = new LinkedHashMap<>();
        double total = 0.0;
        for (var row : context.getRows()) {
            if ("skipped".equalsIgnoreCase(row.getStatus())) {
                continue;
            }
            String type = row.getType() != null && !row.getType().isBlank() ? row.getType() : "其他";
            Double price = row.getCorrectedTotalPrice() != null ? row.getCorrectedTotalPrice() : row.getTotalPrice();
            double amount = price != null ? price : 0.0;
            categories.merge(type, amount, Double::sum);
            total += amount;
        }
        categories.put("合计", total);
        allocation.setPriceSummaryByCategory(categories);
        allocation.setBalanced(true);
        allocation.setBalanceMessage("由账单行按类型汇总");
        return allocation;
    }

    private String resolveSheetName(ExportContext context, String defaultName) {
        String sheetConfig = context.getTemplate().getSheetConfigJson();
        if (sheetConfig == null || sheetConfig.isBlank()) {
            return defaultName;
        }
        try {
            JsonNode node = objectMapper.readTree(sheetConfig);
            if (node.hasNonNull("sheetName")) {
                return node.get("sheetName").asText();
            }
        } catch (Exception ignored) {
            // use default
        }
        return defaultName;
    }

    private AllocationResult parseAllocationResult(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AllocationResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "hospital";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
