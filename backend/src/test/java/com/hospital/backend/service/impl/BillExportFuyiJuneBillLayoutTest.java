package com.hospital.backend.service.impl;

import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.dto.request.hospital.BillSheetMeta;
import com.hospital.backend.export.BillColumnLayout;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用本地「附一6月账单.xlsx」科室列表模拟 42 sheet 导出，验收 11 列表头一致。
 */
class BillExportFuyiJuneBillLayoutTest {

    private static final Path JUNE_BILL = Path.of("..", "测试用例", "黑龙江中医药大学附属第一医院",
            "原始表格", "附一6月账单.xlsx").normalize();

    @Test
    @EnabledIf("juneBillAvailable")
    void allJuneBillSheetsHaveConsistentFuyi11ColHeaders() throws Exception {
        List<String> sheetNames = loadSheetNamesFromJuneBill();
        assertThat(sheetNames).hasSizeGreaterThanOrEqualTo(35);

        HospitalReconciliationServiceImpl service = BillExportFuyiLayoutTest.newServiceWithNullDependencies();
        Method writeSheet = HospitalReconciliationServiceImpl.class.getDeclaredMethod(
                "writeSheetFromTemplate",
                XSSFWorkbook.class,
                XSSFSheet.class,
                String.class,
                List.class,
                Map.class,
                boolean.class,
                BillColumnLayout.class);
        writeSheet.setAccessible(true);

        try (XSSFWorkbook workbook = HospitalReconciliationServiceImpl.createProgrammaticBillTemplateWorkbook(
                BillColumnLayout.FUYI_EXTENDED_11COL)) {
            XSSFSheet masterTemplate = workbook.cloneSheet(0);
            int masterIdx = workbook.getSheetIndex(masterTemplate);

            for (int i = 0; i < sheetNames.size(); i++) {
                String sheetName = sheetNames.get(i);
                XSSFSheet sheet = i == 0 ? workbook.getSheetAt(0) : workbook.cloneSheet(masterIdx);
                if (i > 0) {
                    int sheetIdx = workbook.getSheetIndex(sheet);
                    workbook.setSheetName(sheetIdx, safeSheetName(sheetName, i));
                } else {
                    workbook.setSheetName(0, safeSheetName(sheetName, i));
                }

                BillRowItem row = juneSampleRow(sheetName);
                Map<String, BillSheetMeta> metaMap = Map.of(sheetName, juneMeta(sheetName));

                writeSheet.invoke(service, workbook, sheet, sheetName,
                        List.of(row), metaMap, false, BillColumnLayout.FUYI_EXTENDED_11COL);

                assertFuyi11ColHeaderRow(sheet.getRow(8), sheetName);

                if ("宫腔镜".equals(sheetName)) {
                    Row dataRow = sheet.getRow(10);
                    assertThat(dataRow.getCell(11).getNumericCellValue()).isCloseTo(17.58, org.assertj.core.data.Offset.offset(0.01));
                    assertThat(dataRow.getCell(12).getNumericCellValue()).isEqualTo(52.74);
                    assertThat(dataRow.getCell(13).getNumericCellValue()).isEqualTo(52.74);
                }
            }

            workbook.removeSheetAt(masterIdx);
            assertThat(workbook.getNumberOfSheets()).isEqualTo(sheetNames.size());
        }
    }

    static boolean juneBillAvailable() {
        return Files.isRegularFile(JUNE_BILL);
    }

    private static List<String> loadSheetNamesFromJuneBill() throws Exception {
        try (Workbook wb = new XSSFWorkbook(Files.newInputStream(JUNE_BILL))) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                names.add(wb.getSheetName(i));
            }
            return names;
        }
    }

    private static void assertFuyi11ColHeaderRow(Row headerRow, String sheetName) {
        assertThat(headerRow).as("sheet %s row9", sheetName).isNotNull();
        assertThat(headerRow.getCell(8).getStringCellValue()).isEqualTo("包数");
        assertThat(headerRow.getCell(9).getStringCellValue()).isEqualTo("包装材料");
        assertThat(headerRow.getCell(10).getStringCellValue()).isEqualTo("单包内器械数量/把");
        assertThat(headerRow.getCell(11).getStringCellValue()).isEqualTo("单价（把）");
        assertThat(headerRow.getCell(12).getStringCellValue()).isEqualTo("单价");
        assertThat(headerRow.getCell(13).getStringCellValue()).isEqualTo("总价");
    }

    private static BillRowItem juneSampleRow(String sheetName) {
        BillRowItem row = new BillRowItem();
        row.setSheetName(sheetName);
        row.setPackName("宫腔镜".equals(sheetName) ? "镜头-3件(盒1)/Z2060" : "测试包");
        row.setPackageMaterial("纸塑袋 10cm (小）");
        row.setPackCount(1);
        row.setInstrumentCount(3);
        double unit = "宫腔镜".equals(sheetName) ? 52.74 : 28.0;
        row.setUnitPrice(unit);
        row.setTotalPrice(unit);
        row.setExpectedUnitPrice(unit);
        row.setCorrectedTotalPrice(unit);
        return row;
    }

    private static BillSheetMeta juneMeta(String sheetName) {
        BillSheetMeta meta = new BillSheetMeta();
        meta.setSheetName(sheetName);
        meta.setHospitalDisplayName("黑龙江中医药大学附属第一医院");
        meta.setDateRangeText("2026/06/01-2026/06/30");
        return meta;
    }

    private static String safeSheetName(String name, int index) {
        if (name == null || name.isBlank()) {
            return "Sheet" + (index + 1);
        }
        return name.length() > 31 ? name.substring(0, 31) : name;
    }
}
