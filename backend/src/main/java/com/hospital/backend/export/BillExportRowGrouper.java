package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 导出前行聚合（D1/D2/D4/D6/D7/D8）：合并省医院子包拆行、排除省二 kit 组件行、
 * 折叠重复 key、国药铂康 packCount 拆行等。
 */
@Component
public class BillExportRowGrouper {

    private static final Set<String> SUB_PACK_MERGE_CODES = Set.of("SHENG-YY-NG", "SHENG-YY-XF", "HRB-HSZ");
    private static final Set<String> KIT_COMPONENT_EXCLUDE_CODES = Set.of("ERYY-SB");
    /** D6/D8：DB 偶发完全重复行导致 export 总额翻倍，按业务 key 去重保留首行 */
    private static final Set<String> EXACT_DEDUPE_CODES = Set.of("ZUYAN-NG", "HRB-HIT");
    private static final Set<String> DUPLICATE_AGGREGATE_CODES = Set.of();
    static final Set<String> GUOYAO_CODES = Set.of("GUOYAO-MAIN", "GUOYAO-2", "GUOYAO-3");
    private static final Set<String> LOW_TEMP_SHEET_SPLIT_CODES = Set.of("XINFA-HSZ");

    /** 如「膝关节镜器械 1/5」「半髋器械 2/2」「上肢器械（国药科学）3/4」 */
    private static final Pattern SUB_PACK_SUFFIX = Pattern.compile("^(.*)\\s+(\\d+)/(\\d+)$");
    private static final Pattern SUB_PACK_SUFFIX_TIGHT = Pattern.compile("^(.*[^\\d/])(\\d+)/(\\d+)$");
    /** 如「微创钉棒器械  1/4（国药科学）」「半髋器械/1/2（国药科学）」 */
    private static final Pattern SUB_PACK_SUFFIX_VENDOR = Pattern.compile("^(.*?)\\s+(\\d+)/(\\d+)\\s*（[^）]+）$");
    private static final Pattern SUB_PACK_SUFFIX_SLASH_VENDOR = Pattern.compile("^(.*)/(\\d+)/(\\d+)（[^）]+）$");

    public List<HospitalReconciliationRow> apply(String customerCode, List<HospitalReconciliationRow> rows) {
        if (rows == null || rows.isEmpty() || customerCode == null) {
            return rows;
        }
        List<HospitalReconciliationRow> working = new ArrayList<>(rows);
        if (LOW_TEMP_SHEET_SPLIT_CODES.contains(customerCode)) {
            working = splitLowTempDressingSheets(working);
        }
        if (SUB_PACK_MERGE_CODES.contains(customerCode)) {
            working = mergeSubPackSuffixRows(working);
        }
        if (KIT_COMPONENT_EXCLUDE_CODES.contains(customerCode)) {
            working = excludeKitComponentRows(working);
        }
        if (EXACT_DEDUPE_CODES.contains(customerCode)) {
            working = dedupeExactDuplicateRows(working);
        }
        if (DUPLICATE_AGGREGATE_CODES.contains(customerCode)) {
            working = aggregateByOrderAndPackName(working);
        }
        return working;
    }

    List<HospitalReconciliationRow> dedupeExactDuplicateRows(List<HospitalReconciliationRow> rows) {
        Map<String, HospitalReconciliationRow> unique = new LinkedHashMap<>();
        for (HospitalReconciliationRow row : rows) {
            String packName = row.getPackName() != null ? row.getPackName().trim() : "";
            String key = groupKey(row.getOrderNo(), packName, row.getType(), row.getDeliveryDate())
                    + "|" + safeInt(row.getPackCount())
                    + "|" + BillExportPriceResolver.resolveTotalPrice(row);
            unique.putIfAbsent(key, row);
        }
        return new ArrayList<>(unique.values());
    }

    List<HospitalReconciliationRow> mergeSubPackSuffixRows(List<HospitalReconciliationRow> rows) {
        Map<String, HospitalReconciliationRow> merged = new LinkedHashMap<>();
        List<HospitalReconciliationRow> passthrough = new ArrayList<>();
        for (HospitalReconciliationRow row : rows) {
            String packName = row.getPackName() != null ? row.getPackName().trim() : "";
            SubPackMatch match = parseSubPackSuffix(packName);
            if (match == null) {
                passthrough.add(row);
                continue;
            }
            String baseName = match.baseName();
            String key = groupKey(row.getOrderNo(), baseName, row.getType(), row.getDeliveryDate());
            merged.merge(key, cloneWithPackName(row, baseName), this::mergeRows);
        }
        List<HospitalReconciliationRow> result = new ArrayList<>(passthrough.size() + merged.size());
        result.addAll(passthrough);
        result.addAll(merged.values());
        return result;
    }

    List<HospitalReconciliationRow> excludeKitComponentRows(List<HospitalReconciliationRow> rows) {
        List<HospitalReconciliationRow> result = new ArrayList<>(rows.size());
        for (HospitalReconciliationRow row : rows) {
            String packName = row.getPackName() != null ? row.getPackName().trim() : "";
            if (isEryySbKitComponentSlashRow(packName)) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 仅排除「电切镜/电切环」类裸 kit 汇总行（无 {@code -N} 序号前缀）；
     * 保留「STROZ腹腔镜-1（30度）/Z2060」「持针器-1/Z1026」「组件-1/Z3040」与「器械 1/5」。
     */
    boolean isEryySbKitComponentSlashRow(String packName) {
        if (packName == null || packName.isBlank() || !packName.contains("/")) {
            return false;
        }
        if (parseSubPackSuffix(packName) != null) {
            return false;
        }
        // 省二松北处理后表：任意「…-数字…/类别号」均为独立计费行
        if (packName.matches(".*-\\d+[^/]*/.*")) {
            return false;
        }
        int slash = packName.indexOf('/');
        if (slash <= 0 || slash >= packName.length() - 1) {
            return false;
        }
        String beforeSlash = packName.substring(0, slash).trim();
        // 裸「电切镜/电切环」汇总行（无 -N 组件序号）
        return !beforeSlash.matches(".*-\\d+.*");
    }

    List<HospitalReconciliationRow> aggregateByOrderAndPackName(List<HospitalReconciliationRow> rows) {
        Map<String, HospitalReconciliationRow> merged = new LinkedHashMap<>();
        for (HospitalReconciliationRow row : rows) {
            String packName = row.getPackName() != null ? row.getPackName().trim() : "";
            String key = groupKey(row.getOrderNo(), packName, row.getType(), row.getDeliveryDate());
            merged.merge(key, cloneRow(row), this::mergeRows);
        }
        return new ArrayList<>(merged.values());
    }

    /** D1：国药 export 前先合并重复 key，便于汽轮机核算后再按 packCount=1 拆行对齐铂康表。 */
    List<HospitalReconciliationRow> aggregateGuoyaoDuplicateRows(List<HospitalReconciliationRow> rows) {
        return aggregateByOrderAndPackName(rows);
    }

    /**
     * D1：国药铂康处理后表每行 packCount=1；export 聚合后 packCount&gt;1 的行拆成 N 行，
     * 单价不变，每行总价=单价。
     */
    List<HospitalReconciliationRow> splitGuoyaoPlatinumRows(List<HospitalReconciliationRow> rows) {
        List<HospitalReconciliationRow> result = new ArrayList<>();
        for (HospitalReconciliationRow row : rows) {
            int packCount = safeInt(row.getPackCount());
            if (packCount <= 1) {
                result.add(row);
                continue;
            }
            Double unitPrice = BillExportPriceResolver.resolveUnitPrice(row);
            int instrumentCount = safeInt(row.getInstrumentCount());
            for (int i = 0; i < packCount; i++) {
                HospitalReconciliationRow copy = cloneRow(row);
                copy.setPackCount(1);
                if (unitPrice != null) {
                    copy.setExpectedUnitPrice(unitPrice);
                    copy.setUnitPrice(unitPrice);
                    copy.setCorrectedTotalPrice(unitPrice);
                    copy.setTotalPrice(unitPrice);
                }
                if (instrumentCount > 0) {
                    int base = instrumentCount / packCount;
                    int remainder = instrumentCount % packCount;
                    copy.setInstrumentCount(base + (i < remainder ? 1 : 0));
                }
                result.add(copy);
            }
        }
        return result;
    }

    private SubPackMatch parseSubPackSuffix(String packName) {
        Matcher m = SUB_PACK_SUFFIX.matcher(packName);
        if (m.matches()) {
            return new SubPackMatch(m.group(1).trim());
        }
        m = SUB_PACK_SUFFIX_TIGHT.matcher(packName);
        if (m.matches()) {
            return new SubPackMatch(m.group(1).trim());
        }
        m = SUB_PACK_SUFFIX_VENDOR.matcher(packName);
        if (m.matches()) {
            return new SubPackMatch(m.group(1).trim() + vendorSuffix(packName));
        }
        m = SUB_PACK_SUFFIX_SLASH_VENDOR.matcher(packName);
        if (m.matches()) {
            return new SubPackMatch(m.group(1).trim() + vendorSuffix(packName));
        }
        return null;
    }

    private static String vendorSuffix(String packName) {
        int start = packName.indexOf('（');
        return start >= 0 ? packName.substring(start).trim() : "";
    }

    private static final class SubPackMatch {
        private final String baseName;

        private SubPackMatch(String baseName) {
            this.baseName = baseName;
        }

        private String baseName() {
            return baseName;
        }
    }

    private HospitalReconciliationRow mergeRows(HospitalReconciliationRow left, HospitalReconciliationRow right) {
        left.setPackCount(safeInt(left.getPackCount()) + safeInt(right.getPackCount()));
        left.setInstrumentCount(safeInt(left.getInstrumentCount()) + safeInt(right.getInstrumentCount()));
        left.setTotalPrice(sumNullable(left.getTotalPrice(), right.getTotalPrice()));
        left.setCorrectedTotalPrice(sumNullable(left.getCorrectedTotalPrice(), right.getCorrectedTotalPrice()));
        left.setExpectedUnitPrice(resolveMergedUnitPrice(left));
        return left;
    }

    private Double resolveMergedUnitPrice(HospitalReconciliationRow row) {
        Double total = BillExportPriceResolver.resolveTotalPrice(row);
        int packCount = safeInt(row.getPackCount());
        if (total != null && packCount > 0) {
            return BillExportPriceResolver.resolveUnitPrice(row);
        }
        return row.getExpectedUnitPrice() != null ? row.getExpectedUnitPrice() : row.getUnitPrice();
    }

    private static Double sumNullable(Double a, Double b) {
        if (a == null && b == null) {
            return null;
        }
        return (a != null ? a : 0.0) + (b != null ? b : 0.0);
    }

    private static int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private static String groupKey(String orderNo, String packName, String type, String deliveryDate) {
        return String.join("|",
                normalize(orderNo),
                normalize(packName),
                normalize(type),
                normalize(deliveryDate));
    }

    private static String normalize(String value) {
        return value != null ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private static HospitalReconciliationRow cloneWithPackName(HospitalReconciliationRow row, String packName) {
        HospitalReconciliationRow copy = cloneRow(row);
        copy.setPackName(packName);
        return copy;
    }

    private static HospitalReconciliationRow cloneRow(HospitalReconciliationRow row) {
        HospitalReconciliationRow copy = new HospitalReconciliationRow();
        copy.setId(row.getId());
        copy.setJobId(row.getJobId());
        copy.setSheetName(row.getSheetName());
        copy.setRowNumber(row.getRowNumber());
        copy.setDeliveryDate(row.getDeliveryDate());
        copy.setOrderNo(row.getOrderNo());
        copy.setType(row.getType());
        copy.setCategoryNo(row.getCategoryNo());
        copy.setPackName(row.getPackName());
        copy.setPackageMaterial(row.getPackageMaterial());
        copy.setPackCount(row.getPackCount());
        copy.setInstrumentCount(row.getInstrumentCount());
        copy.setUnitPrice(row.getUnitPrice());
        copy.setTotalPrice(row.getTotalPrice());
        copy.setExpectedUnitPrice(row.getExpectedUnitPrice());
        copy.setCorrectedTotalPrice(row.getCorrectedTotalPrice());
        copy.setDifference(row.getDifference());
        copy.setStatus(row.getStatus());
        copy.setPricingRule(row.getPricingRule());
        copy.setMatchedPriceOption(row.getMatchedPriceOption());
        copy.setIsUrgent(row.getIsUrgent());
        return copy;
    }

    /** 新发红十字：敷料/低温行写入「{科室}低温敷料」Sheet，与处理后表一致。 */
    List<HospitalReconciliationRow> splitLowTempDressingSheets(List<HospitalReconciliationRow> rows) {
        List<HospitalReconciliationRow> result = new ArrayList<>(rows.size());
        for (HospitalReconciliationRow row : rows) {
            if (!isLowTempDressingExportRow(row)) {
                result.add(row);
                continue;
            }
            String sheet = row.getSheetName() != null ? row.getSheetName().trim() : "";
            if (sheet.contains("低温")) {
                result.add(row);
                continue;
            }
            String dept = sheet.isBlank() ? "默认" : sheet.replaceAll("低温.*$", "").trim();
            if (dept.isBlank()) {
                dept = "默认";
            }
            HospitalReconciliationRow copy = cloneRow(row);
            copy.setSheetName(dept + "低温敷料");
            result.add(copy);
        }
        return result;
    }

    private boolean isLowTempDressingExportRow(HospitalReconciliationRow row) {
        String type = row.getType() != null ? row.getType() : "";
        String material = row.getPackageMaterial() != null ? row.getPackageMaterial() : "";
        String pack = row.getPackName() != null ? row.getPackName() : "";
        String combined = (type + material + pack).toLowerCase();
        if (combined.contains("低温") || combined.contains("等离子") || combined.contains("eto")) {
            return type.contains("敷料") || type.contains("辅料") || material.contains("敷料") || pack.contains("敷料");
        }
        return false;
    }
}
