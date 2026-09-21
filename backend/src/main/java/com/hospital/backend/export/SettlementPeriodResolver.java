package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.service.impl.ExcelBillImportSupport;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Optional;

/**
 * 结款函账期解析：优先 job.sourceDateRange，回退原始 Excel B4/表头。
 */
public final class SettlementPeriodResolver {

    private SettlementPeriodResolver() {
    }

    public static Optional<SettlementPeriodFormatter.BillingPeriod> resolve(HospitalReconciliationJob job) {
        if (job == null) {
            return Optional.empty();
        }
        Optional<SettlementPeriodFormatter.BillingPeriod> fromJob =
                SettlementPeriodFormatter.parse(job.getSourceDateRange());
        if (fromJob.isPresent()) {
            return fromJob;
        }
        return resolveFromSourceFile(job.getSourceFilePath());
    }

    public static Optional<String> resolveDateRangeText(HospitalReconciliationJob job) {
        if (job == null) {
            return Optional.empty();
        }
        if (job.getSourceDateRange() != null && !job.getSourceDateRange().isBlank()) {
            return Optional.of(job.getSourceDateRange().trim());
        }
        return readDateRangeTextFromSourceFile(job.getSourceFilePath());
    }

    public static Optional<SettlementPeriodFormatter.BillingPeriod> resolveFromSourceFile(String sourceFilePath) {
        return readDateRangeTextFromSourceFile(sourceFilePath)
                .flatMap(SettlementPeriodFormatter::parse);
    }

    public static String extractDateRangeFromHeaderTexts(List<String> headerAreaTexts) {
        if (headerAreaTexts == null || headerAreaTexts.isEmpty()) {
            return "";
        }
        for (String text : headerAreaTexts) {
            if (ExcelBillImportSupport.isDateRangeText(text)) {
                return text.trim();
            }
        }
        return "";
    }

    private static Optional<String> readDateRangeTextFromSourceFile(String sourceFilePath) {
        if (sourceFilePath == null || sourceFilePath.isBlank()) {
            return Optional.empty();
        }
        File sourceFile = new File(sourceFilePath);
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            return Optional.empty();
        }
        try (FileInputStream fis = new FileInputStream(sourceFile);
             Workbook workbook = WorkbookFactory.create(fis)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String fromB4 = readCellText(sheet, 3, 1);
                if (ExcelBillImportSupport.isDateRangeText(fromB4)) {
                    return Optional.of(fromB4.trim());
                }
                for (int rowIdx = 0; rowIdx <= Math.min(8, sheet.getLastRowNum()); rowIdx++) {
                    Row row = sheet.getRow(rowIdx);
                    if (row == null) {
                        continue;
                    }
                    short lastCell = row.getLastCellNum();
                    for (int col = 0; col < lastCell; col++) {
                        Cell cell = row.getCell(col);
                        if (cell == null) {
                            continue;
                        }
                        String text = cell.toString();
                        if (ExcelBillImportSupport.isDateRangeText(text)) {
                            return Optional.of(text.trim());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static String readCellText(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return "";
        }
        return cell.toString().trim();
    }
}
