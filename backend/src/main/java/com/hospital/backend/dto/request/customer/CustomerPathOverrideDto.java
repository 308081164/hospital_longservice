package com.hospital.backend.dto.request.customer;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CustomerPathOverrideDto {

    private Boolean disableLowTemp;

    private BigDecimal forceHighTempUnitPrice;
}
