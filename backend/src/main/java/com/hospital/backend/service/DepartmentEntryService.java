package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.deptphysician.SaveDepartmentEntryRequest;
import com.hospital.backend.dto.response.deptphysician.DepartmentEntryResponse;

import java.util.List;

public interface DepartmentEntryService {

    Result<List<DepartmentEntryResponse>> listEntries(Long customerId, String keyword, Boolean isActive);

    Result<DepartmentEntryResponse> createEntry(Long customerId, SaveDepartmentEntryRequest request);

    Result<DepartmentEntryResponse> updateEntry(Long customerId, Long entryId, SaveDepartmentEntryRequest request);

    Result<Boolean> deleteEntry(Long customerId, Long entryId);
}
