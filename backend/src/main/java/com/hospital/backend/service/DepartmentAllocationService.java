package com.hospital.backend.service;

import com.hospital.backend.allocation.AllocationConfig;
import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.allocation.RunAllocationRequest;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.entity.RosterEntry;

import java.util.List;
import java.util.Optional;

public interface DepartmentAllocationService {

    Result<AllocationResult> runAllocation(Long jobId, RunAllocationRequest request);

    Result<AllocationResult> getAllocationResult(Long jobId);

    List<AllocationResult.RosterMatchHint> buildRosterHints(
            Long customerId,
            List<HospitalReconciliationRow> rows,
            List<RosterEntry> roster);

    Optional<RosterEntry> matchRosterInText(String text, List<RosterEntry> roster);

    boolean matchesAdjustmentKeyword(String text, AllocationConfig config);

    boolean isLowTemperatureRow(HospitalReconciliationRow row, AllocationConfig config);

    double rowAmount(HospitalReconciliationRow row);
}
