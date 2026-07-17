package com.hospital.backend.service.impl;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.logistics.SaveLogisticsImportRequest;
import com.hospital.backend.dto.response.logistics.LogisticsImportResponse;
import com.hospital.backend.entity.LogisticsImport;
import com.hospital.backend.mapper.LogisticsImportMapper;
import com.hospital.backend.service.LogisticsImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LogisticsImportServiceImpl implements LogisticsImportService {

    private final LogisticsImportMapper logisticsImportMapper;

    @Override
    public Result<List<LogisticsImportResponse>> listByCustomer(Long customerId) {
        return Result.success(logisticsImportMapper.selectByCustomerId(customerId).stream()
                .map(this::toResponse)
                .toList());
    }

    @Override
    public Result<List<LogisticsImportResponse>> listByCustomerAndMonth(Long customerId, String billingMonth) {
        return Result.success(logisticsImportMapper.selectByCustomerAndMonth(customerId, billingMonth).stream()
                .map(this::toResponse)
                .toList());
    }

    @Override
    @Transactional
    public Result<LogisticsImportResponse> create(Long customerId, SaveLogisticsImportRequest request) {
        LogisticsImport record = fromRequest(customerId, request);
        logisticsImportMapper.insert(record);
        return Result.success(toResponse(logisticsImportMapper.selectById(record.getId())));
    }

    @Override
    @Transactional
    public Result<LogisticsImportResponse> update(Long customerId, Long importId, SaveLogisticsImportRequest request) {
        LogisticsImport existing = logisticsImportMapper.selectById(importId);
        if (existing == null || !customerId.equals(existing.getCustomerId())) {
            return Result.fail(404, "Logistics import not found");
        }
        existing.setJobId(request.getJobId());
        existing.setBillingMonth(request.getBillingMonth());
        existing.setTripDate(request.getTripDate());
        existing.setRoute(request.getRoute());
        existing.setTripCount(request.getTripCount() != null ? request.getTripCount() : 1);
        existing.setFeeAmount(request.getFeeAmount());
        existing.setNotes(request.getNotes());
        logisticsImportMapper.updateById(existing);
        return Result.success(toResponse(logisticsImportMapper.selectById(importId)));
    }

    @Override
    @Transactional
    public Result<Boolean> delete(Long customerId, Long importId) {
        LogisticsImport existing = logisticsImportMapper.selectById(importId);
        if (existing == null || !customerId.equals(existing.getCustomerId())) {
            return Result.fail(404, "Logistics import not found");
        }
        logisticsImportMapper.deleteById(importId);
        return Result.success(true);
    }

    @Override
    @Transactional
    public int linkImportsToJob(Long customerId, String billingMonth, Long jobId) {
        if (customerId == null || jobId == null || billingMonth == null || billingMonth.isBlank()) {
            return 0;
        }
        return logisticsImportMapper.linkJobByCustomerAndMonth(customerId, billingMonth, jobId);
    }

    private LogisticsImport fromRequest(Long customerId, SaveLogisticsImportRequest request) {
        LogisticsImport record = new LogisticsImport();
        record.setCustomerId(customerId);
        record.setJobId(request.getJobId());
        record.setBillingMonth(request.getBillingMonth());
        record.setTripDate(request.getTripDate());
        record.setRoute(request.getRoute());
        record.setTripCount(request.getTripCount() != null ? request.getTripCount() : 1);
        record.setFeeAmount(request.getFeeAmount());
        record.setNotes(request.getNotes());
        return record;
    }

    private LogisticsImportResponse toResponse(LogisticsImport record) {
        return LogisticsImportResponse.builder()
                .id(record.getId())
                .customerId(record.getCustomerId())
                .jobId(record.getJobId())
                .billingMonth(record.getBillingMonth())
                .tripDate(record.getTripDate())
                .route(record.getRoute())
                .tripCount(record.getTripCount())
                .feeAmount(record.getFeeAmount())
                .notes(record.getNotes())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
