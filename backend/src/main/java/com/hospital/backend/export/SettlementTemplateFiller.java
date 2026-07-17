package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.service.BillingPolicyApplier;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds settlement fee rows from job settlement fields (monthly breakdown, logistics).
 * P4-10：结款函灭菌费独立打折（settlement_only 策略，不影响 row expected）。
 */
@Slf4j
@Component
public class SettlementTemplateFiller {

    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    public List<SettlementFeeRow> buildFeeRows(HospitalReconciliationJob job, double sterilizeTotal) {
        return buildFeeRows(job, sterilizeTotal, null);
    }

    public List<SettlementFeeRow> buildFeeRows(
            HospitalReconciliationJob job,
            double sterilizeTotal,
            JsonNode compiledRules) {
        double displaySterilize = sterilizeTotal;
        String sterilizeRemark = "";

        if (compiledRules != null) {
            BillingPolicyApplier.BillDetailDiscount settlementDiscount =
                    BillingPolicyApplier.applySettlementDiscount(
                            compiledRules, "", "", "", job.getHospitalName(), sterilizeTotal);
            if (settlementDiscount != null) {
                displaySterilize = settlementDiscount.price();
                sterilizeRemark = settlementDiscount.note();
            }
        }

        List<SettlementFeeRow> rows = new ArrayList<>();
        int seq = 1;

        rows.add(SettlementFeeRow.builder()
                .sequence(seq++)
                .itemName("灭菌费")
                .amount(displaySterilize)
                .remark(sterilizeRemark)
                .build());

        if (job.getLogisticsFee() != null) {
            Map<String, Object> logisticsBreakdown = parseLogisticsBreakdown(job.getLogisticsBreakdown());
            double cardDeducted = logisticsBreakdown != null && logisticsBreakdown.get("cardDeducted") instanceof Number n
                    ? n.doubleValue() : 0;
            double payableFee = logisticsBreakdown != null && logisticsBreakdown.get("payableFee") instanceof Number p
                    ? p.doubleValue() : job.getLogisticsFee();
            rows.add(SettlementFeeRow.builder()
                    .sequence(seq++)
                    .itemName("物流费")
                    .amount(payableFee)
                    .remark(buildLogisticsRemark(job, cardDeducted))
                    .build());
            if (cardDeducted > 0) {
                rows.add(SettlementFeeRow.builder()
                        .sequence(seq++)
                        .itemName("物流卡抵扣")
                        .amount(-cardDeducted)
                        .remark("卡内扣减")
                        .build());
            }
        }

        parseUrgentBreakdown(job).ifPresent(urgent -> {
            if (Math.abs(urgent.nominalSurcharge()) >= 0.01) {
                rows.add(SettlementFeeRow.builder()
                        .sequence(rows.size() + 1)
                        .itemName("加急灭菌费")
                        .amount(urgent.nominalSurcharge())
                        .remark(String.format("%.0f%%", urgent.baseMultiplier() * 100))
                        .build());
            }
            if (Math.abs(urgent.adjustedSurcharge()) >= 0.01) {
                rows.add(SettlementFeeRow.builder()
                        .sequence(rows.size() + 1)
                        .itemName("加急灭菌费(减免后)")
                        .amount(urgent.adjustedSurcharge())
                        .remark(String.format("%.1f%%", urgent.adjustedMultiplier() * 100))
                        .build());
            }
            if (Math.abs(urgent.nominalUrgentLogisticsTotal()) >= 0.01) {
                rows.add(SettlementFeeRow.builder()
                        .sequence(rows.size() + 1)
                        .itemName("加急物流费")
                        .amount(urgent.nominalUrgentLogisticsTotal())
                        .remark(urgent.urgentTripCount() + " 趟 × "
                                + formatAmount(urgent.urgentLogisticsFeePerTrip()))
                        .build());
            }
            if (Math.abs(urgent.adjustedUrgentLogisticsTotal()) >= 0.01) {
                rows.add(SettlementFeeRow.builder()
                        .sequence(rows.size() + 1)
                        .itemName("加急物流费(减免后)")
                        .amount(urgent.adjustedUrgentLogisticsTotal())
                        .remark(String.format("%.0f%%", urgent.urgentLogisticsDiscountRate() * 100))
                        .build());
            }
        });

        parseMonthlyBreakdown(job).ifPresent(breakdown -> {
            if (breakdown.adjustment() != null && Math.abs(breakdown.adjustment()) >= 0.01) {
                String label = breakdown.adjustment() > 0 ? "低消补差" : "封顶调减";
                rows.add(SettlementFeeRow.builder()
                        .sequence(rows.size() + 1)
                        .itemName(label)
                        .amount(breakdown.adjustment())
                        .remark(Optional.ofNullable(breakdown.minCharge())
                                .map(m -> "低消 " + m).orElse(""))
                        .build());
            }
        });

        parseDeductionBreakdown(job).ifPresent(deduction -> {
            if (Math.abs(deduction.deductionAmount()) >= 0.01) {
                rows.add(SettlementFeeRow.builder()
                        .sequence(rows.size() + 1)
                        .itemName(deduction.policyName() != null ? deduction.policyName() : "设备抵扣")
                        .amount(deduction.deductionAmount())
                        .remark("月度固定减免")
                        .build());
            }
        });

        parseExternalInstrumentTotal(job).ifPresent(externalTotal -> {
            if (Math.abs(externalTotal) >= 0.01) {
                rows.add(SettlementFeeRow.builder()
                        .sequence(rows.size() + 1)
                        .itemName("外来器械")
                        .amount(externalTotal)
                        .remark("科室借调/外来器械汇总")
                        .build());
            }
        });

        resequence(rows);
        return rows;
    }

    public double computeTotalAmount(List<SettlementFeeRow> feeRows) {
        java.util.Set<String> adjustedBaseNames = feeRows.stream()
                .map(SettlementFeeRow::getItemName)
                .filter(name -> name != null && name.endsWith("(减免后)"))
                .map(name -> name.substring(0, name.length() - "(减免后)".length()))
                .collect(java.util.stream.Collectors.toSet());
        return feeRows.stream()
                .filter(row -> {
                    String name = row.getItemName();
                    if (name == null) {
                        return true;
                    }
                    return !adjustedBaseNames.contains(name);
                })
                .mapToDouble(SettlementFeeRow::getAmount)
                .sum();
    }

    private void resequence(List<SettlementFeeRow> rows) {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setSequence(i + 1);
        }
    }

    private Optional<MonthlyBreakdown> parseMonthlyBreakdown(HospitalReconciliationJob job) {
        String json = job.getMonthlyBreakdown();
        if (json == null || json.isBlank()) {
            Double adjustment = job.getSettlementAdjustment();
            if (adjustment != null && Math.abs(adjustment) >= 0.01) {
                return Optional.of(new MonthlyBreakdown(null, adjustment));
            }
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            Double adjustment = node.has("adjustment") ? node.get("adjustment").asDouble() : null;
            Double minCharge = node.has("minCharge") ? node.get("minCharge").asDouble() : null;
            if (adjustment == null) {
                adjustment = job.getSettlementAdjustment();
            }
            if (adjustment == null || Math.abs(adjustment) < 0.01) {
                return Optional.empty();
            }
            return Optional.of(new MonthlyBreakdown(minCharge, adjustment));
        } catch (Exception e) {
            log.warn("Failed to parse monthly_breakdown for job {}: {}", job.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private record MonthlyBreakdown(Double minCharge, Double adjustment) {}

    private Optional<UrgentBreakdown> parseUrgentBreakdown(HospitalReconciliationJob job) {
        String json = job.getUrgentBreakdown();
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.has("urgentRowCount") || node.path("urgentRowCount").asInt(0) <= 0) {
                return Optional.empty();
            }
            return Optional.of(new UrgentBreakdown(
                    node.path("baseMultiplier").asDouble(1.25),
                    node.path("adjustedMultiplier").asDouble(1.025),
                    node.path("nominalSurcharge").asDouble(0),
                    node.path("adjustedSurcharge").asDouble(0),
                    node.path("urgentTripCount").asInt(0),
                    node.path("urgentLogisticsFeePerTrip").asDouble(150),
                    node.path("urgentLogisticsDiscountRate").asDouble(0.9),
                    node.path("nominalUrgentLogisticsTotal").asDouble(0),
                    node.path("adjustedUrgentLogisticsTotal").asDouble(0)
            ));
        } catch (Exception e) {
            log.warn("Failed to parse urgent_breakdown for job {}: {}", job.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<DeductionBreakdown> parseDeductionBreakdown(HospitalReconciliationJob job) {
        String json = job.getDeductionBreakdown();
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            double deductionAmount = node.has("deductionAmount")
                    ? node.path("deductionAmount").asDouble()
                    : -node.path("monthlyAmount").asDouble(0);
            if (Math.abs(deductionAmount) < 0.01) {
                return Optional.empty();
            }
            return Optional.of(new DeductionBreakdown(
                    deductionAmount,
                    node.path("policyName").asText("设备抵扣")
            ));
        } catch (Exception e) {
            log.warn("Failed to parse deduction_breakdown for job {}: {}", job.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private record UrgentBreakdown(
            double baseMultiplier,
            double adjustedMultiplier,
            double nominalSurcharge,
            double adjustedSurcharge,
            int urgentTripCount,
            double urgentLogisticsFeePerTrip,
            double urgentLogisticsDiscountRate,
            double nominalUrgentLogisticsTotal,
            double adjustedUrgentLogisticsTotal
    ) {}

    private record DeductionBreakdown(double deductionAmount, String policyName) {}

    private Optional<Double> parseExternalInstrumentTotal(HospitalReconciliationJob job) {
        String json = job.getAllocationResult();
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.has("externalInstrumentTotal")) {
                return Optional.of(node.path("externalInstrumentTotal").asDouble(0));
            }
        } catch (Exception e) {
            log.warn("Failed to parse external instrument total for job {}: {}", job.getId(), e.getMessage());
        }
        return Optional.empty();
    }

    private static String formatAmount(double value) {
        return String.format("%.2f", value);
    }

    private String buildLogisticsRemark(HospitalReconciliationJob job, double cardDeducted) {
        StringBuilder remark = new StringBuilder();
        if (job.getLogisticsTripCount() != null) {
            remark.append(job.getLogisticsTripCount()).append(" 趟");
        }
        if (cardDeducted > 0) {
            if (!remark.isEmpty()) {
                remark.append("；");
            }
            remark.append("卡抵扣 ").append(cardDeducted);
        }
        return remark.toString();
    }

    private Map<String, Object> parseLogisticsBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JsonUtils.parseToMap(json);
    }

    @Getter
    @Builder
    public static class SettlementFeeRow {
        private int sequence;
        private String itemName;
        private double amount;
        private String remark;

        public void setSequence(int sequence) {
            this.sequence = sequence;
        }
    }
}
