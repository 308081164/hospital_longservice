package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.export.ExportV2Request;
import com.hospital.backend.dto.request.hospital.CreateExportLogRequest;
import com.hospital.backend.dto.request.hospital.HospitalBillTemplateExportRequest;
import com.hospital.backend.dto.request.hospital.HospitalSettlementTemplateExportRequest;
import com.hospital.backend.dto.request.hospital.ReconciliationReviewRequest;
import com.hospital.backend.dto.response.hospital.ReconciliationExportLogResponse;
import com.hospital.backend.dto.response.hospital.ReconciliationJobResponse;
import com.hospital.backend.dto.response.hospital.TemplateRefResponse;
import com.hospital.backend.dto.response.export.ExportPreviewResponse;
import com.hospital.backend.dto.response.export.ExportValidationResponse;
import com.hospital.backend.export.ExportEngineService;
import com.hospital.backend.service.DailySplitService;
import com.hospital.backend.service.HospitalReconciliationService;
import com.hospital.backend.service.InstrumentAuditReportService;
import com.hospital.backend.service.ReconciliationUnmatchedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HospitalReconciliationController {

    private final HospitalReconciliationService hospitalReconciliationService;
    private final ReconciliationUnmatchedService reconciliationUnmatchedService;
    private final ExportEngineService exportEngineService;
    private final InstrumentAuditReportService instrumentAuditReportService;
    private final DailySplitService dailySplitService;

    @GetMapping("/hospital-reconciliations/{jobId}/unmatched-products")
    public Result<Map<String, Object>> listUnmatchedProducts(@PathVariable Long jobId) {
        return reconciliationUnmatchedService.listUnmatchedProducts(jobId);
    }

    @PostMapping("/hospital-reconciliations")
    public Result<ReconciliationJobResponse> createReconciliation(
            @RequestParam("payload_json") String payloadJson,
            @RequestParam("source_file") MultipartFile sourceFile) {
        return hospitalReconciliationService.createReconciliation(payloadJson, sourceFile);
    }

    @PostMapping("/hospital-reconciliations/import")
    public Result<ReconciliationJobResponse> importAndProcess(
            @RequestParam("source_file") MultipartFile sourceFile,
            @RequestParam("rule_id") Long ruleId,
            @RequestParam("operator_name") String operatorName,
            @RequestParam(value = "hospital_name", required = false) String hospitalNameParam) {
        return hospitalReconciliationService.importAndProcess(
                sourceFile, ruleId, operatorName, hospitalNameParam);
    }

    @GetMapping("/hospital-reconciliations")
    public Result<List<ReconciliationJobResponse>> listReconciliations(
            @RequestParam(value = "hospital_name", required = false) String hospitalName) {
        return hospitalReconciliationService.listReconciliations(hospitalName);
    }

    @GetMapping("/hospital-reconciliations/{jobId}")
    public Result<ReconciliationJobResponse> getReconciliation(@PathVariable Long jobId) {
        return hospitalReconciliationService.getReconciliation(jobId);
    }

    @GetMapping("/hospital-reconciliations/{jobId}/rows")
    public Result<Map<String, Object>> getReconciliationRows(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "200") int size) {
        return hospitalReconciliationService.getReconciliationRows(jobId, page, size);
    }

    @PatchMapping("/hospital-reconciliations/{jobId}/review")
    @org.springframework.security.access.prepost.PreAuthorize(
            "hasAnyRole('SUPER','R_SUPER','R_ADMIN','R_BILLING_REVIEWER','R_BILLING_OPERATOR',"
                    + "'R_BILLING_CONFIG','R_USER')")
    public Result<ReconciliationJobResponse> reviewReconciliation(
            @PathVariable Long jobId,
            @Valid @RequestBody ReconciliationReviewRequest request) {
        return hospitalReconciliationService.reviewReconciliation(jobId, request);
    }

    @PutMapping("/hospital-reconciliations/{jobId}/rows")
    public Result<ReconciliationJobResponse> updateRows(
            @PathVariable Long jobId,
            @RequestBody List<Map<String, Object>> updatedRows) {
        return hospitalReconciliationService.updateRows(jobId, updatedRows);
    }

    @PatchMapping("/hospital-reconciliations/{jobId}/rows/urgent")
    public Result<ReconciliationJobResponse> updateRowsUrgent(
            @PathVariable Long jobId,
            @Valid @RequestBody com.hospital.backend.dto.request.hospital.UpdateRowsUrgentRequest request) {
        return hospitalReconciliationService.updateRowsUrgent(jobId, request);
    }

    @PostMapping("/hospital-reconciliations/{jobId}/reprice")
    public Result<Map<String, Object>> reprice(@PathVariable Long jobId) {
        return hospitalReconciliationService.reprice(jobId);
    }

    @PostMapping("/hospital-reconciliations/{jobId}/exports")
    public Result<ReconciliationExportLogResponse> createExportLog(
            @PathVariable Long jobId,
            @Valid @RequestBody CreateExportLogRequest request) {
        return hospitalReconciliationService.createExportLog(jobId, request);
    }

    @GetMapping("/hospital-reconciliations/templates/settlement")
    public Result<List<TemplateRefResponse>> listSettlementTemplates() {
        return hospitalReconciliationService.listSettlementTemplates();
    }

    @GetMapping("/hospital-reconciliations/templates/bill")
    public Result<List<TemplateRefResponse>> listBillTemplates() {
        return hospitalReconciliationService.listBillTemplates();
    }

    @GetMapping("/hospital-reconciliations/templates/settlement/{templateId}/preview")
    public ResponseEntity<String> previewSettlementTemplate(@PathVariable String templateId) {
        return hospitalReconciliationService.previewSettlementTemplate(templateId);
    }

    @PostMapping("/hospital-reconciliations/export-template-bill")
    public ResponseEntity<byte[]> exportTemplateBill(
            @RequestBody HospitalBillTemplateExportRequest request) {
        return hospitalReconciliationService.exportTemplateBill(request);
    }

    @PostMapping("/hospital-reconciliations/export-template-settlement")
    public ResponseEntity<byte[]> exportTemplateSettlement(
            @RequestBody HospitalSettlementTemplateExportRequest request) {
        return hospitalReconciliationService.exportTemplateSettlement(request);
    }

    @PostMapping("/hospital-reconciliations/{jobId}/export-department-summary")
    public ResponseEntity<byte[]> exportDepartmentSummary(@PathVariable Long jobId) {
        return hospitalReconciliationService.exportDepartmentSummary(jobId);
    }

    @PostMapping("/hospital-reconciliations/{jobId}/export-anomalies")
    public ResponseEntity<byte[]> exportAnomalies(@PathVariable Long jobId) {
        return hospitalReconciliationService.exportAnomalies(jobId);
    }

    @PostMapping("/hospital-reconciliations/export-html-settlement")
    public ResponseEntity<String> exportHtmlSettlement(
            @RequestBody HospitalSettlementTemplateExportRequest request) {
        return hospitalReconciliationService.exportHtmlSettlement(request);
    }

    @PostMapping("/hospital-reconciliations/print-template-bill")
    public ResponseEntity<String> printTemplateBill(
            @RequestBody HospitalBillTemplateExportRequest request) {
        return hospitalReconciliationService.printTemplateBill(request);
    }

    @PostMapping("/hospital-reconciliations/print-template-settlement")
    public ResponseEntity<String> printTemplateSettlement(
            @RequestBody HospitalSettlementTemplateExportRequest request) {
        return hospitalReconciliationService.printTemplateSettlement(request);
    }

    /** Export Engine v2 — strategy-based export from persisted job rows */
    @PostMapping("/hospital-reconciliations/{jobId}/export-v2")
    public ResponseEntity<byte[]> exportV2(
            @PathVariable Long jobId,
            @RequestBody(required = false) ExportV2Request request) {
        if (request == null) {
            request = new ExportV2Request();
        }
        return exportEngineService.exportV2(jobId, request);
    }

    @GetMapping("/hospital-reconciliations/{jobId}/export-preview")
    public Result<ExportPreviewResponse> previewExport(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "bill") String exportType,
            @RequestParam(required = false) Long templateId) {
        return Result.success(exportEngineService.previewExport(jobId, exportType, templateId));
    }

    @GetMapping("/hospital-reconciliations/{jobId}/export-validation")
    public Result<ExportValidationResponse> validateExport(@PathVariable Long jobId) {
        return Result.success(exportEngineService.validateBeforeExport(jobId));
    }

    @GetMapping("/hospital-reconciliations/{jobId}/logistics-allocation")
    public Result<com.hospital.backend.dto.response.logistics.LogisticsAllocationPreviewResponse> getLogisticsAllocation(
            @PathVariable Long jobId) {
        return hospitalReconciliationService.getLogisticsAllocationPreview(jobId);
    }

    @PostMapping("/hospital-reconciliations/{jobId}/export-logistics-allocation")
    public ResponseEntity<byte[]> exportLogisticsAllocation(@PathVariable Long jobId) {
        return hospitalReconciliationService.exportLogisticsAllocation(jobId);
    }

    @GetMapping("/hospital-reconciliations/{jobId}/export-instrument-audit")
    public Result<Map<String, Object>> exportInstrumentAudit(@PathVariable Long jobId) {
        return instrumentAuditReportService.buildAuditReport(jobId);
    }

    @PostMapping("/hospital-reconciliations/{jobId}/split-daily")
    public Result<Map<String, Object>> splitDaily(@PathVariable Long jobId) {
        return dailySplitService.splitJobByDate(jobId);
    }

}
