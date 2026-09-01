package com.hospital.backend.service.impl;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelBillImportSupportTest {

  @Test
  void parsesHrbHszCombinedEightColumnBill() throws Exception {
    Path file = Path.of("测试用例/待匹配/处理后表格/5月__红十字5月账单.xlsx");
    try (InputStream in = Files.newInputStream(file)) {
      List<Map<String, Object>> rows = ExcelBillImportSupport.parseWorkbook(in);
      assertThat(rows).isNotEmpty();
      Set<String> sheets = rows.stream()
          .map(r -> String.valueOf(r.get("sheetName")))
          .collect(Collectors.toSet());
      assertThat(sheets).contains("ICU病房", "产二科");
      assertThat(rows.stream().anyMatch(r -> "湿化瓶-1/Z3032".equals(r.get("packName")))).isTrue();
    }
  }

  @Test
  void parsesZuyanNgOriginalMultiSheetBill() throws Exception {
    Path file = Path.of("测试用例/祖研-黑龙江省中医医院（南岗院区）/原始表格/祖研南岗6月账单.xlsx");
    try (InputStream in = Files.newInputStream(file)) {
      List<Map<String, Object>> rows = ExcelBillImportSupport.parseWorkbook(in);
      assertThat(rows).hasSizeGreaterThan(50);
      assertThat(rows.stream().anyMatch(r -> "美容科".equals(r.get("sheetName"))
          && String.valueOf(r.get("packName")).contains("排针"))).isTrue();
    }
  }

  @Test
  void extractsHospitalDisplayNameFromHeaderAreaAfterFirstHeaderRow() throws Exception {
    Path file = Path.of("../测试用例/呼兰区红十字医院/原始表格/呼兰红十字6月账单.xlsx");
    if (!Files.exists(file)) {
      file = Path.of("测试用例/呼兰区红十字医院/原始表格/呼兰红十字6月账单.xlsx");
    }
    org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(file), "fixture missing");
    byte[] bytes = Files.readAllBytes(file);
    List<String> names = ExcelBillImportSupport.extractHospitalDisplayNames(bytes);
    assertThat(names).isNotEmpty();
    assertThat(names.stream().anyMatch(n -> n.contains("呼兰") && n.contains("红十字"))).isTrue();
  }

  @Test
  void detectsInlineDepartmentMarker() {
    assertThat(ExcelBillImportSupport.isInlineDepartmentMarkerRow("ICU病房", "", "", "")).isTrue();
    assertThat(ExcelBillImportSupport.isInlineDepartmentMarkerRow("哈尔滨红十字妇产医院", "", "", "")).isFalse();
    assertThat(ExcelBillImportSupport.isInlineDepartmentMarkerRow("2026-06-03", "1608752", "额外包", "包名")).isFalse();
  }

  /**
   * 客户反馈：账单里器械数列是公式（Excel 中正常显示数值），导入后系统显示无器械数。
   * 原因：解析时 FORMULA 类型单元格落入 default 分支被当作空串，器械数被抹为 0。
   * 期望：读取公式的缓存计算结果（与 Excel 显示一致）。
   */
  @Test
  void preservesInstrumentCountFromFormulaCells() throws Exception {
    String[] headers = {"发货日期", "发货单号", "类型", "包类别号", "包名", "包装材料", "包数", "器械数", "单价", "总价"};
    try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
      org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("手术室");
      org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        header.createCell(i).setCellValue(headers[i]);
      }
      // 行1：静态器械数=1
      org.apache.poi.ss.usermodel.Row r1 = sheet.createRow(1);
      r1.createCell(0).setCellValue("2026-08-01");
      r1.createCell(1).setCellValue("1001");
      r1.createCell(2).setCellValue("敷料包(纸塑袋)");
      r1.createCell(4).setCellValue("治疗巾/W9050");
      r1.createCell(5).setCellValue("无纺布-90×90-50g");
      r1.createCell(6).setCellValue(1);
      r1.createCell(7).setCellValue(1);
      r1.createCell(8).setCellValue(18);
      r1.createCell(9).setCellValue(18);
      // 行2：器械数为公式 =1+1（Excel 保存后缓存值为 2）
      org.apache.poi.ss.usermodel.Row r2 = sheet.createRow(2);
      r2.createCell(0).setCellValue("2026-08-01");
      r2.createCell(1).setCellValue("1002");
      r2.createCell(2).setCellValue("敷料包(纸塑袋)");
      r2.createCell(4).setCellValue("手术衣/W9050");
      r2.createCell(5).setCellValue("无纺布-90×90-50g");
      r2.createCell(6).setCellValue(2);
      r2.createCell(7).setCellFormula("1+1");
      r2.createCell(8).setCellValue(18);
      r2.createCell(9).setCellValue(36);
      // 行3：器械数为引用公式 =H2（缓存值为 1）
      org.apache.poi.ss.usermodel.Row r3 = sheet.createRow(3);
      r3.createCell(0).setCellValue("2026-08-01");
      r3.createCell(1).setCellValue("1003");
      r3.createCell(2).setCellValue("敷料包(纸塑袋)");
      r3.createCell(4).setCellValue("中单/W9050");
      r3.createCell(5).setCellValue("无纺布-90×90-50g");
      r3.createCell(6).setCellValue(1);
      r3.createCell(7).setCellFormula("H2");
      r3.createCell(8).setCellValue(18);
      r3.createCell(9).setCellValue(18);
      // 计算公式缓存值（模拟 Excel 保存后的文件形态）
      wb.getCreationHelper().createFormulaEvaluator().evaluateAll();

      java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
      wb.write(bos);
      List<Map<String, Object>> rows = ExcelBillImportSupport.parseWorkbook(
          new java.io.ByteArrayInputStream(bos.toByteArray()));

      assertThat(rows).hasSize(3);
      assertThat(rows.get(0).get("instrumentCount")).isEqualTo(1);
      assertThat(rows.get(1).get("instrumentCount")).isEqualTo(2);
      assertThat(rows.get(2).get("instrumentCount")).isEqualTo(1);
    }
  }
}
