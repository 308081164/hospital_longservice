package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.external.SaveExternalInstrumentRequest;
import com.hospital.backend.dto.response.external.ExternalInstrumentResponse;
import com.hospital.backend.service.ExternalInstrumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ExternalInstrumentController {

    private final ExternalInstrumentService externalInstrumentService;

    @GetMapping("/api/v1/customers/{customerId}/external-instruments")
    public Result<List<ExternalInstrumentResponse>> listCatalog(@PathVariable Long customerId) {
        return externalInstrumentService.listCatalog(customerId);
    }

    @PostMapping("/api/v1/customers/{customerId}/external-instruments")
    public Result<ExternalInstrumentResponse> createCatalogEntry(
            @PathVariable Long customerId,
            @Valid @RequestBody SaveExternalInstrumentRequest request) {
        return externalInstrumentService.createCatalogEntry(customerId, request);
    }

    @GetMapping("/api/hospital-reconciliations/{jobId}/external-instruments")
    public Result<List<ExternalInstrumentResponse>> listByJob(@PathVariable Long jobId) {
        return externalInstrumentService.listByJob(jobId);
    }

    @PostMapping("/api/hospital-reconciliations/{jobId}/external-instruments")
    public Result<ExternalInstrumentResponse> createJobEntry(
            @PathVariable Long jobId,
            @Valid @RequestBody SaveExternalInstrumentRequest request) {
        return externalInstrumentService.createJobEntry(jobId, request);
    }

    @PostMapping("/api/hospital-reconciliations/{jobId}/external-instruments/import")
    public Result<Integer> importJobExcel(
            @PathVariable Long jobId,
            @RequestParam("file") MultipartFile file) {
        return externalInstrumentService.importJobExcel(jobId, file);
    }

    @PutMapping("/api/v1/external-instruments/{id}")
    public Result<ExternalInstrumentResponse> updateEntry(
            @PathVariable Long id,
            @Valid @RequestBody SaveExternalInstrumentRequest request) {
        return externalInstrumentService.updateEntry(id, request);
    }

    @DeleteMapping("/api/v1/external-instruments/{id}")
    public Result<Boolean> deleteEntry(@PathVariable Long id) {
        return externalInstrumentService.deleteEntry(id);
    }
}
