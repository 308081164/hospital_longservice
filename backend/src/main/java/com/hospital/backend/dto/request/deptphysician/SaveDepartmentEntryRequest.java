package com.hospital.backend.dto.request.deptphysician;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveDepartmentEntryRequest {

    @NotBlank
    private String departmentName;

    private String code;

    private String notes;

    private Boolean isActive = true;
}
