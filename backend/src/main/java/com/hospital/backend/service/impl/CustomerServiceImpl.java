package com.hospital.backend.service.impl;

import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.customer.*;
import com.hospital.backend.dto.response.customer.CustomerBillingPolicyResponse;
import com.hospital.backend.dto.response.customer.CustomerProductRuleResponse;
import com.hospital.backend.dto.response.customer.CustomerResponse;
import com.hospital.backend.entity.*;
import com.hospital.backend.mapper.*;
import com.hospital.backend.service.CustomerService;
import com.hospital.backend.service.BillingMode;
import com.hospital.backend.service.BillingModeInference;
import com.hospital.backend.service.BillingRuleGroupSyncService;
import com.hospital.backend.util.ProductRuleNameUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerMapper customerMapper;
    private final CustomerAliasMapper aliasMapper;
    private final CustomerDiscountMapper discountMapper;
    private final CustomerBillingPolicyMapper billingPolicyMapper;
    private final CustomerProductRuleMapper productRuleMapper;
    private final ProductMapper productMapper;
    private final BillingRuleGroupSyncService billingRuleGroupSyncService;
    private final DepartmentEntryMapper departmentEntryMapper;
    private final PhysicianEntryMapper physicianEntryMapper;

    private static final Set<String> PRICING_RULE_TYPES = Set.of(
            "FIXED_PRICE", "PRICE_PER_INSTRUMENT", "MULTIPLIER", "FOLD", "EXTRA_FEE", "ADD_FEE");

    private static final Set<String> PRODUCT_BOUND_RULE_TYPES = Set.of(
            "FIXED_PRICE", "PRICE_PER_INSTRUMENT", "MULTIPLIER");

    private static final Set<String> SETTLEMENT_RULE_TYPES = Set.of("FOLD", "EXTRA_FEE", "ADD_FEE");

    private static final Set<String> TEMPERATURE_SCOPES = Set.of("HT", "LT", "ANY");

    private static final Set<String> BILLING_PRICING_MODES = Set.of("standard", "special_only", "hybrid");

    private static final Set<String> BILLING_MODES = Set.of("PER_PACK", "PER_INSTRUMENT", "PACK_NAME_SUFFIX");

    private static final Set<String> PIECE_COUNT_SOURCES = Set.of(
            "EFFECTIVE_COUNT", "ZSD_PER_PACK", "PACK_NAME_LAST_NUMBER");

    @Override
    public Result<List<CustomerResponse>> listCustomers() {
        List<CustomerResponse> data = customerMapper.selectAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return Result.success(data);
    }

    @Override
    public Result<CustomerResponse> getCustomer(Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            return Result.fail(404, "客户不存在");
        }
        return Result.success(toResponse(customer));
    }

    @Override
    @Transactional
    public Result<CustomerResponse> createCustomer(SaveCustomerRequest request) {
        if (customerMapper.selectByCode(request.getCode()) != null) {
            return Result.fail(400, "客户编码已存在");
        }
        String standardPricingError = resolveStandardPricingOverride(request);
        if (standardPricingError != null) {
            return Result.fail(400, standardPricingError);
        }
        Customer customer = new Customer();
        applyRequest(customer, request);
        customerMapper.insert(customer);
        saveAliases(customer.getId(), request.getAliases());
        saveDiscounts(customer.getId(), request.getDiscounts());
        saveProductRules(customer.getId(), request.getProductRules());
        return Result.success(toResponse(customerMapper.selectById(customer.getId())));
    }

    @Override
    @Transactional
    public Result<CustomerResponse> updateCustomer(Long id, SaveCustomerRequest request) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            return Result.fail(404, "客户不存在");
        }
        Customer existingByCode = customerMapper.selectByCode(request.getCode());
        if (existingByCode != null && !existingByCode.getId().equals(id)) {
            return Result.fail(400, "客户编码已被其他客户使用");
        }
        String standardPricingError = resolveStandardPricingOverride(request);
        if (standardPricingError != null) {
            return Result.fail(400, standardPricingError);
        }
        applyRequest(customer, request);
        customerMapper.updateById(customer);
        aliasMapper.deleteByCustomerId(id);
        discountMapper.deleteByCustomerId(id);
        productRuleMapper.deleteByCustomerId(id);
        saveAliases(id, request.getAliases());
        saveDiscounts(id, request.getDiscounts());
        saveProductRules(id, request.getProductRules());
        return Result.success(toResponse(customerMapper.selectById(id)));
    }

    @Override
    @Transactional
    public Result<Map<String, Boolean>> deleteCustomer(Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            return Result.fail(404, "客户不存在");
        }
        customerMapper.deleteById(id);
        return Result.success(Map.of("success", true));
    }

    @Override
    public Result<List<CustomerProductRuleResponse>> listProductRules(Long customerId) {
        if (customerMapper.selectById(customerId) == null) {
            return Result.fail(404, "客户不存在");
        }
        List<CustomerProductRuleResponse> data = productRuleMapper.selectByCustomerId(customerId).stream()
                .filter(rule -> PRICING_RULE_TYPES.contains(rule.getRuleType()))
                .map(this::toProductRuleResponse)
                .collect(Collectors.toList());
        return Result.success(data);
    }

    @Override
    @Transactional
    public Result<CustomerProductRuleResponse> createProductRule(
            Long customerId, SaveCustomerProductRuleRequest request) {
        if (customerMapper.selectById(customerId) == null) {
            return Result.fail(404, "客户不存在");
        }
        String validationError = validateProductRuleRequest(request, null);
        if (validationError != null) {
            return Result.fail(400, validationError);
        }
        CustomerProductRule existing = findDuplicatePricingRule(customerId, request, null).orElse(null);
        if (existing != null) {
            return Result.fail(400, "该商品在相同匹配条件下已配置独立计价策略");
        }
        CustomerProductRule rule = buildProductRuleEntity(customerId, request);
        productRuleMapper.insert(rule);
        return Result.success(toProductRuleResponse(productRuleMapper.selectById(rule.getId())));
    }

    @Override
    @Transactional
    public Result<CustomerProductRuleResponse> updateProductRule(
            Long customerId, Long ruleId, SaveCustomerProductRuleRequest request) {
        CustomerProductRule rule = productRuleMapper.selectById(ruleId);
        if (rule == null || !customerId.equals(rule.getCustomerId())) {
            return Result.fail(404, "计价策略不存在");
        }
        String validationError = validateProductRuleRequest(request, ruleId);
        if (validationError != null) {
            return Result.fail(400, validationError);
        }
        CustomerProductRule duplicate = findDuplicatePricingRule(customerId, request, ruleId).orElse(null);
        if (duplicate != null) {
            return Result.fail(400, "该商品在相同匹配条件下已配置独立计价策略");
        }
        applyProductRuleRequest(rule, request);
        productRuleMapper.updateById(rule);
        return Result.success(toProductRuleResponse(productRuleMapper.selectById(ruleId)));
    }

    @Override
    @Transactional
    public Result<Map<String, Boolean>> deleteProductRule(Long customerId, Long ruleId) {
        CustomerProductRule rule = productRuleMapper.selectById(ruleId);
        if (rule == null || !customerId.equals(rule.getCustomerId())) {
            return Result.fail(404, "计价策略不存在");
        }
        productRuleMapper.deleteById(ruleId);
        return Result.success(Map.of("success", true));
    }

    @Override
    public Result<List<CustomerBillingPolicyResponse>> listBillingPolicies(Long customerId) {
        if (customerMapper.selectById(customerId) == null) {
            return Result.fail(404, "客户不存在");
        }
        List<CustomerBillingPolicyResponse> data = billingPolicyMapper.selectByCustomerId(customerId).stream()
                .map(this::toBillingPolicyResponse)
                .collect(Collectors.toList());
        return Result.success(data);
    }

    @Override
    @Transactional
    public Result<CustomerBillingPolicyResponse> createBillingPolicy(
            Long customerId, SaveCustomerBillingPolicyRequest request) {
        if (customerMapper.selectById(customerId) == null) {
            return Result.fail(404, "客户不存在");
        }
        String validationError = validateBillingPolicyRequest(request);
        if (validationError != null) {
            return Result.fail(400, validationError);
        }
        CustomerBillingPolicy policy = buildBillingPolicyEntity(customerId, request);
        billingPolicyMapper.insert(policy);
        return Result.success(toBillingPolicyResponse(billingPolicyMapper.selectById(policy.getId())));
    }

    @Override
    @Transactional
    public Result<CustomerBillingPolicyResponse> updateBillingPolicy(
            Long customerId, Long policyId, SaveCustomerBillingPolicyRequest request) {
        CustomerBillingPolicy policy = billingPolicyMapper.selectById(policyId);
        if (policy == null || !customerId.equals(policy.getCustomerId())) {
            return Result.fail(404, "计费策略不存在");
        }
        String validationError = validateBillingPolicyRequest(request);
        if (validationError != null) {
            return Result.fail(400, validationError);
        }
        applyBillingPolicyRequest(policy, request);
        billingPolicyMapper.updateById(policy);
        return Result.success(toBillingPolicyResponse(billingPolicyMapper.selectById(policyId)));
    }

    @Override
    @Transactional
    public Result<Map<String, Boolean>> deleteBillingPolicy(Long customerId, Long policyId) {
        CustomerBillingPolicy policy = billingPolicyMapper.selectById(policyId);
        if (policy == null || !customerId.equals(policy.getCustomerId())) {
            return Result.fail(404, "计费策略不存在");
        }
        billingPolicyMapper.deleteById(policyId);
        return Result.success(Map.of("success", true));
    }

    private void applyRequest(Customer customer, SaveCustomerRequest request) {
        customer.setCode(request.getCode());
        customer.setCanonicalName(request.getCanonicalName());
        customer.setStatus(request.getStatus() != null ? request.getStatus() : "active");
        customer.setCapMode(request.getCapMode());
        customer.setChargeDoubleBagWhenCapped(
                request.getChargeDoubleBagWhenCapped() != null ? request.getChargeDoubleBagWhenCapped() : false);
        customer.setDefaultRuleId(request.getDefaultRuleId());
        customer.setBillingEnabled(request.getBillingEnabled() != null ? request.getBillingEnabled() : false);
        customer.setBillingPricingMode(normalizeBillingPricingMode(request.getBillingPricingMode()));
        customer.setPathOverride(buildPathOverrideJson(request.getPathOverride()));
        customer.setExportNameMapping(normalizeExportNameMapping(request.getExportNameMapping()));
        customer.setStandardPricingOverride(request.getStandardPricingOverride());
        customer.setNotes(request.getNotes());
    }

    private void saveAliases(Long customerId, List<CustomerAliasDto> aliases) {
        if (aliases == null) {
            return;
        }
        for (CustomerAliasDto dto : aliases) {
            if (dto.getAlias() == null || dto.getAlias().isBlank()) {
                continue;
            }
            CustomerAlias alias = new CustomerAlias();
            alias.setCustomerId(customerId);
            alias.setAlias(dto.getAlias().trim());
            alias.setMatchType(dto.getMatchType() != null ? dto.getMatchType() : "contains");
            alias.setSource(dto.getSource() != null ? dto.getSource() : "manual");
            alias.setPriority(dto.getPriority() != null ? dto.getPriority() : 100);
            alias.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
            aliasMapper.insert(alias);
        }
    }

    private void saveDiscounts(Long customerId, List<CustomerDiscountDto> discounts) {
        if (discounts == null) {
            return;
        }
        for (CustomerDiscountDto dto : discounts) {
            if (dto.getDiscountRate() == null) {
                continue;
            }
            CustomerDiscount discount = new CustomerDiscount();
            discount.setCustomerId(customerId);
            discount.setName(dto.getName() != null ? dto.getName() : "默认折扣");
            discount.setDiscountRate(dto.getDiscountRate());
            discount.setApplyStage(resolveLegacyApplyStage(dto));
            discount.setSkipWhenFixedPrice(dto.getSkipWhenFixedPrice() != null ? dto.getSkipWhenFixedPrice() : false);
            discount.setPriority(dto.getPriority() != null ? dto.getPriority() : 100);
            discount.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
            discount.setEffectiveFrom(dto.getEffectiveFrom());
            discount.setEffectiveTo(dto.getEffectiveTo());
            discountMapper.insert(discount);
        }
        syncDiscountPolicies(customerId, discounts);
    }

    private void syncDiscountPolicies(Long customerId, List<CustomerDiscountDto> discounts) {
        billingPolicyMapper.deleteByCustomerIdAndType(customerId, "DISCOUNT");
        if (discounts == null) {
            return;
        }
        for (CustomerDiscountDto dto : discounts) {
            if (dto.getDiscountRate() == null) {
                continue;
            }
            SaveCustomerBillingPolicyRequest policyRequest = new SaveCustomerBillingPolicyRequest();
            policyRequest.setPolicyType("DISCOUNT");
            policyRequest.setName(dto.getName() != null ? dto.getName() : "默认折扣");
            policyRequest.setTemperature(normalizeTemperature(dto.getTemperature()));
            policyRequest.setRate(dto.getDiscountRate());
            policyRequest.setSkipWhenFixedPrice(
                    dto.getSkipWhenFixedPrice() != null ? dto.getSkipWhenFixedPrice() : false);
            applyDiscountStagesToPolicyRequest(policyRequest, dto);
            policyRequest.setPriority(dto.getPriority() != null ? dto.getPriority() : 100);
            policyRequest.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
            CustomerBillingPolicy policy = buildBillingPolicyEntity(customerId, policyRequest);
            billingPolicyMapper.insert(policy);
        }
    }

    private void saveProductRules(Long customerId, List<CustomerProductRuleDto> rules) {
        if (rules == null) {
            return;
        }
        for (CustomerProductRuleDto dto : rules) {
            if (dto.getRuleType() == null) {
                continue;
            }
            if (PRICING_RULE_TYPES.contains(dto.getRuleType())) {
                if (requiresProductBinding(dto.getRuleType()) && dto.getProductId() == null) {
                    boolean hasKeywords = dto.getKeywords() != null
                            && dto.getKeywords().stream().anyMatch(k -> k != null && !k.isBlank());
                    if (!hasKeywords) {
                        continue;
                    }
                }
                SaveCustomerProductRuleRequest request = new SaveCustomerProductRuleRequest();
                request.setProductId(dto.getProductId());
                request.setRuleType(dto.getRuleType());
                request.setMatchMode(dto.getMatchMode());
                request.setName(dto.getName());
                request.setPriority(dto.getPriority());
                request.setPrice(dto.getPrice());
                request.setAcceptedPrices(dto.getAcceptedPrices());
                request.setMultiplier(dto.getMultiplier());
                request.setFee(dto.getFee());
                request.setThreshold(dto.getThreshold());
                request.setFoldRatio(dto.getFoldRatio());
                request.setKeywords(dto.getKeywords());
                request.setExcludeKeywords(dto.getExcludeKeywords());
                request.setMaterials(dto.getMaterials());
                request.setTemperature(dto.getTemperature());
                request.setBagSizeEquals(dto.getBagSizeEquals());
                request.setMaxBagSizeExclusive(dto.getMaxBagSizeExclusive());
                request.setMinInstrumentCount(dto.getMinInstrumentCount());
                request.setMaxInstrumentCount(dto.getMaxInstrumentCount());
                request.setSkipPackaging(dto.getSkipPackaging());
                request.setSkipDiscount(dto.getSkipDiscount());
                request.setIsActive(dto.getIsActive());
                if (validateProductRuleRequest(request, null) != null) {
                    continue;
                }
                CustomerProductRule rule = buildProductRuleEntity(customerId, request);
                productRuleMapper.insert(rule);
                continue;
            }
            if (dto.getName() == null) {
                continue;
            }
            CustomerProductRule rule = new CustomerProductRule();
            rule.setCustomerId(customerId);
            rule.setRuleType(dto.getRuleType());
            rule.setName(dto.getName());
            rule.setPriority(dto.getPriority() != null ? dto.getPriority() : 100);
            rule.setProductId(dto.getProductId());
            rule.setMatchMode(dto.getMatchMode() != null ? dto.getMatchMode() : "first");
            rule.setKeywords(dto.getKeywords() != null ? JsonUtils.toJson(dto.getKeywords()) : null);
            rule.setExcludeKeywords(dto.getExcludeKeywords() != null ? JsonUtils.toJson(dto.getExcludeKeywords()) : null);
            rule.setMaterials(dto.getMaterials() != null ? JsonUtils.toJson(dto.getMaterials()) : null);
            rule.setTemperature(normalizeTemperatureOptional(dto.getTemperature()));
            rule.setBagSizeEquals(dto.getBagSizeEquals());
            rule.setMaxBagSizeExclusive(dto.getMaxBagSizeExclusive());
            rule.setMinInstrumentCount(dto.getMinInstrumentCount());
            rule.setMaxInstrumentCount(dto.getMaxInstrumentCount());
            rule.setPrice(dto.getPrice());
            rule.setAcceptedPrices(serializeDecimalList(dto.getAcceptedPrices()));
            rule.setFee(dto.getFee());
            rule.setMultiplier(dto.getMultiplier());
            rule.setThreshold(dto.getThreshold());
            rule.setFoldRatio(dto.getFoldRatio());
            rule.setSkipPackaging(dto.getSkipPackaging() != null ? dto.getSkipPackaging() : false);
            rule.setSkipDiscount(dto.getSkipDiscount() != null ? dto.getSkipDiscount() : false);
            rule.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
            productRuleMapper.insert(rule);
        }
        billingRuleGroupSyncService.syncDefaultGroupFromProductRules(customerId, null);
    }

    private String validateProductRuleRequest(SaveCustomerProductRuleRequest request, Long excludeRuleId) {
        String ruleType = request.getRuleType();
        if (!PRICING_RULE_TYPES.contains(ruleType)) {
            return "不支持的规则类型: " + ruleType;
        }
        boolean hasKeywords = request.getKeywords() != null
                && request.getKeywords().stream().anyMatch(k -> k != null && !k.isBlank());
        if (requiresProductBinding(ruleType)) {
            if (request.getProductId() == null && !hasKeywords) {
                return "请绑定商品或填写匹配关键词";
            }
            if (request.getProductId() != null && productMapper.selectById(request.getProductId()) == null) {
                return "商品不存在";
            }
        } else if (request.getProductId() != null && productMapper.selectById(request.getProductId()) == null) {
            return "商品不存在";
        }
        boolean hasName = request.getName() != null && !request.getName().isBlank();
        if (!requiresProductBinding(ruleType)
                && request.getProductId() == null
                && !hasKeywords
                && !(SETTLEMENT_RULE_TYPES.contains(ruleType) && hasName)) {
            return "请绑定商品或填写匹配关键词";
        }
        if ("FIXED_PRICE".equals(ruleType) || "PRICE_PER_INSTRUMENT".equals(ruleType)) {
            if ("any_price".equalsIgnoreCase(normalizeMatchMode(request.getMatchMode()))) {
                if (cleanDecimalList(request.getAcceptedPrices()).size() < 2) {
                    return "多报价模式至少需要 2 个候选价格";
                }
            } else if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                return "固定价格必须大于 0";
            }
            String billingModeError = validateBillingModeRequest(request);
            if (billingModeError != null) {
                return billingModeError;
            }
        } else if ("MULTIPLIER".equals(ruleType)) {
            if (request.getMultiplier() == null) {
                return "倍率不能为空";
            }
            if (request.getMultiplier().compareTo(new BigDecimal("0.01")) < 0
                    || request.getMultiplier().compareTo(new BigDecimal("99")) > 0) {
                return "倍率须在 0.01 ~ 99 之间";
            }
        } else if ("FOLD".equals(ruleType)) {
            if (request.getThreshold() == null || request.getThreshold() <= 0) {
                return "折算阈值必须大于 0";
            }
            if (request.getFoldRatio() == null || request.getFoldRatio().compareTo(BigDecimal.ZERO) <= 0) {
                return "折算除数必须大于 0";
            }
        } else if ("EXTRA_FEE".equals(ruleType) || "ADD_FEE".equals(ruleType)) {
            if (request.getFee() == null || request.getFee().compareTo(BigDecimal.ZERO) <= 0) {
                return "加收金额必须大于 0";
            }
        }
        String keywordConflict = validateKeywordExclusion(request.getKeywords(), request.getExcludeKeywords());
        if (keywordConflict != null) {
            return keywordConflict;
        }
        return null;
    }

    private static boolean requiresProductBinding(String ruleType) {
        return PRODUCT_BOUND_RULE_TYPES.contains(ruleType);
    }

    private String validateBillingModeRequest(SaveCustomerProductRuleRequest request) {
        if (request.getBillingMode() != null && !request.getBillingMode().isBlank()
                && !BILLING_MODES.contains(request.getBillingMode().trim().toUpperCase())) {
            return "不支持的计价方式: " + request.getBillingMode();
        }
        if (request.getPieceCountSource() != null && !request.getPieceCountSource().isBlank()
                && !PIECE_COUNT_SOURCES.contains(request.getPieceCountSource().trim().toUpperCase())) {
            return "不支持的件数来源: " + request.getPieceCountSource();
        }
        BillingMode mode = BillingMode.fromString(request.getBillingMode());
        if (mode == null) {
            mode = BillingModeInference.inferFromRuleTypeAndKeywords(
                    request.getRuleType(),
                    request.getKeywords() != null ? request.getKeywords() : List.of());
        }
        if (mode == BillingMode.PACK_NAME_SUFFIX) {
            boolean hasKeywords = request.getKeywords() != null
                    && request.getKeywords().stream().anyMatch(k -> k != null && !k.isBlank());
            if (!hasKeywords) {
                return "按包名后缀数字计价时，匹配关键词不能为空";
            }
        }
        return null;
    }

    private CustomerProductRule buildProductRuleEntity(Long customerId, SaveCustomerProductRuleRequest request) {
        Product product = request.getProductId() != null ? productMapper.selectById(request.getProductId()) : null;
        CustomerProductRule rule = new CustomerProductRule();
        rule.setCustomerId(customerId);
        applyProductRuleRequest(rule, request);
        if (rule.getName() == null || rule.getName().isBlank()) {
            if (product != null) {
                rule.setName(product.getName());
            } else if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
                rule.setName(request.getKeywords().get(0).trim());
            } else {
                rule.setName("特色计价策略");
            }
        }
        return rule;
    }

    private void applyProductRuleRequest(CustomerProductRule rule, SaveCustomerProductRuleRequest request) {
        Product product = request.getProductId() != null ? productMapper.selectById(request.getProductId()) : null;
        rule.setRuleType(request.getRuleType());
        rule.setMatchMode(normalizeMatchMode(request.getMatchMode()));
        rule.setProductId(request.getProductId());
        rule.setName(ProductRuleNameUtils.resolveProductRuleName(
                request.getName(), product, request.getKeywords()));
        rule.setPriority(request.getPriority() != null ? request.getPriority() : 100);
        rule.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        rule.setKeywords(serializeStringList(request.getKeywords()));
        rule.setExcludeKeywords(serializeStringList(request.getExcludeKeywords()));
        rule.setMaterials(serializeStringList(request.getMaterials()));
        rule.setTemperature(normalizeTemperatureOptional(request.getTemperature()));
        rule.setBagSizeEquals(request.getBagSizeEquals());
        rule.setMaxBagSizeExclusive(request.getMaxBagSizeExclusive());
        rule.setMinInstrumentCount(request.getMinInstrumentCount());
        rule.setMaxInstrumentCount(request.getMaxInstrumentCount());
        rule.setThreshold(request.getThreshold());
        rule.setFoldRatio(request.getFoldRatio());
        rule.setFee(request.getFee());
        applyBillingModeFields(rule, request);
        String ruleType = rule.getRuleType();
        if ("FIXED_PRICE".equals(ruleType) || "PRICE_PER_INSTRUMENT".equals(ruleType)) {
            List<BigDecimal> accepted = cleanDecimalList(request.getAcceptedPrices());
            if ("any_price".equalsIgnoreCase(rule.getMatchMode()) && !accepted.isEmpty()) {
                rule.setAcceptedPrices(serializeDecimalList(accepted));
                rule.setPrice(accepted.get(0));
            } else {
                rule.setPrice(request.getPrice());
                rule.setAcceptedPrices(null);
            }
            rule.setMultiplier(null);
            rule.setFee(null);
            rule.setSkipPackaging(Boolean.TRUE.equals(request.getSkipPackaging()));
            rule.setSkipDiscount(request.getSkipDiscount() != null
                    ? request.getSkipDiscount()
                    : Boolean.TRUE.equals(rule.getSkipDiscount()));
        } else if ("MULTIPLIER".equals(ruleType)) {
            rule.setMultiplier(request.getMultiplier());
            rule.setPrice(null);
            rule.setFee(null);
            rule.setSkipPackaging(Boolean.TRUE.equals(request.getSkipPackaging()));
            rule.setSkipDiscount(request.getSkipDiscount() != null ? request.getSkipDiscount() : false);
        } else if ("FOLD".equals(ruleType)) {
            rule.setPrice(null);
            rule.setMultiplier(null);
            rule.setFee(null);
            rule.setSkipPackaging(false);
            rule.setSkipDiscount(false);
        } else if ("EXTRA_FEE".equals(ruleType) || "ADD_FEE".equals(ruleType)) {
            rule.setPrice(null);
            rule.setMultiplier(null);
            rule.setSkipPackaging(false);
            rule.setSkipDiscount(false);
        }
    }

    private void applyBillingModeFields(CustomerProductRule rule, SaveCustomerProductRuleRequest request) {
        String ruleType = request.getRuleType();
        if (!"FIXED_PRICE".equals(ruleType) && !"PRICE_PER_INSTRUMENT".equals(ruleType)) {
            rule.setBillingMode(null);
            rule.setPieceCountSource(null);
            return;
        }
        BillingMode mode = BillingMode.fromString(request.getBillingMode());
        if (mode == null) {
            mode = BillingModeInference.inferFromRuleTypeAndKeywords(
                    ruleType,
                    request.getKeywords() != null ? request.getKeywords() : List.of());
        }
        rule.setBillingMode(mode.name());
        rule.setRuleType(BillingModeInference.inferRuleType(mode));
        String pieceCountSource = request.getPieceCountSource();
        if (pieceCountSource == null || pieceCountSource.isBlank()) {
            pieceCountSource = BillingModeInference.defaultPieceCountSource(mode);
        }
        rule.setPieceCountSource(pieceCountSource);
    }

    private String serializeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> cleaned = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (cleaned.isEmpty()) {
            return null;
        }
        return JsonUtils.toJson(cleaned);
    }

    private Optional<CustomerProductRule> findDuplicatePricingRule(
            Long customerId, SaveCustomerProductRuleRequest request, Long excludeRuleId) {
        return productRuleMapper.selectByCustomerId(customerId).stream()
                .filter(rule -> PRICING_RULE_TYPES.contains(rule.getRuleType()))
                .filter(rule -> excludeRuleId == null || !excludeRuleId.equals(rule.getId()))
                .filter(rule -> Objects.equals(rule.getProductId(), request.getProductId()))
                .filter(rule -> hasSameMatchSignature(rule, request))
                .findFirst();
    }

    private boolean hasSameMatchSignature(CustomerProductRule existing, SaveCustomerProductRuleRequest request) {
        return Objects.equals(existing.getRuleType(), request.getRuleType())
                && Objects.equals(normalizeMatchMode(existing.getMatchMode()), normalizeMatchMode(request.getMatchMode()))
                && Objects.equals(existing.getProductId(), request.getProductId())
                && Objects.equals(normalizeJsonList(existing.getMaterials()), normalizeJsonList(serializeStringList(request.getMaterials())))
                && Objects.equals(normalizeJsonList(existing.getKeywords()), normalizeJsonList(serializeStringList(request.getKeywords())))
                && Objects.equals(normalizeJsonList(existing.getExcludeKeywords()), normalizeJsonList(serializeStringList(request.getExcludeKeywords())))
                && Objects.equals(existing.getBagSizeEquals(), request.getBagSizeEquals())
                && Objects.equals(existing.getMaxBagSizeExclusive(), request.getMaxBagSizeExclusive())
                && Objects.equals(existing.getMinInstrumentCount(), request.getMinInstrumentCount())
                && Objects.equals(existing.getMaxInstrumentCount(), request.getMaxInstrumentCount())
                && Objects.equals(
                        normalizeTemperatureOptional(existing.getTemperature()),
                        normalizeTemperatureOptional(request.getTemperature()));
    }

    private String normalizeJsonList(String json) {
        if (json == null || json.isBlank()) {
            return "[]";
        }
        return json.trim();
    }

    private CustomerProductRuleResponse toProductRuleResponse(CustomerProductRule rule) {
        Product product = rule.getProductId() != null ? productMapper.selectById(rule.getProductId()) : null;
        return CustomerProductRuleResponse.builder()
                .id(rule.getId())
                .customerId(rule.getCustomerId())
                .ruleType(rule.getRuleType())
                .matchMode(rule.getMatchMode())
                .name(rule.getName())
                .priority(rule.getPriority())
                .productId(rule.getProductId())
                .productName(product != null ? product.getName() : null)
                .keywords(parseStringList(rule.getKeywords()))
                .excludeKeywords(parseStringList(rule.getExcludeKeywords()))
                .materials(parseStringList(rule.getMaterials()))
                .temperature(rule.getTemperature())
                .bagSizeEquals(rule.getBagSizeEquals())
                .maxBagSizeExclusive(rule.getMaxBagSizeExclusive())
                .minInstrumentCount(rule.getMinInstrumentCount())
                .maxInstrumentCount(rule.getMaxInstrumentCount())
                .price(rule.getPrice())
                .acceptedPrices(parseDecimalList(rule.getAcceptedPrices()))
                .fixedPrice(rule.getPrice())
                .multiplier(rule.getMultiplier())
                .fee(rule.getFee())
                .threshold(rule.getThreshold())
                .foldRatio(rule.getFoldRatio())
                .billingMode(rule.getBillingMode())
                .pieceCountSource(rule.getPieceCountSource())
                .conditionsJson(rule.getConditionsJson())
                .skipPackaging(rule.getSkipPackaging())
                .skipDiscount(rule.getSkipDiscount())
                .isActive(rule.getIsActive())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JsonUtils.getObjectMapper().readValue(
                    json,
                    JsonUtils.getObjectMapper().getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private CustomerResponse toResponse(Customer customer) {
        List<CustomerAliasDto> aliases = aliasMapper.selectByCustomerId(customer.getId()).stream()
                .map(this::toAliasDto)
                .collect(Collectors.toList());
        List<CustomerDiscountDto> discounts = loadDiscounts(customer.getId());
        List<CustomerProductRuleDto> productRules = productRuleMapper.selectByCustomerId(customer.getId()).stream()
                .map(this::toProductRuleDto)
                .collect(Collectors.toList());

        return CustomerResponse.builder()
                .id(customer.getId())
                .code(customer.getCode())
                .canonicalName(customer.getCanonicalName())
                .status(customer.getStatus())
                .capMode(customer.getCapMode())
                .chargeDoubleBagWhenCapped(customer.getChargeDoubleBagWhenCapped())
                .defaultRuleId(customer.getDefaultRuleId())
                .billingEnabled(customer.getBillingEnabled())
                .billingPricingMode(customer.getBillingPricingMode())
                .pathOverride(parsePathOverrideDto(customer.getPathOverride()))
                .exportNameMapping(customer.getExportNameMapping())
                .standardPricingOverride(customer.getStandardPricingOverride())
                .notes(customer.getNotes())
                .aliases(aliases)
                .discounts(discounts)
                .productRules(productRules)
                .aliasCount(aliases.size())
                .departmentCount(departmentEntryMapper.countActiveByCustomerId(customer.getId()))
                .physicianCount(physicianEntryMapper.countActiveByCustomerId(customer.getId()))
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }

    private CustomerAliasDto toAliasDto(CustomerAlias alias) {
        CustomerAliasDto dto = new CustomerAliasDto();
        dto.setId(alias.getId());
        dto.setAlias(alias.getAlias());
        dto.setMatchType(alias.getMatchType());
        dto.setSource(alias.getSource());
        dto.setPriority(alias.getPriority());
        dto.setIsActive(alias.getIsActive());
        return dto;
    }

    private CustomerDiscountDto toDiscountDto(CustomerDiscount discount) {
        CustomerDiscountDto dto = new CustomerDiscountDto();
        dto.setId(discount.getId());
        dto.setName(discount.getName());
        dto.setDiscountRate(discount.getDiscountRate());
        dto.setApplyStage(discount.getApplyStage());
        dto.setApplyStages(readApplyStagesFromLegacyStage(discount.getApplyStage()));
        dto.setSkipWhenFixedPrice(discount.getSkipWhenFixedPrice());
        dto.setPriority(discount.getPriority());
        dto.setIsActive(discount.getIsActive());
        dto.setEffectiveFrom(discount.getEffectiveFrom());
        dto.setEffectiveTo(discount.getEffectiveTo());
        return dto;
    }

    private CustomerProductRuleDto toProductRuleDto(CustomerProductRule rule) {
        CustomerProductRuleDto dto = new CustomerProductRuleDto();
        dto.setId(rule.getId());
        dto.setRuleType(rule.getRuleType());
        dto.setMatchMode(rule.getMatchMode());
        dto.setName(rule.getName());
        dto.setPriority(rule.getPriority());
        dto.setProductId(rule.getProductId());
        if (rule.getKeywords() != null && !rule.getKeywords().isBlank()) {
            try {
                dto.setKeywords(JsonUtils.getObjectMapper().readValue(
                        rule.getKeywords(),
                        JsonUtils.getObjectMapper().getTypeFactory().constructCollectionType(List.class, String.class)
                ));
            } catch (Exception ignored) {
                dto.setKeywords(List.of());
            }
        }
        if (rule.getExcludeKeywords() != null && !rule.getExcludeKeywords().isBlank()) {
            try {
                dto.setExcludeKeywords(JsonUtils.getObjectMapper().readValue(
                        rule.getExcludeKeywords(),
                        JsonUtils.getObjectMapper().getTypeFactory().constructCollectionType(List.class, String.class)
                ));
            } catch (Exception ignored) {
                dto.setExcludeKeywords(List.of());
            }
        }
        if (rule.getMaterials() != null && !rule.getMaterials().isBlank()) {
            try {
                dto.setMaterials(JsonUtils.getObjectMapper().readValue(
                        rule.getMaterials(),
                        JsonUtils.getObjectMapper().getTypeFactory().constructCollectionType(List.class, String.class)
                ));
            } catch (Exception ignored) {
                dto.setMaterials(List.of());
            }
        }
        dto.setBagSizeEquals(rule.getBagSizeEquals());
        dto.setTemperature(rule.getTemperature());
        dto.setMaxBagSizeExclusive(rule.getMaxBagSizeExclusive());
        dto.setMinInstrumentCount(rule.getMinInstrumentCount());
        dto.setMaxInstrumentCount(rule.getMaxInstrumentCount());
        dto.setPrice(rule.getPrice());
        dto.setAcceptedPrices(parseDecimalList(rule.getAcceptedPrices()));
        dto.setFee(rule.getFee());
        dto.setMultiplier(rule.getMultiplier());
        dto.setProductName(rule.getProductId() != null
                ? Optional.ofNullable(productMapper.selectById(rule.getProductId()))
                        .map(Product::getName)
                        .orElse(null)
                : null);
        dto.setThreshold(rule.getThreshold());
        dto.setFoldRatio(rule.getFoldRatio());
        dto.setSkipPackaging(rule.getSkipPackaging());
        dto.setSkipDiscount(rule.getSkipDiscount());
        dto.setIsActive(rule.getIsActive());
        return dto;
    }

    private String normalizeMatchMode(String matchMode) {
        return matchMode != null && !matchMode.isBlank() ? matchMode.trim() : "first";
    }

    private List<BigDecimal> cleanDecimalList(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0)
                .distinct()
                .toList();
    }

    private String serializeDecimalList(List<BigDecimal> values) {
        List<BigDecimal> cleaned = cleanDecimalList(values);
        if (cleaned.isEmpty()) {
            return null;
        }
        return JsonUtils.toJson(cleaned);
    }

    private List<BigDecimal> parseDecimalList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JsonUtils.getObjectMapper().readValue(
                    json,
                    JsonUtils.getObjectMapper().getTypeFactory().constructCollectionType(List.class, BigDecimal.class)
            );
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String validateKeywordExclusion(List<String> keywords, List<String> excludeKeywords) {
        if (keywords == null || excludeKeywords == null) {
            return null;
        }
        Set<String> normalizedKeywords = keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> k.trim().toLowerCase())
                .collect(Collectors.toSet());
        for (String exclude : excludeKeywords) {
            if (exclude == null || exclude.isBlank()) {
                continue;
            }
            String normalizedExclude = exclude.trim().toLowerCase();
            if (normalizedKeywords.contains(normalizedExclude)) {
                return "排除关键词不能与匹配关键词重复：" + exclude.trim();
            }
        }
        return null;
    }

    private List<CustomerDiscountDto> loadDiscounts(Long customerId) {
        List<CustomerBillingPolicy> policies = billingPolicyMapper.selectByCustomerIdAndType(customerId, "DISCOUNT");
        if (!policies.isEmpty()) {
            return policies.stream().map(this::discountDtoFromPolicy).collect(Collectors.toList());
        }
        return discountMapper.selectByCustomerId(customerId).stream()
                .map(this::toDiscountDto)
                .collect(Collectors.toList());
    }

    private CustomerDiscountDto discountDtoFromPolicy(CustomerBillingPolicy policy) {
        CustomerDiscountDto dto = new CustomerDiscountDto();
        dto.setId(policy.getId());
        dto.setName(policy.getName());
        dto.setPriority(policy.getPriority());
        dto.setIsActive(policy.getIsActive());
        dto.setApplyStage(readPolicyApplyStage(policy.getParams()));
        dto.setApplyStages(readPolicyApplyStages(policy.getParams()));
        dto.setTemperature(readPolicyTemperature(policy.getScope()));
        Map<String, Object> params = readPolicyParams(policy.getParams());
        Object rate = params.get("rate");
        if (rate instanceof Number number) {
            dto.setDiscountRate(BigDecimal.valueOf(number.doubleValue()));
        }
        Object skipWhenFixedPrice = params.get("skipWhenFixedPrice");
        dto.setSkipWhenFixedPrice(skipWhenFixedPrice instanceof Boolean bool ? bool : false);
        return dto;
    }

    private CustomerBillingPolicyResponse toBillingPolicyResponse(CustomerBillingPolicy policy) {
        Map<String, Object> params = readPolicyParams(policy.getParams());
        BigDecimal rate = null;
        Object rateValue = params.get("rate");
        if (rateValue instanceof Number number) {
            rate = BigDecimal.valueOf(number.doubleValue());
        }
        BigDecimal feePerTrip = null;
        Object feeValue = params.get("feePerTrip");
        if (feeValue instanceof Number number) {
            feePerTrip = BigDecimal.valueOf(number.doubleValue());
        }
        BigDecimal minCharge = null;
        Object minChargeValue = params.get("minCharge");
        if (minChargeValue instanceof Number number) {
            minCharge = BigDecimal.valueOf(number.doubleValue());
        }
        BigDecimal maxCap = null;
        Object maxCapValue = params.get("maxCap");
        if (maxCapValue instanceof Number number) {
            maxCap = BigDecimal.valueOf(number.doubleValue());
        }
        BigDecimal baseMultiplier = readBigDecimalParam(params, "baseMultiplier");
        BigDecimal adjustedMultiplier = readBigDecimalParam(params, "adjustedMultiplier");
        BigDecimal urgentLogisticsFeePerTrip = readBigDecimalParam(params, "urgentLogisticsFeePerTrip");
        BigDecimal urgentLogisticsDiscountRate = readBigDecimalParam(params, "urgentLogisticsDiscountRate");
        BigDecimal monthlyAmount = readBigDecimalParam(params, "monthlyAmount");
        Object skipWhenFixedPrice = params.get("skipWhenFixedPrice");
        String tripSource = params.get("tripSource") != null ? params.get("tripSource").toString() : null;
        String allocationMode = params.get("allocationMode") != null ? params.get("allocationMode").toString() : null;
        java.util.List<Integer> billingWeekdays = readIntegerListParam(params.get("billingWeekdays"));
        java.util.List<String> excludeDepartments = readStringListParam(params.get("excludeDepartments"));
        Boolean cardDeductionEnabled = readBooleanParam(params, "cardDeductionEnabled");
        String cardDeductMode = params.get("cardDeductMode") != null ? params.get("cardDeductMode").toString() : null;
        BigDecimal cardMonthlyCap = readBigDecimalParam(params, "cardMonthlyCap");
        Long logisticsMergeGroupId = readLongParam(params, "logisticsMergeGroupId");
        Boolean mergeSameDay = readBooleanParam(params, "mergeSameDay");
        Long singleOwnerCustomerId = readLongParam(params, "singleOwnerCustomerId");
        return CustomerBillingPolicyResponse.builder()
                .id(policy.getId())
                .customerId(policy.getCustomerId())
                .policyType(policy.getPolicyType())
                .name(policy.getName())
                .temperature(readPolicyTemperature(policy.getScope()))
                .rate(rate)
                .skipWhenFixedPrice(skipWhenFixedPrice instanceof Boolean bool ? bool : null)
                .feePerTrip(feePerTrip)
                .tripSource(tripSource)
                .allocationMode(allocationMode)
                .billingWeekdays(billingWeekdays)
                .excludeDepartments(excludeDepartments)
                .cardDeductionEnabled(cardDeductionEnabled)
                .cardDeductMode(cardDeductMode)
                .cardMonthlyCap(cardMonthlyCap)
                .logisticsMergeGroupId(logisticsMergeGroupId)
                .mergeSameDay(mergeSameDay)
                .singleOwnerCustomerId(singleOwnerCustomerId)
                .minCharge(minCharge)
                .maxCap(maxCap)
                .applyStage(readPolicyApplyStage(policy.getParams()))
                .baseMultiplier(baseMultiplier)
                .adjustedMultiplier(adjustedMultiplier)
                .urgentLogisticsFeePerTrip(urgentLogisticsFeePerTrip)
                .urgentLogisticsDiscountRate(urgentLogisticsDiscountRate)
                .monthlyAmount(monthlyAmount)
                .priority(policy.getPriority())
                .isActive(policy.getIsActive())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }

    private CustomerBillingPolicy buildBillingPolicyEntity(Long customerId, SaveCustomerBillingPolicyRequest request) {
        CustomerBillingPolicy policy = new CustomerBillingPolicy();
        policy.setCustomerId(customerId);
        applyBillingPolicyRequest(policy, request);
        return policy;
    }

    private void applyBillingPolicyRequest(CustomerBillingPolicy policy, SaveCustomerBillingPolicyRequest request) {
        policy.setPolicyType(request.getPolicyType().trim().toUpperCase());
        policy.setName(request.getName());
        policy.setPriority(request.getPriority() != null ? request.getPriority() : 100);
        policy.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        policy.setScope(buildPolicyScopeJson(request));
        policy.setParams(buildPolicyParamsJson(request));
    }

    private String validateBillingPolicyRequest(SaveCustomerBillingPolicyRequest request) {
        if (request.getPolicyType() == null || request.getPolicyType().isBlank()) {
            return "策略类型不能为空";
        }
        String policyType = request.getPolicyType().trim().toUpperCase();
        if ("DISCOUNT".equals(policyType)) {
            if (request.getRate() == null || request.getRate().compareTo(BigDecimal.ZERO) <= 0) {
                return "折扣率必须大于 0";
            }
            String temperature = normalizeTemperature(request.getTemperature());
            if (!TEMPERATURE_SCOPES.contains(temperature)) {
                return "温度作用域无效";
            }
        } else if ("LOGISTICS".equals(policyType)) {
            if (request.getFeePerTrip() == null || request.getFeePerTrip().compareTo(BigDecimal.ZERO) <= 0) {
                return "物流单价必须大于 0";
            }
        } else if ("MONTHLY_SETTLEMENT".equals(policyType)) {
            boolean hasMin = request.getMinCharge() != null && request.getMinCharge().compareTo(BigDecimal.ZERO) > 0;
            boolean hasMax = request.getMaxCap() != null && request.getMaxCap().compareTo(BigDecimal.ZERO) > 0;
            if (!hasMin && !hasMax) {
                return "月度结算策略需配置最低消费或封顶金额";
            }
            if (hasMin && hasMax && request.getMinCharge().compareTo(request.getMaxCap()) > 0) {
                return "最低消费不能大于封顶金额";
            }
        } else if ("URGENT".equals(policyType)) {
            if (request.getBaseMultiplier() != null
                    && request.getBaseMultiplier().compareTo(BigDecimal.ZERO) <= 0) {
                return "加急倍率必须大于 0";
            }
            if (request.getAdjustedMultiplier() != null
                    && request.getAdjustedMultiplier().compareTo(BigDecimal.ZERO) <= 0) {
                return "减免后加急倍率必须大于 0";
            }
        } else if ("DEDUCTION".equals(policyType)) {
            if (request.getMonthlyAmount() == null || request.getMonthlyAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return "设备抵扣金额必须大于 0";
            }
        } else {
            return "不支持的策略类型: " + policyType;
        }
        return null;
    }

    private String buildPolicyScopeJson(SaveCustomerBillingPolicyRequest request) {
        Map<String, String> scope = new LinkedHashMap<>();
        if ("DISCOUNT".equalsIgnoreCase(request.getPolicyType())) {
            scope.put("temperature", normalizeTemperature(request.getTemperature()));
        }
        return JsonUtils.toJson(scope);
    }

    private String buildPolicyParamsJson(SaveCustomerBillingPolicyRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        if ("DISCOUNT".equalsIgnoreCase(request.getPolicyType())) {
            params.put("rate", request.getRate());
            params.put("skipWhenFixedPrice",
                    request.getSkipWhenFixedPrice() != null ? request.getSkipWhenFixedPrice() : false);
            writeDiscountApplyStages(params, request.getApplyStages(), request.getApplyStage());
        } else if ("LOGISTICS".equalsIgnoreCase(request.getPolicyType())) {
            params.put("feePerTrip", request.getFeePerTrip());
            if (request.getTripSource() != null && !request.getTripSource().isBlank()) {
                params.put("tripSource", request.getTripSource().trim().toLowerCase());
            }
            if (request.getAllocationMode() != null && !request.getAllocationMode().isBlank()) {
                params.put("allocationMode", request.getAllocationMode().trim().toLowerCase());
            }
            if (request.getBillingWeekdays() != null && !request.getBillingWeekdays().isEmpty()) {
                params.put("billingWeekdays", request.getBillingWeekdays());
            }
            if (request.getExcludeDepartments() != null && !request.getExcludeDepartments().isEmpty()) {
                params.put("excludeDepartments", request.getExcludeDepartments());
            }
            if (request.getCardDeductionEnabled() != null) {
                params.put("cardDeductionEnabled", request.getCardDeductionEnabled());
            }
            if (request.getCardDeductMode() != null && !request.getCardDeductMode().isBlank()) {
                params.put("cardDeductMode", request.getCardDeductMode().trim().toLowerCase());
            }
            if (request.getCardMonthlyCap() != null) {
                params.put("cardMonthlyCap", request.getCardMonthlyCap());
            }
            if (request.getLogisticsMergeGroupId() != null) {
                params.put("logisticsMergeGroupId", request.getLogisticsMergeGroupId());
            }
            if (request.getMergeSameDay() != null) {
                params.put("mergeSameDay", request.getMergeSameDay());
            }
            if (request.getSingleOwnerCustomerId() != null) {
                params.put("singleOwnerCustomerId", request.getSingleOwnerCustomerId());
            }
        } else if ("MONTHLY_SETTLEMENT".equalsIgnoreCase(request.getPolicyType())) {
            if (request.getMinCharge() != null) {
                params.put("minCharge", request.getMinCharge());
            }
            if (request.getMaxCap() != null) {
                params.put("maxCap", request.getMaxCap());
            }
        } else if ("URGENT".equalsIgnoreCase(request.getPolicyType())) {
            if (request.getBaseMultiplier() != null) {
                params.put("baseMultiplier", request.getBaseMultiplier());
            }
            if (request.getAdjustedMultiplier() != null) {
                params.put("adjustedMultiplier", request.getAdjustedMultiplier());
            }
            if (request.getUrgentLogisticsFeePerTrip() != null) {
                params.put("urgentLogisticsFeePerTrip", request.getUrgentLogisticsFeePerTrip());
            }
            if (request.getUrgentLogisticsDiscountRate() != null) {
                params.put("urgentLogisticsDiscountRate", request.getUrgentLogisticsDiscountRate());
            }
        } else if ("DEDUCTION".equalsIgnoreCase(request.getPolicyType())) {
            if (request.getMonthlyAmount() != null) {
                params.put("monthlyAmount", request.getMonthlyAmount());
            }
        }
        return JsonUtils.toJson(params);
    }

    private String normalizeBillingPricingMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "standard";
        }
        String normalized = mode.trim().toLowerCase();
        return BILLING_PRICING_MODES.contains(normalized) ? normalized : "standard";
    }

    private String buildPathOverrideJson(CustomerPathOverrideDto pathOverride) {
        if (pathOverride == null) {
            return null;
        }
        boolean hasDisable = Boolean.TRUE.equals(pathOverride.getDisableLowTemp());
        boolean hasForce = pathOverride.getForceHighTempUnitPrice() != null
                && pathOverride.getForceHighTempUnitPrice().compareTo(BigDecimal.ZERO) > 0;
        if (!hasDisable && !hasForce) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        if (hasDisable) {
            json.put("disableLowTemp", true);
        }
        if (hasForce) {
            json.put("forceHighTempUnitPrice", pathOverride.getForceHighTempUnitPrice());
        }
        return JsonUtils.toJson(json);
    }

    private String normalizeExportNameMapping(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if ("{}".equals(trimmed)) {
            return null;
        }
        try {
            Map<String, Object> parsed = readPolicyParams(trimmed);
            if (parsed == null || parsed.isEmpty()) {
                return null;
            }
            return JsonUtils.toJson(parsed);
        } catch (Exception e) {
            return null;
        }
    }

    /** Validates and normalizes {@link SaveCustomerRequest#getStandardPricingOverride()}; mutates request on success. */
    private String resolveStandardPricingOverride(SaveCustomerRequest request) {
        String raw = request.getStandardPricingOverride();
        if (raw == null || raw.isBlank()) {
            request.setStandardPricingOverride(null);
            return null;
        }
        String trimmed = raw.trim();
        if ("{}".equals(trimmed)) {
            request.setStandardPricingOverride(null);
            return null;
        }
        try {
            Map<String, Object> parsed = JsonUtils.getObjectMapper().readValue(trimmed, Map.class);
            if (parsed == null || parsed.isEmpty()) {
                request.setStandardPricingOverride(null);
                return null;
            }
            request.setStandardPricingOverride(JsonUtils.toJson(parsed));
            return null;
        } catch (Exception e) {
            return "标准灭菌价覆盖 JSON 格式无效，请检查语法与结构";
        }
    }

    private CustomerPathOverrideDto parsePathOverrideDto(String pathOverrideJson) {
        if (pathOverrideJson == null || pathOverrideJson.isBlank()) {
            return null;
        }
        Map<String, Object> raw = readPolicyParams(pathOverrideJson);
        if (raw.isEmpty()) {
            return null;
        }
        CustomerPathOverrideDto dto = new CustomerPathOverrideDto();
        Object disableLowTemp = raw.get("disableLowTemp");
        if (disableLowTemp instanceof Boolean bool) {
            dto.setDisableLowTemp(bool);
        }
        Object forcePrice = raw.get("forceHighTempUnitPrice");
        if (forcePrice instanceof Number number) {
            dto.setForceHighTempUnitPrice(BigDecimal.valueOf(number.doubleValue()));
        }
        return dto;
    }

    private BigDecimal readBigDecimalParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return null;
    }

    private Boolean readBooleanParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return null;
    }

    private Long readLongParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Integer> readIntegerListParam(Object raw) {
        if (!(raw instanceof java.util.List<?> list)) {
            return null;
        }
        java.util.List<Integer> result = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                result.add(number.intValue());
            }
        }
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> readStringListParam(Object raw) {
        if (!(raw instanceof java.util.List<?> list)) {
            return null;
        }
        java.util.List<String> result = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(item.toString());
            }
        }
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPolicyParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return Map.of();
        }
        try {
            return JsonUtils.getObjectMapper().readValue(paramsJson, Map.class);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private String readPolicyApplyStage(String paramsJson) {
        List<String> stages = readPolicyApplyStages(paramsJson);
        return stages.isEmpty() ? "bill_detail" : stages.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<String> readPolicyApplyStages(String paramsJson) {
        Map<String, Object> params = readPolicyParams(paramsJson);
        Object stages = params.get("applyStages");
        if (stages instanceof List<?> list && !list.isEmpty()) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();
        }
        Object stage = params.get("applyStage");
        if (stage != null && !String.valueOf(stage).isBlank()) {
            return List.of(String.valueOf(stage).trim().toLowerCase());
        }
        return List.of("bill_detail");
    }

    private List<String> readApplyStagesFromLegacyStage(String applyStage) {
        if (applyStage == null || applyStage.isBlank()) {
            return List.of("bill_detail");
        }
        return List.of(applyStage.trim().toLowerCase());
    }

    private String resolveLegacyApplyStage(CustomerDiscountDto dto) {
        if (dto.getApplyStages() != null && !dto.getApplyStages().isEmpty()) {
            return dto.getApplyStages().get(0);
        }
        return dto.getApplyStage() != null ? dto.getApplyStage() : "after_base";
    }

    private void applyDiscountStagesToPolicyRequest(
            SaveCustomerBillingPolicyRequest policyRequest, CustomerDiscountDto dto) {
        if (dto.getApplyStages() != null && !dto.getApplyStages().isEmpty()) {
            policyRequest.setApplyStages(dto.getApplyStages());
            policyRequest.setApplyStage(dto.getApplyStages().get(0));
            return;
        }
        if (dto.getApplyStage() != null && !dto.getApplyStage().isBlank()) {
            policyRequest.setApplyStage(dto.getApplyStage().trim().toLowerCase());
        }
    }

    private void writeDiscountApplyStages(
            Map<String, Object> params, List<String> applyStages, String applyStage) {
        List<String> normalized = normalizeApplyStages(applyStages, applyStage);
        if (normalized.isEmpty()) {
            return;
        }
        params.put("applyStages", normalized);
        if (normalized.size() == 1) {
            params.put("applyStage", normalized.get(0));
        }
    }

    private List<String> normalizeApplyStages(List<String> applyStages, String applyStage) {
        List<String> source;
        if (applyStages != null && !applyStages.isEmpty()) {
            source = applyStages;
        } else if (applyStage != null && !applyStage.isBlank()) {
            source = List.of(applyStage);
        } else {
            return List.of();
        }
        return source.stream()
                .map(String::valueOf)
                .map(String::trim)
                .map(String::toLowerCase)
                .map(stage -> "after_base".equals(stage) ? "bill_detail" : stage)
                .filter(stage -> "bill_detail".equals(stage)
                        || "settlement_only".equals(stage)
                        || "export_only".equals(stage))
                .distinct()
                .toList();
    }

    private String readPolicyTemperature(String scopeJson) {
        if (scopeJson == null || scopeJson.isBlank()) {
            return "ANY";
        }
        try {
            Map<String, Object> scope = JsonUtils.getObjectMapper().readValue(scopeJson, Map.class);
            Object temperature = scope.get("temperature");
            return temperature != null ? normalizeTemperature(String.valueOf(temperature)) : "ANY";
        } catch (Exception ignored) {
            return "ANY";
        }
    }

    private String normalizeTemperature(String temperature) {
        if (temperature == null || temperature.isBlank()) {
            return "ANY";
        }
        String normalized = temperature.trim().toUpperCase();
        return TEMPERATURE_SCOPES.contains(normalized) ? normalized : "ANY";
    }

    private String normalizeTemperatureOptional(String temperature) {
        if (temperature == null || temperature.isBlank()) {
            return null;
        }
        String normalized = temperature.trim().toUpperCase();
        return TEMPERATURE_SCOPES.contains(normalized) ? normalized : null;
    }
}
