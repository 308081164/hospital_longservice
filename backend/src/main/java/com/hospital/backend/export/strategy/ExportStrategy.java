package com.hospital.backend.export.strategy;

import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;

public interface ExportStrategy {

    String strategyKey();

    ExportResult export(ExportContext context) throws Exception;
}
