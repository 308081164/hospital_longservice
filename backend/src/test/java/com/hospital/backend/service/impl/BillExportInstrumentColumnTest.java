package com.hospital.backend.service.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.dto.request.hospital.BillSheetMeta;
import com.hospital.backend.export.BillColumnLayout;
import com.hospital.backend.service.ClerkExportLayoutMerger;
import com.hospital.backend.service.ClerkRuleCompiler;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 电力医院账单导出保留器械数列：包数与单价之间。
 */
class BillExportInstrumentColumnTest {

    @Test
    void dianliKeepRuleSelectsInstrumentLayoutAndOtherHospitalsStayStandard() throws Exception {
        ClerkRuleCompiler compiler = new ClerkRuleCompiler(new ClerkRuleIndex());
        ObjectNode dianli = compiler.compileForCustomer("ZY3-DIANLI");
        assertThat(layoutFromCompiled(dianli)).isEqualTo(BillColumnLayout.STANDARD_WITH_INSTRUMENT);

        ObjectNode huaxia = compiler.compileForCustomer("HUAXIA-EYE");
        assertThat(layoutFromCompiled(huaxia)).isEqualTo(BillColumnLayout.STANDARD_8COL);
    }

    @Test
    void programmaticSheetWritesInstrumentCountAndKeepsColumnVisible() throws Exception {
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

        BillRowItem row = new BillRowItem();
        row.setSheetName("口腔科");
        row.setPackName("拔牙钳-1/z1029");
        row.setType("额外包(纸塑袋)");
        row.setPackCount(9);
        row.setInstrumentCount(9);
        row.setUnitPrice(8.0);
        row.setTotalPrice(72.0);
        row.setExpectedUnitPrice(8.0);
        row.setCorrectedTotalPrice(72.0);

        BillSheetMeta meta = new BillSheetMeta();
        meta.setSheetName("口腔科");
        meta.setHospitalDisplayName("黑龙江省中医药大学附属第三医院（电力）");

        BillColumnLayout layout = BillColumnLayout.STANDARD_WITH_INSTRUMENT;
        try (XSSFWorkbook workbook = HospitalReconciliationServiceImpl.createProgrammaticBillTemplateWorkbook(layout)) {
            writeSheet.invoke(service, workbook, workbook.getSheetAt(0), "口腔科",
                    List.of(row), Map.of("口腔科", meta), true, layout);

            XSSFSheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(8);
            assertThat(header.getCell(8).getStringCellValue()).isEqualTo("包数");
            assertThat(header.getCell(9).getStringCellValue()).isEqualTo("器械数");
            assertThat(header.getCell(10).getStringCellValue()).isEqualTo("单价");
            assertThat(header.getCell(11).getStringCellValue()).isEqualTo("总价");
            assertThat(sheet.isColumnHidden(9)).isFalse();

            Row data = sheet.getRow(10);
            assertThat(data.getCell(8).getNumericCellValue()).isEqualTo(9.0);
            assertThat(data.getCell(9).getNumericCellValue()).isEqualTo(9.0);
            assertThat(data.getCell(10).getNumericCellValue()).isEqualTo(8.0);
            assertThat(data.getCell(11).getNumericCellValue()).isEqualTo(72.0);
        }
    }

    private static BillColumnLayout layoutFromCompiled(ObjectNode compiled) {
        BillColumnLayout layout = BillColumnLayout.STANDARD_8COL;
        for (var node : compiled.path("exportLayouts")) {
            layout = ClerkExportLayoutMerger.layoutForKeepColumns(layout, node.path("params").path("keepColumns"));
        }
        return layout;
    }
}
