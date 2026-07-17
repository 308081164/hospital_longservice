package com.hospital.backend.dto.response.logistics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CustomerGroupResponse {

    private Long id;

    private String name;

    @JsonProperty("group_type")
    private String groupType;

    private String config;

    @JsonProperty("is_active")
    private Boolean isActive;

    private List<CustomerGroupMemberResponse> members;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
