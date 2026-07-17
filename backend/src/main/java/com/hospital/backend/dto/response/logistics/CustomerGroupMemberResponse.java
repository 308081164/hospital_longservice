package com.hospital.backend.dto.response.logistics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerGroupMemberResponse {

    private Long id;

    @JsonProperty("group_id")
    private Long groupId;

    @JsonProperty("customer_id")
    private Long customerId;

    @JsonProperty("share_ratio")
    private Double shareRatio;
}
