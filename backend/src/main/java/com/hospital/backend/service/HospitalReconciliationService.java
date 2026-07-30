package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.hospital.CreateExportLogRequest;
import com.hospital.backend.dto.request.hospital.HospitalBillTemplateExportRequest;
import com.hospital.backend.dto.request.hospital.HospitalSettlementTemplateExportRequest;
import com.hospital.backend.dto.request.hospital.ReconciliationReviewRequest;
import com.hospital.backend.dto.response.logistics.LogisticsAllocationPreviewResponse;
import com.hospital.backend.dto.response.hospital.ReconciliationExportLogResponse;
import com.hospital.backend.dto.response.hospital.ReconciliationJobResponse;
import com.hospital.backend.dto.response.hospital.TemplateRefResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface HospitalReconciliationService {

    Result<ReconciliationJobResponse> createReconciliation(String payloadJson, MultipartFile sourceFile);

    Result<ReconciliationJobResponse> importAndProcess(
            MultipartFile sourceFile,
            Long ruleId,
            String operatorName,
            String hospitalNameParam);

    Result<List<ReconciliationJobResponse>> listReconciliations(String hospitalName);

    Result<ReconciliationJobResponse> getReconciliation(Long jobId);

    Result<Map<String, Object>> getReconciliationRows(Long jobId, int page, int size, String sheetName);

    Result<ReconciliationJobResponse> reviewReconciliation(Long jobId, ReconciliationReviewRequest request);

    Result<ReconciliationJobResponse> updateRows(Long jobId, List<Map<String, Object>> updatedRows);

    Result<ReconciliationJobResponse> updateRowsUrgent(
            Long jobId,
            com.hospital.backend.dto.request.hospital.UpdateRowsUrgentRequest request);

    Result<Map<String, Object>> reprice(Long jobId);

    Result<ReconciliationExportLogResponse> createExportLog(Long jobId, CreateExportLogRequest request);

    Result<List<TemplateRefResponse>> listSettlementTemplates();

    Result<List<TemplateRefResponse>> listBillTemplates();

    ResponseEntity<String> previewSettlementTemplate(String templateId);

    ResponseEntity<byte[]> exportTemplateBill(HospitalBillTemplateExportRequest request);

    ResponseEntity<byte[]> exportTemplateSettlement(HospitalSettlementTemplateExportRequest request);

    ResponseEntity<byte[]> exportDepartmentSummary(Long jobId);

    ResponseEntity<byte[]> exportAnomalies(Long jobId);

    ResponseEntity<String> exportHtmlSettlement(HospitalSettlementTemplateExportRequest request);

    ResponseEntity<String> printTemplateBill(HospitalBillTemplateExportRequest request);

    ResponseEntity<String> printTemplateSettlement(HospitalSettlementTemplateExportRequest request);

    Result<LogisticsAllocationPreviewResponse> getLogisticsAllocationPreview(Long jobId);

    ResponseEntity<byte[]> exportLogisticsAllocation(Long jobId);

    Result<Map<String, Object>> importExternalInstruments(Long jobId, MultipartFile file);
}
