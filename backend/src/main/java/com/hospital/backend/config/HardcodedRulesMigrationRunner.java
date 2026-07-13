package com.hospital.backend.config;

import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.*;
import com.hospital.backend.mapper.*;
import com.hospital.backend.service.DefaultPricingTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 幂等迁移：将原 PricingEngine Java 硬编码规则、公司/导出/敷料默认值写入数据库。
 */
@Slf4j
@Component
@Order(110)
@RequiredArgsConstructor
public class HardcodedRulesMigrationRunner implements CommandLineRunner {

    private static final String MIGRATION_MARKER = "hardcoded_rules_migrated_v1";

    private final SysSettingMapper sysSettingMapper;
    private final HospitalPricingRuleMapper pricingRuleMapper;
    private final CustomerMapper customerMapper;
    private final CustomerAliasMapper customerAliasMapper;
    private final CustomerDiscountMapper customerDiscountMapper;
    private final CustomerProductRuleMapper customerProductRuleMapper;

    @Override
    public void run(String... args) {
        seedSysSettings();
        seedDefaultPricingRuleTemplate();
        seedMissingCustomers();
        seedEngineProductRules();
        markMigrationComplete();
    }

    private void seedSysSettings() {
        upsertSetting("company.name", "\"黑龙江省铂康医疗灭菌有限公司\"", "公司全称");
        upsertSetting("company.bank-account", "\"\"", "银行账号（生产环境通过环境变量注入）");
        upsertSetting("company.bank-name", "\"\"", "开户银行（生产环境通过环境变量注入）");
        upsertSetting("export.billFilePrefix", "\"账单_\"", "账单导出文件名前缀");
        upsertSetting("export.warningFilePrefix", "\"异常_\"", "异常表导出文件名前缀");
        upsertSetting("export.settlementFilePrefix", "\"结款函_\"", "结款函导出文件名前缀");
        upsertSetting("pricing.freeBagFeeThreshold", "16.5", "高温纸塑袋免袋费阈值");
    }

    private void upsertSetting(String key, String jsonValue, String description) {
        if (sysSettingMapper.countByKey(key) > 0) {
            return;
        }
        SysSetting setting = new SysSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(jsonValue);
        setting.setDescription(description);
        sysSettingMapper.insert(setting);
        log.info("Seeded sys_setting: {}", key);
    }

    private void seedDefaultPricingRuleTemplate() {
        List<HospitalPricingRule> existing = pricingRuleMapper.selectByNameContainingOrderByIsActiveDescUpdatedAtDesc("标准灭菌计费规则");
        if (!existing.isEmpty()) {
            return;
        }
        HospitalPricingRule rule = new HospitalPricingRule();
        rule.setName("标准灭菌计费规则");
        rule.setVersion("v2.0");
        rule.setDescription("系统默认模板（自原 PricingEngine / 铂康 id=8 迁移）");
        rule.setIsActive(true);
        rule.setRulesJson(JsonUtils.toJson(DefaultPricingTemplate.buildRulesMap()));
        pricingRuleMapper.insert(rule);
        log.info("Seeded default hospital_pricing_rule template (id={})", rule.getId());
    }

    private void seedMissingCustomers() {
        ensureCustomer("HRB-HTFH", "哈尔滨航天风华医院", null, null, false,
                List.of(alias("哈尔滨航天风华医院", "engine")),
                List.of(), List.of());
        ensureCustomer("HRB-MHM", "哈尔滨美涵美医疗美容有限公司", null, null, false,
                List.of(alias("哈尔滨美涵美医疗美容有限公司", "engine")),
                List.of(), List.of());
        ensureCustomer("HRB-DLFB", "哈尔滨市道里区妇幼保健院", null, null, false,
                List.of(alias("哈尔滨市道里区妇幼保健院", "engine")),
                List.of(), List.of());
        ensureCustomer("HLFB-SF", "黑龙江省妇幼保健院（人口）", null, null, false,
                List.of(alias("黑龙江省妇幼保健院（人口）", "engine"),
                        alias("黑龙江省妇幼保健院(人口)", "engine")),
                List.of(), List.of());
        ensureCustomer("HL-ZGH", "黑龙江总工会医院", null, null, false,
                List.of(alias("黑龙江总工会医院", "engine")),
                List.of(), List.of());
        ensureCustomer("ZXYSJT", "显著医生集团中西医结合门诊", null, "none", false,
                List.of(alias("显著医生集团中西医结合门诊", "engine")),
                List.of(), List.of());
    }

    private void seedEngineProductRules() {
        // ---- 省二院松北 fixedPrices (9) ----
        String sb = "ERYY-SB";
        ensureRule(sb, "FIXED_PRICE", "黑龙江省第二医院（松北区）3.6空心钉工具包固定单价", 10,
                bd("190.05"), List.of("3.6空心钉工具包"), null, null, true, true);
        ensureRule(sb, "FIXED_PRICE", "黑龙江省第二医院（松北区）3.6空心钉固定单价", 20,
                bd("13.3"), List.of("3.6空心钉"), null, null, true, true);
        ensureRule(sb, "FIXED_PRICE", "黑龙江省第二医院（松北区）7.3空心钉固定单价", 30,
                bd("13.3"), List.of("7.3空心钉"), null, null, true, true);
        ensureRule(sb, "FIXED_PRICE", "黑龙江省第二医院（松北区）手术衣无纺布固定单价", 40,
                bd("26.6"), List.of("手术衣"), List.of("无纺布"), null, true, true);
        ensureRule(sb, "FIXED_PRICE", "黑龙江省第二医院（松北区）手术衣纸塑袋固定单价", 50,
                bd("28.0"), List.of("手术衣"), List.of("纸塑袋"), null, true, true);
        ensureRule(sb, "FIXED_PRICE", "黑龙江省第二医院（松北区）钉固定单价", 60,
                bd("35.0"), List.of("钉"), null, null, true, true);
        ensureRule(sb, "FIXED_PRICE", "黑龙江省第二医院（松北区）软镜固定单价", 70,
                bd("210.0"), List.of("软镜"), null, null, true, true);
        ensureRule(sb, "FIXED_PRICE", "黑龙江省第二医院（松北区）泌尿显微镜头固定单价", 80,
                bd("210.0"), List.of("泌尿显微镜头"), null, null, true, true);
        ensureRule(sb, "FIXED_PRICE", "黑龙江省第二医院（松北区）小腔包固定单价", 90,
                bd("53.55"), List.of("小腔包"), null, null, true, true);

        // ---- 省二院南岗 fixedPrices (9) ----
        String ng = "ERYY-NG";
        ensureRule(ng, "FIXED_PRICE", "黑龙江省第二医院（南岗区）3.6空心钉工具包固定单价", 10,
                bd("205.45"), List.of("3.6空心钉工具包"), null, null, true, true);
        ensureRule(ng, "FIXED_PRICE", "黑龙江省第二医院（南岗区）3.6空心钉固定单价", 20,
                bd("13.3"), List.of("3.6空心钉"), null, null, true, true);
        ensureRule(ng, "FIXED_PRICE", "黑龙江省第二医院（南岗区）7.3空心钉固定单价", 30,
                bd("13.3"), List.of("7.3空心钉"), null, null, true, true);
        ensureRule(ng, "FIXED_PRICE", "黑龙江省第二医院（南岗区）手术衣无纺布固定单价", 40,
                bd("26.6"), List.of("手术衣"), List.of("无纺布"), null, true, true);
        ensureRule(ng, "FIXED_PRICE", "黑龙江省第二医院（南岗区）手术衣纸塑袋固定单价", 50,
                bd("28.0"), List.of("手术衣"), List.of("纸塑袋"), null, true, true);
        ensureRule(ng, "FIXED_PRICE", "黑龙江省第二医院（南岗区）钉固定单价", 60,
                bd("140.0"), List.of("钉"), null, null, true, true);
        ensureRule(ng, "FIXED_PRICE", "黑龙江省第二医院（南岗区）软镜固定单价", 70,
                bd("210.0"), List.of("软镜"), null, null, true, true);
        ensureRule(ng, "FIXED_PRICE", "黑龙江省第二医院（南岗区）泌尿显微镜头固定单价", 80,
                bd("210.0"), List.of("泌尿显微镜头"), null, null, true, true);
        ensureRule(ng, "FIXED_PRICE", "黑龙江省第二医院（南岗区）小腔包固定单价", 90,
                bd("49.7"), List.of("小腔包"), null, null, true, true);

        // ---- 其他 fixedPrices ----
        ensureRule("NEAU-YY", "PRICE_PER_INSTRUMENT", "东北农业大学医院洁牙机尖每件 5.5 元", 10,
                bd("5.5"), List.of("洁牙机尖"), null, null, true, false);
        ensureRule("HRB-HTFH", "PRICE_PER_INSTRUMENT", "航天风华挖勺每件 5.5 元", 10,
                bd("5.5"), List.of("挖勺"), null, null, true, false);
        ensureRule("ZXYSJT", "FIXED_PRICE", "显著医生集团 30cm 棉球固定单价", 10,
                bd("4.0"), List.of("棉球"), null, 30, true, false);

        // ---- foldRules (7) ----
        ensureFoldRule("HRB-SD-MB", "松电机扩针 5 件算 1 件", 10,
                List.of("机扩针"), null, 5, bd("5"));
        ensureFoldRule("HRB-HTFH", "航天风华镍钛锉 5 件算 1 件", 20,
                List.of("镍钛锉"), null, 5, bd("5"));
        ensureFoldRule("HRB-MHM", "美涵 20cm 以下 5 件算 1 件", 10,
                List.of(), 20, 5, bd("5"));
        ensureFoldRule("HY-HYY", "海员总医院松北 5 件算 1 件", 10,
                List.of(), null, 5, bd("5"));
        ensureFoldRule("ZYY-DSFY", "中医药大学四院 5 件算 1 件", 10,
                List.of(), null, 5, bd("5"));
        ensureFoldRule("HRB-DLFB", "道里妇幼口腔小件 5 件算 1 件", 10,
                List.of("机扩针", "镍钛锉", "根管锉", "根管针", "洁牙机尖", "车针", "探针", "球钻"),
                null, 5, bd("5"));
        ensureFoldRule("HLFB-SF", "省妇幼人口口腔小件 5 件算 1 件", 10,
                List.of("机扩针", "镍钛锉", "根管锉", "根管针", "洁牙机尖", "车针", "探针", "球钻"),
                null, 5, bd("5"));

        // ---- extraFees ----
        ensureExtraFee("HL-ZGH", "镜头租借公司筐加收", 10,
                bd("8.0"), List.of("镜头"));
    }

    private void ensureRule(String customerCode, String ruleType, String name, int priority,
                            BigDecimal price, List<String> keywords, List<String> materials,
                            Integer bagSizeEquals, boolean skipPackaging, boolean skipDiscount) {
        Customer customer = customerMapper.selectByCode(customerCode);
        if (customer == null) {
            log.warn("Customer {} not found, skip rule {}", customerCode, name);
            return;
        }
        if (customerProductRuleMapper.countByCustomerIdAndName(customer.getId(), name) > 0) {
            return;
        }
        CustomerProductRule rule = new CustomerProductRule();
        rule.setCustomerId(customer.getId());
        rule.setRuleType(ruleType);
        rule.setName(name);
        rule.setPriority(priority);
        rule.setPrice(price);
        rule.setKeywords(keywords != null ? JsonUtils.toJson(keywords) : null);
        rule.setMaterials(materials != null ? JsonUtils.toJson(materials) : null);
        rule.setBagSizeEquals(bagSizeEquals);
        rule.setSkipPackaging(skipPackaging);
        rule.setSkipDiscount(skipDiscount);
        rule.setIsActive(true);
        customerProductRuleMapper.insert(rule);
        log.info("Migrated product rule: {} → {}", customerCode, name);
    }

    private void ensureFoldRule(String customerCode, String name, int priority,
                                List<String> keywords, Integer maxBagSizeExclusive,
                                int threshold, BigDecimal foldRatio) {
        Customer customer = customerMapper.selectByCode(customerCode);
        if (customer == null) {
            return;
        }
        if (customerProductRuleMapper.countByCustomerIdAndName(customer.getId(), name) > 0) {
            return;
        }
        CustomerProductRule rule = new CustomerProductRule();
        rule.setCustomerId(customer.getId());
        rule.setRuleType("FOLD");
        rule.setName(name);
        rule.setPriority(priority);
        rule.setKeywords(keywords != null && !keywords.isEmpty() ? JsonUtils.toJson(keywords) : null);
        rule.setMaxBagSizeExclusive(maxBagSizeExclusive);
        rule.setThreshold(threshold);
        rule.setFoldRatio(foldRatio);
        rule.setIsActive(true);
        customerProductRuleMapper.insert(rule);
        log.info("Migrated fold rule: {} → {}", customerCode, name);
    }

    private void ensureExtraFee(String customerCode, String name, int priority,
                                BigDecimal fee, List<String> keywords) {
        Customer customer = customerMapper.selectByCode(customerCode);
        if (customer == null) {
            return;
        }
        if (customerProductRuleMapper.countByCustomerIdAndName(customer.getId(), name) > 0) {
            return;
        }
        CustomerProductRule rule = new CustomerProductRule();
        rule.setCustomerId(customer.getId());
        rule.setRuleType("EXTRA_FEE");
        rule.setName(name);
        rule.setPriority(priority);
        rule.setFee(fee);
        rule.setKeywords(keywords != null ? JsonUtils.toJson(keywords) : null);
        rule.setIsActive(true);
        customerProductRuleMapper.insert(rule);
        log.info("Migrated extra fee: {} → {}", customerCode, name);
    }

    private void ensureCustomer(String code, String name, Long defaultRuleId, String capMode,
                                boolean chargeDoubleBag, List<AliasSeed> aliases,
                                List<DiscountSeed> discounts, List<ProductRuleSeed> productRules) {
        if (customerMapper.selectByCode(code) != null) {
            return;
        }
        Customer customer = new Customer();
        customer.setCode(code);
        customer.setCanonicalName(name);
        customer.setStatus("active");
        customer.setDefaultRuleId(defaultRuleId);
        customer.setCapMode(capMode);
        customer.setChargeDoubleBagWhenCapped(chargeDoubleBag);
        customerMapper.insert(customer);

        for (AliasSeed a : aliases) {
            CustomerAlias alias = new CustomerAlias();
            alias.setCustomerId(customer.getId());
            alias.setAlias(a.alias);
            alias.setMatchType("contains");
            alias.setSource(a.source);
            alias.setPriority(100);
            alias.setIsActive(true);
            customerAliasMapper.insert(alias);
        }
        for (DiscountSeed d : discounts) {
            CustomerDiscount discount = new CustomerDiscount();
            discount.setCustomerId(customer.getId());
            discount.setName(d.name);
            discount.setDiscountRate(d.rate);
            discount.setApplyStage("after_base");
            discount.setSkipWhenFixedPrice(true);
            discount.setPriority(100);
            discount.setIsActive(true);
            customerDiscountMapper.insert(discount);
        }
    }

    private void markMigrationComplete() {
        if (sysSettingMapper.countByKey(MIGRATION_MARKER) > 0) {
            return;
        }
        SysSetting marker = new SysSetting();
        marker.setSettingKey(MIGRATION_MARKER);
        marker.setSettingValue("true");
        marker.setDescription("HardcodedRulesMigrationRunner v1 完成标记");
        sysSettingMapper.insert(marker);
        log.info("Hardcoded rules migration complete.");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static AliasSeed alias(String alias, String source) {
        return new AliasSeed(alias, source);
    }

    private record AliasSeed(String alias, String source) {}
    private record DiscountSeed(String name, BigDecimal rate) {}
    private record ProductRuleSeed(String ruleType, String name, int priority, BigDecimal price, List<String> keywords) {}
}
