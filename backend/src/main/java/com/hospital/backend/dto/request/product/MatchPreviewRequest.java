package com.hospital.backend.dto.request.product;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MatchPreviewRequest {

    private String type;

    private String packName;

    private String packageMaterial;

    private String categoryNo;

    private Integer instrumentCount;
}
