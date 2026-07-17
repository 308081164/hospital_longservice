package com.hospital.backend.dto.response.roster;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RosterImportResultResponse {

    private int importedCount;

    private int skippedCount;

    private List<String> errors = new ArrayList<>();
}
