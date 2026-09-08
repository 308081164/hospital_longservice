package com.hospital.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.config.BaselineRuleIndex;
import com.hospital.backend.dto.response.billing.RuleVerificationResult;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.entity.SysSetting;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.CustomerProductRuleMapper;
import com.hospital.backend.mapper.SysSettingMapper;
import com.hospital.backend.service.BillingConditionEvaluator;
import com.hospital.backend.service.RulesVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RulesVerificationServiceImpl implements RulesVerificationService {

    public static final String LAST_VERIFY_KEY = "billing_rules_last_verify";

    private final BaselineRuleIndex baselineRuleIndex;
    private final CustomerMapper customerMapper;
    private final CustomerProductRuleMapper productRuleMapper;
    private final SysSettingMapper sysSettingMapper;

    @Override
    public RuleVerificationResult verifyCustomer(String customerCode) {
        Customer customer = customerMapper.selectByCode(customerCode);
        if (customer == null) {
            return RuleVerificationResult.builder()
                    .ok(false)
                    .customerCode(customerCode)
                    .missing(List.of())
                    .changed(List.of("客户不存在"))
                    .extra(List.of())
                    .build();
        }
        return verifyCustomerInternal(customer);
    }

    @Override
    public RuleVerificationResult verifyCustomerId(Long customerId) {
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            return RuleVerificationResult.builder()
                    .ok(false)
                    .customerCode(null)
                    .missing(List.of())
                    .changed(List.of("客户不存在"))
                    .extra(List.of())
                    .build();
        }
        return verifyCustomerInternal(customer);
    }

    @Override
    public Map<String, Object> verifyAll() {
        List<String> failed = new ArrayList<>();
        int totalMissing = 0;
        int totalChanged = 0;
        int totalExtra = 0;
        for (String code : baselineRuleIndex.customerCodes()) {
            RuleVerificationResult result = verifyCustomer(code);
            if (!result.isOk()) {
                failed.add(code);
                totalMissing += result.getMissing().size();
                totalChanged += result.getChanged().size();
                totalExtra += result.getExtra().size();
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ok", failed.isEmpty());
        summary.put("baselineHash", baselineRuleIndex.baselineHash());
        summary.put("failedCustomers", failed);
        summary.put("totalMissing", totalMissing);
        summary.put("totalChanged", totalChanged);
        summary.put("totalExtra", totalExtra);
        summary.put("checkedAt", java.time.Instant.now().toString());
        persistLastVerifySummary(summary);
        return summary;
    }

    @Override
    public void persistLastVerifySummary(Map<String, Object> summary) {
        try {
            SysSetting row = sysSettingMapper.selectByKey(LAST_VERIFY_KEY);
            String json = JsonUtils.toJson(summary);
            if (row == null) {
                row = new SysSetting();
                row.setSettingKey(LAST_VERIFY_KEY);
                row.setSettingValue(json);
                row.setDescription("Last billing rules baseline verify summary");
                sysSettingMapper.insert(row);
            } else {
                row.setSettingValue(json);
                sysSettingMapper.updateByKey(row);
            }
        } catch (Exception ignored) {
            // non-fatal
        }
    }

    private RuleVerificationResult verifyCustomerInternal(Customer customer) {
        String code = customer.getCode();
        JsonNode baseline = baselineRuleIndex.baselineForCustomer(code);
        if (baseline == null) {
            return RuleVerificationResult.builder()
                    .ok(true)
                    .customerCode(code)
                    .missing(List.of())
                    .changed(List.of())
                    .extra(List.of())
                    .build();
        }
        Map<String, Map<String, Object>> expected = indexRules(baseline.path("productRules"));
        Map<String, Map<String, Object>> actual = indexRulesFromDb(customer.getId());

        Set<String> missing = new LinkedHashSet<>();
        Set<String> changed = new LinkedHashSet<>();
        Set<String> extra = new LinkedHashSet<>();

        for (Map.Entry<String, Map<String, Object>> entry : expected.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> exp = entry.getValue();
            Map<String, Object> act = actual.get(name);
            if (act == null) {
                if (Boolean.TRUE.equals(exp.get("isActive"))) {
                    missing.add(name);
                }
                continue;
            }
            if (!Boolean.TRUE.equals(exp.get("isActive"))) {
                continue;
            }
            if (!Boolean.TRUE.equals(act.get("isActive"))) {
                missing.add(name);
                continue;
            }
            if (!ruleSignatureEquals(exp, act)) {
                changed.add(name);
            }
        }
        for (String name : actual.keySet()) {
            if (!expected.containsKey(name) && Boolean.TRUE.equals(actual.get(name).get("isActive"))) {
                extra.add(name);
            }
        }

        boolean ok = missing.isEmpty() && changed.isEmpty() && extra.isEmpty();
        return RuleVerificationResult.builder()
                .ok(ok)
                .customerCode(code)
                .missing(List.copyOf(missing))
                .changed(List.copyOf(changed))
                .extra(List.copyOf(extra))
                .build();
    }

    private Map<String, Map<String, Object>> indexRules(JsonNode rulesNode) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (!rulesNode.isArray()) {
            return map;
        }
        for (JsonNode rule : rulesNode) {
            String name = rule.path("name").asText("").trim();
            if (name.isEmpty()) {
                continue;
            }
            map.put(name, normalizeFromJson(rule));
        }
        return map;
    }

    private Map<String, Map<String, Object>> indexRulesFromDb(Long customerId) {
        return productRuleMapper.selectByCustomerId(customerId).stream()
                .collect(Collectors.toMap(
                        CustomerProductRule::getName,
                        this::normalizeFromEntity,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private Map<String, Object> normalizeFromJson(JsonNode rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ruleType", rule.path("ruleType").asText("FIXED_PRICE"));
        map.put("price", effectivePriceFromJson(rule));
        map.put("keywords", sortedKeywords(rule.path("keywords")));
        map.put("priority", rule.has("priority") ? rule.get("priority").asInt(100) : 100);
        map.put("foldRatio", decimal(rule.path("foldRatio")));
        map.put("threshold", rule.has("threshold") && !rule.get("threshold").isNull()
                ? rule.get("threshold").asInt() : null);
        map.put("isActive", !rule.has("isActive") || rule.get("isActive").asBoolean(true));
        map.put("billingMode", textOrNull(rule, "billingMode"));
        map.put("keywordMatchMode", normalizeKeywordMatchMode(textOrNull(rule, "keywordMatchMode")));
        map.put("conditionsJson", normalizeConditionsText(resolveConditionsJsonFromJson(rule)));
        return map;
    }

    private Map<String, Object> normalizeFromEntity(CustomerProductRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ruleType", rule.getRuleType());
        map.put("price", effectivePriceFromEntity(rule));
        map.put("keywords", parseKeywordList(rule.getKeywords()));
        map.put("priority", rule.getPriority() != null ? rule.getPriority() : 100);
        map.put("foldRatio", rule.getFoldRatio());
        map.put("threshold", rule.getThreshold());
        map.put("isActive", Boolean.TRUE.equals(rule.getIsActive()));
        map.put("billingMode", rule.getBillingMode());
        map.put("keywordMatchMode", normalizeKeywordMatchMode(rule.getKeywordMatchMode()));
        map.put("conditionsJson", normalizeConditionsText(rule.getConditionsJson()));
        return map;
    }

    /** 与 BaselineRuleSyncServiceImpl 导入路径一致：conditionsJson + acceptedTypes 合并。 */
    static String resolveConditionsJsonFromJson(JsonNode rule) {
        String conditionsJson = null;
        if (rule.hasNonNull("conditionsJson")) {
            JsonNode node = rule.get("conditionsJson");
            conditionsJson = node.isTextual() ? node.asText() : node.toString();
        }
        if (rule.has("acceptedTypes")) {
            conditionsJson = BillingConditionEvaluator.mergeAcceptedTypesIntoConditions(
                    conditionsJson, rule.get("acceptedTypes"));
        }
        return conditionsJson;
    }

    static BigDecimal effectivePriceFromJson(JsonNode rule) {
        if (rule.hasNonNull("price")) {
            return decimal(rule.path("price"));
        }
        if (rule.hasNonNull("fee")) {
            return decimal(rule.path("fee"));
        }
        return null;
    }

    static BigDecimal effectivePriceFromEntity(CustomerProductRule rule) {
        if (rule.getPrice() != null) {
            return rule.getPrice();
        }
        return rule.getFee();
    }

    /** baseline 未指定时与 DB 默认 exact_token 视为等价。 */
    static String normalizeKeywordMatchMode(String mode) {
        if (mode == null || mode.isBlank() || "exact_token".equals(mode)) {
            return null;
        }
        return mode;
    }

    private boolean ruleSignatureEquals(Map<String, Object> a, Map<String, Object> b) {
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

    private static boolean decimalEquals(Object a, Object b) {
        BigDecimal da = toDecimal(a);
        BigDecimal db = toDecimal(b);
        if (da == null && db == null) {
            return true;
        }
        if (da == null || db == null) {
            return false;
        }
        return da.compareTo(db) == 0;
    }

    private static BigDecimal toDecimal(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof BigDecimal bd) {
            return bd;
        }
        if (val instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return BigDecimal.valueOf(node.asDouble());
    }

    private static String textOrNull(JsonNode rule, String field) {
        JsonNode node = rule.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text.isBlank() ? null : text;
    }

    private static List<String> sortedKeywords(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && !item.asText().isBlank()) {
                out.add(item.asText());
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    private static List<String> parseKeywordList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = JsonUtils.getObjectMapper().readValue(
                    json,
                    JsonUtils.getObjectMapper().getTypeFactory().constructCollectionType(List.class, String.class));
            return list.stream().filter(s -> s != null && !s.isBlank()).sorted().collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }


    private static String normalizeConditionsText(String raw) {
        return JsonUtils.canonicalJsonText(raw);
    }
}
