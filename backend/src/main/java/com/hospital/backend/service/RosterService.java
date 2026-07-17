package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.roster.SaveRosterEntryRequest;
import com.hospital.backend.dto.response.roster.RosterEntryResponse;
import com.hospital.backend.dto.response.roster.RosterImportResultResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RosterService {

    Result<List<RosterEntryResponse>> listEntries(Long customerId);

    Result<RosterEntryResponse> createEntry(Long customerId, SaveRosterEntryRequest request);

    Result<RosterEntryResponse> updateEntry(Long customerId, Long entryId, SaveRosterEntryRequest request);

    Result<Boolean> deleteEntry(Long customerId, Long entryId);

    Result<RosterImportResultResponse> importExcel(Long customerId, MultipartFile file, boolean replaceExisting);
}
