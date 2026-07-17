package com.hospital.backend.export.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ColumnMappingConfig {

    private List<String> removeColumns = new ArrayList<>();
    private List<InsertColumnSpec> insertColumns = new ArrayList<>();
    private List<String> keepColumns = new ArrayList<>();
    private Map<String, String> renameColumns = new LinkedHashMap<>();

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InsertColumnSpec {
        private String header;
        private String afterHeader;
        private String defaultValue;
    }
}
