package com.hospital.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class HospitalReconciliationExportLog {

    private Long id;

    @JsonProperty("job_id")
    private Long jobId;

    @JsonProperty("export_type")
    private String exportType;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("operator_name")
    private String operatorName;

    private LocalDateTime createdAt;
}
