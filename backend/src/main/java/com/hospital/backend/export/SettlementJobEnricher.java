package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
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

    public void enrichForExport(
            HospitalReconciliationJob job,
            JsonNode compiledRules,
            List<HospitalReconciliationRow> rows) {
        if (job == null || compiledRules == null) {
            return;
        }
        settlementJobFieldsApplier.applyAll(job, compiledRules, rows, false);
    }
}
