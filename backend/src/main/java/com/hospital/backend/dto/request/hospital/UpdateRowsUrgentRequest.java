package com.hospital.backend.dto.request.hospital;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateRowsUrgentRequest {

    /** sheetName + rowNumber 定位行；为空则对 rowIds 生效 */
    private List<RowRef> rows;

    private List<Long> rowIds;

    @NotNull(message = "isUrgent 不能为空")
    private Boolean isUrgent;

    @Getter
    @Setter
    public static class RowRef {
        private String sheetName;
        private Integer rowNumber;
    }
}
