package com.hospital.backend.dto.request.hospital;

import lombok.Data;

import java.util.List;

@Data
public class HospitalBillTemplateExportRequest {

    private String hospitalName;

    private String templateId;

    private List<BillRowItem> rows;

    private List<BillSheetMeta> sheetMetas;
}
