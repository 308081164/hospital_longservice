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
