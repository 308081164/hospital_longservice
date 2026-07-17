package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.external.SaveExternalInstrumentRequest;
import com.hospital.backend.dto.response.external.ExternalInstrumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ExternalInstrumentService {

    Result<List<ExternalInstrumentResponse>> listCatalog(Long customerId);

    Result<List<ExternalInstrumentResponse>> listByJob(Long jobId);

    Result<ExternalInstrumentResponse> createCatalogEntry(Long customerId, SaveExternalInstrumentRequest request);

    Result<ExternalInstrumentResponse> createJobEntry(Long jobId, SaveExternalInstrumentRequest request);

    Result<ExternalInstrumentResponse> updateEntry(Long id, SaveExternalInstrumentRequest request);

    Result<Boolean> deleteEntry(Long id);

    Result<Integer> importJobExcel(Long jobId, MultipartFile file);

    double sumJobTotal(Long jobId);
}
