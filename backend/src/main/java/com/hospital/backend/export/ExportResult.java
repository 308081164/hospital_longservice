package com.hospital.backend.export;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExportResult {

    private final byte[] content;
    private final String fileName;
    private final String contentType;
    private final String strategyKey;
    private final Long templateId;
}
