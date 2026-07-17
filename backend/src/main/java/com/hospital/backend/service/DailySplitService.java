package com.hospital.backend.service;

import com.hospital.backend.common.Result;

import java.util.Map;

public interface DailySplitService {

    /**
     * 按 deliveryDate 拆分对账 Job，返回日结汇总（FR-M14-01）。
     */
    Result<Map<String, Object>> splitJobByDate(Long jobId);
}
