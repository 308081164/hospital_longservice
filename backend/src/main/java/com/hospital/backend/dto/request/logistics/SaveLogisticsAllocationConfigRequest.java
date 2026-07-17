package com.hospital.backend.dto.request.logistics;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class SaveLogisticsAllocationConfigRequest {

    /** none | dept_ratio | equal | proportional | single_owner | cross_hospital_merge */
    @NotBlank
    private String allocationMode;

    private String groupName;

    private List<Long> memberCustomerIds;

    /** customerId -> ratio (0..1), optional */
    private Map<Long, Double> shareRatios;

    private Boolean mergeSameDay;

    /** When true, propagate allocation fields to all member LOGISTICS policies */
    private Boolean syncToMembers;

    private List<Integer> billingWeekdays;

    private List<String> excludeDepartments;

    private Long singleOwnerCustomerId;
}
