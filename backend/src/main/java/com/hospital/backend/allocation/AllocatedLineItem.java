package com.hospital.backend.allocation;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AllocatedLineItem {

    private Long sourceRowId;

    private Integer sourceRowNumber;

    private String sourceSheetName;

    private String targetSheetName;

    private String allocationType;

    private String packName;

    private String categoryNo;

    private Integer packCount;

    private Integer instrumentCount;

    private Double amount;

    private String matchedDoctor;

    private String matchedDepartment;

    private String matchReason;
}
