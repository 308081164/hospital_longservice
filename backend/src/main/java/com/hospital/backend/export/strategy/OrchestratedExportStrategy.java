package com.hospital.backend.export.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.ExternalInstrument;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.SheetOrchestrator;
import com.hospital.backend.mapper.ExternalInstrumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrchestratedExportStrategy implements ExportStrategy {

    private final SheetOrchestrator sheetOrchestrator;
    private final ExternalInstrumentMapper externalInstrumentMapper;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.ORCHESTRATED_L3;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        AllocationResult allocation = parseAllocationResult(context.getJob().getAllocationResult());
        List<ExternalInstrument> externalInstruments =
                externalInstrumentMapper.selectByJobId(context.getJobId());
        byte[] content = sheetOrchestrator.buildOrchestratedWorkbook(
                context.getHospitalName(),
                context.getRows(),
                allocation,
                externalInstruments);
        String fileName = safeName(context.getHospitalName()) + "_orchestrated_v2_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        return ExportResult.builder()
                .content(content)
                .fileName(fileName)
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .strategyKey(strategyKey())
                .templateId(context.getTemplate().getTemplateId())
                .build();
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
