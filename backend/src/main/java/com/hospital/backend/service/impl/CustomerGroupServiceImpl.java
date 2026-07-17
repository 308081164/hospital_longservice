package com.hospital.backend.service.impl;

import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.logistics.SaveCustomerGroupRequest;
import com.hospital.backend.dto.request.logistics.SaveLogisticsAllocationConfigRequest;
import com.hospital.backend.dto.response.logistics.CustomerGroupMemberResponse;
import com.hospital.backend.dto.response.logistics.CustomerGroupResponse;
import com.hospital.backend.dto.response.logistics.LogisticsAllocationConfigResponse;
import com.hospital.backend.entity.CustomerBillingPolicy;
import com.hospital.backend.entity.CustomerGroup;
import com.hospital.backend.entity.CustomerGroupMember;
import com.hospital.backend.mapper.CustomerBillingPolicyMapper;
import com.hospital.backend.mapper.CustomerGroupMapper;
import com.hospital.backend.mapper.CustomerGroupMemberMapper;
import com.hospital.backend.service.CustomerGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerGroupServiceImpl implements CustomerGroupService {

    private static final Set<String> CROSS_HOSPITAL_MODES = Set.of(
            "equal", "proportional", "single_owner", "cross_hospital_merge");

    private final CustomerGroupMapper customerGroupMapper;
    private final CustomerGroupMemberMapper customerGroupMemberMapper;
    private final CustomerBillingPolicyMapper customerBillingPolicyMapper;

    @Override
    public Result<List<CustomerGroupResponse>> listGroups(String groupType) {
        return Result.success(customerGroupMapper.selectAll(groupType).stream()
                .map(this::toResponse)
                .toList());
    }

    @Override
    public Result<CustomerGroupResponse> getGroup(Long id) {
        CustomerGroup group = customerGroupMapper.selectById(id);
        if (group == null) {
            return Result.fail(404, "Customer group not found");
        }
        return Result.success(toResponse(group));
    }

    @Override
    @Transactional
    public Result<CustomerGroupResponse> createGroup(SaveCustomerGroupRequest request) {
        CustomerGroup group = new CustomerGroup();
        group.setName(request.getName());
        group.setGroupType(request.getGroupType());
        group.setConfig(request.getConfig());
        group.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        customerGroupMapper.insert(group);
        replaceMembers(group.getId(), request.getMembers());
        return Result.success(toResponse(customerGroupMapper.selectById(group.getId())));
    }

    @Override
    @Transactional
    public Result<CustomerGroupResponse> updateGroup(Long id, SaveCustomerGroupRequest request) {
        CustomerGroup existing = customerGroupMapper.selectById(id);
        if (existing == null) {
            return Result.fail(404, "Customer group not found");
        }
        existing.setName(request.getName());
        existing.setGroupType(request.getGroupType());
        existing.setConfig(request.getConfig());
        if (request.getIsActive() != null) {
            existing.setIsActive(request.getIsActive());
        }
        customerGroupMapper.updateById(existing);
        replaceMembers(id, request.getMembers());
        return Result.success(toResponse(customerGroupMapper.selectById(id)));
    }

    @Override
    @Transactional
    public Result<Boolean> deleteGroup(Long id) {
        CustomerGroup existing = customerGroupMapper.selectById(id);
        if (existing == null) {
            return Result.fail(404, "Customer group not found");
        }
        customerGroupMemberMapper.deleteByGroupId(id);
        customerGroupMapper.deleteById(id);
        return Result.success(true);
    }

    @Override
    @Transactional
    public Result<LogisticsAllocationConfigResponse> syncAllocationConfig(
            Long groupId,
            SaveLogisticsAllocationConfigRequest request) {
        String mode = normalizeAllocationMode(request.getAllocationMode());
        if (mode == null) {
            return Result.fail(400, "Invalid allocation mode");
        }

        CustomerGroup group = customerGroupMapper.selectById(groupId);
        if (group == null || !"logistics_merge".equalsIgnoreCase(group.getGroupType())) {
            return Result.fail(404, "Customer group not found");
        }

        if (CROSS_HOSPITAL_MODES.contains(mode)) {
            if (request.getMemberCustomerIds() == null || request.getMemberCustomerIds().size() < 2) {
                return Result.fail(400, "Cross-hospital allocation requires at least 2 members");
            }
            if ("single_owner".equals(mode) && request.getSingleOwnerCustomerId() == null) {
                return Result.fail(400, "single_owner mode requires singleOwnerCustomerId");
            }
        }

        if (request.getGroupName() != null && !request.getGroupName().isBlank()) {
            group.setName(request.getGroupName().trim());
        }
        group.setConfig(buildAllocationConfigJson(request, mode));
        customerGroupMapper.updateById(group);

        List<SaveCustomerGroupRequest.CustomerGroupMemberPayload> memberPayloads = new ArrayList<>();
        if (request.getMemberCustomerIds() != null) {
            for (Long customerId : request.getMemberCustomerIds()) {
                if (customerId == null) {
                    continue;
                }
                SaveCustomerGroupRequest.CustomerGroupMemberPayload payload =
                        new SaveCustomerGroupRequest.CustomerGroupMemberPayload();
                payload.setCustomerId(customerId);
                if (request.getShareRatios() != null && request.getShareRatios().containsKey(customerId)) {
                    payload.setShareRatio(request.getShareRatios().get(customerId));
                }
                memberPayloads.add(payload);
            }
        }
        replaceMembers(groupId, memberPayloads);

        int syncedCount = 0;
        boolean syncToMembers = request.getSyncToMembers() == null || request.getSyncToMembers();
        if (syncToMembers && request.getMemberCustomerIds() != null) {
            for (Long customerId : request.getMemberCustomerIds()) {
                if (customerId == null) {
                    continue;
                }
                if (syncLogisticsPolicyAllocation(customerId, groupId, request, mode)) {
                    syncedCount++;
                }
            }
        }

        Map<Long, Double> shareRatios = new LinkedHashMap<>();
        if (request.getShareRatios() != null) {
            shareRatios.putAll(request.getShareRatios());
        }

        return Result.success(LogisticsAllocationConfigResponse.builder()
                .groupId(groupId)
                .groupName(group.getName())
                .allocationMode(mode)
                .memberCustomerIds(request.getMemberCustomerIds())
                .shareRatios(shareRatios)
                .mergeSameDay(request.getMergeSameDay() == null || request.getMergeSameDay())
                .syncToMembers(syncToMembers)
                .billingWeekdays(request.getBillingWeekdays())
                .excludeDepartments(request.getExcludeDepartments())
                .singleOwnerCustomerId(request.getSingleOwnerCustomerId())
                .syncedPolicyCount(syncedCount)
                .build());
    }

    private boolean syncLogisticsPolicyAllocation(
            Long customerId,
            Long groupId,
            SaveLogisticsAllocationConfigRequest request,
            String mode) {
        List<CustomerBillingPolicy> policies =
                customerBillingPolicyMapper.selectByCustomerIdAndType(customerId, "LOGISTICS");
        if (policies.isEmpty()) {
            return false;
        }
        CustomerBillingPolicy policy = policies.get(0);
        Map<String, Object> params = readPolicyParams(policy.getParams());
        params.put("allocationMode", mode);
        if (request.getBillingWeekdays() != null && !request.getBillingWeekdays().isEmpty()) {
            params.put("billingWeekdays", request.getBillingWeekdays());
        } else {
            params.remove("billingWeekdays");
        }
        if (request.getExcludeDepartments() != null && !request.getExcludeDepartments().isEmpty()) {
            params.put("excludeDepartments", request.getExcludeDepartments());
        } else {
            params.remove("excludeDepartments");
        }
        if (CROSS_HOSPITAL_MODES.contains(mode)) {
            params.put("logisticsMergeGroupId", groupId);
            params.put("mergeSameDay", request.getMergeSameDay() == null || request.getMergeSameDay());
        } else {
            params.remove("logisticsMergeGroupId");
            params.remove("mergeSameDay");
        }
        if ("single_owner".equals(mode) && request.getSingleOwnerCustomerId() != null) {
            params.put("singleOwnerCustomerId", request.getSingleOwnerCustomerId());
        } else {
            params.remove("singleOwnerCustomerId");
        }
        policy.setParams(JsonUtils.toJson(params));
        customerBillingPolicyMapper.updateById(policy);
        return true;
    }

    private String buildAllocationConfigJson(SaveLogisticsAllocationConfigRequest request, String mode) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("allocationMode", mode);
        config.put("mergeSameDay", request.getMergeSameDay() == null || request.getMergeSameDay());
        config.put("syncToMembers", request.getSyncToMembers() == null || request.getSyncToMembers());
        if (request.getBillingWeekdays() != null && !request.getBillingWeekdays().isEmpty()) {
            config.put("billingWeekdays", request.getBillingWeekdays());
        }
        if (request.getExcludeDepartments() != null && !request.getExcludeDepartments().isEmpty()) {
            config.put("excludeDepartments", request.getExcludeDepartments());
        }
        if (request.getSingleOwnerCustomerId() != null) {
            config.put("singleOwnerCustomerId", request.getSingleOwnerCustomerId());
        }
        return JsonUtils.toJson(config);
    }

    private String normalizeAllocationMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        String normalized = mode.trim().toLowerCase();
        return Set.of("none", "dept_ratio", "equal", "proportional", "single_owner", "cross_hospital_merge")
                .contains(normalized) ? normalized : null;
    }

    private Map<String, Object> readPolicyParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = JsonUtils.getObjectMapper().readValue(paramsJson, Map.class);
            return parsed != null ? new LinkedHashMap<>(parsed) : new LinkedHashMap<>();
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private void replaceMembers(Long groupId, List<SaveCustomerGroupRequest.CustomerGroupMemberPayload> members) {
        customerGroupMemberMapper.deleteByGroupId(groupId);
        if (members == null) {
            return;
        }
        for (SaveCustomerGroupRequest.CustomerGroupMemberPayload payload : members) {
            if (payload.getCustomerId() == null) {
                continue;
            }
            CustomerGroupMember member = new CustomerGroupMember();
            member.setGroupId(groupId);
            member.setCustomerId(payload.getCustomerId());
            member.setShareRatio(payload.getShareRatio());
            customerGroupMemberMapper.insert(member);
        }
    }

    private CustomerGroupResponse toResponse(CustomerGroup group) {
        List<CustomerGroupMemberResponse> members = customerGroupMemberMapper.selectByGroupId(group.getId()).stream()
                .map(member -> CustomerGroupMemberResponse.builder()
                        .id(member.getId())
                        .groupId(member.getGroupId())
                        .customerId(member.getCustomerId())
                        .shareRatio(member.getShareRatio())
                        .build())
                .toList();
        return CustomerGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .groupType(group.getGroupType())
                .config(group.getConfig())
                .isActive(group.getIsActive())
                .members(members)
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }
}
