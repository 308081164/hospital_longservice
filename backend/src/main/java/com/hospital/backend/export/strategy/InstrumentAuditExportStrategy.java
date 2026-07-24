package com.hospital.backend.export.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.ExportType;
import com.hospital.backend.export.InstrumentAuditDataBuilder;
import com.hospital.backend.export.SummarySheetWriter;
import com.hospital.backend.mapper.ExternalInstrumentMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InstrumentAuditExportStrategy implements ExportStrategy {

    private final InstrumentAuditDataBuilder auditDataBuilder;
    private final SummarySheetWriter summarySheetWriter;
    private final ExternalInstrumentMapper externalInstrumentMapper;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.INSTRUMENT_AUDIT;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        InstrumentAuditDataBuilder.InstrumentAuditData data = auditDataBuilder.build(context.getRows());
        byte[] content;
        if (useExternalInstrumentLayout(context)) {
            content = buildExternalInstrumentWorkbook(context);
        } else {
            content = buildStandardAuditWorkbook(context, data);
        }
        String fileName = safeName(context.getHospitalName()) + "_instrument_audit_v2_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        return ExportResult.builder()
                .content(content)
                .fileName(fileName)
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .strategyKey(strategyKey())
                .templateId(context.getTemplate().getTemplateId())
                .build();
    }

    private byte[] buildStandardAuditWorkbook(
            ExportContext context,
            InstrumentAuditDataBuilder.InstrumentAuditData data) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var headerStyle = summarySheetWriter.createHeaderStyle(workbook);
            summarySheetWriter.writeInstrumentAuditSheets(
                    workbook,
                    headerStyle,
                    context.getHospitalName(),
                    data.pieceRows(),
                    data.instrumentRows(),
                    data.packagingRows());
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildExternalInstrumentWorkbook(ExportContext context) throws Exception {
        var instruments = externalInstrumentMapper.selectByJobId(context.getJobId());
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var headerStyle = summarySheetWriter.createHeaderStyle(workbook);
            summarySheetWriter.writeExternalInstrumentSheet(workbook, headerStyle, instruments);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private boolean useExternalInstrumentLayout(ExportContext context) {
        if (context.getExportType() != ExportType.INSTRUMENT_AUDIT) {
            return false;
        }
        String sheetConfig = context.getTemplate().getSheetConfigJson();
        if (sheetConfig == null || sheetConfig.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(sheetConfig);
            return node.has("layout") && "external_instrument".equalsIgnoreCase(node.get("layout").asText());
        } catch (Exception e) {
            return false;
        }
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "hospital";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
