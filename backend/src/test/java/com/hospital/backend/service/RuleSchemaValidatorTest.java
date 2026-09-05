package com.hospital.backend.service;

import com.hospital.backend.service.DefaultPricingTemplate;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleSchemaValidatorTest {

    private final RuleSchemaValidator validator = new RuleSchemaValidator();

    @Test
    void acceptsDefaultTemplate() {
        RuleSchemaValidator.ValidationResult result = validator.validate(DefaultPricingTemplate.buildRulesMap());
        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsMissingNeedleKeywords() {
        Map<String, Object> rules = new LinkedHashMap<>(DefaultPricingTemplate.buildRulesMap());
        @SuppressWarnings("unchecked")
        Map<String, Object> needle = new LinkedHashMap<>((Map<String, Object>) rules.get("needle"));
        needle.put("keywords", List.of());
        rules.put("needle", needle);

        RuleSchemaValidator.ValidationResult result = validator.validate(rules);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("小件识别"));
    }

    @Test
    void acceptsNeedleKeywordConfigsAsKeywordSource() {
        // keywords 为空但配置了关键词独立配置时校验通过（独立配置关键词本身即识别词）
        Map<String, Object> rules = new LinkedHashMap<>(DefaultPricingTemplate.buildRulesMap());
        @SuppressWarnings("unchecked")
        Map<String, Object> needle = new LinkedHashMap<>((Map<String, Object>) rules.get("needle"));
        needle.put("keywords", List.of());
        needle.put("keywordConfigs", List.of(
                Map.of("keyword", "车针", "matchMode", "contains", "threshold", 5, "foldRatio", 5),
                Map.of("keyword", "克氏针", "matchMode", "exact", "threshold", 3, "foldRatio", 3)));
        rules.put("needle", needle);

        RuleSchemaValidator.ValidationResult result = validator.validate(rules);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsDuplicateNeedleKeywordConfigs() {
        Map<String, Object> rules = new LinkedHashMap<>(DefaultPricingTemplate.buildRulesMap());
        @SuppressWarnings("unchecked")
        Map<String, Object> needle = new LinkedHashMap<>((Map<String, Object>) rules.get("needle"));
        needle.put("keywordConfigs", List.of(
                Map.of("keyword", "车针", "threshold", 5),
                Map.of("keyword", "车针", "threshold", 3)));
        rules.put("needle", needle);

        RuleSchemaValidator.ValidationResult result = validator.validate(rules);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("重复"));
    }

    @Test
    void rejectsInvalidNeedleKeywordConfigValues() {
        Map<String, Object> rules = new LinkedHashMap<>(DefaultPricingTemplate.buildRulesMap());
        @SuppressWarnings("unchecked")
        Map<String, Object> needle = new LinkedHashMap<>((Map<String, Object>) rules.get("needle"));
        needle.put("keywordConfigs", List.of(
                Map.of("keyword", "车针", "matchMode", "fuzzy", "threshold", -1, "foldRatio", 0)));
        rules.put("needle", needle);

        RuleSchemaValidator.ValidationResult result = validator.validate(rules);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("匹配模式"));
        assertThat(result.errors()).anyMatch(e -> e.contains("触发件数"));
        assertThat(result.errors()).anyMatch(e -> e.contains("折算比例"));
    }

    @Test
    void rejectsBlankNeedleKeywordConfigKeyword() {
        Map<String, Object> rules = new LinkedHashMap<>(DefaultPricingTemplate.buildRulesMap());
        @SuppressWarnings("unchecked")
        Map<String, Object> needle = new LinkedHashMap<>((Map<String, Object>) rules.get("needle"));
        needle.put("keywordConfigs", List.of(Map.of("keyword", "  ", "threshold", 3)));
        rules.put("needle", needle);

        RuleSchemaValidator.ValidationResult result = validator.validate(rules);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("关键词不能为空"));
    }

    @Test
    void rejectsEmptyBagSizes() {
        Map<String, Object> rules = new LinkedHashMap<>(DefaultPricingTemplate.buildRulesMap());
        @SuppressWarnings("unchecked")
        Map<String, Object> ht = new LinkedHashMap<>((Map<String, Object>) rules.get("highTemperature"));
        @SuppressWarnings("unchecked")
        Map<String, Object> pp = new LinkedHashMap<>((Map<String, Object>) ht.get("paperPlastic"));
        pp.put("bagSizes", List.of());
        ht.put("paperPlastic", pp);
        rules.put("highTemperature", ht);

        RuleSchemaValidator.ValidationResult result = validator.validate(rules);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("高温纸塑袋"));
    }
}
