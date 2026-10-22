package com.hospital.backend.dto.request.hospital;

import lombok.Data;

import java.util.List;

@Data
public class HospitalSettlementTemplateExportRequest {

    private String hospitalName;

    private String templateId;

    private String companyName;

    private String titleText;

    private String recipientLabel;

    private String hospitalDisplayName;

    private String dateRangeText;

    private String sheetName;

    private List<SettlementFeeRow> feeRows;

    private Double totalAmount;

    private String uppercaseTotal;

    /** 合计行备注。低消并进合计时写「执行低消1000元」。 */
    private String totalRemark;

    private String closingText;
}
