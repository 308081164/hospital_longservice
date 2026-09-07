package com.hospital.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.service.BillingConditionEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 baseline JSON 经 import 字段映射后，应与 verify 签名一致。
 */
class RulesVerificationRoundTripTest {

    @Test
    void baselineImportRoundTripMatchesVerifySignature() throws Exception {
        List<String> mismatches = new ArrayList<>();
        for (String code : List.of("HLJ-FY-RK", "HRB-WY", "FNN-YY", "GUOYAO-2")) {
            JsonNode root = JsonUtils.getObjectMapper().readTree(
                    new ClassPathResource("billing-rules/baseline/" + code + ".json").getInputStream());
            for (JsonNode ruleNode : root.path("productRules")) {
                if (!ruleNode.path("isActive").asBoolean(true)) {
                    continue;
                }
                CustomerProductRule entity = toEntityLikeImport(ruleNode);
                Map<String, Object> expected = normalizeFromJsonLikeVerify(ruleNode);
                Map<String, Object> actual = normalizeFromEntityLikeVerify(entity);
                if (!signatureEquals(expected, actual)) {
                    mismatches.add(code + " / " + ruleNode.path("name").asText()
                            + " expected=" + expected + " actual=" + actual);
                }
            }
        }
        assertThat(mismatches).isEmpty();
    }

    private static CustomerProductRule toEntityLikeImport(JsonNode ruleNode) {
        CustomerProductRule rule = new CustomerProductRule();
        rule.setRuleType(text(ruleNode, "ruleType", "FIXED_PRICE"));
        rule.setBillingMode(textOrNull(ruleNode, "billingMode"));
        rule.setPriority(intVal(ruleNode, "priority", 100));
        rule.setPrice(ruleNode.hasNonNull("price") ? decimal(ruleNode, "price") : null);
        rule.setFee(ruleNode.hasNonNull("fee") ? decimal(ruleNode, "fee") : null);
        if (ruleNode.hasNonNull("foldRatio")) {
            rule.setFoldRatio(decimal(ruleNode, "foldRatio"));
        } else {
            rule.setFoldRatio(null);
        }
        if (ruleNode.has("threshold") && !ruleNode.get("threshold").isNull()) {
            rule.setThreshold(ruleNode.get("threshold").asInt());
        } else {
            rule.setThreshold(null);
        }
        if (ruleNode.has("keywords")) {
            rule.setKeywords(toJsonArray(ruleNode.get("keywords")));
        }
        rule.setKeywordMatchMode(textOrNull(ruleNode, "keywordMatchMode"));
        String conditionsJson = null;
        if (ruleNode.hasNonNull("conditionsJson")) {
            JsonNode node = ruleNode.get("conditionsJson");
            conditionsJson = node.isTextual() ? node.asText() : node.toString();
        }
        if (ruleNode.has("acceptedTypes")) {
            conditionsJson = BillingConditionEvaluator.mergeAcceptedTypesIntoConditions(
                    conditionsJson, ruleNode.get("acceptedTypes"));
        }
        rule.setConditionsJson(conditionsJson);
        rule.setIsActive(true);
        return rule;
    }

    private static Map<String, Object> normalizeFromJsonLikeVerify(JsonNode rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ruleType", rule.path("ruleType").asText("FIXED_PRICE"));
        map.put("price", RulesVerificationServiceImpl.effectivePriceFromJson(rule));
        map.put("keywords", sortedKeywords(rule.path("keywords")));
        map.put("priority", rule.has("priority") ? rule.get("priority").asInt(100) : 100);
        map.put("foldRatio", decimalOrNull(rule, "foldRatio"));
        map.put("threshold", rule.has("threshold") && !rule.get("threshold").isNull()
                ? rule.get("threshold").asInt() : null);
        map.put("billingMode", textOrNull(rule, "billingMode"));
        map.put("keywordMatchMode", textOrNull(rule, "keywordMatchMode"));
        map.put("conditionsJson", normalizeJson(RulesVerificationServiceImpl.resolveConditionsJsonFromJson(rule)));
        return map;
    }

    private static Map<String, Object> normalizeFromEntityLikeVerify(CustomerProductRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ruleType", rule.getRuleType());
        map.put("price", RulesVerificationServiceImpl.effectivePriceFromEntity(rule));
        map.put("keywords", parseKeywords(rule.getKeywords()));
        map.put("priority", rule.getPriority() != null ? rule.getPriority() : 100);
        map.put("foldRatio", rule.getFoldRatio());
        map.put("threshold", rule.getThreshold());
        map.put("billingMode", rule.getBillingMode());
        map.put("keywordMatchMode", rule.getKeywordMatchMode());
        map.put("conditionsJson", normalizeJson(rule.getConditionsJson()));
        return map;
    }

    private static boolean signatureEquals(Map<String, Object> a, Map<String, Object> b) {
        return Objects.equals(a.get("ruleType"), b.get("ruleType"))
                && decimalEquals(a.get("price"), b.get("price"))
                && Objects.equals(a.get("keywords"), b.get("keywords"))
                && Objects.equals(a.get("priority"), b.get("priority"))
                && decimalEquals(a.get("foldRatio"), b.get("foldRatio"))
                && Objects.equals(a.get("threshold"), b.get("threshold"))
                && Objects.equals(a.get("billingMode"), b.get("billingMode"))
                && Objects.equals(a.get("keywordMatchMode"), b.get("keywordMatchMode"))
                && Objects.equals(a.get("conditionsJson"), b.get("conditionsJson"));
    }

    private static String normalizeJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.getObjectMapper().writeValueAsString(
                    JsonUtils.getObjectMapper().readTree(raw));
        } catch (Exception e) {
            return raw.trim();
        }
    }

    private static boolean decimalEquals(Object a, Object b) {
        BigDecimal da = a == null ? null : (a instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) a).doubleValue()));
        BigDecimal db = b == null ? null : (b instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) b).doubleValue()));
        if (da == null && db == null) {
            return true;
        }
        if (da == null || db == null) {
            return false;
        }
        return da.compareTo(db) == 0;
    }

    private static List<String> sortedKeywords(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                out.add(item.asText());
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    private static List<String> parseKeywords(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = JsonUtils.getObjectMapper().readValue(
                    json,
                    JsonUtils.getObjectMapper().getTypeFactory().constructCollectionType(List.class, String.class));
            list.sort(String::compareTo);
            return list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        if (!node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asText();
    }

    private static String textOrNull(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value.isBlank() ? null : value;
    }

    private static Integer intVal(JsonNode node, String field, Integer defaultValue) {
        if (!node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asInt();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return BigDecimal.valueOf(node.get(field).asDouble());
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return BigDecimal.valueOf(node.get(field).asDouble());
    }

    private static String toJsonArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return JsonUtils.toJson(values);
    }
}
