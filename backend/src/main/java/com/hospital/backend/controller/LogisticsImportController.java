package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.logistics.SaveLogisticsImportRequest;
import com.hospital.backend.dto.response.logistics.LogisticsImportResponse;
import com.hospital.backend.service.LogisticsImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/logistics-imports")
@RequiredArgsConstructor
public class LogisticsImportController {

    private final LogisticsImportService logisticsImportService;

    @GetMapping
    public Result<List<LogisticsImportResponse>> listImports(
            @PathVariable Long customerId,
            @RequestParam(required = false) String billingMonth) {
        if (billingMonth != null && !billingMonth.isBlank()) {
            return logisticsImportService.listByCustomerAndMonth(customerId, billingMonth);
        }
        return logisticsImportService.listByCustomer(customerId);
    }

    @PostMapping
    public Result<LogisticsImportResponse> createImport(
            @PathVariable Long customerId,
            @Valid @RequestBody SaveLogisticsImportRequest request) {
        return logisticsImportService.create(customerId, request);
    }

    @PutMapping("/{importId}")
    public Result<LogisticsImportResponse> updateImport(
            @PathVariable Long customerId,
            @PathVariable Long importId,
            @Valid @RequestBody SaveLogisticsImportRequest request) {
        return logisticsImportService.update(customerId, importId, request);
    }

    @DeleteMapping("/{importId}")
    public Result<Boolean> deleteImport(
            @PathVariable Long customerId,
            @PathVariable Long importId) {
        return logisticsImportService.delete(customerId, importId);
    }
}
