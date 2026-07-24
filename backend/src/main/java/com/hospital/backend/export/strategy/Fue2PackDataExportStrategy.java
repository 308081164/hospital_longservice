package com.hospital.backend.export.strategy;

import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.ExportType;
import com.hospital.backend.export.InstrumentAuditDataBuilder;
import com.hospital.backend.export.SummarySheetWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class Fue2PackDataExportStrategy implements ExportStrategy {

    private final InstrumentAuditDataBuilder auditDataBuilder;
    private final SummarySheetWriter summarySheetWriter;

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.FUE2_PACK_DATA;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        List<SummarySheetWriter.Fue2PackRow> rows = auditDataBuilder.buildFue2PackRows(context.getRows());
        boolean includeAmount = context.getExportType() == ExportType.PRICE_SUMMARY;
        String sheetName = includeAmount ? "包数据汇总" : "包数据";
        byte[] content = summarySheetWriter.buildSingleSheetWorkbook(sheetName, (sheet, headerStyle) ->
                summarySheetWriter.writeFue2PackDataSheet(
                        sheet, headerStyle, context.getHospitalName(), rows, includeAmount));
        String suffix = includeAmount ? "price_summary" : "instrument_audit";
        String fileName = safeName(context.getHospitalName()) + "_" + suffix + "_fue2_v2_"
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
