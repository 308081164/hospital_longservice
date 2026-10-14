package com.hospital.backend.service.impl;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 红十字发货单：空行不能把 D8 读成「额外包」；美容科（朝鲜医院）、生殖中心是科室。
 */
class ExcelBillImportSupportRedCrossSheetTest {

    @Test
    void packCategoryAndShiftedDetailAreNotHospitalNames() {
        assertThat(ExcelBillImportSupport.isPackCategoryName("额外包")).isTrue();
        assertThat(ExcelBillImportSupport.isPackCategoryName("额外包(ETO)")).isTrue();
        assertThat(ExcelBillImportSupport.isPackCategoryName("敷料包（无纺布）")).isTrue();
        assertThat(ExcelBillImportSupport.isAcceptableFixedCellHospitalName("额外包")).isFalse();

        assertThat(ExcelBillImportSupport.isHospitalSummaryRow("哈尔滨红十字妇产医院")).isTrue();
        assertThat(ExcelBillImportSupport.isHospitalSummaryRow("美容科（朝鲜医院）")).isFalse();
        assertThat(ExcelBillImportSupport.isHospitalSummaryRow("生殖中心")).isFalse();
        assertThat(ExcelBillImportSupport.isInlineDepartmentMarkerRow("美容科（朝鲜医院）", "", "", "")).isTrue();
        assertThat(ExcelBillImportSupport.isInlineDepartmentMarkerRow("生殖中心", "", "", "")).isTrue();
        assertThat(ExcelBillImportSupport.isInlineDepartmentMarkerRow("哈尔滨红十字妇产医院", "", "", "")).isFalse();

        List<List<Object>> shifted = new ArrayList<>();
        shifted.add(List.of("", "发货单汇总表-显示包装材料"));
        shifted.add(List.of("", "从:2026/5/1 至: 2026/10/13"));
        shifted.add(List.of());
        shifted.add(List.of());
        shifted.add(List.of("发货日期", "", "发货单号", "基本类型", "类型", "", "包名"));
        shifted.add(List.of("哈尔滨红十字妇产医院"));
        shifted.add(List.of("ICU病房"));
        shifted.add(List.of("2026-05-07", "", "1600711", "额外包", "额外包(ETO)", "", "湿化瓶-1/Z3032"));
        shifted.add(List.of("2026-05-09", "", "1601564", "额外包", "额外包(ETO)", "", "呼吸机湿化瓶-1/Z3032"));

        assertThat(ExcelBillImportSupport.resolveHospitalNameFromColumnD(shifted, 4)).isEmpty();
        assertThat(ExcelBillImportSupport.resolveHospitalNameFromColumnASummaryRows(shifted, 4))
                .isEqualTo("哈尔滨红十字妇产医院");
    }

    @Test
    void parsesRedCrossSheetWithBlankRowsAndDepartmentTitles() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("DeliveryNoteSummaryWithPackingM");
            sheet.createRow(0).createCell(1).setCellValue("发货单汇总表-显示包装材料");
            sheet.createRow(3).createCell(1).setCellValue("从:2026/5/1 至: 2026/10/13");
            sheet.createRow(7).createCell(3).setCellValue("额外包");

            Row header = sheet.createRow(11);
            String[] headers = {
                    "发货日期", "", "发货单号", "基本类型", "类型", "", "包名",
                    "", "", "包装材料", "包数", "", "", "器械数", "单价", "总价"
            };
            for (int i = 0; i < headers.length; i++) {
                if (!headers[i].isEmpty()) {
                    header.createCell(i).setCellValue(headers[i]);
                }
            }
            sheet.createRow(12).createCell(0).setCellValue("哈尔滨红十字妇产医院");
            sheet.createRow(13).createCell(0).setCellValue("ICU病房");
            fillDetail(sheet.createRow(14), "2026-05-07", "1600711", "湿化瓶-1/Z3032");
            sheet.createRow(15).createCell(0).setCellValue("美容科（朝鲜医院）");
            fillDetail(sheet.createRow(16), "2026-05-08", "1600712", "美容包-1");
            sheet.createRow(17).createCell(0).setCellValue("生殖中心");
            fillDetail(sheet.createRow(18), "2026-05-09", "1600713", "取卵针-1");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            byte[] bytes = bos.toByteArray();

            assertThat(ExcelBillImportSupport.extractHospitalDisplayNames(bytes))
                    .containsExactly("哈尔滨红十字妇产医院");

            List<Map<String, Object>> rows = ExcelBillImportSupport.parseWorkbook(new ByteArrayInputStream(bytes));
            Set<String> sheets = rows.stream()
                    .map(row -> String.valueOf(row.get("sheetName")))
                    .collect(Collectors.toSet());
            assertThat(sheets).contains("ICU病房", "美容科（朝鲜医院）", "生殖中心");
            assertThat(sheets).doesNotContain("哈尔滨红十字妇产医院", "额外包", "DeliveryNoteSummaryWithPackingM");

            Map<String, Object> wet = rows.stream()
                    .filter(row -> "湿化瓶-1/Z3032".equals(row.get("packName")))
                    .findFirst()
                    .orElseThrow();
            assertThat(wet.get("sheetName")).isEqualTo("ICU病房");
            assertThat(wet.get("type")).isEqualTo("额外包(ETO)");
            assertThat(rows.stream().filter(row -> "美容科（朝鲜医院）".equals(row.get("sheetName"))).count())
                    .isEqualTo(1);
            assertThat(rows.stream().filter(row -> "生殖中心".equals(row.get("sheetName"))).count())
                    .isEqualTo(1);
        }
    }

    private static void fillDetail(Row row, String date, String orderNo, String packName) {
        row.createCell(0).setCellValue(date);
        row.createCell(2).setCellValue(orderNo);
        row.createCell(3).setCellValue("额外包");
        row.createCell(4).setCellValue("额外包(ETO)");
        row.createCell(6).setCellValue(packName);
        row.createCell(9).setCellValue("高温纸塑袋300*320");
        row.createCell(10).setCellValue(1);
        row.createCell(13).setCellValue(1);
        row.createCell(14).setCellValue(22);
        row.createCell(15).setCellValue(22);
    }
}
