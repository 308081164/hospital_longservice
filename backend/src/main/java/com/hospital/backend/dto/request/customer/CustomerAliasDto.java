package com.hospital.backend.dto.request.customer;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerAliasDto {

    private Long id;

    private String alias;

    private String matchType;

    private String source;

    private Integer priority;

    private Boolean isActive;
}
