package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.export.SaveExportTemplateRequest;
import com.hospital.backend.dto.response.export.ExportTemplateResponse;

import java.util.List;

public interface ExportTemplateService {

    Result<List<ExportTemplateResponse>> listTemplates(Long customerId, String templateType);

    Result<ExportTemplateResponse> getTemplate(Long id);

    Result<ExportTemplateResponse> createTemplate(SaveExportTemplateRequest request);

    Result<ExportTemplateResponse> updateTemplate(Long id, SaveExportTemplateRequest request);

    Result<Boolean> deleteTemplate(Long id);
}
