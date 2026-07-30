package com.hospital.backend.dto.request.hospital;

import lombok.Data;

import java.util.List;

@Data
public class HospitalBillTemplateExportRequest {

    private String hospitalName;

    private String templateId;

    private List<BillRowItem> rows;

    private List<BillSheetMeta> sheetMetas;

    /** auto | dept_split | combined */
    private String billLayout;

    /** auto | hospitalName | ruleName */
    private String d8DisplaySource;

    /** standard_8col | fuyi_extended_11col */
    private String billColumnLayout;
}
