package com.hospital.backend.dto.request.hospital;

import lombok.Data;

import java.util.ArrayList;
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

    /** 内勤规则追加裁剪列（与模板 removeColumns 合并） */
    private List<String> clerkRemoveColumns = new ArrayList<>();

    public void addClerkRemoveColumn(String column) {
        if (column == null || column.isBlank()) {
            return;
        }
        if (clerkRemoveColumns == null) {
            clerkRemoveColumns = new ArrayList<>();
        }
        if (!clerkRemoveColumns.contains(column)) {
            clerkRemoveColumns.add(column);
        }
    }
}
