package com.hospital.backend.export.strategy;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.response.logistics.LogisticsAllocationPreviewResponse;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.SummarySheetWriter;
import com.hospital.backend.service.HospitalReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LogisticsAllocationExportStrategy implements ExportStrategy {

    private final HospitalReconciliationService reconciliationService;
    private final SummarySheetWriter summarySheetWriter;

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.LOGISTICS_ALLOCATION;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        Result<LogisticsAllocationPreviewResponse> preview =
                reconciliationService.getLogisticsAllocationPreview(context.getJobId());
        if (preview.getCode() != 200 || preview.getData() == null) {
            throw new IllegalStateException("物流分摊预览失败: " + preview.getMsg());
        }
        List<Map<String, Object>> deptAllocations = preview.getData().getDeptAllocations();
        byte[] content = summarySheetWriter.buildSingleSheetWorkbook("物流分摊", (sheet, headerStyle) ->
                summarySheetWriter.writeLogisticsAllocationSheet(
                        sheet, headerStyle, context.getHospitalName(), deptAllocations));
        String fileName = safeName(context.getHospitalName()) + "_logistics_allocation_v2_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        return ExportResult.builder()
                .content(content)
                .fileName(fileName)
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .strategyKey(strategyKey())
                .templateId(context.getTemplate().getTemplateId())
                .build();
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "hospital";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
