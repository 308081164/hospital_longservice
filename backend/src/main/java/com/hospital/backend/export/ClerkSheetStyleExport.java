package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 结款函导出入口：样式表医院走专用工作簿，其余医院返回 null，继续用原模板。
 */
@Component
@RequiredArgsConstructor
public class ClerkSheetStyleExport {

    private final SettlementTemplateFiller settlementTemplateFiller;
    private final MergedSettlementPartnerLookup partnerLookup;

    public byte[] render(
            HospitalReconciliationJob job,
            List<HospitalReconciliationRow> rows,
            JsonNode compiled) throws Exception {
        if (job == null || !ClerkSheetStyle.applies(compiled)) {
            return null;
        }
        if (ClerkSheetStyle.isMerged(compiled)) {
            double self = settlementTemplateFiller.resolveSterilizeInputTotal(job, compiled, rows);
            double partner = partnerLookup.partnerSterilize(job, compiled);
            boolean selfIsXiangfang = "xiangfang".equals(
                    compiled.path("mergedSettlement").path("selfSide").asText(""));
            double sanfu = selfIsXiangfang ? partner : self;
            double xiangfang = selfIsXiangfang ? self : partner;
            double logistics = job.getLogisticsFee() != null ? job.getLogisticsFee() : 0;
            return ClerkSheetStyleWorkbook.mergedLetter(job, compiled, sanfu, xiangfang, logistics);
        }
        if (ClerkSheetStyle.isGuoyao(compiled)) {
            return ClerkSheetStyleWorkbook.guoyaoLetter(job, compiled, rows);
        }
        if (ClerkSheetStyle.discountColumnRate(compiled) != null) {
            double external = ClerkSheetStyle.externalOriginal(job, rows);
            double sterilize = settlementTemplateFiller.resolveSterilizeInputTotal(job, compiled, rows);
            if (rows != null) {
                double externalInRows = 0;
                for (HospitalReconciliationRow row : rows) {
                    if (ClerkSheetStyle.isExternal(row)) {
                        Double total = BillExportPriceResolver.resolveTotalPrice(row);
                        if (total == null) {
                            total = row.getCorrectedTotalPrice() != null
                                    ? row.getCorrectedTotalPrice()
                                    : row.getTotalPrice();
                        }
                        if (total != null) {
                            externalInRows += total;
                        }
                    }
                }
                if (externalInRows > 0) {
                    sterilize = ClerkSheetStyle.round2(Math.max(0, sterilize - externalInRows));
                }
            }
            double logistics = job.getLogisticsFee() != null ? job.getLogisticsFee() : 0;
            return ClerkSheetStyleWorkbook.discountColumnLetter(job, compiled, sterilize, external, logistics);
        }
        return null;
    }
}
