package com.hospital.backend.dto.request.roster;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveRosterEntryRequest {

    @NotBlank
    private String doctorName;

    @NotBlank
    private String department;

    private String surgicalRoom;

    private String notes;

    private Boolean isActive = true;
}
