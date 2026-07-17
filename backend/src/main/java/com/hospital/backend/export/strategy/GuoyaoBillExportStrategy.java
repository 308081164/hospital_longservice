package com.hospital.backend.export.strategy;

import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.GuoyaoQuantityAlgorithm;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 国药总医院账单导出 — 在标准账单基础上应用汽轮机核算。
 */
@Component
public class GuoyaoBillExportStrategy implements ExportStrategy {

    private final StandardBillExportStrategy standardBillExportStrategy;
    private final GuoyaoQuantityAlgorithm guoyaoQuantityAlgorithm;

    public GuoyaoBillExportStrategy(
            StandardBillExportStrategy standardBillExportStrategy,
            GuoyaoQuantityAlgorithm guoyaoQuantityAlgorithm) {
        this.standardBillExportStrategy = standardBillExportStrategy;
        this.guoyaoQuantityAlgorithm = guoyaoQuantityAlgorithm;
    }

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.GUOYAO_BILL;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        List<HospitalReconciliationRow> adjusted = new ArrayList<>(context.getRows().size());
        for (HospitalReconciliationRow row : context.getRows()) {
            HospitalReconciliationRow copy = cloneRow(row);
            guoyaoQuantityAlgorithm.applyToRow(copy);
            adjusted.add(copy);
        }
        ExportContext adjustedContext = ExportContext.builder()
                .jobId(context.getJobId())
                .exportType(context.getExportType())
                .job(context.getJob())
                .rows(adjusted)
                .template(context.getTemplate())
                .customerId(context.getCustomerId())
                .hospitalName(context.getHospitalName())
                .build();
        ExportResult base = standardBillExportStrategy.export(adjustedContext);
        return ExportResult.builder()
                .content(base.getContent())
                .fileName(base.getFileName().replace("_bill_v2_", "_guoyao_bill_v2_"))
                .contentType(base.getContentType())
                .strategyKey(strategyKey())
                .templateId(context.getTemplate().getTemplateId())
                .build();
    }

    private HospitalReconciliationRow cloneRow(HospitalReconciliationRow row) {
        HospitalReconciliationRow copy = new HospitalReconciliationRow();
        copy.setId(row.getId());
        copy.setJobId(row.getJobId());
        copy.setSheetName(row.getSheetName());
        copy.setRowNumber(row.getRowNumber());
        copy.setDeliveryDate(row.getDeliveryDate());
        copy.setOrderNo(row.getOrderNo());
        copy.setType(row.getType());
        copy.setCategoryNo(row.getCategoryNo());
        copy.setPackName(row.getPackName());
        copy.setPackageMaterial(row.getPackageMaterial());
        copy.setPackCount(row.getPackCount());
        copy.setInstrumentCount(row.getInstrumentCount());
        copy.setUnitPrice(row.getUnitPrice());
        copy.setTotalPrice(row.getTotalPrice());
        copy.setExpectedUnitPrice(row.getExpectedUnitPrice());
        copy.setCorrectedTotalPrice(row.getCorrectedTotalPrice());
        copy.setDifference(row.getDifference());
        copy.setStatus(row.getStatus());
        copy.setPricingRule(row.getPricingRule());
        return copy;
    }
}
