package com.hospital.backend.allocation;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AllocationConfig {

    /** Sheet names treated as operating rooms (default: contains 手术室) */
    private List<String> operatingRoomSheetPatterns = List.of("手术室");

    /** Keywords for fee adjustment rows (deducted from OR net, original kept) */
    private List<String> adjustmentKeywords = List.of("电钻", "肖", "啸");

    /** Keywords identifying low-temperature instruments */
    private List<String> lowTempKeywords = List.of("低温等离子", "老肯低温", "EO", "ETO", "低温");

    /** Department prefix rules: prefix → department name */
    private Map<String, String> departmentPrefixRules = Map.of(
            "骨四", "骨四科",
            "补一", "补一科"
    );

    /** Supply room borrow: department → pack count (hospital-provided monthly table) */
    private Map<String, Integer> supplyRoomBorrowCounts = new java.util.LinkedHashMap<>();

    public List<String> effectiveAdjustmentKeywords() {
        return adjustmentKeywords != null && !adjustmentKeywords.isEmpty()
                ? adjustmentKeywords
                : List.of("电钻", "肖", "啸");
    }

    public List<String> effectiveLowTempKeywords() {
        return lowTempKeywords != null && !lowTempKeywords.isEmpty()
                ? lowTempKeywords
                : List.of("低温等离子", "老肯低温", "EO", "ETO", "低温");
    }

    public List<String> effectiveOrPatterns() {
        if (operatingRoomSheetPatterns == null || operatingRoomSheetPatterns.isEmpty()) {
            return List.of("手术室");
        }
        return operatingRoomSheetPatterns;
    }
}
