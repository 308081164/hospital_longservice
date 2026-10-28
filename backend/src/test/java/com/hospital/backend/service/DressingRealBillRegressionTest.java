package com.hospital.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实账单回归（2026-10-28）：敷料包不再「保留睿思原价」。
 *
 * 用例数据来自本地真实对账明细（hospital_reconciliation_row，原 pricing_rule = 敷料包保留睿思原价），
 * 直接走 ReconciliationPricingOrchestrator.processRow，验证新代码下：
 * 1) 不再返回 preserve / 「保留睿思原价」；
 * 2) 每行都能给出非空期望单价。
 */
class DressingRealBillRegressionTest {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    private List<Map<String, Object>> loadRows() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/dressing-real-bills.json")) {
            assertThat(in).as("dressing-real-bills.json 缺失").isNotNull();
            return MAPPER.readValue(in, new TypeReference<List<Map<String, Object>>>() {});
        }
    }

    @Test
    void realDressingRowsNoLongerKeepOriginalPrice() throws Exception {
        List<Map<String, Object>> rows = loadRows();
        assertThat(rows).isNotEmpty();

        ReconciliationPricingOrchestrator orchestrator = new ReconciliationPricingOrchestrator(
                new ClerkBillPriceRuleApplier(),
                new com.hospital.backend.export.ExportStageDiscountApplier());
        PricingEngine engine = new PricingEngine(PricingEngineTestSupport.defaultRules());

        int checked = 0;
        int changed = 0;
        double totalDelta = 0;
        for (Map<String, Object> row : rows) {
            var result = orchestrator.processRow(row, engine, null, null, Set.of());
            Object bill = row.get("unitPrice");
            double billPrice = bill instanceof Number n ? n.doubleValue() : 0;
            if (result.expectedUnitPrice != null && Math.abs(result.expectedUnitPrice - billPrice) > 0.005) {
                changed++;
                totalDelta += result.expectedUnitPrice - billPrice;
            }
            assertThat(result.pricingPath).as("不应再走 preserve: " + row.get("packName")).isNotEqualTo("preserve");
            assertThat(result.pricingRule).as("不应再命中保留睿思原价: " + row.get("packName"))
                    .doesNotContain("保留睿思原价");
            assertThat(result.expectedUnitPrice).as("期望单价不应为空: " + row.get("packName")).isNotNull();
            checked++;
        }
        assertThat(checked).isEqualTo(rows.size());
        System.out.printf("REAL-BILL DRESSING: rows=%d changed=%d totalDelta=%.2f%n", checked, changed, totalDelta);
    }
}
