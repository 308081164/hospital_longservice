package com.hospital.backend.export;

import com.hospital.backend.dto.request.export.ExportV2Request;
import com.hospital.backend.dto.request.hospital.HospitalBillTemplateExportRequest;
import com.hospital.backend.dto.request.hospital.HospitalSettlementTemplateExportRequest;
import com.hospital.backend.dto.response.export.ExportPreviewResponse;
import com.hospital.backend.dto.response.export.ExportValidationResponse;
import org.springframework.http.ResponseEntity;

public interface ExportEngineService {

    ResponseEntity<byte[]> exportV2(Long jobId, ExportV2Request request);

    ResponseEntity<byte[]> exportBill(HospitalBillTemplateExportRequest request);

    ResponseEntity<byte[]> exportSettlement(HospitalSettlementTemplateExportRequest request);

    ExportPreviewResponse previewExport(Long jobId, String exportType, Long templateId);

    ExportValidationResponse validateBeforeExport(Long jobId);
}
