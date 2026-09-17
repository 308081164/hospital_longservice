package com.hospital.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 读取 classpath clerk-rules/baseline/{CODE}.json 与 index.json（内勤规则期望态）。
 */
@Slf4j
@Component
public class ClerkRuleIndex {

    private static final String INDEX_FILE = "clerk-rules/index.json";
    private static final String BASELINE_DIR = "clerk-rules/baseline/";

    private volatile String baselineHash = "";
    private volatile List<String> customerCodes = List.of();
    private volatile Map<String, JsonNode> baselineByCode = Map.of();

    public ClerkRuleIndex() {
        reload();
    }

    public void reload() {
        try {
            JsonNode index = readJson(INDEX_FILE);
            baselineHash = index.hasNonNull("baseline_hash") ? index.get("baseline_hash").asText() : "";
            Map<String, JsonNode> loaded = new ConcurrentHashMap<>();
            Set<String> codes = new HashSet<>();
            JsonNode customers = index.path("customers");
            if (customers.isArray()) {
                for (JsonNode entry : customers) {
                    String code = entry.path("code").asText(null);
                    if (code == null || code.isBlank()) {
                        continue;
                    }
                    codes.add(code);
                    String file = entry.path("file").asText(BASELINE_DIR + code + ".json");
                    String classpath = file.startsWith("clerk-rules/") ? file : BASELINE_DIR + code + ".json";
                    loaded.put(code, readJson(classpath));
                }
            }
            customerCodes = List.copyOf(codes);
            baselineByCode = loaded;
        } catch (Exception e) {
            log.warn("Failed to load clerk-rules baseline index: {}", e.getMessage());
            baselineHash = "";
            customerCodes = List.of();
            baselineByCode = Map.of();
        }
    }

    public String baselineHash() {
        return baselineHash;
    }

    public List<String> customerCodes() {
        return customerCodes;
    }

    public JsonNode baselineForCustomer(String customerCode) {
        if (customerCode == null) {
            return null;
        }
        return baselineByCode.get(customerCode);
    }

    public List<JsonNode> listBaselines() {
        List<JsonNode> list = new ArrayList<>();
        for (String code : customerCodes) {
            JsonNode baseline = baselineByCode.get(code);
            if (baseline != null) {
                list.add(baseline);
            }
        }
        return list;
    }

    private static JsonNode readJson(String classpath) throws Exception {
        ClassPathResource resource = new ClassPathResource(classpath);
        try (InputStream in = resource.getInputStream()) {
            return JsonUtils.getObjectMapper().readTree(in);
        }
    }
}
