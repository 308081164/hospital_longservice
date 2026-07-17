package com.hospital.backend.allocation;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartmentSheetSummary {

    private String departmentName;

    private String sheetType;

    private int packCount;

    private int instrumentCount;

    private double grossAmount;

    private double adjustmentAmount;

    private double netAmount;

    private int lineCount;
}
