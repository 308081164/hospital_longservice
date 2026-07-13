package com.hospital.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.dto.request.product.MatchConditionDto;
import com.hospital.backend.dto.request.product.MatchPreviewRequest;
import com.hospital.backend.dto.response.product.MatchPreviewResponse;
import com.hospital.backend.entity.Product;
import com.hospital.backend.entity.ProductAlias;
import com.hospital.backend.entity.ProductCategory;
import com.hospital.backend.entity.ProductMatchRule;
import com.hospital.backend.entity.ProductVariant;
import com.hospital.backend.imports.bokang.PackNameSpecParser;
import com.hospital.backend.mapper.ProductAliasMapper;
import com.hospital.backend.mapper.ProductCategoryMapper;
import com.hospital.backend.mapper.ProductMapper;
import com.hospital.backend.mapper.ProductMatchRuleMapper;
import com.hospital.backend.mapper.ProductVariantMapper;
import com.hospital.backend.service.ProductMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMatchServiceImpl implements ProductMatchService {

    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductMatchRuleMapper matchRuleMapper;
    private final ProductAliasMapper aliasMapper;
    private final ProductVariantMapper variantMapper;

    private volatile List<Product> cachedProducts = List.of();
    private volatile Map<Long, ProductCategory> cachedCategories = Map.of();
    private volatile Map<Long, List<ProductMatchRule>> cachedFamilyRules = Map.of();
    private volatile Map<Long, List<ProductAlias>> cachedAliases = Map.of();
    private volatile List<ProductVariant> cachedVariants = List.of();
    private volatile Map<Long, ProductVariant> cachedVariantById = Map.of();
    private volatile List<ProductMatchRule> cachedVariantRules = List.of();

    /** 须在 SchemaMigrationRunner（CommandLineRunner）建表完成后再加载缓存 */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        refreshCache();
    }

    @Override
    public void refreshCache() {
        List<Product> products = productMapper.selectAllActive();
        Map<Long, ProductCategory> categories = new HashMap<>();
        for (ProductCategory category : categoryMapper.selectAllActive()) {
            categories.put(category.getId(), category);
        }
        Map<Long, List<ProductMatchRule>> familyRules = new HashMap<>();
        Map<Long, List<ProductAlias>> aliases = new HashMap<>();
        for (Product product : products) {
            familyRules.put(product.getId(), matchRuleMapper.selectByProductId(product.getId()));
            aliases.put(product.getId(), aliasMapper.selectByProductId(product.getId()));
        }

        List<ProductVariant> variants = variantMapper.selectAllActive();
        Map<Long, ProductVariant> variantById = new HashMap<>();
        for (ProductVariant variant : variants) {
            variantById.put(variant.getId(), variant);
        }

        List<ProductMatchRule> variantRules = matchRuleMapper.selectAllActiveVariantRules();

        cachedProducts = List.copyOf(products);
        cachedCategories = Map.copyOf(categories);
        cachedFamilyRules = Map.copyOf(familyRules);
        cachedAliases = Map.copyOf(aliases);
        cachedVariants = List.copyOf(variants);
        cachedVariantById = Map.copyOf(variantById);
        cachedVariantRules = List.copyOf(variantRules);
        log.info("Product match cache refreshed: {} products, {} variants, {} variant rules",
                products.size(), variants.size(), variantRules.size());
    }

    @Override
    public Optional<MatchPreviewResponse> matchRow(MatchPreviewRequest request) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", request.getType());
        row.put("packName", request.getPackName());
        row.put("packageMaterial", request.getPackageMaterial());
        row.put("categoryNo", request.getCategoryNo());
        row.put("instrumentCount", request.getInstrumentCount());
        return matchRow(row);
    }

    @Override
    public Optional<MatchPreviewResponse> matchRow(Map<String, Object> row) {
        // 1. variant 级规则优先
        for (ProductMatchRule rule : cachedVariantRules) {
            if (!Boolean.TRUE.equals(rule.getIsActive()) || rule.getVariantId() == null) {
                continue;
            }
            if (evaluateRule(rule, row)) {
                ProductVariant variant = cachedVariantById.get(rule.getVariantId());
                if (variant != null) {
                    Product product = findProduct(variant.getProductId());
                    ProductCategory category = product != null ? cachedCategories.get(product.getCategoryId()) : null;
                    if (product != null && category != null) {
                        return Optional.of(buildVariantResponse(product, category, variant, rule.getId(), "variant_rule"));
                    }
                }
            }
        }

        // 2. 精确 fingerprint 兜底（无规则时）
        String packName = str(row, "packName");
        String type = str(row, "type");
        String material = str(row, "packageMaterial");
        String fp = PackNameSpecParser.specFingerprint(packName, type, material);
        for (ProductVariant variant : cachedVariants) {
            if (fp.equals(variant.getSpecFingerprint())) {
                Product product = findProduct(variant.getProductId());
                ProductCategory category = product != null ? cachedCategories.get(product.getCategoryId()) : null;
                if (product != null && category != null) {
                    return Optional.of(buildVariantResponse(product, category, variant, null, "fingerprint"));
                }
            }
        }

        // 3. 别名 + 族级规则
        for (Product product : cachedProducts) {
            ProductCategory category = cachedCategories.get(product.getCategoryId());
            if (category == null) {
                continue;
            }

            Optional<MatchPreviewResponse> aliasMatch = matchAliases(product, category, row);
            if (aliasMatch.isPresent()) {
                return aliasMatch;
            }

            List<ProductMatchRule> rules = cachedFamilyRules.getOrDefault(product.getId(), List.of());
            for (ProductMatchRule rule : rules) {
                if (!Boolean.TRUE.equals(rule.getIsActive()) || rule.getVariantId() != null) {
                    continue;
                }
                if (evaluateRule(rule, row)) {
                    return Optional.of(buildResponse(product, category, rule.getId(), null, "match_rule"));
                }
            }
        }
        return Optional.empty();
    }

    private Product findProduct(Long productId) {
        for (Product product : cachedProducts) {
            if (product.getId().equals(productId)) {
                return product;
            }
        }
        return productMapper.selectById(productId);
    }

    private Optional<MatchPreviewResponse> matchAliases(Product product, ProductCategory category, Map<String, Object> row) {
        List<ProductAlias> aliases = cachedAliases.getOrDefault(product.getId(), List.of());
        String packName = str(row, "packName");
        for (ProductAlias alias : aliases) {
            if (!Boolean.TRUE.equals(alias.getIsActive())) {
                continue;
            }
            String aliasText = alias.getAlias();
            if (aliasText == null || aliasText.isBlank()) {
                continue;
            }
            boolean matched = "EXACT".equalsIgnoreCase(alias.getMatchType())
                    ? packName.equals(aliasText)
                    : packName.contains(aliasText);
            if (matched) {
                return Optional.of(buildResponse(product, category, null, aliasText, "alias"));
            }
        }
        return Optional.empty();
    }

    private MatchPreviewResponse buildVariantResponse(Product product, ProductCategory category,
                                                      ProductVariant variant, Long ruleId, String source) {
        String pricingPath = product.getPricingMode() != null && !product.getPricingMode().isBlank()
                ? product.getPricingMode()
                : category.getPricingPath();
        return MatchPreviewResponse.builder()
                .matched(true)
                .productId(product.getId())
                .productName(product.getName())
                .categoryId(category.getId())
                .categoryCode(category.getCode())
                .categoryName(category.getName())
                .pricingPath(pricingPath)
                .pricingMode(product.getPricingMode())
                .publicPrice(variant.getPublicPrice() != null ? variant.getPublicPrice() : product.getPublicPrice())
                .originalPrice(variant.getOriginalPrice() != null ? variant.getOriginalPrice() : product.getOriginalPrice())
                .matchedRuleId(ruleId)
                .variantId(variant.getId())
                .variantDisplayName(variant.getDisplayName())
                .specFingerprint(variant.getSpecFingerprint())
                .variantPublicPrice(variant.getPublicPrice())
                .source(source)
                .build();
    }

    private MatchPreviewResponse buildResponse(Product product, ProductCategory category,
                                               Long ruleId, String alias, String source) {
        String pricingPath = product.getPricingMode() != null && !product.getPricingMode().isBlank()
                ? product.getPricingMode()
                : category.getPricingPath();
        return MatchPreviewResponse.builder()
                .matched(true)
                .productId(product.getId())
                .productName(product.getName())
                .categoryId(category.getId())
                .categoryCode(category.getCode())
                .categoryName(category.getName())
                .pricingPath(pricingPath)
                .pricingMode(product.getPricingMode())
                .publicPrice(product.getPublicPrice())
                .originalPrice(product.getOriginalPrice())
                .matchedRuleId(ruleId)
                .matchedAlias(alias)
                .source(source)
                .build();
    }

    private boolean evaluateRule(ProductMatchRule rule, Map<String, Object> row) {
        String matchType = rule.getMatchType();
        if (matchType == null) {
            return false;
        }
        return switch (matchType.toUpperCase()) {
            case "EXACT_NAME" -> evaluateExact(rule, row);
            case "CONTAINS" -> evaluateContains(rule, row);
            case "REGEX" -> evaluateRegex(rule, row);
            case "COMPOSITE" -> evaluateComposite(rule, row);
            default -> false;
        };
    }

    private boolean evaluateExact(ProductMatchRule rule, Map<String, Object> row) {
        List<String> fields = resolveFields(rule);
        String pattern = rule.getPatternValue();
        if (pattern == null) {
            return false;
        }
        for (String field : fields) {
            if (pattern.equals(fieldValue(row, field))) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateContains(ProductMatchRule rule, Map<String, Object> row) {
        List<String> fields = resolveFields(rule);
        String pattern = rule.getPatternValue();
        if (pattern == null) {
            return false;
        }
        for (String field : fields) {
            if (fieldValue(row, field).contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateRegex(ProductMatchRule rule, Map<String, Object> row) {
        List<String> fields = resolveFields(rule);
        String pattern = rule.getPatternValue();
        if (pattern == null) {
            return false;
        }
        Pattern compiled;
        try {
            compiled = Pattern.compile(pattern);
        } catch (PatternSyntaxException ex) {
            log.warn("Invalid regex in product match rule {}: {}", rule.getId(), pattern);
            return false;
        }
        for (String field : fields) {
            if (compiled.matcher(fieldValue(row, field)).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateComposite(ProductMatchRule rule, Map<String, Object> row) {
        List<MatchConditionDto> conditions = parseConditions(rule.getConditionsJson());
        if (conditions == null || conditions.isEmpty()) {
            return false;
        }
        for (MatchConditionDto condition : conditions) {
            if (!evaluateCondition(condition, row)) {
                return false;
            }
        }
        return true;
    }

    private List<MatchConditionDto> parseConditions(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.getObjectMapper().readValue(json, new TypeReference<List<MatchConditionDto>>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse match conditions: {}", ex.getMessage());
            return null;
        }
    }

    private boolean evaluateCondition(MatchConditionDto condition, Map<String, Object> row) {
        String operator = condition.getOperator() == null ? "" : condition.getOperator().toUpperCase();
        String fieldValue = fieldValue(row, condition.getField());
        String expected = condition.getValue() == null ? "" : condition.getValue();

        return switch (operator) {
            case "EQ" -> fieldValue.equals(expected);
            case "NE" -> !fieldValue.equals(expected);
            case "CONTAINS" -> fieldValue.contains(expected);
            case "NOT_CONTAINS" -> !fieldValue.contains(expected);
            case "REGEX" -> matchesRegex(fieldValue, expected);
            case "GT" -> compareNumber(fieldValue, expected) > 0;
            case "GTE" -> compareNumber(fieldValue, expected) >= 0;
            case "LT" -> compareNumber(fieldValue, expected) < 0;
            case "LTE" -> compareNumber(fieldValue, expected) <= 0;
            default -> false;
        };
    }

    private int compareNumber(String actual, String expected) {
        try {
            double a = actual.isBlank() ? 0 : Double.parseDouble(actual);
            double b = expected.isBlank() ? 0 : Double.parseDouble(expected);
            return Double.compare(a, b);
        } catch (NumberFormatException ex) {
            return actual.compareTo(expected);
        }
    }

    private boolean matchesRegex(String value, String pattern) {
        try {
            return Pattern.compile(pattern).matcher(value).find();
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }

    private List<String> resolveFields(ProductMatchRule rule) {
        if (rule.getMatchFields() != null && !rule.getMatchFields().isBlank()) {
            try {
                List<String> fields = JsonUtils.getObjectMapper().readValue(
                        rule.getMatchFields(), new TypeReference<List<String>>() {});
                if (fields != null && !fields.isEmpty()) {
                    return fields;
                }
            } catch (Exception ex) {
                log.warn("Failed to parse match_fields for rule {}: {}", rule.getId(), ex.getMessage());
            }
        }
        if (rule.getTargetField() != null && !rule.getTargetField().isBlank()) {
            return List.of(rule.getTargetField());
        }
        return List.of("pack_name");
    }

    private String fieldValue(Map<String, Object> row, String field) {
        if (field == null) {
            return "";
        }
        String key = switch (field) {
            case "pack_name", "packName" -> "packName";
            case "type" -> "type";
            case "package_material", "packageMaterial" -> "packageMaterial";
            case "category_no", "categoryNo" -> "categoryNo";
            case "instrument_count", "instrumentCount" -> "instrumentCount";
            default -> field;
        };
        Object value = row.get(key);
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    private String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
