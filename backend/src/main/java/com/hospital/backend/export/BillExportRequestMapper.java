package com.hospital.backend.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.dto.request.hospital.HospitalBillTemplateExportRequest;
import com.hospital.backend.entity.HospitalReconciliationRow;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class BillExportRequestMapper {

    public HospitalBillTemplateExportRequest fromContext(ExportContext context) {
        HospitalBillTemplateExportRequest request = new HospitalBillTemplateExportRequest();
        request.setHospitalName(context.getHospitalName());
        request.setTemplateId(String.valueOf(context.getJobId()));
        request.setRows(context.getRows().stream().map(this::toBillRowItem).toList());
        if (context.getTemplate() != null && context.getTemplate().getColumnMapping() != null) {
            var mapping = context.getTemplate().getColumnMapping();
            request.setBillLayout(mapping.getBillLayout());
            request.setD8DisplaySource(mapping.getD8DisplaySource());
        }
        return request;
    }

    private BillRowItem toBillRowItem(HospitalReconciliationRow row) {
        BillRowItem item = new BillRowItem();
        item.setSheetName(row.getSheetName());
        item.setRowNumber(row.getRowNumber());
        item.setDeliveryDate(row.getDeliveryDate());
        item.setOrderNo(row.getOrderNo());
        item.setType(row.getType());
        item.setCategoryNo(row.getCategoryNo());
        item.setPackName(row.getPackName());
        item.setPackageMaterial(row.getPackageMaterial());
        item.setPackCount(row.getPackCount());
        item.setInstrumentCount(row.getInstrumentCount());
        item.setUnitPrice(row.getUnitPrice());
        item.setTotalPrice(row.getTotalPrice());
        item.setExpectedUnitPrice(row.getExpectedUnitPrice());
        item.setCorrectedTotalPrice(row.getCorrectedTotalPrice());
        if (row.getUnitPrice() != null) {
            item.setOriginal(java.util.Map.of("importUnitPrice", row.getUnitPrice()));
        }
        Double exportUnit = BillExportPriceResolver.resolveUnitPrice(row);
        Double exportTotal = BillExportPriceResolver.resolveTotalPrice(row);
        if (exportUnit != null) {
            item.setExpectedUnitPrice(exportUnit);
            item.setUnitPrice(exportUnit);
        }
        if (exportTotal != null) {
            item.setCorrectedTotalPrice(exportTotal);
            item.setTotalPrice(exportTotal);
        }
        item.setDifference(row.getDifference());
        item.setStatus(row.getStatus());
        item.setPricingRule(row.getPricingRule());
        if (row.getNotesJson() != null && !row.getNotesJson().isBlank()) {
            try {
                item.setNotes(JsonUtils.getObjectMapper().readValue(
                        row.getNotesJson(), new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                item.setNotes(Collections.emptyList());
            }
        }
        return item;
    }
}
