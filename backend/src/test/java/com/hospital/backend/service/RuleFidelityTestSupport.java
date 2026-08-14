package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerProductRule;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Build compiled pricing rules from billing-rules-manifest.json without DB.
 */
public final class RuleFidelityTestSupport {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();
    private static JsonNode manifestCache;

    private RuleFidelityTestSupport() {
    }

    public static JsonNode manifest() throws Exception {
        if (manifestCache == null) {
            try (InputStream in = RuleFidelityTestSupport.class.getResourceAsStream("/billing-rules-manifest.json")) {
                if (in == null) {
                    throw new IllegalStateException("billing-rules-manifest.json missing from test resources");
                }
                manifestCache = MAPPER.readTree(in);
            }
        }
        return manifestCache;
    }

    public static JsonNode compileForCustomerCode(String customerCode) throws Exception {
        JsonNode customerNode = manifest().path("customers").path(customerCode);
        if (customerNode.isMissingNode()) {
            throw new IllegalArgumentException("Unknown customer code: " + customerCode);
        }
        ObjectNode compiled = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        ObjectNode billingProfile = compiled.putObject("billingProfile");
        String mode = customerNode.path("billingPricingMode").asText("standard");
        if (mode == null || mode.isBlank()) {
            mode = "standard";
        }
        boolean enabled;
        if (customerNode.hasNonNull("billingEnabled")) {
            enabled = customerNode.path("billingEnabled").asBoolean(false);
        } else {
            enabled = "hybrid".equalsIgnoreCase(mode)
                    || "special_only".equalsIgnoreCase(mode)
                    || customerNode.path("active_rule_count").asInt(0) > 0;
        }
        billingProfile.put("enabled", enabled);
        billingProfile.put("pricingMode", mode);
        if (!enabled) {
            return compiled;
        }
        mergeManifestRules(compiled, customerNode, customerNode.path("name").asText(customerCode));
        return compiled;
    }

    private static void mergeManifestRules(ObjectNode compiled, JsonNode customerNode, String hospitalName) {
        ObjectNode specialRules = (ObjectNode) compiled.path("specialRules");
        ArrayNode fixed = MAPPER.createArrayNode();
        ArrayNode folds = MAPPER.createArrayNode();
        ArrayNode extras = MAPPER.createArrayNode();
        ArrayNode multipliers = MAPPER.createArrayNode();
        for (JsonNode rule : customerNode.path("productRules")) {
            if (!rule.path("isActive").asBoolean(true)) {
                continue;
            }
            ObjectNode node = rule.deepCopy();
            node.putArray("hospitals").add(hospitalName);
            if ("PRICE_PER_INSTRUMENT".equals(rule.path("ruleType").asText(""))) {
                node.put("pricePerInstrument", true);
            }
            String type = rule.path("ruleType").asText("");
            switch (type) {
                case "FOLD" -> folds.add(node);
                case "EXTRA_FEE", "ADD_FEE" -> extras.add(node);
                case "MULTIPLIER" -> multipliers.add(node);
                default -> fixed.add(node);
            }
        }
        prepend(specialRules, "fixedPrices", fixed);
        prepend(specialRules, "foldRules", folds);
        prepend(specialRules, "extraFees", extras);
        prepend(specialRules, "priceMultipliers", multipliers);
    }

    private static void prepend(ObjectNode specialRules, String field, ArrayNode customerRules) {
        if (customerRules.isEmpty()) {
            return;
        }
        ArrayNode merged = MAPPER.createArrayNode();
        merged.addAll(customerRules);
        JsonNode existing = specialRules.path(field);
        if (existing.isArray()) {
            merged.addAll((ArrayNode) existing);
        }
        specialRules.set(field, merged);
    }

    public static Map<String, Object> rowFromJson(JsonNode row, String hospitalName) {
        Map<String, Object> map = new HashMap<>();
        map.put("hospitalName", hospitalName);
        map.put("department", text(row, "department", "sheet", "手术室"));
        map.put("type", inferType(row));
        map.put("packName", text(row, "packName", "", ""));
        map.put("packageMaterial", inferPackageMaterial(row));
        map.put("instrumentCount", intVal(row, "instrumentCount", 1));
        map.put("packCount", numVal(row, "packCount", 1.0));
        map.put("unitPrice", numVal(row, "unitPrice", numVal(row, "rawUnit", 0.0)));
        map.put("totalPrice", numVal(row, "totalPrice", numVal(row, "unitPrice", 0.0)));
        return map;
    }

    private static String inferType(JsonNode row) {
        String type = text(row, "type", "", "");
        if (type != null && !type.isBlank()) {
            return type;
        }
        String packName = text(row, "packName", "", "");
        if (packName.contains("环钻") || packName.contains("整形") || packName.contains("脂充")) {
            return "器械包(ZSD)";
        }
        return "";
    }

    private static String inferPackageMaterial(JsonNode row) {
        String material = text(row, "packageMaterial", "", "");
        if (material != null && !material.isBlank()) {
            return material;
        }
        String type = text(row, "type", "", "");
        if (type.contains("无纺布")) {
            return "无纺布-90×90-50g";
        }
        if (type.contains("纸塑")) {
            return type;
        }
        String packName = text(row, "packName", "", "");
        if (packName.contains("环钻") || packName.contains("整形") || packName.contains("脂充")) {
            return "无纺布-90×90-50g";
        }
        return "";
    }

    private static String text(JsonNode node, String field, String fallbackField, String defaultValue) {
        if (node.hasNonNull(field)) {
            return node.path(field).asText();
        }
        if (fallbackField != null && node.hasNonNull(fallbackField)) {
            return node.path(fallbackField).asText();
        }
        return defaultValue;
    }

    private static int intVal(JsonNode node, String field, int defaultValue) {
        if (node.has(field) && !node.path(field).isNull()) {
            return node.path(field).asInt(defaultValue);
        }
        return defaultValue;
    }

    private static double numVal(JsonNode node, String field, double defaultValue) {
        if (node.has(field) && !node.path(field).isNull()) {
            return node.path(field).asDouble(defaultValue);
        }
        return defaultValue;
    }
}
