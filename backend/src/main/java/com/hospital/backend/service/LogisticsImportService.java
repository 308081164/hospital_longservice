package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.logistics.SaveLogisticsImportRequest;
import com.hospital.backend.dto.response.logistics.LogisticsImportResponse;

import java.util.List;

public interface LogisticsImportService {

    Result<List<LogisticsImportResponse>> listByCustomer(Long customerId);

    Result<List<LogisticsImportResponse>> listByCustomerAndMonth(Long customerId, String billingMonth);

    Result<LogisticsImportResponse> create(Long customerId, SaveLogisticsImportRequest request);

    Result<LogisticsImportResponse> update(Long customerId, Long importId, SaveLogisticsImportRequest request);

    Result<Boolean> delete(Long customerId, Long importId);

    /** INT-05：将同客户同账期未关联的物流导入记录绑定到对账任务 */
    int linkImportsToJob(Long customerId, String billingMonth, Long jobId);
}
