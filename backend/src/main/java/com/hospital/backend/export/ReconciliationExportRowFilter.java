package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 导出前行过滤（D2/D4）：排除加急/结款 sheet、占位零价行（省医院）等，不影响 DB 计价结果。
 */
@Component
public class ReconciliationExportRowFilter {

    private static final Set<String> SHENG_YY_CODES = Set.of("SHENG-YY-NG", "SHENG-YY-XF");
    /** D2：加急单落在主 sheet 时，按订单号整单排除（与处理后表「加急」独立 sheet 口径一致） */
    private static final Set<String> URGENT_ORDER_FILTER_CODES = Set.of("HRB-HSZ");
    /** 附三：处理后 bill 含「外来器械」独立 sheet，export 需保留 */
    private static final Set<String> EXTERNAL_INSTRUMENT_BILL_CODES = Set.of("ZY3-DIANLI");
    private static final Set<String> SETTLEMENT_SHEET_KEYWORDS = Set.of("加急", "结款", "结款函");
    private static final Set<String> HRB_HSZ_URGENT_TWO_PACK =
            Set.of("剖宫包□", "麻杯1换药碗2弯盘1/W6050");
    private static final Set<String> HRB_HSZ_URGENT_SPLIT_PACKS =
            Set.of("剖宫包□", "麻杯1换药碗2弯盘1/W6050", "刮宫包（10件）");
    private static final Set<String> HRB_HSZ_URGENT_SPLIT_ALLOWED = Set.of(
            "剖宫包□", "麻杯1换药碗2弯盘1/W6050", "刮宫包（10件）",
            "持物钳罐-2/W6050", "弯盘1碗1/W6050");
    private static final int HRB_HSZ_URGENT_CAIGE_MIN = 8;

    public List<HospitalReconciliationRow> apply(String customerCode, List<HospitalReconciliationRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        Set<String> urgentOrderNos = collectUrgentOrderNos(customerCode, rows);
        Map<String, List<HospitalReconciliationRow>> byOrder =
                "HRB-HSZ".equals(customerCode) ? groupByOrder(rows) : Map.of();
        List<HospitalReconciliationRow> result = new ArrayList<>(rows.size());
        for (HospitalReconciliationRow row : rows) {
            if (shouldIncludeForExport(customerCode, row, urgentOrderNos, byOrder)) {
                result.add(row);
            }
        }
        return result;
    }

    boolean shouldIncludeForExport(HospitalReconciliationRow row) {
        return shouldIncludeForExport(null, row, Set.of(), Map.of());
    }

    boolean shouldIncludeForExport(String customerCode, HospitalReconciliationRow row) {
        return shouldIncludeForExport(customerCode, row, Set.of(), Map.of());
    }

    boolean shouldIncludeForExport(String customerCode, HospitalReconciliationRow row, Set<String> urgentOrderNos) {
        return shouldIncludeForExport(customerCode, row, urgentOrderNos, Map.of());
    }

    boolean shouldIncludeForExport(
            String customerCode,
            HospitalReconciliationRow row,
            Set<String> urgentOrderNos,
            Map<String, List<HospitalReconciliationRow>> byOrder) {
        if (row == null || "skipped".equalsIgnoreCase(row.getStatus())) {
            return false;
        }
        if (isExternalInstrumentBillRow(customerCode, row.getSheetName())) {
            return true;
        }
        if (Boolean.TRUE.equals(row.getIsUrgent())) {
            return false;
        }
        if (isSettlementOrUrgentSheet(row.getSheetName())) {
            return false;
        }
        if (urgentOrderNos != null && !urgentOrderNos.isEmpty()) {
            String orderNo = row.getOrderNo();
            if (orderNo != null && urgentOrderNos.contains(orderNo.trim())) {
                return false;
            }
        }
        if ("HRB-HSZ".equals(customerCode) && isHrbHszUrgentBillRow(row, byOrder)) {
            return false;
        }
        if (customerCode != null && SHENG_YY_CODES.contains(customerCode) && isShengYyRentalExportRow(row)) {
            return false;
        }
        if (customerCode == null || !SHENG_YY_CODES.contains(customerCode)) {
            return true;
        }
        String packName = row.getPackName();
        if (packName == null || packName.isBlank()) {
            return false;
        }
        Double corrected = BillExportPriceResolver.resolveTotalPrice(row);
        if (corrected != null && Math.abs(corrected) > 0.001) {
            return true;
        }
        Double rawTotal = row.getTotalPrice();
        if (rawTotal != null && Math.abs(rawTotal) > 0.001) {
            return true;
        }
        String type = row.getType() != null ? row.getType() : "";
        String normalizedType = type.replaceAll("\\s+", "");
        if (normalizedType.contains("敷料包") && normalizedType.contains("纸塑袋")) {
            return true;
        }
        return isZeroPriceOverrideRow(row);
    }

    boolean isSettlementOrUrgentSheet(String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            return false;
        }
        String normalizedSheet = sheetName.replaceAll("\\s+", "");
        for (String keyword : SETTLEMENT_SHEET_KEYWORDS) {
            if (normalizedSheet.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** D4：省医院处理后表不含「手术室（备货）」租赁器械，bill export 与之对齐。 */
    boolean isShengYyRentalExportRow(HospitalReconciliationRow row) {
        if (isBeihuoSheet(row.getSheetName())) {
            return true;
        }
        String type = row.getType();
        if (type == null || type.isBlank()) {
            return false;
        }
        return type.replaceAll("\\s+", "").contains("骨科租赁器械包");
    }

    boolean isBeihuoSheet(String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            return false;
        }
        return sheetName.replaceAll("\\s+", "").contains("备货");
    }

    Set<String> collectUrgentOrderNos(String customerCode, List<HospitalReconciliationRow> rows) {
        if (customerCode == null || !URGENT_ORDER_FILTER_CODES.contains(customerCode)) {
            return Set.of();
        }
        Set<String> urgent = new HashSet<>();
        for (HospitalReconciliationRow row : rows) {
            if (!isSettlementOrUrgentSheet(row.getSheetName())) {
                continue;
            }
            String orderNo = row.getOrderNo();
            if (orderNo != null && !orderNo.isBlank()) {
                urgent.add(orderNo.trim());
            }
        }
        return urgent;
    }

    /**
     * D2：DB 无「加急」sheet 时，按铂康处理后表口径识别加急 bill 行。
     * - 整单：仅剖宫包□+麻杯且剖宫包数≥8；
     * - 拆单：1614138 类（剖宫≥8 + 刮宫，另含持物钳/弯盘留在 bill）。
     */
    boolean isHrbHszUrgentBillRow(HospitalReconciliationRow row, Map<String, List<HospitalReconciliationRow>> byOrder) {
        if (row == null || row.getOrderNo() == null || byOrder == null || byOrder.isEmpty()) {
            return false;
        }
        List<HospitalReconciliationRow> orderRows = byOrder.get(row.getOrderNo().trim());
        if (orderRows == null || orderRows.isEmpty()) {
            return false;
        }
        Integer caigeCount = null;
        Set<String> packNames = new HashSet<>();
        for (HospitalReconciliationRow orderRow : orderRows) {
            String packName = normalizePack(orderRow.getPackName());
            if (packName.isEmpty()) {
                continue;
            }
            packNames.add(packName);
            if ("剖宫包□".equals(packName)) {
                int count = safeInt(orderRow.getPackCount());
                caigeCount = caigeCount == null ? count : Math.max(caigeCount, count);
            }
        }
        if (caigeCount == null || caigeCount < HRB_HSZ_URGENT_CAIGE_MIN) {
            return false;
        }
        String packName = normalizePack(row.getPackName());
        if (packNames.equals(HRB_HSZ_URGENT_TWO_PACK)) {
            return HRB_HSZ_URGENT_TWO_PACK.contains(packName);
        }
        if (packNames.size() <= 5
                && packNames.contains("刮宫包（10件）")
                && packNames.stream().allMatch(HRB_HSZ_URGENT_SPLIT_ALLOWED::contains)) {
            return HRB_HSZ_URGENT_SPLIT_PACKS.contains(packName);
        }
        return false;
    }

    private static Map<String, List<HospitalReconciliationRow>> groupByOrder(List<HospitalReconciliationRow> rows) {
        return rows.stream()
                .filter(row -> row.getOrderNo() != null && !row.getOrderNo().isBlank())
                .collect(Collectors.groupingBy(row -> row.getOrderNo().trim()));
    }

    private static String normalizePack(String packName) {
        return packName != null ? packName.trim() : "";
    }

    private static int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private boolean isZeroPriceOverrideRow(HospitalReconciliationRow row) {
        String rule = row.getPricingRule();
        if (rule == null) {
            return false;
        }
        String lower = rule.toLowerCase(Locale.ROOT);
        return lower.contains("0元") || lower.contains("零价") || lower.contains("仅记录");
    }

    private static boolean isExternalInstrumentBillRow(String customerCode, String sheetName) {
        return customerCode != null
                && EXTERNAL_INSTRUMENT_BILL_CODES.contains(customerCode)
                && sheetName != null
                && sheetName.contains("外来器械");
    }
}
