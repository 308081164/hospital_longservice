package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.dto.response.hospital.ReconciliationJobResponse;
import com.hospital.backend.export.BillExportLayoutResolver;
import com.hospital.backend.export.ExportTemplateResolver;
import com.hospital.backend.export.ExportType;
import com.hospital.backend.export.model.ColumnMappingConfig;
import com.hospital.backend.export.model.ResolvedExportTemplate;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按医院名称解析可用导出类型，供列表卡片与导出下拉使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalExportCapabilityService {

    private static final List<String> DEFAULT_TYPES = List.of("bill", "settlement");
    private static final Set<String> STANDARD_TYPES = Set.of("bill", "settlement");

    private final CustomerResolver customerResolver;
    private final ExportTemplateResolver exportTemplateResolver;
    private final BillExportLayoutResolver billExportLayoutResolver;

    private Map<String, List<String>> hospitalExportTypes = Map.of();

    @PostConstruct
    void loadCapabilities() {
        try (InputStream in = new ClassPathResource("hospital-export-capabilities.json").getInputStream()) {
            JsonNode root = JsonUtils.getObjectMapper().readTree(in);
            JsonNode hospitals = root.path("hospitals");
            Map<String, List<String>> loaded = new LinkedHashMap<>();
            hospitals.fields().forEachRemaining(entry -> {
                List<String> types = JsonUtils.getObjectMapper().convertValue(
                        entry.getValue(),
                        JsonUtils.getObjectMapper().getTypeFactory()
                                .constructCollectionType(List.class, String.class));
                loaded.put(entry.getKey(), List.copyOf(types));
            });
            hospitalExportTypes = Collections.unmodifiableMap(loaded);
            log.info("Loaded export capabilities for {} hospitals", hospitalExportTypes.size());
        } catch (Exception e) {
            log.warn("Failed to load hospital-export-capabilities.json, using bill+settlement only: {}", e.getMessage());
            hospitalExportTypes = Map.of();
        }
    }

    public List<String> getExportTypes(String hospitalName) {
        if (hospitalName == null || hospitalName.isBlank()) {
            return DEFAULT_TYPES;
        }
        return hospitalExportTypes.getOrDefault(hospitalName.trim(), DEFAULT_TYPES);
    }

    public boolean hasSpecialExport(String hospitalName) {
        List<String> types = getExportTypes(hospitalName);
        boolean hasExtraTypes = types.stream().anyMatch(type -> !STANDARD_TYPES.contains(type));
        boolean billingEnabled = customerResolver.resolveByName(hospitalName)
                .map(c -> Boolean.TRUE.equals(c.getBillingEnabled()))
                .orElse(false);
        return billingEnabled || hasExtraTypes;
    }

    public String buildExportProfileLabel(String hospitalName) {
        boolean billingEnabled = customerResolver.resolveByName(hospitalName)
                .map(c -> Boolean.TRUE.equals(c.getBillingEnabled()))
                .orElse(false);
        String billLayout = resolveBillLayout(hospitalName);
        return billExportLayoutResolver.buildExportProfileLabel(billingEnabled, billLayout);
    }

    public void enrichJobResponse(ReconciliationJobResponse response, String hospitalName) {
        List<String> types = getExportTypes(hospitalName);
        response.setExportTypes(types);
        boolean billingEnabled = customerResolver.resolveByName(hospitalName)
                .map(c -> Boolean.TRUE.equals(c.getBillingEnabled()))
                .orElse(false);
        response.setBillingEnabled(billingEnabled);
        response.setHasSpecialExport(billingEnabled || types.stream().anyMatch(type -> !STANDARD_TYPES.contains(type)));
        response.setExportProfileLabel(buildExportProfileLabel(hospitalName));
    }

    private String resolveBillLayout(String hospitalName) {
        Long customerId = customerResolver.resolveByName(hospitalName).map(c -> c.getId()).orElse(null);
        try {
            ResolvedExportTemplate template = exportTemplateResolver.resolve(customerId, ExportType.BILL);
            ColumnMappingConfig mapping = template.getColumnMapping();
            return billExportLayoutResolver.resolveBillLayout(mapping);
        } catch (Exception e) {
            return BillExportLayoutResolver.LAYOUT_AUTO;
        }
    }
}
