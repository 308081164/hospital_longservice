package com.hospital.backend.dto.response.roster;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RosterEntryResponse {

    private Long id;

    private Long customerId;

    private String doctorName;

    private String department;

    private String surgicalRoom;

    private String notes;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
