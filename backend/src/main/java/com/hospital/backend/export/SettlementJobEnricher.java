package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.ExternalInstrument;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.mapper.ExternalInstrumentMapper;
import com.hospital.backend.service.SettlementJobFieldsApplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Enriches job settlement fields in-memory before export (logistics pipeline / urgent / monthly).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementJobEnricher {

    private final SettlementJobFieldsApplier settlementJobFieldsApplier;
    private final ExternalInstrumentMapper externalInstrumentMapper;

    public void enrichForExport(
            HospitalReconciliationJob job,
            JsonNode compiledRules,
            List<HospitalReconciliationRow> rows) {
        if (job == null || compiledRules == null) {
            return;
        }
        enrichAllocationExternalTotal(job);
        enrichUrgentFlags(rows);
        settlementJobFieldsApplier.applyAll(job, compiledRules, rows, false);
    }

    private void enrichUrgentFlags(List<HospitalReconciliationRow> rows) {
        if (rows == null) {
            return;
        }
        for (HospitalReconciliationRow row : rows) {
            if (Boolean.TRUE.equals(row.getIsUrgent())) {
                continue;
            }
            String sheetName = row.getSheetName();
            if (sheetName != null && sheetName.contains("加急")) {
                row.setIsUrgent(true);
                continue;
            }
            String notes = row.getBillingNotes();
            if (notes != null && notes.contains("加急")) {
                row.setIsUrgent(true);
            }
        }
    }

    private void enrichAllocationExternalTotal(HospitalReconciliationJob job) {
        if (job.getId() == null) {
            return;
        }
        try {
            double existing = parseExternalInstrumentTotal(job.getAllocationResult());
            if (existing > 0.01) {
                return;
            }
            List<ExternalInstrument> externalRows = externalInstrumentMapper.selectByJobId(job.getId());
            if (externalRows == null || externalRows.isEmpty()) {
                return;
            }
            double total = externalRows.stream()
                    .mapToDouble(row -> row.getTotalAmount() != null
                            ? row.getTotalAmount().doubleValue()
                            : 0)
                    .sum();
            if (total <= 0.01) {
                return;
            }
            ObjectNode node;
            if (job.getAllocationResult() == null || job.getAllocationResult().isBlank()) {
                node = JsonUtils.getObjectMapper().createObjectNode();
            } else {
                node = (ObjectNode) JsonUtils.getObjectMapper().readTree(job.getAllocationResult());
            }
            node.put("externalInstrumentTotal", total);
            job.setAllocationResult(node.toString());
        } catch (Exception e) {
            log.warn("Failed to enrich external instrument total for job {}: {}", job.getId(), e.getMessage());
        }
    }

    private static double parseExternalInstrumentTotal(String json) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            JsonNode node = JsonUtils.getObjectMapper().readTree(json);
            if (node.has("externalInstrumentTotal")) {
                return node.path("externalInstrumentTotal").asDouble(0);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return 0;
    }
}
