package com.hospital.backend.export.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.InstrumentAuditDataBuilder;
import com.hospital.backend.export.SummarySheetWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FuyiDeptSummaryExportStrategy implements ExportStrategy {

    private final SummarySheetWriter summarySheetWriter;
    private final InstrumentAuditDataBuilder auditDataBuilder;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.FUYI_DEPT_SUMMARY;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        Map<String, SummarySheetWriter.DeptFeeRow> deptRows = buildDeptRows(context);
        byte[] content = summarySheetWriter.buildSingleSheetWorkbook("各科室费用汇总", (sheet, headerStyle) ->
                summarySheetWriter.writeFuyiDeptSummarySheet(
                        sheet, headerStyle, context.getHospitalName(), deptRows));
        String fileName = safeName(context.getHospitalName()) + "_dept_summary_v2_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        return ExportResult.builder()
                .content(content)
                .fileName(fileName)
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .strategyKey(strategyKey())
                .templateId(context.getTemplate().getTemplateId())
                .build();
    }

    private Map<String, SummarySheetWriter.DeptFeeRow> buildDeptRows(ExportContext context) {
        AllocationResult allocation = parseAllocationResult(context.getJob().getAllocationResult());
        if (allocation != null && allocation.getDepartmentSummaries() != null
                && !allocation.getDepartmentSummaries().isEmpty()) {
            Map<String, SummarySheetWriter.DeptFeeRow> rows = new java.util.LinkedHashMap<>();
            for (var summary : allocation.getDepartmentSummaries()) {
                rows.put(summary.getDepartmentName(), new SummarySheetWriter.DeptFeeRow(
                        summary.getDepartmentName(),
                        summary.getNetAmount(),
                        0.0,
                        summary.getNetAmount()));
            }
            return rows;
        }
        return auditDataBuilder.buildFuyiDeptRows(context.getRows());
    }

    private AllocationResult parseAllocationResult(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AllocationResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "hospital";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
