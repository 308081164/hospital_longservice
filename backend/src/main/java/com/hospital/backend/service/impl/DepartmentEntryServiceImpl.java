package com.hospital.backend.service.impl;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.deptphysician.SaveDepartmentEntryRequest;
import com.hospital.backend.dto.response.deptphysician.DepartmentEntryResponse;
import com.hospital.backend.entity.DepartmentEntry;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.DepartmentEntryMapper;
import com.hospital.backend.service.DepartmentEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentEntryServiceImpl implements DepartmentEntryService {

    private final DepartmentEntryMapper departmentEntryMapper;
    private final CustomerMapper customerMapper;

    @Override
    public Result<List<DepartmentEntryResponse>> listEntries(Long customerId, String keyword, Boolean isActive) {
        if (customerMapper.selectById(customerId) == null) {
            return Result.fail(404, "客户不存在");
        }
        String kw = keyword != null ? keyword.trim().toLowerCase() : null;
        List<DepartmentEntryResponse> list = departmentEntryMapper.selectByCustomerId(customerId).stream()
                .map(this::toResponse)
                .filter(entry -> matchesKeyword(entry, kw))
                .filter(entry -> matchesActive(entry.getIsActive(), isActive))
                .toList();
        return Result.success(list);
    }

    @Override
    @Transactional
    public Result<DepartmentEntryResponse> createEntry(Long customerId, SaveDepartmentEntryRequest request) {
        if (customerMapper.selectById(customerId) == null) {
            return Result.fail(404, "客户不存在");
        }
        DepartmentEntry entry = new DepartmentEntry();
        entry.setCustomerId(customerId);
        entry.setDepartmentName(request.getDepartmentName().trim());
        entry.setCode(trimOrNull(request.getCode()));
        entry.setNotes(request.getNotes());
        entry.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        entry.setUsageCount(0);
        departmentEntryMapper.insert(entry);
        return Result.success(toResponse(departmentEntryMapper.selectById(entry.getId())));
    }

    @Override
    @Transactional
    public Result<DepartmentEntryResponse> updateEntry(
            Long customerId, Long entryId, SaveDepartmentEntryRequest request) {
        DepartmentEntry existing = departmentEntryMapper.selectById(entryId);
        if (existing == null || !customerId.equals(existing.getCustomerId())) {
            return Result.fail(404, "科室记录不存在");
        }
        existing.setDepartmentName(request.getDepartmentName().trim());
        existing.setCode(trimOrNull(request.getCode()));
        existing.setNotes(request.getNotes());
        existing.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        departmentEntryMapper.updateById(existing);
        return Result.success(toResponse(departmentEntryMapper.selectById(entryId)));
    }

    @Override
    @Transactional
    public Result<Boolean> deleteEntry(Long customerId, Long entryId) {
        DepartmentEntry existing = departmentEntryMapper.selectById(entryId);
        if (existing == null || !customerId.equals(existing.getCustomerId())) {
            return Result.fail(404, "科室记录不存在");
        }
        departmentEntryMapper.deleteById(entryId);
        return Result.success(true);
    }

    private DepartmentEntryResponse toResponse(DepartmentEntry entry) {
        return DepartmentEntryResponse.builder()
                .id(entry.getId())
                .customerId(entry.getCustomerId())
                .departmentName(entry.getDepartmentName())
                .code(entry.getCode())
                .notes(entry.getNotes())
                .usageCount(entry.getUsageCount())
                .isActive(entry.getIsActive())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean matchesKeyword(DepartmentEntryResponse entry, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        return containsIgnoreCase(entry.getDepartmentName(), keyword)
                || containsIgnoreCase(entry.getCode(), keyword)
                || containsIgnoreCase(entry.getNotes(), keyword);
    }

    private static boolean matchesActive(Boolean entryActive, Boolean filterActive) {
        if (filterActive == null) {
            return true;
        }
        boolean active = entryActive == null || entryActive;
        return active == filterActive;
    }

    private static boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}
