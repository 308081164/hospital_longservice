package com.hospital.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 读取 classpath manifest，提供各客户已登记规则名集合（供 UI 警示「未录种子」）。
 */
@Slf4j
@Component
public class ManifestRuleIndex {

    private static final String MANIFEST_FILE = "billing-seeds/billing-rules-manifest.json";

    private volatile Map<String, Set<String>> ruleNamesByCustomerCode = Map.of();

    public ManifestRuleIndex() {
        reload();
    }

    public void reload() {
        try (InputStream in = new ClassPathResource(MANIFEST_FILE).getInputStream()) {
            JsonNode root = JsonUtils.getObjectMapper().readTree(in);
            JsonNode customers = root.path("customers");
            if (!customers.isObject()) {
                ruleNamesByCustomerCode = Map.of();
                return;
            }
            Map<String, Set<String>> index = new ConcurrentHashMap<>();
            customers.fields().forEachRemaining(entry -> {
                String code = entry.getKey();
                Set<String> names = new HashSet<>();
                JsonNode rules = entry.getValue().path("productRules");
                if (rules.isArray()) {
                    for (JsonNode rule : rules) {
                        if (rule.hasNonNull("name")) {
                            names.add(rule.get("name").asText());
                        }
                    }
                }
                index.put(code, names);
            });
            ruleNamesByCustomerCode = index;
        } catch (Exception e) {
            log.warn("Failed to load manifest rule index: {}", e.getMessage());
            ruleNamesByCustomerCode = Map.of();
        }
    }

    public boolean isRegisteredInManifest(String customerCode, String ruleName) {
        if (customerCode == null || ruleName == null || ruleName.isBlank()) {
            return false;
        }
        Set<String> names = ruleNamesByCustomerCode.get(customerCode);
        return names != null && names.contains(ruleName);
    }

    public Set<String> ruleNamesForCustomer(String customerCode) {
        Set<String> names = ruleNamesByCustomerCode.get(customerCode);
        return names == null ? Set.of() : Collections.unmodifiableSet(names);
    }
}
