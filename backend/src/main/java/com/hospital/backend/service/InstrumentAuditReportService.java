package com.hospital.backend.service;

import com.hospital.backend.common.Result;

import java.util.Map;

public interface InstrumentAuditReportService {

    /**
     * 生成器械量表/把数表与灭菌包装表（FR-M13-01/02）。
     */
    Result<Map<String, Object>> buildAuditReport(Long jobId);
}
