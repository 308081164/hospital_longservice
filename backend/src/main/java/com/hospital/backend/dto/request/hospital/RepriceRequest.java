package com.hospital.backend.dto.request.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 全量重算请求（可选携带规则覆盖，不落库）。 */
@Getter
@Setter
public class RepriceRequest {

    @JsonProperty("disabledCategories")
    private List<String> disabledCategories;
}
