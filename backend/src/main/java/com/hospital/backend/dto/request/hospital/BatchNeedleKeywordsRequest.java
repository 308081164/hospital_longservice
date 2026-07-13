package com.hospital.backend.dto.request.hospital;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchNeedleKeywordsRequest {

    @NotEmpty(message = "关键词列表不能为空")
    private List<String> keywords;

    private String operator;
}
