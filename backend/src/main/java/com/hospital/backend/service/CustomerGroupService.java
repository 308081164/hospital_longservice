package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.logistics.SaveCustomerGroupRequest;
import com.hospital.backend.dto.request.logistics.SaveLogisticsAllocationConfigRequest;
import com.hospital.backend.dto.response.logistics.CustomerGroupResponse;
import com.hospital.backend.dto.response.logistics.LogisticsAllocationConfigResponse;

import java.util.List;

public interface CustomerGroupService {

    Result<List<CustomerGroupResponse>> listGroups(String groupType);

    Result<CustomerGroupResponse> getGroup(Long id);

    Result<CustomerGroupResponse> createGroup(SaveCustomerGroupRequest request);

    Result<CustomerGroupResponse> updateGroup(Long id, SaveCustomerGroupRequest request);

    Result<Boolean> deleteGroup(Long id);

    Result<LogisticsAllocationConfigResponse> syncAllocationConfig(
            Long groupId,
            SaveLogisticsAllocationConfigRequest request);
}
