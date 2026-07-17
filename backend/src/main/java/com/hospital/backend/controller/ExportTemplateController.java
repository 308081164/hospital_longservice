package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.export.SaveExportTemplateRequest;
import com.hospital.backend.dto.response.export.ExportTemplateResponse;
import com.hospital.backend.service.ExportTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/export-templates")
@RequiredArgsConstructor
public class ExportTemplateController {

    private final ExportTemplateService exportTemplateService;

    @GetMapping
    public Result<List<ExportTemplateResponse>> listTemplates(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String templateType) {
        return exportTemplateService.listTemplates(customerId, templateType);
    }

    @GetMapping("/{id}")
    public Result<ExportTemplateResponse> getTemplate(@PathVariable Long id) {
        return exportTemplateService.getTemplate(id);
    }

    @PostMapping
    public Result<ExportTemplateResponse> createTemplate(
            @Valid @RequestBody SaveExportTemplateRequest request) {
        return exportTemplateService.createTemplate(request);
    }

    @PutMapping("/{id}")
    public Result<ExportTemplateResponse> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody SaveExportTemplateRequest request) {
        return exportTemplateService.updateTemplate(id, request);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteTemplate(@PathVariable Long id) {
        return exportTemplateService.deleteTemplate(id);
    }
}
