package com.hospital.backend.export;

import com.hospital.backend.dto.request.hospital.HospitalBillTemplateExportRequest;
import com.hospital.backend.dto.request.hospital.HospitalSettlementTemplateExportRequest;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

/**
 * Bridge to legacy POI export routines in {@code HospitalReconciliationServiceImpl}.
 * Kept separate to avoid circular dependency without moving ~3000 lines of export code.
 */
public interface ReconciliationLegacyExportBridge {

    byte[] generateBillExportBytes(HospitalBillTemplateExportRequest request) throws IOException;

    byte[] postProcessBillExport(byte[] content, String templateId);

    byte[] generateSettlementExportBytes(HospitalSettlementTemplateExportRequest request) throws IOException;

    ResponseEntity<byte[]> buildExcelDownloadResponse(byte[] content, String filename);
}
