package com.hospital.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.export.SaveExportTemplateRequest;
import com.hospital.backend.dto.response.export.ExportTemplateResponse;
import com.hospital.backend.entity.ExportTemplate;
import com.hospital.backend.export.ExportTemplateResolver;
import com.hospital.backend.mapper.ExportTemplateMapper;
import com.hospital.backend.service.ExportTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportTemplateServiceImpl implements ExportTemplateService {

    private final ExportTemplateMapper exportTemplateMapper;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    @Override
    public Result<List<ExportTemplateResponse>> listTemplates(Long customerId, String templateType) {
        List<ExportTemplate> templates = exportTemplateMapper.selectAll(customerId, templateType);
        return Result.success(templates.stream().map(this::toResponse).toList());
    }

    @Override
    public Result<ExportTemplateResponse> getTemplate(Long id) {
        ExportTemplate template = exportTemplateMapper.selectById(id);
        if (template == null) {
            return Result.fail(404, "Export template not found: " + id);
        }
        return Result.success(toResponse(template));
    }

    @Override
    @Transactional
    public Result<ExportTemplateResponse> createTemplate(SaveExportTemplateRequest request) {
        ExportTemplate template = fromRequest(request);
        exportTemplateMapper.insert(template);
        return Result.success(toResponse(exportTemplateMapper.selectById(template.getId())));
    }

    @Override
    @Transactional
    public Result<ExportTemplateResponse> updateTemplate(Long id, SaveExportTemplateRequest request) {
        ExportTemplate existing = exportTemplateMapper.selectById(id);
        if (existing == null) {
            return Result.fail(404, "Export template not found: " + id);
        }
        existing.setCustomerId(request.getCustomerId());
        existing.setTemplateType(request.getTemplateType());
        existing.setName(request.getName());
        existing.setStoragePath(request.getStoragePath());
        existing.setColumnMapping(request.getColumnMapping());
        existing.setSheetConfig(request.getSheetConfig());
        existing.setIsActive(request.getIsActive());
        exportTemplateMapper.updateById(existing);
        return Result.success(toResponse(exportTemplateMapper.selectById(id)));
    }

    @Override
    @Transactional
    public Result<Boolean> deleteTemplate(Long id) {
        ExportTemplate existing = exportTemplateMapper.selectById(id);
        if (existing == null) {
            return Result.fail(404, "Export template not found: " + id);
        }
        exportTemplateMapper.deleteById(id);
        return Result.success(true);
    }

    private ExportTemplate fromRequest(SaveExportTemplateRequest request) {
        ExportTemplate template = new ExportTemplate();
        template.setCustomerId(request.getCustomerId());
        template.setTemplateType(request.getTemplateType());
        template.setName(request.getName());
        template.setStoragePath(request.getStoragePath() != null ? request.getStoragePath() : "");
        template.setColumnMapping(request.getColumnMapping());
        template.setSheetConfig(request.getSheetConfig());
        template.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        return template;
    }

    private ExportTemplateResponse toResponse(ExportTemplate template) {
        return ExportTemplateResponse.builder()
                .id(template.getId())
                .customerId(template.getCustomerId())
                .templateType(template.getTemplateType())
                .name(template.getName())
                .storagePath(template.getStoragePath())
                .columnMapping(template.getColumnMapping())
                .sheetConfig(template.getSheetConfig())
                .isActive(template.getIsActive())
                .strategyKey(extractStrategyKey(template))
                .build();
    }

    private String extractStrategyKey(ExportTemplate template) {
        if (template.getSheetConfig() == null || template.getSheetConfig().isBlank()) {
            return "settlement".equalsIgnoreCase(template.getTemplateType())
                    ? ExportTemplateResolver.DEFAULT_SETTLEMENT_STRATEGY
                    : ExportTemplateResolver.DEFAULT_BILL_STRATEGY;
        }
        try {
            JsonNode node = objectMapper.readTree(template.getSheetConfig());
            if (node.hasNonNull("strategyKey")) {
                return node.get("strategyKey").asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return ExportTemplateResolver.DEFAULT_BILL_STRATEGY;
    }
}
