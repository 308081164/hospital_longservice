package com.hospital.backend.export;

import com.hospital.backend.export.model.ColumnMappingConfig;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnTransformPipelineTest {

    private final ColumnTransformPipeline pipeline = new ColumnTransformPipeline();

    @Test
    void removesConfiguredColumnsByHeaderName() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("账单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("包名");
            header.createCell(1).setCellValue("器械数");
            header.createCell(2).setCellValue("总价");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("测试包");
            data.createCell(1).setCellValue(12);
            data.createCell(2).setCellValue(99.5);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            workbookBytes = bos.toByteArray();
        }

        ColumnMappingConfig config = new ColumnMappingConfig();
        config.setRemoveColumns(List.of("器械数"));
        byte[] transformed = pipeline.apply(workbookBytes, config);

        try (XSSFWorkbook result = new XSSFWorkbook(new java.io.ByteArrayInputStream(transformed))) {
            Row header = result.getSheetAt(0).getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("包名");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("总价");
            assertThat(header.getPhysicalNumberOfCells()).isEqualTo(2);
        }
    }

    @Test
    void keepColumnsModeDropsUnlistedHeaders() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("账单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("包名");
            header.createCell(1).setCellValue("备注");
            header.createCell(2).setCellValue("总价");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            workbookBytes = bos.toByteArray();
        }

        ColumnMappingConfig config = new ColumnMappingConfig();
        config.setKeepColumns(List.of("包名", "总价"));
        byte[] transformed = pipeline.apply(workbookBytes, config);

        try (XSSFWorkbook result = new XSSFWorkbook(new java.io.ByteArrayInputStream(transformed))) {
            Row header = result.getSheetAt(0).getRow(0);
            assertThat(header.getPhysicalNumberOfCells()).isEqualTo(2);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("包名");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("总价");
        }
    }
}
