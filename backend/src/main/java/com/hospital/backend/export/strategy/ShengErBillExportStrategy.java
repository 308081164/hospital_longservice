package com.hospital.backend.export.strategy;

import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.model.ColumnMappingConfig;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 黑龙江省第二医院（南岗）账单导出骨架 — 在标准列基础上保留器械数列。
 * 完整 POI 模板填充在 Batch-A 配置阶段接入 storage_path。
 */
@Component
public class ShengErBillExportStrategy implements ExportStrategy {

    private final StandardBillExportStrategy standardBillExportStrategy;

    public ShengErBillExportStrategy(StandardBillExportStrategy standardBillExportStrategy) {
        this.standardBillExportStrategy = standardBillExportStrategy;
    }

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.SHENG_ER_BILL;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        ExportResult base = standardBillExportStrategy.export(context);
        return ExportResult.builder()
                .content(base.getContent())
                .fileName(base.getFileName().replace("_bill_v2_", "_sheng_er_bill_v2_"))
                .contentType(base.getContentType())
                .strategyKey(strategyKey())
                .templateId(context.getTemplate().getTemplateId())
                .build();
    }

    /** Column mapping used when post-processing legacy workbook exports. */
    public static ColumnMappingConfig defaultColumnMapping() {
        ColumnMappingConfig config = new ColumnMappingConfig();
        config.setKeepColumns(List.of(
                "发货日期", "单号", "类型", "包类别号", "包名", "器械数", "包数", "单价", "总价"
        ));
        return config;
    }
}
