package com.hospital.backend.dto.request.deptphysician;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SavePhysicianEntryRequest {

    @NotBlank
    private String physicianName;

    private Long departmentEntryId;

    private String departmentName;

    private String code;

    private String notes;

    private Boolean isActive = true;
}
