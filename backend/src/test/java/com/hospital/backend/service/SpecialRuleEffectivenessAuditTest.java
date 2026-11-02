package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 特殊计价规则生效性审计（2026-11-02）。
 *
 * 逐医院、逐条 productRule 合成「应当命中」的账单行，交给编译后的 PricingEngine 计价，
 * 以 pricingRule 文本是否包含该规则名判定「规则是否真的生效」。
 * 报告写入 target/special-rule-effectiveness.md，并同时在控制台输出按医院汇总。
 */
class SpecialRuleEffectivenessAuditTest {

    private static final int[] DEFAULT_COUNTS = {1, 2, 3, 5, 6, 10, 20};

    @Test
    void auditEverySpecialPricingRule() throws Exception {
        JsonNode manifest = PricingEngineTestSupport.manifest();
        StringBuilder md = new StringBuilder();
        md.append("# 特殊计价规则生效性审计\n\n");
        md.append("| 医院 | 代码 | 规则数 | 命中 | 未命中 | 覆盖率 |\n|---|---|---|---|---|---|\n");

        int totalRules = 0;
        int totalHit = 0;
        Map<String, List<String>> missesByHospital = new LinkedHashMap<>();

        for (Iterator<Map.Entry<String, JsonNode>> it = manifest.path("customers").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String code = entry.getKey();
            JsonNode customer = entry.getValue();
            JsonNode rules = customer.path("productRules");
            if (!rules.isArray() || rules.isEmpty()) {
                continue;
            }
            String hospital = customer.path("name").asText(code);
            PricingEngine engine;
            try {
                engine = PricingEngineTestSupport.engineForCustomerCode(code);
            } catch (RuntimeException ex) {
                missesByHospital.put(code, List.of("编译失败: " + ex.getMessage()));
                md.append("| ").append(hospital).append(" | ").append(code)
                        .append(" | ").append(rules.size()).append(" | 0 | ").append(rules.size())
                        .append(" | 0% |\n");
                totalRules += rules.size();
                continue;
            }

            int hit = 0;
            List<String> misses = new ArrayList<>();
            for (JsonNode rule : rules) {
                if (!rule.path("isActive").asBoolean(true)) {
                    continue;
                }
                totalRules++;
                String name = rule.path("name").asText("");
                if (tryHit(engine, customer, rule)) {
                    hit++;
                    totalHit++;
                } else {
                    misses.add(name);
                }
            }
            if (!misses.isEmpty()) {
                missesByHospital.put(code, misses);
            }
            double rate = rules.size() == 0 ? 0 : (hit * 100.0 / rules.size());
            md.append("| ").append(hospital).append(" | ").append(code)
                    .append(" | ").append(rules.size()).append(" | ").append(hit)
                    .append(" | ").append(rules.size() - hit)
                    .append(" | ").append(String.format("%.0f%%", rate)).append(" |\n");
        }

        md.append("\n## 未命中明细\n\n");
        missesByHospital.forEach((code, names) -> {
            md.append("### ").append(code).append("\n");
            for (String n : names) {
                md.append("- ").append(n).append("\n");
            }
            md.append("\n");
        });

        Files.createDirectories(Path.of("target"));
        Path report = Path.of("target", "special-rule-effectiveness.md");
        Files.writeString(report, md.toString(), StandardCharsets.UTF_8);

        System.out.println("[SPECIAL-RULE-AUDIT] rules=" + totalRules + " hit=" + totalHit
                + " misses=" + (totalRules - totalHit) + " report=" + report.toAbsolutePath());
        for (Map.Entry<String, List<String>> e : missesByHospital.entrySet()) {
            System.out.println("[SPECIAL-RULE-AUDIT] " + e.getKey() + " misses=" + e.getValue());
        }

        assertThat(totalRules).as("应当能审计到特殊计价规则").isGreaterThan(0);
        assertThat(report).exists();
    }

    /** 用规则自身的条件合成账单行，命中一次即算规则生效。 */
    private boolean tryHit(PricingEngine engine, JsonNode customer, JsonNode rule) {
        String hospital = customer.path("name").asText("");
        String ruleName = rule.path("name").asText("");
        for (String packName : candidates(rule)) {
            for (String type : candidateTypes(rule)) {
                for (String material : candidateMaterials(rule, packName)) {
                    for (String department : candidateDepartments(rule)) {
                        for (int count : candidateCounts(rule)) {
                            Map<String, Object> row = PricingEngineTestSupport.row(
                                    hospital, type, packName, material, count, 1, 999, 999);
                            row.put("department", department);
                            PricingEngine.ProcessedResult result;
                            try {
                                result = engine.processRow(row);
                            } catch (RuntimeException ex) {
                                continue;
                            }
                            if (result != null && result.pricingRule != null
                                    && result.pricingRule.contains(ruleName)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private List<String> candidateDepartments(JsonNode rule) {
        List<String> out = new ArrayList<>();
        String conditionsJson = rule.path("conditionsJson").asText("");
        if (!conditionsJson.isBlank()) {
            int idx = conditionsJson.indexOf("\"department\"");
            if (idx >= 0) {
                int v = conditionsJson.indexOf("\"value\"", idx);
                int q1 = conditionsJson.indexOf('"', v + 7);
                int q2 = conditionsJson.indexOf('"', q1 + 1);
                if (q1 > 0 && q2 > q1) {
                    out.add(conditionsJson.substring(q1 + 1, q2));
                }
            }
        }
        out.add("手术室");
        return out;
    }

    private List<String> candidates(JsonNode rule) {
        List<String> out = new ArrayList<>();
        JsonNode keywords = rule.path("keywords");
        boolean regexMode = "regex".equalsIgnoreCase(rule.path("keywordMatchMode").asText(""));
        if (keywords != null && keywords.isArray()) {
            for (JsonNode k : keywords) {
                String raw = k.asText("");
                int at = raw.indexOf('@');
                String body = at > 0 ? raw.substring(0, at) : raw;
                String suffix = at > 0 ? raw.substring(at + 1) : "";
                if ("regex".equalsIgnoreCase(suffix) || regexMode) {
                    out.add(sampleForRegex(body));
                    if (body.contains("克氏针")) {
                        out.add("克氏针 0.8 双");
                        out.add("克氏针 2.0 双");
                    }
                } else if ("needle_box".equalsIgnoreCase(suffix)) {
                    // 引擎的 needle_box 只认「针盒1针58」「(针5盒1)」两种写法
                    out.add("针盒1针58");
                    out.add("(针5盒1)");
                } else if (!body.isBlank()) {
                    out.add(body);
                }
            }
        }
        if (out.isEmpty()) {
            out.add("测试包-1/Z9999");
        }
        return out;
    }

    private List<String> candidateTypes(JsonNode rule) {
        List<String> out = new ArrayList<>();
        JsonNode accepted = rule.path("acceptedTypes");
        if (accepted.isArray()) {
            accepted.forEach(n -> out.add(n.asText("")));
        }
        JsonNode conditions = rule.path("conditions");
        if (conditions.isArray()) {
            for (JsonNode cond : conditions) {
                String field = cond.path("field").asText("");
                if ("type".equalsIgnoreCase(field) || "packType".equalsIgnoreCase(field)) {
                    JsonNode value = cond.path("value");
                    if (value.isArray()) {
                        value.forEach(n -> out.add(n.asText("")));
                    } else if (!value.asText("").isBlank()) {
                        out.add(value.asText(""));
                    }
                }
            }
        }
        out.removeIf(String::isBlank);
        // 泛型类型要展开成真实账单写法（packTypeEquivalent 支持「额外包」前缀匹配，
        // 但直接用泛型字符串会走不到包装识别）
        List<String> expanded = new ArrayList<>(out);
        for (String t : out) {
            if ("额外包".equals(t)) {
                expanded.add("额外包(纸塑袋)");
                expanded.add("额外包（无纺布）");
                expanded.add("额外包（低温等离子）");
                expanded.add("额外包（ETO）");
            } else if ("器械包".equals(t)) {
                expanded.add("器械包（ZSD）");
                expanded.add("器械包(低温等离子)");
            } else if ("单包装包".equals(t)) {
                expanded.add("单包装包(等离子)");
                expanded.add("单包装包（EO）");
            } else if ("敷料包".equals(t)) {
                expanded.add("敷料包（无纺布）");
                expanded.add("敷料包（纸塑袋）");
            }
        }
        if (expanded.isEmpty()) {
            expanded.add("额外包(纸塑袋)");
        }
        return expanded;
    }

    private String sampleForRegex(String regex) {
        if (regex.contains("克氏针")) return "克氏针 0.8 双";
        if (regex.contains("小件盒")) return "小件盒-1件";
        if (regex.contains("W12050")) return "器械包-1件/W12050";
        if (regex.contains("针盒")) return "针盒1针多少3";
        if (regex.contains("双")) return "测试包-1/双";
        String plain = regex.replaceAll("\\.[*+]\\?|\\.[0-9]+\\([^)]*\\)|\\[[^]]*\\]|\\^|\\$|\\{.*?\\}", " ");
        plain = plain.replace(".*", " ").replace(".+", " ").replace("\\", "");
        if (plain.length() >= 40) {
            plain = plain.substring(0, 39);
        }
        return plain.isBlank() ? "测试包-1/Z9999" : plain.trim();
    }

    private List<String> candidateMaterials(JsonNode rule, String packName) {
        List<String> out = new ArrayList<>();
        String mat = rule.path("packagingMaterial").asText("");
        if (!mat.isBlank()) {
            out.add(mat);
        }
        if (packName.contains("纸塑") || packName.contains("纸塑袋")) {
            out.add("高温纸塑袋10cm");
        }
        if (packName.contains("无纺布")) {
            out.add("无纺布-90×90-50g");
        }
        boolean lowTemp = "LT".equalsIgnoreCase(rule.path("temperature").asText(""));
        if (lowTemp) {
            out.add("低温纸塑袋20cm");
            out.add("低温纸塑袋10cm");
        } else if (out.isEmpty()) {
            out.add("高温纸塑袋10cm");
            out.add("无纺布-90×90-50g");
        }
        return out;
    }

    private int[] candidateCounts(JsonNode rule) {
        Integer min = rule.hasNonNull("minInstrumentCount") ? rule.get("minInstrumentCount").asInt() : null;
        Integer max = rule.hasNonNull("maxInstrumentCount") ? rule.get("maxInstrumentCount").asInt() : null;
        if (min != null || max != null) {
            int lo = min != null ? Math.max(1, min) : 1;
            int hi = max != null ? Math.max(lo, max) : lo + 3;
            List<Integer> counts = new ArrayList<>();
            for (int c = lo; c <= Math.min(hi, lo + 5); c++) {
                counts.add(c);
            }
            return counts.stream().mapToInt(Integer::intValue).toArray();
        }
        return DEFAULT_COUNTS;
    }
}
