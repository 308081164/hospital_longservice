package com.hospital.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class HospitalReconciliationRow {

    private Long id;

    @JsonProperty("job_id")
    private Long jobId;

    @JsonProperty("sheet_name")
    private String sheetName;

    @JsonProperty("row_number")
    private Integer rowNumber;

    @JsonProperty("delivery_date")
    private String deliveryDate;

    @JsonProperty("order_no")
    private String orderNo;

    private String type;

    @JsonProperty("category_no")
    private String categoryNo;

    @JsonProperty("pack_name")
    private String packName;

    @JsonProperty("package_material")
    private String packageMaterial;

    @JsonProperty("pack_count")
    private Integer packCount = 0;

    @JsonProperty("instrument_count")
    private Integer instrumentCount = 0;

    @JsonProperty("unit_price")
    private Double unitPrice;

    @JsonProperty("total_price")
    private Double totalPrice;

    @JsonProperty("expected_unit_price")
    private Double expectedUnitPrice;

    @JsonProperty("corrected_total_price")
    private Double correctedTotalPrice;

    private Double difference;

    private String status;

    @JsonProperty("pricing_rule")
    private String pricingRule;

    @JsonProperty("matched_product_id")
    private Long matchedProductId;

    @JsonProperty("matched_variant_id")
    private Long matchedVariantId;

    @JsonProperty("pricing_path")
    private String pricingPath;

    @JsonProperty("notes_json")
    private String notesJson;

    @JsonProperty("matched_rule_id")
    private Long matchedRuleId;

    @JsonProperty("matched_price_option")
    private Double matchedPriceOption;

    @JsonProperty("billing_notes")
    private String billingNotes;

    private LocalDateTime createdAt;
}
