package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ProductAlias {

    private Long id;

    private Long productId;

    private String alias;

    private String matchType = "CONTAINS";

    private Integer priority = 100;

    private Boolean isActive = true;

    private LocalDateTime createdAt;
}
