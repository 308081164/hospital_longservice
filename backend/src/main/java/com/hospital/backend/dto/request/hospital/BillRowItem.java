package com.hospital.backend.dto.request.hospital;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillRowItem {

    private String sheetName;

    private Integer rowNumber;

    private String deliveryDate;

    private String orderNo;

    private String type;

    private String categoryNo;

    private String packName;

    private String packageMaterial;

    private Integer packCount;

    private Integer instrumentCount;

    private Double unitPrice;

    private Double totalPrice;

    private Double expectedUnitPrice;

    /**
     * 折扣后、收到分之前的单价。总价用它乘包数再收分；单价列仍用 {@link #expectedUnitPrice}。
     */
    private Double unroundedUnitPrice;

    private Double correctedTotalPrice;

    private Double difference;

    private String status;

    private String pricingRule;

    private List<String> notes;

    private Map<String, Object> original;
}
