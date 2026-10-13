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
    void extractsHospitalNameFromD8Only() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("手术室");
            sheet.createRow(0).createCell(0).setCellValue("发货单汇总表-显示包装材料");
            sheet.createRow(7).createCell(3).setCellValue("哈尔滨冰城医疗美容医院");
            Row header = sheet.createRow(8);
            header.createCell(0).setCellValue("发货日期");
            header.createCell(1).setCellValue("发货单号");
            header.createCell(2).setCellValue("类型");
            header.createCell(4).setCellValue("包名");
            header.createCell(5).setCellValue("包装材料");
            header.createCell(6).setCellValue("包数");
            header.createCell(7).setCellValue("器械数");
            header.createCell(8).setCellValue("单价");
            header.createCell(9).setCellValue("总价");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            List<String> names = ExcelBillImportSupport.extractHospitalDisplayNames(bos.toByteArray());
            assertThat(names).containsExactly("哈尔滨冰城医疗美容医院");
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
            assertThat(names).containsExactly("黑龙江谋大医院");
        }
    }

    @Test
    void extractsHospitalNameFromD9WhenPresent() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("手术室");
            sheet.createRow(0).createCell(0).setCellValue("发货单汇总表-显示包装材料");
            Row header = sheet.createRow(7);
            header.createCell(0).setCellValue("发货日期");
            header.createCell(1).setCellValue("发货单号");
            header.createCell(4).setCellValue("包名");
            header.createCell(5).setCellValue("包装材料");
            header.createCell(7).setCellValue("器械数");
            header.createCell(8).setCellValue("单价");
            header.createCell(9).setCellValue("总价");
            sheet.createRow(8).createCell(3).setCellValue("呼兰中医院");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            List<String> names = ExcelBillImportSupport.extractHospitalDisplayNames(bos.toByteArray());
            assertThat(names).containsExactly("呼兰中医院");
        }
    }

    @Test
    void extractsHospitalNameFromColumnASummaryWhenDColumnEmpty() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("账单");
            Row header = sheet.createRow(7);
            header.createCell(0).setCellValue("发货日期");
            header.createCell(1).setCellValue("发货单号");
            header.createCell(4).setCellValue("包名");
            header.createCell(8).setCellValue("单价");
            header.createCell(9).setCellValue("总价");
            Row summary = sheet.createRow(8);
            summary.createCell(0).setCellValue("三精肾病医院");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            List<String> names = ExcelBillImportSupport.extractHospitalDisplayNames(bos.toByteArray());
            assertThat(names).containsExactly("三精肾病医院");
        }
    }

    @Test
    void prefersDColumnHospitalNameOverColumnASummary() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("账单");
            sheet.createRow(7).createCell(3).setCellValue("呼兰中医院");
            Row header = sheet.createRow(8);
            header.createCell(0).setCellValue("发货日期");
            header.createCell(4).setCellValue("包名");
            header.createCell(8).setCellValue("单价");
            header.createCell(9).setCellValue("总价");
            sheet.createRow(9).createCell(0).setCellValue("三精肾病医院");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            List<String> names = ExcelBillImportSupport.extractHospitalDisplayNames(bos.toByteArray());
            assertThat(names).containsExactly("呼兰中医院");
        }
    }

    @Test
    void extractsClinicNameFromD9() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("中西医科");
            Row header = sheet.createRow(7);
            header.createCell(3).setCellValue("发货日期");
            header.createCell(4).setCellValue("发货单号");
            header.createCell(5).setCellValue("类型");
            header.createCell(7).setCellValue("包名");
            header.createCell(10).setCellValue("包装材料");
            header.createCell(11).setCellValue("包数");
            header.createCell(12).setCellValue("器械数");
            header.createCell(13).setCellValue("单价");
            header.createCell(14).setCellValue("总价");
            sheet.createRow(8).createCell(3).setCellValue("晟龄中西医结合诊所");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            List<String> names = ExcelBillImportSupport.extractHospitalDisplayNames(bos.toByteArray());
            assertThat(names).containsExactly("晟龄中西医结合诊所");
        }
    }

    @Test
    void extractsClinicNameFromDeliveryDateColumnWhenTableIsNotOnColumnD() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("中西医科");
            Row header = sheet.createRow(0);
            header.createCell(1).setCellValue("发货日期");
            header.createCell(2).setCellValue("发货单号");
            header.createCell(3).setCellValue("类型");
            header.createCell(4).setCellValue("包名");
            header.createCell(5).setCellValue("包装材料");
            header.createCell(6).setCellValue("包数");
            header.createCell(7).setCellValue("器械数");
            header.createCell(8).setCellValue("单价");
            header.createCell(9).setCellValue("总价");
            Row summary = sheet.createRow(1);
            summary.createCell(1).setCellValue("晟龄中西医结合诊所");
            Row detail = sheet.createRow(2);
            detail.createCell(1).setCellValue("2026-09-14");
            detail.createCell(2).setCellValue("1637674");
            detail.createCell(3).setCellValue("额外包(纸塑袋)");
            detail.createCell(4).setCellValue("镊子缸-2/Z2530");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            List<String> names = ExcelBillImportSupport.extractHospitalDisplayNames(bos.toByteArray());
            assertThat(names).containsExactly("晟龄中西医结合诊所");
        }
    }

    @Test
    void keepsD9NameWithoutInstitutionSuffix() throws Exception {
        for (String name : java.util.List.of(
                "黑龙江东大肛肠",
                "慧鑫综合门诊",
                "方南南口腔",
                "哈尔滨东桉医疗美容有限公司",
                "哈市第二社会福利院",
                "云科健康医疗")) {
            try (XSSFWorkbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("账单");
                Row header = sheet.createRow(7);
                header.createCell(0).setCellValue("发货日期");
                header.createCell(1).setCellValue("发货单号");
                header.createCell(4).setCellValue("包名");
                header.createCell(8).setCellValue("单价");
                header.createCell(9).setCellValue("总价");
                sheet.createRow(8).createCell(3).setCellValue(name);

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                wb.write(bos);

                assertThat(ExcelBillImportSupport.extractHospitalDisplayNames(bos.toByteArray()))
                        .as(name)
                        .containsExactly(name);
            }
        }
    }

    @Test
    void skipsHeaderLabelInD9AndReadsD8() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("账单");
            sheet.createRow(7).createCell(3).setCellValue("方南南口腔");
            Row header = sheet.createRow(8);
            header.createCell(0).setCellValue("发货日期");
            header.createCell(1).setCellValue("发货单号");
            header.createCell(2).setCellValue("类型");
            header.createCell(3).setCellValue("包名");
            header.createCell(8).setCellValue("单价");
            header.createCell(9).setCellValue("总价");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            assertThat(ExcelBillImportSupport.extractHospitalDisplayNames(bos.toByteArray()))
                    .containsExactly("方南南口腔");
        }
    }

    @Test
    void doesNotTreatBareOutpatientDepartmentAsHospital() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("账单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("发货日期");
            header.createCell(1).setCellValue("发货单号");
            header.createCell(4).setCellValue("包名");
            header.createCell(5).setCellValue("包装材料");
            header.createCell(7).setCellValue("器械数");
            header.createCell(8).setCellValue("单价");
            header.createCell(9).setCellValue("总价");
            sheet.createRow(1).createCell(0).setCellValue("门诊部");
            sheet.createRow(2).createCell(0).setCellValue("哈尔滨道外区松电慢性病专科门诊部");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            List<String> names = ExcelBillImportSupport.extractHospitalDisplayNames(bos.toByteArray());
            assertThat(names).containsExactly("哈尔滨道外区松电慢性病专科门诊部");
        }
    }
}
