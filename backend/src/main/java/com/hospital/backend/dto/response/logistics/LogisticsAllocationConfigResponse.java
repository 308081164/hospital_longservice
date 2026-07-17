package com.hospital.backend.dto.response.logistics;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class LogisticsAllocationConfigResponse {

    private Long groupId;

    private String groupName;

    private String allocationMode;

    private List<Long> memberCustomerIds;

    private Map<Long, Double> shareRatios;

    private Boolean mergeSameDay;

    private Boolean syncToMembers;

    private List<Integer> billingWeekdays;

    private List<String> excludeDepartments;

    private Long singleOwnerCustomerId;

    /** Number of member policies updated when syncToMembers=true */
    private Integer syncedPolicyCount;
}
