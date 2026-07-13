package com.hospital.backend.dto.request.hospital;

import lombok.Data;

@Data
public class BillSheetMeta {

    private String sheetName;

    private String titleText;

    private String dateRangeText;

    private String hospitalDisplayName;
}
