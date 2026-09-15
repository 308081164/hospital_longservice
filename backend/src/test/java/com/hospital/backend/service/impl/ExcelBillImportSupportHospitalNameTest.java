package com.hospital.backend.service.impl;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelBillImportSupportHospitalNameTest {

    @Test
    void extractsHospitalNameFromRowAfterHeader() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("手术室");
            sheet.createRow(0).createCell(0).setCellValue("发货单汇总表-显示包装材料");
            Row header = sheet.createRow(7);
            header.createCell(0).setCellValue("发货日期");
            header.createCell(1).setCellValue("发货单号");
            header.createCell(2).setCellValue("类型");
            header.createCell(4).setCellValue("包名");
            header.createCell(5).setCellValue("包装材料");
            header.createCell(6).setCellValue("包数");
            header.createCell(7).setCellValue("器械数");
            header.createCell(8).setCellValue("单价");
            header.createCell(9).setCellValue("总价");
            Row summary = sheet.createRow(8);
            summary.createCell(0).setCellValue("哈尔滨冰城医疗美容医院");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            List<String> names = ExcelBillImportSupport.extractHospitalDisplayNames(bos.toByteArray());
            assertThat(names).anyMatch(n -> n.contains("冰城医疗美容"));
        }
    }

    @Test
    void ignoresDateRangeRowAndReadsHospitalFromDColumn() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("门诊部");
            sheet.createRow(3).createCell(3).setCellValue("从:2026/6/1 00:00:00 至: 2026/6/30 23:59:59.999");
            sheet.createRow(7).createCell(3).setCellValue("黑龙江谋大医院");
            Row header = sheet.createRow(8);
            header.createCell(0).setCellValue("发货日期");
            header.createCell(1).setCellValue("发货单号");
            header.createCell(4).setCellValue("包名");
            header.createCell(5).setCellValue("包装材料");
            header.createCell(7).setCellValue("器械数");
            header.createCell(8).setCellValue("单价");
            header.createCell(9).setCellValue("总价");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            List<String> names = ExcelBillImportSupport.extractHospitalDisplayNames(bos.toByteArray());
            assertThat(names).contains("黑龙江谋大医院");
            assertThat(names).noneMatch(n -> n.contains("至: 2026/6/30"));
        }
    }

    @Test
    void extractsHospitalNameFromRowBeforeHeader() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("手术室");
            sheet.createRow(0).createCell(0).setCellValue("发货单汇总表-显示包装材料");
            sheet.createRow(6).createCell(0).setCellValue("三精肾病医院");
            Row header = sheet.createRow(7);
            header.createCell(0).setCellValue("发货日期");
            header.createCell(1).setCellValue("发货单号");
            header.createCell(4).setCellValue("包名");
            header.createCell(5).setCellValue("包装材料");
            header.createCell(7).setCellValue("器械数");
            header.createCell(8).setCellValue("单价");
            header.createCell(9).setCellValue("总价");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            List<String> names = ExcelBillImportSupport.extractHospitalDisplayNames(bos.toByteArray());
            assertThat(names).contains("三精肾病医院");
        }
    }
}
