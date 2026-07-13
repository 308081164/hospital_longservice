package com.hospital.backend.dto.request.product;

import lombok.Data;

import java.util.List;

@Data
public class BatchMatchPreviewRequest {

    private List<MatchPreviewRequest> rows;
}
