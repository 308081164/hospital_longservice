package com.hospital.backend.export.strategy;

import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.model.ColumnMappingConfig;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 道外区人民医院账单导出骨架 — FR-M3-21 删列（I/J/K/M 对应列名配置化）。
 * Batch-B 上传真实模板后通过 export_template.column_mapping 微调。
 */
@Component
public class DaowaiBillExportStrategy implements ExportStrategy {

    private final StandardBillExportStrategy standardBillExportStrategy;

    public DaowaiBillExportStrategy(StandardBillExportStrategy standardBillExportStrategy) {
        this.standardBillExportStrategy = standardBillExportStrategy;
    }

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.DAOWAI_BILL;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        ExportResult base = standardBillExportStrategy.export(context);
        return ExportResult.builder()
                .content(base.getContent())
                .fileName(base.getFileName().replace("_bill_v2_", "_daowai_bill_v2_"))
                .contentType(base.getContentType())
                .strategyKey(strategyKey())
                .templateId(context.getTemplate().getTemplateId())
                .build();
    }

    public static ColumnMappingConfig defaultColumnMapping() {
        ColumnMappingConfig config = new ColumnMappingConfig();
        config.setRemoveColumns(List.of("器械数", "备注", "差额"));
        return config;
    }
}
