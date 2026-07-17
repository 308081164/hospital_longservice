package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.deptphysician.SavePhysicianEntryRequest;
import com.hospital.backend.dto.response.deptphysician.PhysicianEntryResponse;
import com.hospital.backend.service.PhysicianEntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/physicians")
@RequiredArgsConstructor
public class PhysicianEntryController {

    private final PhysicianEntryService physicianEntryService;

    @GetMapping
    public Result<List<PhysicianEntryResponse>> listEntries(
            @PathVariable Long customerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive) {
        return physicianEntryService.listEntries(customerId, keyword, isActive);
    }

    @PostMapping
    public Result<PhysicianEntryResponse> createEntry(
            @PathVariable Long customerId,
            @Valid @RequestBody SavePhysicianEntryRequest request) {
        return physicianEntryService.createEntry(customerId, request);
    }

    @PutMapping("/{entryId}")
    public Result<PhysicianEntryResponse> updateEntry(
            @PathVariable Long customerId,
            @PathVariable Long entryId,
            @Valid @RequestBody SavePhysicianEntryRequest request) {
        return physicianEntryService.updateEntry(customerId, entryId, request);
    }

    @DeleteMapping("/{entryId}")
    public Result<Boolean> deleteEntry(
            @PathVariable Long customerId,
            @PathVariable Long entryId) {
        return physicianEntryService.deleteEntry(customerId, entryId);
    }
}
