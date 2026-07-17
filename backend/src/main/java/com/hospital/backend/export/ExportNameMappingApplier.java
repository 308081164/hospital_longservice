package com.hospital.backend.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FR-M1-09 — 导出阶段包名/类型名称替换，不影响计价结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExportNameMappingApplier {

    private final CustomerMapper customerMapper;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    public List<HospitalReconciliationRow> apply(Long customerId, List<HospitalReconciliationRow> rows) {
        if (customerId == null || rows == null || rows.isEmpty()) {
            return rows;
        }
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null || customer.getExportNameMapping() == null || customer.getExportNameMapping().isBlank()) {
            return rows;
        }
        Map<String, String> mapping = parseMapping(customer.getExportNameMapping());
        if (mapping.isEmpty()) {
            return rows;
        }
        List<HospitalReconciliationRow> result = new ArrayList<>(rows.size());
        for (HospitalReconciliationRow row : rows) {
            result.add(applyToRow(row, mapping));
        }
        return result;
    }

    HospitalReconciliationRow applyToRow(HospitalReconciliationRow row, Map<String, String> mapping) {
        if (row.getPackName() != null) {
            String mapped = mapValue(row.getPackName(), mapping);
            if (!mapped.equals(row.getPackName())) {
                row.setPackName(mapped);
            }
        }
        if (row.getType() != null) {
            String mapped = mapValue(row.getType(), mapping);
            if (!mapped.equals(row.getType())) {
                row.setType(mapped);
            }
        }
        return row;
    }

    String mapValue(String original, Map<String, String> mapping) {
        if (original == null || original.isBlank()) {
            return original;
        }
        String exact = mapping.get(original);
        if (exact != null) {
            return exact;
        }
        String lower = original.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).equals(lower)) {
                return entry.getValue();
            }
        }
        return original;
    }

    Map<String, String> parseMapping(String json) {
        try {
            Map<String, String> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, String> normalized = new LinkedHashMap<>();
            if (raw != null) {
                raw.forEach((k, v) -> {
                    if (k != null && v != null && !k.isBlank()) {
                        normalized.put(k.trim(), v.trim());
                    }
                });
            }
            return normalized;
        } catch (Exception e) {
            log.warn("Invalid export_name_mapping JSON: {}", e.getMessage());
            return Map.of();
        }
    }
}
