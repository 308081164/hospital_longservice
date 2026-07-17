package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.roster.SaveRosterEntryRequest;
import com.hospital.backend.dto.response.roster.RosterEntryResponse;
import com.hospital.backend.dto.response.roster.RosterImportResultResponse;
import com.hospital.backend.service.RosterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/roster-entries")
@RequiredArgsConstructor
public class RosterController {

    private final RosterService rosterService;

    @GetMapping
    public Result<List<RosterEntryResponse>> listEntries(@PathVariable Long customerId) {
        return rosterService.listEntries(customerId);
    }

    @PostMapping
    public Result<RosterEntryResponse> createEntry(
            @PathVariable Long customerId,
            @Valid @RequestBody SaveRosterEntryRequest request) {
        return rosterService.createEntry(customerId, request);
    }

    @PutMapping("/{entryId}")
    public Result<RosterEntryResponse> updateEntry(
            @PathVariable Long customerId,
            @PathVariable Long entryId,
            @Valid @RequestBody SaveRosterEntryRequest request) {
        return rosterService.updateEntry(customerId, entryId, request);
    }

    @DeleteMapping("/{entryId}")
    public Result<Boolean> deleteEntry(
            @PathVariable Long customerId,
            @PathVariable Long entryId) {
        return rosterService.deleteEntry(customerId, entryId);
    }

    @PostMapping("/import")
    public Result<RosterImportResultResponse> importExcel(
            @PathVariable Long customerId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "replace", defaultValue = "false") boolean replace) {
        return rosterService.importExcel(customerId, file, replace);
    }
}
