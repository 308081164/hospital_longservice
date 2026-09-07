package com.hospital.backend.service;

import com.hospital.backend.entity.CustomerProductRule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 元测试：PricingEngineTestSupport.toProductRules 必须映射 CustomerProductRule 全部业务字段。
 */
class PricingEngineTestSupportFidelityTest {

    private static final Set<String> MAPPED_FIELDS = Set.of(
            "id", "customerId", "ruleType", "name", "priority", "price", "fee", "multiplier",
            "threshold", "foldRatio", "minInstrumentCount", "maxInstrumentCount", "temperature",
            "matchMode", "keywordMatchMode", "billingMode", "pieceCountSource", "extraCount",
            "originalUnitPrice", "skipPackaging", "skipDiscount", "minBagSizeInclusive",
            "maxBagSizeExclusive", "keywords", "excludeKeywords", "conditionsJson", "acceptedPrices",
            "materials", "bagSizeEquals", "productId", "variantId", "isActive", "createdAt", "updatedAt");

    @Test
    void supportMapsAllBusinessFields() {
        Set<String> entityFields = Arrays.stream(CustomerProductRule.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        Set<String> missing = entityFields.stream()
                .filter(f -> !MAPPED_FIELDS.contains(f))
                .collect(Collectors.toSet());
        assertTrue(
                missing.isEmpty(),
                "PricingEngineTestSupport 未映射字段: " + missing + "；请更新 toProductRules 与 MAPPED_FIELDS");
    }
}
