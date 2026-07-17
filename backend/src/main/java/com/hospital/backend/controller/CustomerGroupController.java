package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.logistics.SaveCustomerGroupRequest;
import com.hospital.backend.dto.request.logistics.SaveLogisticsAllocationConfigRequest;
import com.hospital.backend.dto.response.logistics.CustomerGroupResponse;
import com.hospital.backend.dto.response.logistics.LogisticsAllocationConfigResponse;
import com.hospital.backend.service.CustomerGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer-groups")
@RequiredArgsConstructor
public class CustomerGroupController {

    private final CustomerGroupService customerGroupService;

    @GetMapping
    public Result<List<CustomerGroupResponse>> listGroups(
            @RequestParam(required = false) String groupType) {
        return customerGroupService.listGroups(groupType);
    }

    @GetMapping("/{id}")
    public Result<CustomerGroupResponse> getGroup(@PathVariable Long id) {
        return customerGroupService.getGroup(id);
    }

    @PostMapping
    public Result<CustomerGroupResponse> createGroup(@Valid @RequestBody SaveCustomerGroupRequest request) {
        return customerGroupService.createGroup(request);
    }

    @PutMapping("/{id}")
    public Result<CustomerGroupResponse> updateGroup(
            @PathVariable Long id,
            @Valid @RequestBody SaveCustomerGroupRequest request) {
        return customerGroupService.updateGroup(id, request);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteGroup(@PathVariable Long id) {
        return customerGroupService.deleteGroup(id);
    }

    @PutMapping("/{id}/allocation-config")
    public Result<LogisticsAllocationConfigResponse> syncAllocationConfig(
            @PathVariable Long id,
            @Valid @RequestBody SaveLogisticsAllocationConfigRequest request) {
        return customerGroupService.syncAllocationConfig(id, request);
    }
}
