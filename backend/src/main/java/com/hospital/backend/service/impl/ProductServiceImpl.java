package com.hospital.backend.service.impl;

import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.product.*;
import com.hospital.backend.dto.response.product.MatchPreviewResponse;
import com.hospital.backend.dto.response.product.ProductResponse;
import com.hospital.backend.entity.*;
import com.hospital.backend.imports.bokang.PackNameSpecParser;
import com.hospital.backend.mapper.*;
import com.hospital.backend.service.ProductMatchService;
import com.hospital.backend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductMatchRuleMapper matchRuleMapper;
    private final ProductAliasMapper aliasMapper;
    private final ProductMatchService productMatchService;
    private final ProductVariantMapper variantMapper;
    private final CustomerProductRuleMapper customerProductRuleMapper;

    @Override
    public Result<List<ProductResponse>> listProducts(Long categoryId) {
        List<Product> products = categoryId != null
                ? productMapper.selectByCategoryId(categoryId)
                : productMapper.selectAllActive();
        List<ProductResponse> data = products.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return Result.success(data);
    }

    @Override
    public Result<ProductResponse> getProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            return Result.fail(404, "产品不存在");
        }
        return Result.success(toResponse(product));
    }

    @Override
    @Transactional
    public Result<ProductResponse> createProduct(SaveProductRequest request) {
        ProductCategory category = categoryMapper.selectById(request.getCategoryId());
        if (category == null) {
            return Result.fail(400, "产品分类不存在");
        }
        validateMatchRules(request.getMatchRules());

        Product product = new Product();
        applyRequest(product, request);
        product.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        productMapper.insert(product);

        saveMatchRules(product.getId(), request.getMatchRules());
        saveAliases(product.getId(), request.getAliases());
        productMatchService.refreshCache();

        return Result.success(toResponse(productMapper.selectById(product.getId())));
    }

    @Override
    @Transactional
    public Result<ProductResponse> updateProduct(Long id, SaveProductRequest request) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            return Result.fail(404, "产品不存在");
        }
        ProductCategory category = categoryMapper.selectById(request.getCategoryId());
        if (category == null) {
            return Result.fail(400, "产品分类不存在");
        }
        validateMatchRules(request.getMatchRules());

        applyRequest(product, request);
        if (request.getIsActive() != null) {
            product.setIsActive(request.getIsActive());
        }
        productMapper.updateById(product);

        matchRuleMapper.deleteByProductId(id);
        aliasMapper.deleteByProductId(id);
        saveMatchRules(id, request.getMatchRules());
        saveAliases(id, request.getAliases());
        productMatchService.refreshCache();

        return Result.success(toResponse(productMapper.selectById(id)));
    }

    @Override
    @Transactional
    public Result<Map<String, Boolean>> deleteProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            return Result.fail(404, "产品不存在");
        }
        productMapper.softDeleteById(id);
        productMatchService.refreshCache();
        return Result.success(Map.of("success", true));
    }

    @Override
    public Result<MatchPreviewResponse> matchPreview(MatchPreviewRequest request) {
        Optional<MatchPreviewResponse> match = productMatchService.matchRow(request);
        if (match.isPresent()) {
            return Result.success(match.get());
        }
        return Result.success(MatchPreviewResponse.builder().matched(false).build());
    }

    @Override
    public Result<List<MatchPreviewResponse>> batchMatchPreview(BatchMatchPreviewRequest request) {
        List<MatchPreviewResponse> results = new ArrayList<>();
        if (request.getRows() != null) {
            for (MatchPreviewRequest row : request.getRows()) {
                Optional<MatchPreviewResponse> match = productMatchService.matchRow(row);
                results.add(match.orElseGet(() -> MatchPreviewResponse.builder().matched(false).build()));
            }
        }
        return Result.success(results);
    }

    @Override
    @Transactional
    public Result<Map<String, Object>> quickOnboard(QuickOnboardRequest request) {
        Long categoryId = request.getCategoryId();
        if (categoryId == null && request.getCategoryCode() != null) {
            ProductCategory cat = categoryMapper.selectByCode(request.getCategoryCode());
            if (cat != null) {
                categoryId = cat.getId();
            }
        }
        if (categoryId == null) {
            ProductCategory fallback = categoryMapper.selectByCode("SMALL_ITEM");
            categoryId = fallback != null ? fallback.getId() : null;
        }
        if (categoryId == null) {
            return Result.fail(400, "无法确定产品分类");
        }

        Product product = findActiveProductByName(request.getFamilyName());
        if (product == null) {
            product = new Product();
            product.setCategoryId(categoryId);
            product.setName(request.getFamilyName());
            product.setSkuCode(PackNameSpecParser.familyCode(request.getFamilyName()));
            product.setPricingMode(request.getPricingMode());
            product.setPublicPrice(request.getPublicPrice());
            product.setOriginalPrice(request.getPublicPrice());
            product.setPriority(200);
            product.setIsActive(true);
            productMapper.insert(product);

            ProductMatchRule familyRule = new ProductMatchRule();
            familyRule.setProductId(product.getId());
            familyRule.setMatchType("CONTAINS");
            familyRule.setTargetField("pack_name");
            familyRule.setPatternValue(request.getFamilyName());
            familyRule.setPriority(300);
            familyRule.setIsActive(true);
            matchRuleMapper.insert(familyRule);
        }

        PackNameSpecParser.ParsedPack parsed = PackNameSpecParser.parse(
                request.getPackName(), request.getType(), request.getPackageMaterial());

        ProductVariant variant = variantMapper.selectBySpecFingerprint(parsed.specFingerprint);
        if (variant == null) {
            variant = new ProductVariant();
            variant.setProductId(product.getId());
            variant.setSkuCode(PackNameSpecParser.variantSku(parsed.specFingerprint));
            variant.setSpecFingerprint(parsed.specFingerprint);
            variant.setPackName(parsed.packName);
            variant.setType(parsed.type);
            variant.setPackageMaterial(parsed.packageMaterial);
            variant.setFamilyNameParsed(parsed.familyName);
            variant.setSpecSuffix(parsed.specSuffix);
            variant.setInstrumentCountHint(parsed.instrumentCountHint);
            variant.setOrderNoPattern(parsed.orderNoPattern);
            variant.setBagMaterialClass(parsed.bagInfo.materialClass);
            variant.setBagTempClass(parsed.bagInfo.tempClass);
            variant.setBagWidthMm(parsed.bagInfo.widthMm);
            variant.setBagHeightMm(parsed.bagInfo.heightMm);
            variant.setBagSizeLabel(parsed.bagInfo.sizeLabel);
            variant.setDisplayName(parsed.displayName);
            variant.setPublicPrice(request.getPublicPrice());
            variant.setOriginalPrice(request.getPublicPrice());
            variant.setIsActive(true);
            variantMapper.insert(variant);

            ProductMatchRule variantRule = new ProductMatchRule();
            variantRule.setProductId(product.getId());
            variantRule.setVariantId(variant.getId());
            variantRule.setMatchType("COMPOSITE");
            variantRule.setConditionsJson(buildCompositeJson(parsed));
            variantRule.setPriority(100);
            variantRule.setIsActive(true);
            matchRuleMapper.insert(variantRule);
        }

        if (request.getCustomerId() != null && request.getCustomerRuleType() != null) {
            CustomerProductRule cpr = new CustomerProductRule();
            cpr.setCustomerId(request.getCustomerId());
            cpr.setRuleType(request.getCustomerRuleType());
            cpr.setName(request.getFamilyName() + " 快捷录入");
            cpr.setProductId(product.getId());
            cpr.setVariantId(variant.getId());
            cpr.setPrice(request.getCustomerPrice());
            cpr.setKeywords(JsonUtils.toJson(List.of(request.getFamilyName())));
            cpr.setIsActive(true);
            customerProductRuleMapper.insert(cpr);
        }

        productMatchService.refreshCache();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("product_id", product.getId());
        result.put("variant_id", variant.getId());
        result.put("spec_fingerprint", variant.getSpecFingerprint());
        result.put("product_name", product.getName());
        result.put("variant_display_name", variant.getDisplayName());
        return Result.success(result);
    }

    @Override
    public Result<List<Map<String, Object>>> listVariants(Long productId) {
        List<Map<String, Object>> data = variantMapper.selectByProductId(productId).stream()
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", v.getId());
                    m.put("sku_code", v.getSkuCode());
                    m.put("spec_fingerprint", v.getSpecFingerprint());
                    m.put("pack_name", v.getPackName());
                    m.put("type", v.getType());
                    m.put("package_material", v.getPackageMaterial());
                    m.put("display_name", v.getDisplayName());
                    m.put("public_price", v.getPublicPrice());
                    m.put("occurrence_count", v.getOccurrenceCount());
                    return m;
                })
                .collect(Collectors.toList());
        return Result.success(data);
    }

    private Product findActiveProductByName(String name) {
        for (Product p : productMapper.selectAllActive()) {
            if (name.equals(p.getName())) {
                return p;
            }
        }
        return null;
    }

    private String buildCompositeJson(PackNameSpecParser.ParsedPack parsed) {
        List<Map<String, String>> conditions = new ArrayList<>();
        conditions.add(Map.of("field", "pack_name", "operator", "EQ", "value", parsed.packName));
        if (parsed.type != null && !parsed.type.isBlank()) {
            conditions.add(Map.of("field", "type", "operator", "EQ", "value", parsed.type));
        }
        if (parsed.packageMaterial != null && !parsed.packageMaterial.isBlank()) {
            conditions.add(Map.of("field", "package_material", "operator", "EQ", "value", parsed.packageMaterial));
        }
        return JsonUtils.toJson(conditions);
    }

    private void applyRequest(Product product, SaveProductRequest request) {
        product.setCategoryId(request.getCategoryId());
        product.setSkuCode(request.getSkuCode());
        product.setName(request.getName());
        product.setPricingMode(request.getPricingMode());
        product.setPublicPrice(request.getPublicPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setPriority(request.getPriority() != null ? request.getPriority() : 100);
    }

    private void saveMatchRules(Long productId, List<MatchRuleDto> rules) {
        if (rules == null) {
            return;
        }
        for (MatchRuleDto dto : rules) {
            ProductMatchRule rule = new ProductMatchRule();
            rule.setProductId(productId);
            rule.setMatchType(dto.getMatchType());
            rule.setTargetField(dto.getTargetField());
            rule.setPatternValue(dto.getPatternValue());
            rule.setMatchFields(dto.getMatchFields() != null ? JsonUtils.toJson(dto.getMatchFields()) : null);
            rule.setConditionsJson(dto.getConditions() != null ? JsonUtils.toJson(dto.getConditions()) : null);
            rule.setPriority(dto.getPriority() != null ? dto.getPriority() : 100);
            rule.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
            matchRuleMapper.insert(rule);
        }
    }

    private void saveAliases(Long productId, List<ProductAliasDto> aliases) {
        if (aliases == null) {
            return;
        }
        for (ProductAliasDto dto : aliases) {
            ProductAlias alias = new ProductAlias();
            alias.setProductId(productId);
            alias.setAlias(dto.getAlias());
            alias.setMatchType(dto.getMatchType() != null ? dto.getMatchType() : "CONTAINS");
            alias.setPriority(dto.getPriority() != null ? dto.getPriority() : 100);
            alias.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
            aliasMapper.insert(alias);
        }
    }

    private void validateMatchRules(List<MatchRuleDto> rules) {
        if (rules == null) {
            return;
        }
        for (MatchRuleDto rule : rules) {
            String type = rule.getMatchType() == null ? "" : rule.getMatchType().toUpperCase();
            if ("COMPOSITE".equals(type) && (rule.getConditions() == null || rule.getConditions().isEmpty())) {
                throw new IllegalArgumentException("复合匹配规则至少需要一个条件");
            }
            if (!"COMPOSITE".equals(type) && (rule.getPatternValue() == null || rule.getPatternValue().isBlank())) {
                throw new IllegalArgumentException("匹配规则需要填写匹配值");
            }
        }
    }

    private ProductResponse toResponse(Product product) {
        ProductCategory category = categoryMapper.selectById(product.getCategoryId());
        List<MatchRuleDto> rules = matchRuleMapper.selectByProductId(product.getId()).stream()
                .map(this::toMatchRuleDto)
                .collect(Collectors.toList());
        List<ProductAliasDto> aliases = aliasMapper.selectByProductId(product.getId()).stream()
                .map(this::toAliasDto)
                .collect(Collectors.toList());

        return ProductResponse.builder()
                .id(product.getId())
                .categoryId(product.getCategoryId())
                .categoryCode(category != null ? category.getCode() : null)
                .categoryName(category != null ? category.getName() : null)
                .skuCode(product.getSkuCode())
                .name(product.getName())
                .pricingMode(product.getPricingMode())
                .pricingPath(resolvePricingPath(product, category))
                .publicPrice(product.getPublicPrice())
                .originalPrice(product.getOriginalPrice())
                .priority(product.getPriority())
                .isActive(product.getIsActive())
                .matchRules(rules)
                .aliases(aliases)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private String resolvePricingPath(Product product, ProductCategory category) {
        if (product.getPricingMode() != null && !product.getPricingMode().isBlank()) {
            return product.getPricingMode();
        }
        return category != null ? category.getPricingPath() : null;
    }

    private MatchRuleDto toMatchRuleDto(ProductMatchRule rule) {
        MatchRuleDto dto = new MatchRuleDto();
        dto.setId(rule.getId());
        dto.setMatchType(rule.getMatchType());
        dto.setTargetField(rule.getTargetField());
        dto.setPatternValue(rule.getPatternValue());
        if (rule.getMatchFields() != null && !rule.getMatchFields().isBlank()) {
            try {
                dto.setMatchFields(JsonUtils.getObjectMapper().readValue(
                        rule.getMatchFields(),
                        JsonUtils.getObjectMapper().getTypeFactory().constructCollectionType(List.class, String.class)
                ));
            } catch (Exception ignored) {
                dto.setMatchFields(List.of());
            }
        }
        if (rule.getConditionsJson() != null && !rule.getConditionsJson().isBlank()) {
            try {
                dto.setConditions(JsonUtils.getObjectMapper().readValue(
                        rule.getConditionsJson(),
                        JsonUtils.getObjectMapper().getTypeFactory().constructCollectionType(List.class, MatchConditionDto.class)
                ));
            } catch (Exception ignored) {
                dto.setConditions(List.of());
            }
        }
        dto.setPriority(rule.getPriority());
        dto.setIsActive(rule.getIsActive());
        return dto;
    }

    private ProductAliasDto toAliasDto(ProductAlias alias) {
        ProductAliasDto dto = new ProductAliasDto();
        dto.setId(alias.getId());
        dto.setAlias(alias.getAlias());
        dto.setMatchType(alias.getMatchType());
        dto.setPriority(alias.getPriority());
        dto.setIsActive(alias.getIsActive());
        return dto;
    }
}
