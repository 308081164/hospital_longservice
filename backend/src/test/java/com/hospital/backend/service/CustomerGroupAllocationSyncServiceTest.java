package com.hospital.backend.service;

import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.dto.request.logistics.SaveLogisticsAllocationConfigRequest;
import com.hospital.backend.entity.CustomerBillingPolicy;
import com.hospital.backend.entity.CustomerGroup;
import com.hospital.backend.entity.CustomerGroupMember;
import com.hospital.backend.mapper.CustomerBillingPolicyMapper;
import com.hospital.backend.mapper.CustomerGroupMapper;
import com.hospital.backend.mapper.CustomerGroupMemberMapper;
import com.hospital.backend.service.impl.CustomerGroupServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerGroupAllocationSyncServiceTest {

    @Mock
    private CustomerGroupMapper customerGroupMapper;

    @Mock
    private CustomerGroupMemberMapper customerGroupMemberMapper;

    @Mock
    private CustomerBillingPolicyMapper customerBillingPolicyMapper;

    @InjectMocks
    private CustomerGroupServiceImpl customerGroupService;

    @Test
    void syncAllocationConfig_propagatesAllocationFieldsToMemberPolicies() throws Exception {
        CustomerGroup group = new CustomerGroup();
        group.setId(10L);
        group.setName("Merge Group");
        group.setGroupType("logistics_merge");
        group.setIsActive(true);
        when(customerGroupMapper.selectById(10L)).thenReturn(group);

        CustomerBillingPolicy policyA = logisticsPolicy(1L, 101L, "{\"feePerTrip\":80,\"cardDeductionEnabled\":true}");
        CustomerBillingPolicy policyB = logisticsPolicy(2L, 102L, "{\"feePerTrip\":90}");
        when(customerBillingPolicyMapper.selectByCustomerIdAndType(101L, "LOGISTICS"))
                .thenReturn(List.of(policyA));
        when(customerBillingPolicyMapper.selectByCustomerIdAndType(102L, "LOGISTICS"))
                .thenReturn(List.of(policyB));

        SaveLogisticsAllocationConfigRequest request = new SaveLogisticsAllocationConfigRequest();
        request.setAllocationMode("equal");
        request.setMemberCustomerIds(List.of(101L, 102L));
        request.setShareRatios(Map.of(101L, 0.4, 102L, 0.6));
        request.setMergeSameDay(true);
        request.setSyncToMembers(true);
        request.setBillingWeekdays(List.of(1, 2, 3, 4, 5));
        request.setExcludeDepartments(List.of("供应中心"));

        var result = customerGroupService.syncAllocationConfig(10L, request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getSyncedPolicyCount()).isEqualTo(2);
        assertThat(result.getData().getAllocationMode()).isEqualTo("equal");

        ArgumentCaptor<CustomerBillingPolicy> captor = ArgumentCaptor.forClass(CustomerBillingPolicy.class);
        verify(customerBillingPolicyMapper, org.mockito.Mockito.times(2)).updateById(captor.capture());

        Map<String, Object> updatedParams = JsonUtils.getObjectMapper()
                .readValue(captor.getAllValues().get(0).getParams(), Map.class);
        assertThat(updatedParams.get("allocationMode")).isEqualTo("equal");
        assertThat(updatedParams.get("logisticsMergeGroupId")).isEqualTo(10);
        assertThat(updatedParams.get("feePerTrip")).isEqualTo(80);
        assertThat(updatedParams.get("cardDeductionEnabled")).isEqualTo(true);
        assertThat(updatedParams.get("billingWeekdays")).isEqualTo(List.of(1, 2, 3, 4, 5));
        assertThat(updatedParams.get("excludeDepartments")).isEqualTo(List.of("供应中心"));

        verify(customerGroupMemberMapper).deleteByGroupId(10L);
        verify(customerGroupMemberMapper, org.mockito.Mockito.times(2)).insert(any(CustomerGroupMember.class));
        verify(customerGroupMapper).updateById(any(CustomerGroup.class));
    }

    private static CustomerBillingPolicy logisticsPolicy(Long id, Long customerId, String params) {
        CustomerBillingPolicy policy = new CustomerBillingPolicy();
        policy.setId(id);
        policy.setCustomerId(customerId);
        policy.setPolicyType("LOGISTICS");
        policy.setParams(params);
        policy.setIsActive(true);
        return policy;
    }
}
