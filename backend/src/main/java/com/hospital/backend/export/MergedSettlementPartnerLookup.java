package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.mapper.HospitalReconciliationJobMapper;
import com.hospital.backend.mapper.HospitalReconciliationRowMapper;
import com.hospital.backend.service.BillingMonthResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 合并结款函需要对方医院同一账期的灭菌费。找不到同月任务时对方金额为 0，不借用别的月份。
 */
@Component
@RequiredArgsConstructor
public class MergedSettlementPartnerLookup {

    private final HospitalReconciliationJobMapper jobMapper;
    private final HospitalReconciliationRowMapper rowMapper;
    private final ClerkRuleIndex clerkRuleIndex;
    private final SettlementTemplateFiller settlementTemplateFiller;

    public double partnerSterilize(HospitalReconciliationJob job, JsonNode compiled) {
        if (job == null || !ClerkSheetStyle.isMerged(compiled)) {
            return 0;
        }
        String partnerCode = compiled.path("mergedSettlement").path("mergeWith").asText("");
        if (partnerCode.isBlank()) {
            return 0;
        }
        HospitalReconciliationJob partner = findSameMonth(job, partnerCode);
        if (partner == null || partner.getId() == null) {
            return 0;
        }
        List<HospitalReconciliationRow> rows =
                rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(partner.getId());
        return settlementTemplateFiller.resolveSterilizeInputTotal(partner, null, rows);
    }

    private HospitalReconciliationJob findSameMonth(HospitalReconciliationJob job, String partnerCode) {
        String month = BillingMonthResolver.resolve(job);
        JsonNode baseline = clerkRuleIndex.baselineForCustomer(partnerCode);
        String canonical = baseline == null ? "" : baseline.path("customerName").asText("");
        HospitalReconciliationJob found = firstSameMonth(
                canonical.isBlank() ? List.of() : jobMapper.selectByHospitalNameOrderByCreatedAtDesc(canonical),
                job, month, partnerCode);
        if (found != null) {
            return found;
        }
        String token = switch (partnerCode) {
            case "SANFU-SQ" -> "三辅";
            case "XIANGFANG-ZY" -> "香坊";
            default -> canonical;
        };
        if (token == null || token.isBlank()) {
            return null;
        }
        return firstSameMonth(
                jobMapper.selectRecentByHospitalNameContaining(token),
                job, month, partnerCode);
    }

    private HospitalReconciliationJob firstSameMonth(
            List<HospitalReconciliationJob> candidates,
            HospitalReconciliationJob current,
            String month,
            String partnerCode) {
        if (candidates == null) {
            return null;
        }
        for (HospitalReconciliationJob candidate : candidates) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            if (current.getId() != null && current.getId().equals(candidate.getId())) {
                continue;
            }
            if (!matchesPartner(partnerCode, candidate.getHospitalName())) {
                continue;
            }
            if (month != null && !month.equals(BillingMonthResolver.resolve(candidate))) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    static boolean matchesPartner(String partnerCode, String hospitalName) {
        if (hospitalName == null || hospitalName.isBlank()) {
            return false;
        }
        String name = hospitalName.trim();
        return switch (partnerCode) {
            case "SANFU-SQ" -> name.contains("三辅") && !name.contains("祖研");
            case "XIANGFANG-ZY" -> name.contains("香坊") && name.contains("中医")
                    && !name.contains("黑龙江省医院");
            default -> false;
        };
    }
}
