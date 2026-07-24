package com.hospital.backend.controller;

import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.allocation.RunAllocationRequest;
import com.hospital.backend.entity.ExternalInstrument;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.entity.RosterEntry;
import com.hospital.backend.export.ReconciliationLegacyExportBridge;
import com.hospital.backend.export.SheetOrchestrator;
import com.hospital.backend.mapper.ExternalInstrumentMapper;
import com.hospital.backend.mapper.HospitalReconciliationJobMapper;
import com.hospital.backend.mapper.HospitalReconciliationRowMapper;
import com.hospital.backend.mapper.RosterEntryMapper;
import com.hospital.backend.service.CustomerResolver;
import com.hospital.backend.service.DepartmentAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/hospital-reconciliations/{jobId}")
@RequiredArgsConstructor
public class DepartmentAllocationController {

    private final DepartmentAllocationService departmentAllocationService;
    private final HospitalReconciliationJobMapper jobMapper;
    private final HospitalReconciliationRowMapper rowMapper;
    private final RosterEntryMapper rosterEntryMapper;
    private final ExternalInstrumentMapper externalInstrumentMapper;
    private final CustomerResolver customerResolver;
    private final SheetOrchestrator sheetOrchestrator;
    private final ReconciliationLegacyExportBridge legacyExportBridge;

    @PostMapping("/allocate")
    public Result<AllocationResult> runAllocation(
            @PathVariable Long jobId,
            @RequestBody(required = false) RunAllocationRequest request) {
        return departmentAllocationService.runAllocation(jobId, request);
    }

    @GetMapping("/allocation-result")
    public Result<AllocationResult> getAllocationResult(@PathVariable Long jobId) {
        return departmentAllocationService.getAllocationResult(jobId);
    }

    @GetMapping("/roster-hints")
    public Result<List<AllocationResult.RosterMatchHint>> rosterHints(@PathVariable Long jobId) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail(404, "Job not found");
        }
        return customerResolver.resolveByName(job.getHospitalName())
                .map(customer -> {
                    List<RosterEntry> roster = rosterEntryMapper.selectActiveByCustomerId(customer.getId());
                    List<HospitalReconciliationRow> rows =
                            rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(jobId);
                    return Result.success(departmentAllocationService.buildRosterHints(
                            customer.getId(), rows, roster));
                })
                .orElseGet(() -> Result.fail(404, "Customer not found for job"));
    }

    @PostMapping("/export-orchestrated")
    public ResponseEntity<byte[]> exportOrchestrated(@PathVariable Long jobId) {
        try {
            HospitalReconciliationJob job = jobMapper.selectById(jobId);
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            AllocationResult allocation =
                    departmentAllocationService.getAllocationResult(jobId).getData();

            List<HospitalReconciliationRow> rows =
                    rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(jobId);
            List<ExternalInstrument> external = externalInstrumentMapper.selectByJobId(jobId);

            byte[] content = sheetOrchestrator.buildOrchestratedWorkbook(
                    job.getHospitalName(), rows, allocation, external);
            String filename = safeName(job.getHospitalName()) + "_L3导出_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
            return legacyExportBridge.buildExcelDownloadResponse(content, filename);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String safeName(String name) {
        if (name == null) {
            return "hospital";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
