package com.hospital.backend.service.impl;

import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.dto.request.hospital.BillSheetMeta;
import com.hospital.backend.export.BillColumnLayout;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 附一 11 列导出布局回归：程序化模板 + writeSheetFromTemplate 表头一致性。
 */
class BillExportFuyiLayoutTest {

    @Test
    void programmaticFuyiTemplateHasFullHeadersOnRow9() throws Exception {
        try (XSSFWorkbook workbook = HospitalReconciliationServiceImpl.createProgrammaticBillTemplateWorkbook(
                BillColumnLayout.FUYI_EXTENDED_11COL)) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(8);
            assertThat(headerRow.getCell(8).getStringCellValue()).isEqualTo("包数");
            assertThat(headerRow.getCell(9).getStringCellValue()).isEqualTo("包装材料");
            assertThat(headerRow.getCell(10).getStringCellValue()).isEqualTo("单包内器械数量/把");
            assertThat(headerRow.getCell(11).getStringCellValue()).isEqualTo("单价（把）");
            assertThat(headerRow.getCell(12).getStringCellValue()).isEqualTo("单价");
            assertThat(headerRow.getCell(13).getStringCellValue()).isEqualTo("总价");
        }
    }

    @Test
    void writeSheetFromTemplateKeepsFuyiHeadersAcrossTwoSheets() throws Exception {
        HospitalReconciliationServiceImpl service = newServiceWithNullDependencies();
        Method writeSheet = HospitalReconciliationServiceImpl.class.getDeclaredMethod(
                "writeSheetFromTemplate",
                XSSFWorkbook.class,
                org.apache.poi.xssf.usermodel.XSSFSheet.class,
                String.class,
                List.class,
                Map.class,
                boolean.class,
                BillColumnLayout.class);
        writeSheet.setAccessible(true);

        BillRowItem rowA = sampleRow("科室A", "测试包A", "纸塑袋 10cm (小）", 3, 52.74);
        BillRowItem rowB = sampleRow("科室B", "测试包B", "纸塑袋 15cm (中）", 2, 8.79);

        try (XSSFWorkbook workbook = HospitalReconciliationServiceImpl.createProgrammaticBillTemplateWorkbook(
                BillColumnLayout.FUYI_EXTENDED_11COL)) {
            XSSFSheet master = workbook.cloneSheet(0);
            int masterIdx = workbook.getSheetIndex(master);

            writeSheet.invoke(service, workbook, workbook.getSheetAt(0), "科室A",
                    List.of(rowA), Map.of("科室A", meta("科室A")), false, BillColumnLayout.FUYI_EXTENDED_11COL);

            XSSFSheet sheetB = workbook.cloneSheet(masterIdx);
            writeSheet.invoke(service, workbook, sheetB, "科室B",
                    List.of(rowB), Map.of("科室B", meta("科室B")), false, BillColumnLayout.FUYI_EXTENDED_11COL);

            assertFuyiHeaderRow(workbook.getSheetAt(0).getRow(8));
            assertFuyiHeaderRow(sheetB.getRow(8));

            Row dataRowA = workbook.getSheetAt(0).getRow(10);
            assertThat(dataRowA.getCell(9).getStringCellValue()).isEqualTo("纸塑袋 10cm (小）");
            assertThat(dataRowA.getCell(10).getNumericCellValue()).isEqualTo(3.0);
            assertThat(dataRowA.getCell(11).getNumericCellValue()).isCloseTo(17.58, org.assertj.core.data.Offset.offset(0.01));
            assertThat(dataRowA.getCell(12).getNumericCellValue()).isEqualTo(52.74);
            assertThat(dataRowA.getCell(13).getNumericCellValue()).isEqualTo(52.74);
        }
    }

    @Test
    void writeSheetFromTemplateRestoresHeadersWhenCloneHasEmptyJkl() throws Exception {
        HospitalReconciliationServiceImpl service = newServiceWithNullDependencies();
        Method writeSheet = HospitalReconciliationServiceImpl.class.getDeclaredMethod(
                "writeSheetFromTemplate",
                XSSFWorkbook.class,
                org.apache.poi.xssf.usermodel.XSSFSheet.class,
                String.class,
                List.class,
                Map.class,
                boolean.class,
                BillColumnLayout.class);
        writeSheet.setAccessible(true);

        BillRowItem row = sampleRow("宫腔镜", "镜头-3件", "纸塑袋 10cm (小）", 3, 52.74);

        try (XSSFWorkbook workbook = HospitalReconciliationServiceImpl.createProgrammaticBillTemplateWorkbook(
                BillColumnLayout.FUYI_EXTENDED_11COL)) {
            XSSFSheet cloned = workbook.cloneSheet(0);
            Row headerRow = cloned.getRow(8);
            headerRow.getCell(9).setBlank();
            headerRow.getCell(10).setBlank();
            headerRow.getCell(11).setBlank();

            writeSheet.invoke(service, workbook, cloned, "宫腔镜",
                    List.of(row), Map.of("宫腔镜", meta("宫腔镜")), false, BillColumnLayout.FUYI_EXTENDED_11COL);

            assertFuyiHeaderRow(cloned.getRow(8));
        }
    }

    @Test
    void standardLayoutKeepsEightColumnHeaders() throws Exception {
        try (XSSFWorkbook workbook = HospitalReconciliationServiceImpl.createProgrammaticBillTemplateWorkbook(
                BillColumnLayout.STANDARD_8COL)) {
            Row headerRow = workbook.getSheetAt(0).getRow(8);
            assertThat(headerRow.getCell(8).getStringCellValue()).isEqualTo("包数");
            assertThat(headerRow.getCell(9).getStringCellValue()).isEqualTo("单价");
            assertThat(headerRow.getCell(10).getStringCellValue()).isEqualTo("总价");
        }
    }

    private static void assertFuyiHeaderRow(Row headerRow) {
        assertThat(headerRow).isNotNull();
        assertThat(headerRow.getCell(9).getStringCellValue()).isEqualTo("包装材料");
        assertThat(headerRow.getCell(10).getStringCellValue()).isEqualTo("单包内器械数量/把");
        assertThat(headerRow.getCell(11).getStringCellValue()).isEqualTo("单价（把）");
    }

    private static BillRowItem sampleRow(String sheet, String packName, String material,
                                         int instrumentCount, double unitPrice) {
        BillRowItem row = new BillRowItem();
        row.setSheetName(sheet);
        row.setPackName(packName);
        row.setPackageMaterial(material);
        row.setPackCount(1);
        row.setInstrumentCount(instrumentCount);
        row.setUnitPrice(unitPrice);
        row.setTotalPrice(unitPrice);
        row.setExpectedUnitPrice(unitPrice);
        row.setCorrectedTotalPrice(unitPrice);
        return row;
    }

    private static BillSheetMeta meta(String sheetName) {
        BillSheetMeta meta = new BillSheetMeta();
        meta.setSheetName(sheetName);
        meta.setHospitalDisplayName("黑龙江中医药大学附属第一医院");
        meta.setDateRangeText("2026/06/01-2026/06/30");
        return meta;
    }

    static HospitalReconciliationServiceImpl newServiceWithNullDependencies() throws Exception {
        var ctor = HospitalReconciliationServiceImpl.class.getConstructor(
                com.hospital.backend.mapper.HospitalReconciliationJobMapper.class,
                com.hospital.backend.mapper.HospitalReconciliationRowMapper.class,
                com.hospital.backend.mapper.HospitalReconciliationExportLogMapper.class,
                com.hospital.backend.mapper.HospitalPricingRuleMapper.class,
                com.hospital.backend.service.PricingRuleCompiler.class,
                com.hospital.backend.service.ProductMatchService.class,
                com.hospital.backend.service.CustomerResolver.class,
                com.hospital.backend.service.ReconciliationHospitalNameResolver.class,
                com.hospital.backend.service.LogisticsPipelineService.class,
                com.hospital.backend.service.SettlementJobFieldsApplier.class,
                com.hospital.backend.service.LogisticsImportService.class,
                com.hospital.backend.service.ExternalInstrumentService.class,
                com.hospital.backend.export.SheetOrchestrator.class,
                com.hospital.backend.export.BillExportLayoutResolver.class,
                com.hospital.backend.export.D8DisplayNameResolver.class,
                com.hospital.backend.export.ExportTemplateResolver.class,
                com.hospital.backend.service.HospitalExportCapabilityService.class);
        return ctor.newInstance(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
