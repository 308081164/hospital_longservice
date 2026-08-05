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
  void detectsInlineDepartmentMarker() {
    assertThat(ExcelBillImportSupport.isInlineDepartmentMarkerRow("ICU病房", "", "", "")).isTrue();
    assertThat(ExcelBillImportSupport.isInlineDepartmentMarkerRow("哈尔滨红十字妇产医院", "", "", "")).isFalse();
    assertThat(ExcelBillImportSupport.isInlineDepartmentMarkerRow("2026-06-03", "1608752", "额外包", "包名")).isFalse();
  }
}
