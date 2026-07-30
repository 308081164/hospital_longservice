package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.entity.CustomerProductRule;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 DB 规则或 seed 推断 {@link BillingMode}，保证旧规则零行为变更。
 */
public final class BillingModeInference {

    private BillingModeInference() {
    }

    public static BillingMode inferFromRule(CustomerProductRule rule) {
        BillingMode explicit = BillingMode.fromString(rule.getBillingMode());
        if (explicit != null) {
            return explicit;
        }
        return inferFromRuleTypeAndKeywords(rule.getRuleType(), parseKeywordList(rule.getKeywords()));
    }

    public static BillingMode inferFromCompiledNode(JsonNode rule) {
        BillingMode explicit = BillingMode.fromString(rule.path("billingMode").asText(null));
        if (explicit != null) {
            return explicit;
        }
        if (rule.path("pricePerInstrument").asBoolean(false)) {
            if (FixedPriceBillingCountResolver.hasKeyword(rule, "刮勺探针")) {
                return BillingMode.PACK_NAME_SUFFIX;
            }
            return BillingMode.PER_INSTRUMENT;
        }
        return BillingMode.PER_PACK;
    }

    public static BillingMode inferFromRuleTypeAndKeywords(String ruleType, List<String> keywords) {
        if ("PRICE_PER_INSTRUMENT".equals(ruleType)) {
            if (keywords != null && keywords.contains("刮勺探针")) {
                return BillingMode.PACK_NAME_SUFFIX;
            }
            return BillingMode.PER_INSTRUMENT;
        }
        return BillingMode.PER_PACK;
    }

    public static String defaultPieceCountSource(BillingMode mode) {
        return switch (mode) {
            case PACK_NAME_SUFFIX -> FixedPriceBillingCountResolver.PIECE_COUNT_SOURCE_PACK_NAME_LAST_NUMBER;
            case PER_INSTRUMENT -> FixedPriceBillingCountResolver.PIECE_COUNT_SOURCE_EFFECTIVE;
            case PER_PACK -> null;
        };
    }

    public static String inferRuleType(BillingMode mode) {
        return mode == BillingMode.PER_PACK ? "FIXED_PRICE" : "PRICE_PER_INSTRUMENT";
    }

    private static List<String> parseKeywordList(String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = com.hospital.backend.common.JsonUtils.getObjectMapper().readTree(keywordsJson);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> keywords = new ArrayList<>();
            node.forEach(item -> {
                String text = item.asText("").trim();
                if (!text.isEmpty()) {
                    keywords.add(text);
                }
            });
            return keywords;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
