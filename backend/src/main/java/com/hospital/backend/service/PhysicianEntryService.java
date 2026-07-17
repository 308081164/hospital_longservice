package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.deptphysician.SavePhysicianEntryRequest;
import com.hospital.backend.dto.response.deptphysician.PhysicianEntryResponse;

import java.util.List;

public interface PhysicianEntryService {

    Result<List<PhysicianEntryResponse>> listEntries(Long customerId, String keyword, Boolean isActive);

    Result<PhysicianEntryResponse> createEntry(Long customerId, SavePhysicianEntryRequest request);

    Result<PhysicianEntryResponse> updateEntry(Long customerId, Long entryId, SavePhysicianEntryRequest request);

    Result<Boolean> deleteEntry(Long customerId, Long entryId);
}
