package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电力等客户的月度附表：从 clerk 附件模板复制 sheet 追加到账单导出 workbook。
 */
@Component
public class ClerkMonthlySupplementReportGenerator {

    private static final String ATTACHMENT_PREFIX = "clerk-rules/attachments/";

    public boolean hasSupplementReports(JsonNode compiledClerk) {
        if (compiledClerk == null) {
            return false;
        }
        JsonNode refs = compiledClerk.path("attachmentRefs");
        if (refs.isArray()) {
            for (JsonNode ref : refs) {
                String path = ref.asText("");
                if (isSupplementWorkbook(path)) {
                    return true;
                }
            }
        }
        JsonNode rules = compiledClerk.path("clerkRules");
        if (rules.isArray()) {
            for (JsonNode rule : rules) {
                if ("MONTHLY_SUPPLEMENT_REPORT".equals(rule.path("ruleType").asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    public byte[] appendSupplementSheets(byte[] billWorkbook, JsonNode compiledClerk) {
        if (billWorkbook == null || billWorkbook.length == 0 || compiledClerk == null) {
            return billWorkbook;
        }
        List<String> supplementPaths = resolveSupplementPaths(compiledClerk);
        if (supplementPaths.isEmpty()) {
            return billWorkbook;
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(billWorkbook);
             XSSFWorkbook target = new XSSFWorkbook(bis)) {
            for (String path : supplementPaths) {
                appendFromTemplate(target, path);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            target.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            return billWorkbook;
        }
    }

    public List<Map<String, Object>> previewAttachment(String attachmentPath) {
        if (!isSupplementWorkbook(attachmentPath)) {
            return List.of();
        }
        try (Workbook workbook = openWorkbook(attachmentPath)) {
            List<Map<String, Object>> sheets = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("sheetName", sheet.getSheetName());
                info.put("rowCount", sheet.getPhysicalNumberOfRows());
                info.put("previewRows", readPreviewRows(sheet, 5));
                sheets.add(info);
            }
            return sheets;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> resolveSupplementPaths(JsonNode compiledClerk) {
        List<String> paths = new ArrayList<>();
        JsonNode refs = compiledClerk.path("attachmentRefs");
        if (refs.isArray()) {
            for (JsonNode ref : refs) {
                String path = ref.asText("");
                if (isSupplementWorkbook(path)) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    private void appendFromTemplate(XSSFWorkbook target, String attachmentPath) throws Exception {
        try (Workbook source = openWorkbook(attachmentPath)) {
            for (int i = 0; i < source.getNumberOfSheets(); i++) {
                Sheet sourceSheet = source.getSheetAt(i);
                String sheetName = uniqueSheetName(target, sourceSheet.getSheetName());
                Sheet targetSheet = target.createSheet(sheetName);
                copySheet(sourceSheet, targetSheet);
            }
        }
    }

    private static Workbook openWorkbook(String attachmentPath) throws Exception {
        String classpath = attachmentPath.startsWith("attachments/")
                ? ATTACHMENT_PREFIX + attachmentPath.substring("attachments/".length())
                : ATTACHMENT_PREFIX + attachmentPath;
        ClassPathResource resource = new ClassPathResource(classpath);
        return new XSSFWorkbook(resource.getInputStream());
    }

    private static boolean isSupplementWorkbook(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String lower = path.toLowerCase();
        return lower.endsWith(".xlsx") && (lower.contains("器械把数") || lower.contains("包装"));
    }

    private static String uniqueSheetName(XSSFWorkbook target, String baseName) {
        String name = baseName != null && !baseName.isBlank() ? baseName : "附表";
        if (target.getSheet(name) == null) {
            return name;
        }
        int suffix = 2;
        while (target.getSheet(name + "_" + suffix) != null) {
            suffix++;
        }
        return name + "_" + suffix;
    }

    private static void copySheet(Sheet source, Sheet target) {
        for (int r = 0; r <= source.getLastRowNum(); r++) {
            Row sourceRow = source.getRow(r);
            if (sourceRow == null) {
                continue;
            }
            Row targetRow = target.createRow(r);
            for (int c = 0; c < sourceRow.getLastCellNum(); c++) {
                if (sourceRow.getCell(c) == null) {
                    continue;
                }
                targetRow.createCell(c).setCellValue(sourceRow.getCell(c).toString());
            }
        }
    }

    private static List<List<String>> readPreviewRows(Sheet sheet, int maxRows) {
        List<List<String>> rows = new ArrayList<>();
        int limit = Math.min(maxRows, sheet.getLastRowNum() + 1);
        for (int r = 0; r < limit; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                cells.add(row.getCell(c) != null ? row.getCell(c).toString() : "");
            }
            rows.add(cells);
        }
        return rows;
    }
}
