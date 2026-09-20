package com.hospital.backend.export.strategy;

import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.fuyi.FuyiSupplementExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 分科室汇总导出：统一走客户确认包样表版式（标题 + 科室/金额 + 合计）。
 */
@Component
@RequiredArgsConstructor
public class StandardDeptSummaryExportStrategy implements ExportStrategy {

    private final FuyiSupplementExportService supplementExportService;

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.STANDARD_DEPT_SUMMARY;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        byte[] content = supplementExportService.exportDeptSummary(
                context.getJob(), context.getRows());
        String fileName = safeName(context.getHospitalName()) + "_dept_summary_"
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
