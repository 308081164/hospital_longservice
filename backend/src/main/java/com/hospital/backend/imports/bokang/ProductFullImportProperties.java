package com.hospital.backend.imports.bokang;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.import.full-products")
public class ProductFullImportProperties {

    /** IMPORT_BOKANG_FULL_PRODUCTS=1 或 app.import.full-products.enabled=true */
    private boolean enabled = "1".equals(System.getenv("IMPORT_BOKANG_FULL_PRODUCTS"));

    private String dataDir = System.getenv().getOrDefault(
            "BOKANG_DATA_DIR",
            "铂康/建表语句"
    );

    private String rowSqlFile = "hospital_reconciliation_row.sql";
}
