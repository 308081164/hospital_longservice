package com.hospital.backend.allocation;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AllocationResult {

    private Long jobId;

    private Long customerId;

    private double originalGrandTotal;

    private double adjustmentTotal;

    private double externalInstrumentTotal;

    private double logisticsTotal;

    private double reconciledGrandTotal;

    private boolean balanced;

    private String balanceMessage;

    private List<AllocatedLineItem> allocatedLines = new ArrayList<>();

    private List<AllocatedLineItem> adjustmentLines = new ArrayList<>();

    private List<DepartmentSheetSummary> departmentSummaries = new ArrayList<>();

    private Map<String, Double> priceSummaryByCategory = new LinkedHashMap<>();

    private List<RosterMatchHint> rosterHints = new ArrayList<>();

    @Getter
    @Setter
    public static class RosterMatchHint {
        private Long rowId;
        private Integer rowNumber;
        private String packName;
        private String matchedDoctor;
        private String suggestedDepartment;
        private boolean confirmed;
        private String overrideDepartment;
    }
}
