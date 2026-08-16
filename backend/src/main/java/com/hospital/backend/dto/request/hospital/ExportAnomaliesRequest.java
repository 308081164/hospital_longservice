package com.hospital.backend.dto.request.hospital;

import lombok.Data;

@Data
public class ExportAnomaliesRequest {

    /** 是否在异常明细中额外包含「字段一致性问题」（差额为 0 但 billingNotes 有 violations） */
    private Boolean includeFieldConsistency = false;

    public boolean isIncludeFieldConsistency() {
        return Boolean.TRUE.equals(includeFieldConsistency);
    }
}
