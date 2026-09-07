package com.hospital.backend.dto.response.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RuleVerificationResult {

    private final boolean ok;

    @JsonProperty("customer_code")
    private final String customerCode;

    private final List<String> missing;

    private final List<String> changed;

    private final List<String> extra;
}
