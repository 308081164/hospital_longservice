package com.hospital.backend.dto.response.hospital;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReconciliationExportLogResponse {

    private Long id;

    private String exportType;

    private String fileName;

    private String filePath;

    private String operatorName;

    private LocalDateTime createdAt;

    public ReconciliationExportLogResponse(Long id, String exportType, String fileName,
                                           String filePath, String operatorName, LocalDateTime createdAt) {
        this.id = id;
        this.exportType = exportType;
        this.fileName = fileName;
        this.filePath = filePath;
        this.operatorName = operatorName;
        this.createdAt = createdAt;
    }
}
