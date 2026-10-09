package com.hospital.backend.service.impl;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 铂康「发货单汇总表-显示包装材料」（DeliveryNoteSummaryWithPackingMaterial）导入形态。
 */
class ExcelBillImportSupportDeliveryNoteSummaryTest {

    @Test
    void parsesPackingMaterialBillWithInstrumentCountHeaderAlias() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("账单");
            sheet.createRow(0).createCell(0).setCellValue("发货单汇总表-显示包装材料");
            sheet.createRow(3).createCell(0).setCellValue("从:2026/6/1 至: 2026/6/30");
            Row header = sheet.createRow(6);
            header.createCell(0).setCellValue("发货日期");
            header.createCell(1).setCellValue("发货单号");
            header.createCell(2).setCellValue("类型");
            header.createCell(3).setCellValue("包类别号");
            header.createCell(4).setCellValue("包名");
            header.createCell(5).setCellValue("包装材料");
            header.createCell(6).setCellValue("包数");
            // 客户表头：单元格内换行「器械\n数量」
            header.createCell(7).setCellValue("器械\n数量");
            header.createCell(8).setCellValue("单价");
            header.createCell(9).setCellValue("总价");

            Row hospitalSummary = sheet.createRow(7);
            hospitalSummary.createCell(0).setCellValue("哈尔滨红十字妇产医院");
            hospitalSummary.createCell(6).setCellValue(1);
            hospitalSummary.createCell(9).setCellValue(22);

            Row deptMarker = sheet.createRow(8);
            deptMarker.createCell(0).setCellValue("ICU病房");

            Row detail = sheet.createRow(9);
            detail.createCell(0).setCellValue("2026-06-03");
            detail.createCell(1).setCellValue("1608752");
            detail.createCell(2).setCellValue("额外包");
            detail.createCell(4).setCellValue("湿化瓶-1/Z3032");
            detail.createCell(5).setCellValue("无纺布-90×90-50g");
            detail.createCell(6).setCellValue(1);
            detail.createCell(7).setCellValue(3);
            detail.createCell(8).setCellValue(22);
            detail.createCell(9).setCellValue(22);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            byte[] bytes = bos.toByteArray();

            List<String> hospitalNames = ExcelBillImportSupport.extractHospitalDisplayNames(bytes);
            assertThat(hospitalNames).containsExactly("哈尔滨红十字妇产医院");

            List<Map<String, Object>> rows = ExcelBillImportSupport.parseWorkbook(new ByteArrayInputStream(bytes));
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("sheetName")).isEqualTo("ICU病房");
            assertThat(rows.get(0).get("packName")).isEqualTo("湿化瓶-1/Z3032");
            assertThat(rows.get(0).get("packageMaterial")).isEqualTo("无纺布-90×90-50g");
            assertThat(rows.get(0).get("instrumentCount")).isEqualTo(3);
        }
    }

    /**
     * 客户原始表 {@code DeliveryNoteSummaryWithPackingMeterial (2).xlsx}：
     * B 列标题、稀疏列布局、表头「器械\\n数量」与「包装材料」。
     */
    @Test
    void parsesCustomerGoldenDeliveryNoteSummaryWithPackingMaterial() throws Exception {
        Path file = Path.of("../测试用例/fixtures/DeliveryNoteSummaryWithPackingMaterial_golden.xlsx");
        if (!Files.exists(file)) {
            file = Path.of("测试用例/fixtures/DeliveryNoteSummaryWithPackingMaterial_golden.xlsx");
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(file), "golden fixture missing");
        byte[] bytes = Files.readAllBytes(file);

        List<String> hospitalNames = ExcelBillImportSupport.extractHospitalDisplayNames(bytes);
        assertThat(hospitalNames.stream().anyMatch(n -> n.contains("红十字妇产医院"))).isTrue();

        try (InputStream in = Files.newInputStream(file)) {
            List<Map<String, Object>> rows = ExcelBillImportSupport.parseWorkbook(in);
            assertThat(rows).hasSizeGreaterThan(100);
            Set<String> sheets = rows.stream()
                    .map(r -> String.valueOf(r.get("sheetName")))
                    .collect(Collectors.toSet());
            assertThat(sheets).contains("ICU病房");

            Map<String, Object> wetHumidifier = rows.stream()
                    .filter(r -> "湿化瓶-1/Z3032".equals(r.get("packName")))
                    .findFirst()
                    .orElseThrow();
            assertThat(wetHumidifier.get("sheetName")).isEqualTo("ICU病房");
            assertThat(String.valueOf(wetHumidifier.get("packageMaterial"))).contains("高温纸塑袋");
            assertThat(wetHumidifier.get("instrumentCount")).isEqualTo(2);

            assertThat(rows.stream().anyMatch(r -> "呼吸机湿化瓶-1/Z3032".equals(r.get("packName"))
                    && Integer.valueOf(2).equals(r.get("instrumentCount")))).isTrue();
            long rowsWithMaterial = rows.stream()
                    .filter(r -> r.get("packageMaterial") != null
                            && !String.valueOf(r.get("packageMaterial")).isBlank())
                    .count();
            assertThat(rowsWithMaterial).isGreaterThan(100);
            assertThat(rows.stream().filter(r -> "ICU病房".equals(r.get("sheetName")))
                    .allMatch(r -> r.get("instrumentCount") != null
                            && ((Number) r.get("instrumentCount")).intValue() > 0))
                    .isTrue();
        }
    }

    @Test
    void extractsHospitalFromRedCrossMergedFixtureWhenOnlyColumnA() throws Exception {
        Path file = Path.of("../测试用例/待匹配/处理后表格/5月__红十字5月账单.xlsx");
        if (!Files.exists(file)) {
            file = Path.of("测试用例/待匹配/处理后表格/5月__红十字5月账单.xlsx");
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(file), "fixture missing");
        byte[] bytes = Files.readAllBytes(file);
        List<String> names = ExcelBillImportSupport.extractHospitalDisplayNames(bytes);
        assertThat(names.stream().anyMatch(n -> n.contains("红十字妇产医院"))).isTrue();
    }
}
