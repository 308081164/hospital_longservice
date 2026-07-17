package com.hospital.backend.service.impl;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.deptphysician.SavePhysicianEntryRequest;
import com.hospital.backend.dto.response.deptphysician.PhysicianEntryResponse;
import com.hospital.backend.entity.DepartmentEntry;
import com.hospital.backend.entity.PhysicianEntry;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.DepartmentEntryMapper;
import com.hospital.backend.mapper.PhysicianEntryMapper;
import com.hospital.backend.service.PhysicianEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PhysicianEntryServiceImpl implements PhysicianEntryService {

    private final PhysicianEntryMapper physicianEntryMapper;
    private final DepartmentEntryMapper departmentEntryMapper;
    private final CustomerMapper customerMapper;

    @Override
    public Result<List<PhysicianEntryResponse>> listEntries(Long customerId, String keyword, Boolean isActive) {
        if (customerMapper.selectById(customerId) == null) {
            return Result.fail(404, "客户不存在");
        }
        String kw = keyword != null ? keyword.trim().toLowerCase() : null;
        List<PhysicianEntryResponse> list = physicianEntryMapper.selectByCustomerId(customerId).stream()
                .map(this::toResponse)
                .filter(entry -> matchesKeyword(entry, kw))
                .filter(entry -> matchesActive(entry.getIsActive(), isActive))
                .toList();
        return Result.success(list);
    }

    @Override
    @Transactional
    public Result<PhysicianEntryResponse> createEntry(Long customerId, SavePhysicianEntryRequest request) {
        if (customerMapper.selectById(customerId) == null) {
            return Result.fail(404, "客户不存在");
        }
        PhysicianEntry entry = fromRequest(customerId, request);
        physicianEntryMapper.insert(entry);
        return Result.success(toResponse(physicianEntryMapper.selectById(entry.getId())));
    }

    @Override
    @Transactional
    public Result<PhysicianEntryResponse> updateEntry(
            Long customerId, Long entryId, SavePhysicianEntryRequest request) {
        PhysicianEntry existing = physicianEntryMapper.selectById(entryId);
        if (existing == null || !customerId.equals(existing.getCustomerId())) {
            return Result.fail(404, "医生记录不存在");
        }
        applyRequest(existing, request);
        physicianEntryMapper.updateById(existing);
        return Result.success(toResponse(physicianEntryMapper.selectById(entryId)));
    }

    @Override
    @Transactional
    public Result<Boolean> deleteEntry(Long customerId, Long entryId) {
        PhysicianEntry existing = physicianEntryMapper.selectById(entryId);
        if (existing == null || !customerId.equals(existing.getCustomerId())) {
            return Result.fail(404, "医生记录不存在");
        }
        physicianEntryMapper.deleteById(entryId);
        return Result.success(true);
    }

    private PhysicianEntry fromRequest(Long customerId, SavePhysicianEntryRequest request) {
        PhysicianEntry entry = new PhysicianEntry();
        entry.setCustomerId(customerId);
        applyRequest(entry, request);
        entry.setUsageCount(0);
        return entry;
    }

    private void applyRequest(PhysicianEntry entry, SavePhysicianEntryRequest request) {
        entry.setPhysicianName(request.getPhysicianName().trim());
        entry.setDepartmentEntryId(request.getDepartmentEntryId());
        entry.setCode(trimOrNull(request.getCode()));
        entry.setNotes(request.getNotes());
        entry.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        entry.setDepartmentName(resolveDepartmentName(request));
    }

    private String resolveDepartmentName(SavePhysicianEntryRequest request) {
        if (request.getDepartmentEntryId() != null) {
            DepartmentEntry dept = departmentEntryMapper.selectById(request.getDepartmentEntryId());
            if (dept != null) {
                return dept.getDepartmentName();
            }
        }
        if (request.getDepartmentName() != null && !request.getDepartmentName().isBlank()) {
            return request.getDepartmentName().trim();
        }
        return null;
    }

    private PhysicianEntryResponse toResponse(PhysicianEntry entry) {
        return PhysicianEntryResponse.builder()
                .id(entry.getId())
                .customerId(entry.getCustomerId())
                .physicianName(entry.getPhysicianName())
                .departmentEntryId(entry.getDepartmentEntryId())
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

    private static boolean matchesKeyword(PhysicianEntryResponse entry, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        return containsIgnoreCase(entry.getPhysicianName(), keyword)
                || containsIgnoreCase(entry.getDepartmentName(), keyword)
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
