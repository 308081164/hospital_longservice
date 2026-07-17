package com.hospital.backend.dto.request.logistics;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SaveCustomerGroupRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String groupType;

    private String config;

    private Boolean isActive = true;

    private List<CustomerGroupMemberPayload> members;

    @Getter
    @Setter
    public static class CustomerGroupMemberPayload {
        private Long customerId;
        private Double shareRatio;
    }
}
