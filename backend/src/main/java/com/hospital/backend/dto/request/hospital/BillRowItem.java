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

    private Double correctedTotalPrice;

    private Double difference;

    private String status;

    private String pricingRule;

    private List<String> notes;

    private Map<String, Object> original;
}
