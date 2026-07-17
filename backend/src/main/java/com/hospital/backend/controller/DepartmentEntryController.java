package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.deptphysician.SaveDepartmentEntryRequest;
import com.hospital.backend.dto.response.deptphysician.DepartmentEntryResponse;
import com.hospital.backend.service.DepartmentEntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/departments")
@RequiredArgsConstructor
public class DepartmentEntryController {

    private final DepartmentEntryService departmentEntryService;

    @GetMapping
    public Result<List<DepartmentEntryResponse>> listEntries(
            @PathVariable Long customerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive) {
        return departmentEntryService.listEntries(customerId, keyword, isActive);
    }

    @PostMapping
    public Result<DepartmentEntryResponse> createEntry(
            @PathVariable Long customerId,
            @Valid @RequestBody SaveDepartmentEntryRequest request) {
        return departmentEntryService.createEntry(customerId, request);
    }

    @PutMapping("/{entryId}")
    public Result<DepartmentEntryResponse> updateEntry(
            @PathVariable Long customerId,
            @PathVariable Long entryId,
            @Valid @RequestBody SaveDepartmentEntryRequest request) {
        return departmentEntryService.updateEntry(customerId, entryId, request);
    }

    @DeleteMapping("/{entryId}")
    public Result<Boolean> deleteEntry(
            @PathVariable Long customerId,
            @PathVariable Long entryId) {
        return departmentEntryService.deleteEntry(customerId, entryId);
    }
}
