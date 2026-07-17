package com.hospital.backend.dto.response.deptphysician;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PhysicianEntryResponse {

    private Long id;

    @JsonProperty("customer_id")
    private Long customerId;

    @JsonProperty("physician_name")
    private String physicianName;

    @JsonProperty("department_entry_id")
    private Long departmentEntryId;

    @JsonProperty("department_name")
    private String departmentName;

    private String code;

    private String notes;

    @JsonProperty("usage_count")
    private Integer usageCount;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
