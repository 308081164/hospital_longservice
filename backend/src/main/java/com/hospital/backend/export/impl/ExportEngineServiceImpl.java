package com.hospital.backend.export.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.dto.request.export.ExportV2Request;
import com.hospital.backend.dto.request.hospital.HospitalBillTemplateExportRequest;
import com.hospital.backend.dto.request.hospital.HospitalSettlementTemplateExportRequest;
import com.hospital.backend.dto.request.hospital.SettlementFeeRow;
import com.hospital.backend.export.BillExportRequestMapper;
import com.hospital.backend.dto.response.export.ExportPreviewResponse;
import com.hospital.backend.dto.response.export.ExportValidationResponse;
import com.hospital.backend.entity.HospitalReconciliationExportLog;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalPricingRule;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.export.ColumnTransformPipeline;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportEngineService;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.ExportStageDiscountApplier;
import com.hospital.backend.export.ExportType;
import com.hospital.backend.export.ReconciliationExportDataLoader;
import com.hospital.backend.export.ReconciliationLegacyExportBridge;
import com.hospital.backend.export.model.ResolvedExportTemplate;
import com.hospital.backend.export.strategy.ExportStrategy;
import com.hospital.backend.export.strategy.ExportStrategyRegistry;
import com.hospital.backend.export.SettlementTemplateFiller;
import com.hospital.backend.mapper.HospitalReconciliationExportLogMapper;
import com.hospital.backend.mapper.HospitalPricingRuleMapper;
import com.hospital.backend.service.CustomerResolver;
import com.hospital.backend.service.PricingRuleCompiler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportEngineServiceImpl implements ExportEngineService {

    private final ReconciliationExportDataLoader dataLoader;
    private final ExportStrategyRegistry strategyRegistry;
    private final ColumnTransformPipeline columnTransformPipeline;
    private final CustomerResolver customerResolver;
    private final ExportTemplateResolverHelper templateResolverHelper;
    private final HospitalReconciliationExportLogMapper exportLogMapper;
    private final ExportStageDiscountApplier exportStageDiscountApplier;
    private final PricingRuleCompiler pricingRuleCompiler;
    private final HospitalPricingRuleMapper pricingRuleMapper;
    private final SettlementTemplateFiller settlementTemplateFiller;
    private final BillExportRequestMapper billExportRequestMapper;

    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private ReconciliationLegacyExportBridge legacyExportBridge;

    @Override
    public ResponseEntity<byte[]> exportV2(Long jobId, ExportV2Request request) {
        try {
            ExportType exportType = ExportType.fromCode(
                    request.getExportType() != null ? request.getExportType() : "bill");
            ExportContext context = dataLoader.loadContext(jobId, exportType, request.getTemplateId());
            if (shouldUseLegacyTemplateExport(exportType, context)) {
                return exportViaLegacyTemplate(jobId, exportType, context);
            }
            ExportStrategy strategy = strategyRegistry.require(context.getTemplate().getStrategyKey());
            ExportResult result = strategy.export(context);
            byte[] content = columnTransformPipeline.apply(
                    result.getContent(), context.getTemplate().getColumnMapping());
            logExport(jobId, exportType.code(), result.getFileName(), context.getJob());
            return legacyExportBridge.buildExcelDownloadResponse(content, result.getFileName());
        } catch (IllegalArgumentException e) {
            log.warn("exportV2 validation failed jobId={}: {}", jobId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("exportV2 failed jobId={}: {}", jobId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Override
    public ResponseEntity<byte[]> exportBill(HospitalBillTemplateExportRequest request) {
        try {
            applyExportStageDiscounts(request);
            byte[] content = legacyExportBridge.generateBillExportBytes(request);
            content = legacyExportBridge.postProcessBillExport(content, request.getTemplateId());
            content = applyTemplateTransforms(request, content, ExportType.BILL);
            String filename = safeName(request.getHospitalName()) + "_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
            return legacyExportBridge.buildExcelDownloadResponse(content, filename);
        } catch (Exception e) {
            log.error("exportBill failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Override
    public ResponseEntity<byte[]> exportSettlement(HospitalSettlementTemplateExportRequest request) {
        try {
            byte[] content = legacyExportBridge.generateSettlementExportBytes(request);
            content = applyTemplateTransforms(request.getTemplateId(), request.getHospitalName(), content, ExportType.SETTLEMENT);
            String filename = safeName(request.getHospitalName()) + "_settlement_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
            return legacyExportBridge.buildExcelDownloadResponse(content, filename);
        } catch (Exception e) {
            log.error("exportSettlement failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Override
    public ExportPreviewResponse previewExport(Long jobId, String exportType, Long templateId) {
        ExportType type = ExportType.fromCode(exportType != null ? exportType : "bill");
        ExportContext context = dataLoader.loadContext(jobId, type, templateId);
        ResolvedExportTemplate template = context.getTemplate();
        return ExportPreviewResponse.builder()
                .jobId(jobId)
                .exportType(type.code())
                .templateId(template.getTemplateId())
                .templateName(template.getName())
                .strategyKey(template.getStrategyKey())
                .customerOverride(template.isCustomerOverride())
                .rowCount(context.getRows().size())
                .hospitalName(context.getHospitalName())
                .build();
    }

    @Override
    public ExportValidationResponse validateBeforeExport(Long jobId) {
        ExportContext context = dataLoader.loadContext(jobId, ExportType.BILL, null);
        HospitalReconciliationJob job = context.getJob();
        int warnings = job.getWarningRows() != null ? job.getWarningRows() : 0;

        double sterilizeTotal = job.getCorrectedTotalPrice() != null
                ? job.getCorrectedTotalPrice()
                : context.getRows().stream()
                        .mapToDouble(r -> r.getCorrectedTotalPrice() != null
                                ? r.getCorrectedTotalPrice()
                                : (r.getTotalPrice() != null ? r.getTotalPrice() : 0))
                        .sum();
        var feeRows = settlementTemplateFiller.buildFeeRows(job, sterilizeTotal);
        double settlementTotal = settlementTemplateFiller.computeTotalAmount(feeRows);
        Double externalTotal = parseExternalInstrumentTotal(job);
        Double reconciledGrandTotal = parseReconciledGrandTotal(job);
        boolean settlementReconciled = reconciledGrandTotal == null
                || Math.abs(settlementTotal - reconciledGrandTotal) <= 0.02;
        Boolean allocationBalanced = parseAllocationBalanced(job.getAllocationResult());

        boolean ready = settlementReconciled && (allocationBalanced == null || allocationBalanced);
        String message;
        if (!settlementReconciled) {
            message = "结款函勾稽未通过：合计 " + settlementTotal + " 与分项之和不一致";
        } else if (allocationBalanced != null && !allocationBalanced) {
            message = "科室分配勾稽未通过，请先运行 allocate";
        } else if (warnings > 0) {
            message = "存在 " + warnings + " 行待复核，建议先查看详情核对";
        } else {
            message = "导出勾稽通过";
        }

        return ExportValidationResponse.builder()
                .jobId(jobId)
                .totalRows(job.getTotalRows() != null ? job.getTotalRows() : context.getRows().size())
                .warningRows(warnings)
                .correctedRows(job.getCorrectedRows() != null ? job.getCorrectedRows() : 0)
                .totalDifference(job.getTotalDifference())
                .logisticsFee(job.getLogisticsFee())
                .settlementAdjustment(job.getSettlementAdjustment())
                .settlementTotal(settlementTotal)
                .externalInstrumentTotal(externalTotal)
                .settlementReconciled(settlementReconciled)
                .allocationBalanced(allocationBalanced)
                .ready(ready)
                .message(message)
                .build();
    }

    private Double parseReconciledGrandTotal(HospitalReconciliationJob job) {
        String json = job.getAllocationResult();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            var node = JsonUtils.getObjectMapper().readTree(json);
            if (node.has("reconciledGrandTotal")) {
                return node.path("reconciledGrandTotal").asDouble();
            }
        } catch (Exception e) {
            log.warn("Failed to parse reconciledGrandTotal for job {}: {}", job.getId(), e.getMessage());
        }
        return null;
    }

    private Double parseExternalInstrumentTotal(HospitalReconciliationJob job) {
        String json = job.getAllocationResult();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            var node = JsonUtils.getObjectMapper().readTree(json);
            if (node.has("externalInstrumentTotal")) {
                return node.path("externalInstrumentTotal").asDouble();
            }
        } catch (Exception e) {
            log.warn("Failed to parse externalInstrumentTotal for job {}: {}", job.getId(), e.getMessage());
        }
        return null;
    }

    private Boolean parseAllocationBalanced(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            var node = JsonUtils.getObjectMapper().readTree(json);
            if (node.has("balanced")) {
                return node.path("balanced").asBoolean();
            }
        } catch (Exception ignored) {
            // no allocation yet
        }
        return null;
    }

    private void applyExportStageDiscounts(HospitalBillTemplateExportRequest request) {
        if (request.getRows() == null || request.getRows().isEmpty()) {
            return;
        }
        String hospitalName = request.getHospitalName();
        if (hospitalName == null || hospitalName.isBlank()) {
            return;
        }
        try {
            JsonNode compiled = resolveCompiledRules(request, hospitalName);
            if (compiled == null) {
                return;
            }
            request.setRows(exportStageDiscountApplier.apply(compiled, request.getRows()));
        } catch (Exception e) {
            log.warn("export stage discount skipped for {}: {}", hospitalName, e.getMessage());
        }
    }

    private JsonNode resolveCompiledRules(HospitalBillTemplateExportRequest request, String hospitalName)
            throws Exception {
        Long ruleId = null;
        if (request.getTemplateId() != null && !request.getTemplateId().isBlank()) {
            try {
                Long jobId = Long.parseLong(request.getTemplateId());
                ruleId = dataLoader.findJob(jobId).map(HospitalReconciliationJob::getRuleId).orElse(null);
            } catch (NumberFormatException ignored) {
                // templateId may not be job id in all flows
            }
        }
        if (ruleId == null) {
            return null;
        }
        HospitalPricingRule ruleEntity = pricingRuleMapper.selectById(ruleId);
        if (ruleEntity == null || ruleEntity.getRulesJson() == null) {
            return null;
        }
        JsonNode baseRules = JsonUtils.getObjectMapper().readTree(ruleEntity.getRulesJson());
        return pricingRuleCompiler.compile(baseRules, hospitalName);
    }

    private byte[] applyTemplateTransforms(
            HospitalBillTemplateExportRequest request, byte[] content, ExportType exportType) {
        return applyTemplateTransforms(request.getTemplateId(), request.getHospitalName(), content, exportType);
    }

    private byte[] applyTemplateTransforms(
            String templateId, String hospitalName, byte[] content, ExportType exportType) {
        Long customerId = customerResolver.resolveByName(hospitalName).map(c -> c.getId()).orElse(null);
        ResolvedExportTemplate resolved = templateResolverHelper.resolve(customerId, exportType, null);
        return columnTransformPipeline.apply(content, resolved.getColumnMapping());
    }

    /**
     * Bill/settlement exports use the legacy POI template pipeline (same as export-template-bill).
     * The v2 strategy classes only provide simplified workbooks for dept_summary / daily / L3.
     */
    private boolean shouldUseLegacyTemplateExport(ExportType exportType, ExportContext context) {
        return exportType == ExportType.BILL || exportType == ExportType.SETTLEMENT;
    }

    private ResponseEntity<byte[]> exportViaLegacyTemplate(
            Long jobId, ExportType exportType, ExportContext context) throws Exception {
        if (exportType == ExportType.BILL) {
            HospitalBillTemplateExportRequest billRequest = billExportRequestMapper.fromContext(context);
            applyExportStageDiscounts(billRequest);
            byte[] content = legacyExportBridge.generateBillExportBytes(billRequest);
            content = legacyExportBridge.postProcessBillExport(content, billRequest.getTemplateId());
            content = applyTemplateTransforms(
                    billRequest.getTemplateId(), billRequest.getHospitalName(), content, ExportType.BILL);
            String filename = safeName(context.getHospitalName()) + "_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
            logExport(jobId, exportType.code(), filename, context.getJob());
            return legacyExportBridge.buildExcelDownloadResponse(content, filename);
        }
        if (exportType == ExportType.SETTLEMENT) {
            HospitalSettlementTemplateExportRequest settlementRequest = buildSettlementExportRequest(context);
            byte[] content = legacyExportBridge.generateSettlementExportBytes(settlementRequest);
            content = applyTemplateTransforms(
                    settlementRequest.getTemplateId(),
                    settlementRequest.getHospitalName(),
                    content,
                    ExportType.SETTLEMENT);
            String filename = safeName(context.getHospitalName()) + "_settlement_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
            logExport(jobId, exportType.code(), filename, context.getJob());
            return legacyExportBridge.buildExcelDownloadResponse(content, filename);
        }
        throw new IllegalArgumentException("Legacy export not supported for type: " + exportType);
    }

    private HospitalSettlementTemplateExportRequest buildSettlementExportRequest(ExportContext context) {
        HospitalSettlementTemplateExportRequest request = new HospitalSettlementTemplateExportRequest();
        request.setHospitalName(context.getHospitalName());
        request.setTemplateId(String.valueOf(context.getJobId()));
        request.setHospitalDisplayName(context.getHospitalName());
        request.setDateRangeText(context.getJob().getSourceDateRange());
        double sterilizeTotal = context.getJob().getCorrectedTotalPrice() != null
                ? context.getJob().getCorrectedTotalPrice()
                : context.getRows().stream()
                        .mapToDouble(r -> r.getCorrectedTotalPrice() != null
                                ? r.getCorrectedTotalPrice()
                                : (r.getTotalPrice() != null ? r.getTotalPrice() : 0))
                        .sum();
        var fillerRows = settlementTemplateFiller.buildFeeRows(context.getJob(), sterilizeTotal);
        request.setFeeRows(fillerRows.stream().map(this::toSettlementFeeRow).toList());
        double total = settlementTemplateFiller.computeTotalAmount(fillerRows);
        request.setTotalAmount(total);
        return request;
    }

    private SettlementFeeRow toSettlementFeeRow(SettlementTemplateFiller.SettlementFeeRow row) {
        SettlementFeeRow dto = new SettlementFeeRow();
        dto.setIndexLabel(String.valueOf(row.getSequence()));
        dto.setItemLabel(row.getItemName());
        dto.setAmount(row.getAmount());
        dto.setRemark(row.getRemark());
        return dto;
    }

    private void logExport(Long jobId, String exportType, String fileName, HospitalReconciliationJob job) {
        try {
            HospitalReconciliationExportLog exportLog = new HospitalReconciliationExportLog();
            exportLog.setJobId(jobId);
            exportLog.setExportType(exportType + "_v2");
            exportLog.setFileName(fileName);
            exportLog.setFilePath("");
            exportLog.setOperatorName(job.getOperatorName());
            exportLogMapper.insert(exportLog);
        } catch (Exception e) {
            log.warn("Failed to write export log for job {}: {}", jobId, e.getMessage());
        }
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "hospital";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /** Thin wrapper so impl does not depend on ExportTemplateResolver directly twice. */
    @org.springframework.stereotype.Component
    @RequiredArgsConstructor
    static class ExportTemplateResolverHelper {
        private final com.hospital.backend.export.ExportTemplateResolver exportTemplateResolver;

        ResolvedExportTemplate resolve(Long customerId, ExportType exportType, Long templateIdOverride) {
            return exportTemplateResolver.resolve(customerId, exportType, templateIdOverride);
        }
    }
}
