package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.ExportTemplate;
import com.hospital.backend.export.model.ColumnMappingConfig;
import com.hospital.backend.export.model.ResolvedExportTemplate;
import com.hospital.backend.export.strategy.ExportTemplateResolverKeys;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.ExportTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportTemplateResolver {

    public static final String DEFAULT_BILL_STRATEGY = "standard_bill";
    public static final String DEFAULT_SETTLEMENT_STRATEGY = "standard_settlement";

    private final ExportTemplateMapper exportTemplateMapper;
    private final CustomerMapper customerMapper;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    public ResolvedExportTemplate resolve(Long customerId, ExportType exportType) {
        return resolve(customerId, exportType, null);
    }

    public ResolvedExportTemplate resolve(Long customerId, ExportType exportType, Long templateIdOverride) {
        if (templateIdOverride != null) {
            ExportTemplate explicit = exportTemplateMapper.selectById(templateIdOverride);
            if (explicit != null && Boolean.TRUE.equals(explicit.getIsActive())) {
                return toResolved(explicit, true);
            }
        }

        if (customerId != null) {
            List<ExportTemplate> customerTemplates =
                    exportTemplateMapper.selectByCustomerAndType(customerId, exportType.code());
            if (!customerTemplates.isEmpty()) {
                return toResolved(customerTemplates.get(0), true);
            }
        }

        List<ExportTemplate> globalTemplates = exportTemplateMapper.selectGlobalByType(exportType.code());
        if (customerId != null) {
            Customer customer = customerMapper.selectById(customerId);
            if (customer != null && customer.getCode() != null) {
                for (ExportTemplate template : globalTemplates) {
                    if (matchesCustomerCode(template, customer.getCode())) {
                        return toResolved(template, false);
                    }
                }
            }
        }

        for (ExportTemplate template : globalTemplates) {
            String strategyKey = extractStrategyKey(template.getSheetConfig(), template.getTemplateType());
            if (ExportTemplateResolverKeys.STANDARD_BILL.equals(strategyKey)
                    || ExportTemplateResolverKeys.STANDARD_SETTLEMENT.equals(strategyKey)) {
                return toResolved(template, false);
            }
        }

        if (!globalTemplates.isEmpty()) {
            return toResolved(globalTemplates.get(0), false);
        }

        return buildSyntheticDefault(exportType);
    }

    private ResolvedExportTemplate buildSyntheticDefault(ExportType exportType) {
        String strategyKey = switch (exportType) {
            case SETTLEMENT -> DEFAULT_SETTLEMENT_STRATEGY;
            case DEPT_SUMMARY -> ExportTemplateResolverKeys.STANDARD_DEPT_SUMMARY;
            case DAILY -> ExportTemplateResolverKeys.DAILY_SPLIT;
            case PRICE_SUMMARY -> ExportTemplateResolverKeys.STANDARD_PRICE_SUMMARY;
            case INSTRUMENT_AUDIT -> ExportTemplateResolverKeys.INSTRUMENT_AUDIT;
            case LOGISTICS_ALLOCATION -> ExportTemplateResolverKeys.LOGISTICS_ALLOCATION;
            case GRAND_SUMMARY -> ExportTemplateResolverKeys.GRAND_SUMMARY;
            default -> DEFAULT_BILL_STRATEGY;
        };
        return ResolvedExportTemplate.builder()
                .templateId(null)
                .customerId(null)
                .exportType(exportType)
                .name("系统默认-" + exportType.code())
                .storagePath("")
                .strategyKey(strategyKey)
                .columnMapping(new ColumnMappingConfig())
                .sheetConfigJson("{}")
                .customerOverride(false)
                .build();
    }

    private ResolvedExportTemplate toResolved(ExportTemplate template, boolean customerOverride) {
        ColumnMappingConfig mapping = parseColumnMapping(template.getColumnMapping());
        String strategyKey = extractStrategyKey(template.getSheetConfig(), template.getTemplateType());
        return ResolvedExportTemplate.builder()
                .templateId(template.getId())
                .customerId(template.getCustomerId())
                .exportType(ExportType.fromCode(template.getTemplateType()))
                .name(template.getName())
                .storagePath(Optional.ofNullable(template.getStoragePath()).orElse(""))
                .strategyKey(strategyKey)
                .columnMapping(mapping)
                .sheetConfigJson(template.getSheetConfig())
                .customerOverride(customerOverride)
                .build();
    }

    ColumnMappingConfig parseColumnMapping(String json) {
        if (json == null || json.isBlank()) {
            return new ColumnMappingConfig();
        }
        try {
            return objectMapper.readValue(json, ColumnMappingConfig.class);
        } catch (Exception e) {
            log.warn("Failed to parse column_mapping JSON, using empty config: {}", e.getMessage());
            return new ColumnMappingConfig();
        }
    }

    private String extractStrategyKey(String sheetConfigJson, String templateType) {
        if (sheetConfigJson != null && !sheetConfigJson.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(sheetConfigJson);
                if (node.hasNonNull("strategyKey")) {
                    return node.get("strategyKey").asText();
                }
            } catch (Exception e) {
                log.warn("Failed to parse sheet_config JSON: {}", e.getMessage());
            }
        }
        if ("settlement".equalsIgnoreCase(templateType)) {
            return DEFAULT_SETTLEMENT_STRATEGY;
        }
        if ("dept_summary".equalsIgnoreCase(templateType)) {
            return ExportTemplateResolverKeys.STANDARD_DEPT_SUMMARY;
        }
        if ("price_summary".equalsIgnoreCase(templateType)) {
            return ExportTemplateResolverKeys.STANDARD_PRICE_SUMMARY;
        }
        if ("instrument_audit".equalsIgnoreCase(templateType)) {
            return ExportTemplateResolverKeys.INSTRUMENT_AUDIT;
        }
        if ("logistics_allocation".equalsIgnoreCase(templateType)) {
            return ExportTemplateResolverKeys.LOGISTICS_ALLOCATION;
        }
        if ("grand_summary".equalsIgnoreCase(templateType)) {
            return ExportTemplateResolverKeys.GRAND_SUMMARY;
        }
        if ("daily".equalsIgnoreCase(templateType)) {
            return ExportTemplateResolverKeys.DAILY_SPLIT;
        }
        return DEFAULT_BILL_STRATEGY;
    }

    private boolean matchesCustomerCode(ExportTemplate template, String customerCode) {
        if (template.getSheetConfig() == null || template.getSheetConfig().isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(template.getSheetConfig());
            return node.hasNonNull("customerCode")
                    && customerCode.equalsIgnoreCase(node.get("customerCode").asText());
        } catch (Exception e) {
            return false;
        }
    }
}
