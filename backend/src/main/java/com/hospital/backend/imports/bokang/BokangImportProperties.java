package com.hospital.backend.imports.bokang;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.import.bokang")
public class BokangImportProperties {

    /** Enabled via IMPORT_BOKANG_DATA=1 or app.import.bokang.enabled=true */
    private boolean enabled = false;

    /** Directory containing铂康 INSERT dump files */
    private String dataDir = "./铂康/建表语句";

    /** Max distinct product patterns to upsert from row dump */
    private int maxProducts = 500;

    /** Skip test hospital names when importing customers */
    private boolean skipTestHospitals = true;
}
