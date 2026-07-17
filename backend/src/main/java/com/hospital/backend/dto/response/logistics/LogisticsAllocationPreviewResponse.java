package com.hospital.backend.dto.response.logistics;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class LogisticsAllocationPreviewResponse {

    private Long jobId;

    private Double totalLogisticsFee;

    private Double allocationSum;

    private List<Map<String, Object>> deptAllocations;

    private Map<String, Object> logisticsBreakdown;
}
