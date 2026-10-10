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

    /**
     * 账单文件名短称。Excel 全称未命中客户档案时，仍要落到对应内勤折扣。
     * 只做整词相等，避免「省医院」误绑到省二院。
     */
    private static final Map<String, String> FILE_NAME_ALIASES = Map.of(
            "省二院南岗", "ERYY-NG",
            "省二南岗", "ERYY-NG",
            "省二院松北", "ERYY-SB",
            "省二松北", "ERYY-SB",
            "省医院南岗", "SHENG-YY-NG",
            "省医院香坊", "SHENG-YY-XF");

    /**
     * 当 {@link com.hospital.backend.service.CustomerResolver} 未命中时，用内勤 baseline 的 customerName 回退解析。
     * 「南岗区 / 松北区」与规则里的「南岗院区 / 松北院区」视为同一家。
     */
    public String resolveCustomerCodeByHospitalName(String hospitalName) {
        if (hospitalName == null || hospitalName.isBlank()) {
            return null;
        }
        String trimmed = hospitalName.trim();
        String aliased = FILE_NAME_ALIASES.get(trimmed);
        if (aliased != null && baselineByCode.containsKey(aliased)) {
            return aliased;
        }
        String normalized = normalizeCampusLabel(trimmed);
        for (String code : customerCodes) {
            JsonNode baseline = baselineByCode.get(code);
            if (baseline == null) {
                continue;
            }
            String canonical = baseline.path("customerName").asText("").trim();
            if (canonical.isEmpty()) {
                continue;
            }
            if (trimmed.equals(canonical) || normalized.equals(normalizeCampusLabel(canonical))) {
                return code;
            }
        }
        return null;
    }

    /** 账单表头写作「南岗区 / 松北区」，内勤档案写作「南岗院区 / 松北院区」。 */
    public static String normalizeCampusLabel(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.trim()
                .replace("（南岗区）", "（南岗院区）")
                .replace("(南岗区)", "（南岗院区）")
                .replace("（松北区）", "（松北院区）")
                .replace("(松北区)", "（松北院区）");
    }

    /** 客户档案若仍写作「南岗区 / 松北区」，用表头「院区」也能命中。 */
    public static String denormalizeCampusLabel(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.trim()
                .replace("（南岗院区）", "（南岗区）")
                .replace("（松北院区）", "（松北区）");
    }

    /** 原名、区→院区、院区→区，去重且保持顺序。 */
    public static List<String> campusLabelVariants(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> variants = new java.util.LinkedHashSet<>();
        String trimmed = name.trim();
        variants.add(trimmed);
        String normalized = normalizeCampusLabel(trimmed);
        if (!normalized.isEmpty()) {
            variants.add(normalized);
        }
        String denormalized = denormalizeCampusLabel(trimmed);
        if (!denormalized.isEmpty()) {
            variants.add(denormalized);
        }
        return List.copyOf(variants);
    }

    /**
     * 内勤 baseline 的规范医院名。客户档案缺失时，导入任务仍写入「南岗院区」而不是账单 D9 的「南岗区」。
     */
    public String canonicalCustomerName(String hospitalName) {
        String code = resolveCustomerCodeByHospitalName(hospitalName);
        if (code == null) {
            return null;
        }
        JsonNode baseline = baselineForCustomer(code);
        if (baseline == null) {
            return null;
        }
        String name = baseline.path("customerName").asText("").trim();
        return name.isEmpty() ? null : name;
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
