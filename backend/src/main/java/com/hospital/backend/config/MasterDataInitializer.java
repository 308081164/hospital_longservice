package com.hospital.backend.config;

import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.*;
import com.hospital.backend.mapper.*;
import com.hospital.backend.service.ProductMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Ensures application menus and master-data seed data exist on every startup (idempotent).
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class MasterDataInitializer implements CommandLineRunner {

    private final MenuMapper menuMapper;
    private final RoleMapper roleMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final ProductMatchRuleMapper matchRuleMapper;
    private final ProductAliasMapper aliasMapper;
    private final ProductMatchService productMatchService;
    private final CustomerMapper customerMapper;
    private final CustomerAliasMapper customerAliasMapper;
    private final CustomerDiscountMapper customerDiscountMapper;
    private final CustomerProductRuleMapper customerProductRuleMapper;

    @Override
    public void run(String... args) {
        ensureMenus();
        seedCategoriesAndProducts();
        seedCustomers();
        productMatchService.refreshCache();
    }

    private void ensureMenus() {
        Menu trackingCatalog = ensureMenu("catalog", "menus.hospital.title", "/hospital", 1, 0L,
                "ri:file-excel-2-line", "Layout", true, "/hospital/reconciliation");
        ensureMenu("menu", "menus.hospital.reconciliation", "reconciliation", 1, trackingCatalog.getId(),
                "ri:file-excel-2-line", "/hospital/reconciliation", true, null);

        Menu masterDataCatalog = ensureMenu("catalog", "menus.masterData.title", "/master-data", 2, 0L,
                "ri:database-2-line", "Layout", true, "/master-data/customers");
        ensureMenu("menu", "menus.masterData.customers", "customers", 1, masterDataCatalog.getId(),
                "ri:team-line", "/master-data/customers", true, null);
        ensureMenu("menu", "menus.masterData.productCategories", "product-categories", 2, masterDataCatalog.getId(),
                "ri:folder-settings-line", "/master-data/product-categories", true, null);
        ensureMenu("menu", "menus.masterData.products", "products", 3, masterDataCatalog.getId(),
                "ri:product-hunt-line", "/master-data/products", true, null);
        ensureHiddenMenu("menus.billingConfig.deptPhysician", "customers/:customerId/dept-physician", 4,
                masterDataCatalog.getId(), "ri:hospital-line", "/billing-config/dept-physician");

        Menu settingsCatalog = ensureMenu("catalog", "menus.settings.title", "/settings", 3, 0L,
                "ri:settings-3-line", "Layout", true, "/settings/pricing-rules");
        relocateMenu("version-management", settingsCatalog.getId(), "menus.settings.versionManagement",
                "version-management", "/hospital/version-management", 1);
        relocateMenu("pricing-rules", settingsCatalog.getId(), "menus.settings.pricingRules",
                "pricing-rules", "/hospital/pricing-rules", 2);

        Role userRole = roleMapper.selectByName("R_USER");
        if (userRole != null) {
            assignMenuToRole(userRole.getId(), trackingCatalog.getId());
            assignMenuToRole(userRole.getId(), masterDataCatalog.getId());
            assignMenuToRole(userRole.getId(), settingsCatalog.getId());
            for (String path : List.of(
                    "reconciliation", "customers", "product-categories", "products",
                    "version-management", "pricing-rules")) {
                Menu menu = menuMapper.selectByPath(path);
                if (menu != null) {
                    assignMenuToRole(userRole.getId(), menu.getId());
                }
            }
        }
    }

    private void relocateMenu(String path, Long newParentId, String name, String menuPath,
                            String component, int order) {
        Menu menu = menuMapper.selectByPath(path);
        String icon = "pricing-rules".equals(path) ? "ri:price-tag-3-line" : "ri:history-line";
        if (menu == null) {
            ensureMenu("menu", name, menuPath, order, newParentId, icon, component, true, null);
            return;
        }
        boolean changed = false;
        if (!Objects.equals(menu.getParentId(), newParentId)) {
            menu.setParentId(newParentId);
            changed = true;
        }
        if (!Objects.equals(menu.getName(), name)) {
            menu.setName(name);
            changed = true;
        }
        if (!Objects.equals(menu.getComponent(), component)) {
            menu.setComponent(component);
            changed = true;
        }
        if (!Objects.equals(menu.getIcon(), icon)) {
            menu.setIcon(icon);
            changed = true;
        }
        if (!Objects.equals(menu.getOrder(), order)) {
            menu.setOrder(order);
            changed = true;
        }
        if (Boolean.TRUE.equals(menu.getIsHidden())) {
            menu.setIsHidden(false);
            changed = true;
        }
        if (changed) {
            menuMapper.update(menu);
            log.info("Relocated menu: {} → settings (order={})", path, order);
        }
    }

    private void assignMenuToRole(Long roleId, Long menuId) {
        if (!roleMapper.existsRoleMenu(roleId, menuId)) {
            roleMapper.insertRoleMenu(roleId, menuId);
        }
    }

    private void ensureHiddenMenu(String name, String path, int order, Long parentId,
                                  String icon, String component) {
        Menu existing = menuMapper.selectByPath(path);
        if (existing != null) {
            boolean changed = false;
            if (!Boolean.TRUE.equals(existing.getIsHidden())) {
                existing.setIsHidden(true);
                changed = true;
            }
            if (!Objects.equals(existing.getName(), name)) {
                existing.setName(name);
                changed = true;
            }
            if (!Objects.equals(existing.getComponent(), component)) {
                existing.setComponent(component);
                changed = true;
            }
            if (!Objects.equals(existing.getParentId(), parentId)) {
                existing.setParentId(parentId);
                changed = true;
            }
            if (!Objects.equals(existing.getOrder(), order)) {
                existing.setOrder(order);
                changed = true;
            }
            if (!Objects.equals(existing.getIcon(), icon)) {
                existing.setIcon(icon);
                changed = true;
            }
            if (changed) {
                menuMapper.update(existing);
                log.info("Updated hidden menu: {} ({})", name, path);
            }
            return;
        }

        Menu menu = new Menu();
        menu.setMenuType("menu");
        menu.setName(name);
        menu.setPath(path);
        menu.setOrder(order);
        menu.setParentId(parentId);
        menu.setIcon(icon);
        menu.setComponent(component);
        menu.setKeepalive(true);
        menu.setIsHidden(true);
        menuMapper.insert(menu);
        log.info("Created hidden menu: {} ({})", name, path);
    }

    private Menu ensureMenu(String menuType, String name, String path, int order,
                            Long parentId, String icon, String component,
                            boolean keepalive, String redirect) {
        Menu existing = menuMapper.selectByPath(path);
        if (existing != null) {
            boolean changed = false;
            if (!Objects.equals(existing.getName(), name)) {
                existing.setName(name);
                changed = true;
            }
            if (!Objects.equals(existing.getMenuType(), menuType)) {
                existing.setMenuType(menuType);
                changed = true;
            }
            if (!Objects.equals(existing.getIcon(), icon)) {
                existing.setIcon(icon);
                changed = true;
            }
            if (!Objects.equals(existing.getOrder(), order)) {
                existing.setOrder(order);
                changed = true;
            }
            if (!Objects.equals(existing.getParentId(), parentId)) {
                existing.setParentId(parentId);
                changed = true;
            }
            if (!Objects.equals(existing.getComponent(), component)) {
                existing.setComponent(component);
                changed = true;
            }
            if (!Objects.equals(existing.getKeepalive(), keepalive)) {
                existing.setKeepalive(keepalive);
                changed = true;
            }
            if (!Objects.equals(existing.getRedirect(), redirect)) {
                existing.setRedirect(redirect);
                changed = true;
            }
            if (Boolean.TRUE.equals(existing.getIsHidden())) {
                existing.setIsHidden(false);
                changed = true;
            }
            if (changed) {
                menuMapper.update(existing);
                log.info("Updated menu: {} ({})", name, path);
            }
            return existing;
        }

        Menu menu = new Menu();
        menu.setMenuType(menuType);
        menu.setName(name);
        menu.setPath(path);
        menu.setOrder(order);
        menu.setParentId(parentId);
        menu.setIcon(icon);
        menu.setComponent(component);
        menu.setKeepalive(keepalive);
        menu.setRedirect(redirect);
        menu.setIsHidden(false);
        menuMapper.insert(menu);
        log.info("Created menu: {} ({})", name, path);
        return menu;
    }

    private void seedCategoriesAndProducts() {
        if (categoryMapper.selectByCode("SMALL_ITEM") != null) {
            return;
        }

        log.info("Seeding product categories and products from bokang analysis...");

        Long smallItemId = insertCategory("SMALL_ITEM", "小件器械", null, "standard", 10);
        Long dressingCottonId = insertCategory("DRESSING_COTTON", "敷料包（纸塑袋+棉球）", null, "dressing_cotton", 20);
        Long dressingNonwovenId = insertCategory("DRESSING_NONWOVEN", "敷料包（无纺布）", null, "dressing_nonwoven", 21);
        Long htPaperId = insertCategory("HT_PAPER_PLASTIC", "高温纸塑袋", null, "standard", 30);
        Long ltPaperId = insertCategory("LT_PAPER_PLASTIC", "低温纸塑袋", null, "standard", 31);
        Long htNonWovenId = insertCategory("HT_NON_WOVEN", "高温无纺布", null, "standard", 32);
        Long ltNonWovenId = insertCategory("LT_NON_WOVEN", "低温无纺布", null, "standard", 33);
        Long fixedId = insertCategory("FIXED_OVERRIDE", "固定价覆盖", null, "fixed", 40);
        insertCategory("EXTRA_PACK", "额外包", null, "standard", 50);

        insertProduct(smallItemId, "拔髓针", "SMALL-拔髓针", 10,
                rule("CONTAINS", "pack_name", "拔髓针", null, null, 10));
        insertProduct(smallItemId, "洁牙机尖", "SMALL-洁牙机尖", 20,
                rule("CONTAINS", "pack_name", "洁牙机尖", null, null, 10));
        insertProduct(smallItemId, "挖勺", "SMALL-挖勺", 30,
                rule("CONTAINS", "pack_name", "挖勺", null, null, 10));
        insertProduct(smallItemId, "车针", "SMALL-车针", 40,
                rule("CONTAINS", "pack_name", "车针", null, null, 10));
        insertProduct(smallItemId, "机扩针", "SMALL-机扩针", 50,
                rule("CONTAINS", "pack_name", "机扩针", null, null, 10));
        insertProduct(smallItemId, "克氏针", "SMALL-克氏针", 60,
                rule("CONTAINS", "pack_name", "克氏针", null, null, 10));
        insertProduct(smallItemId, "种植盒", "SMALL-种植盒", 70,
                rule("CONTAINS", "pack_name", "种植盒", null, null, 10));
        insertProduct(smallItemId, "肖啸钻头", "SMALL-肖啸钻头", 80,
                rule("CONTAINS", "pack_name", "肖啸钻头", null, null, 10));

        insertProduct(fixedId, "3.6空心钉", "FIXED-3.6空心钉", 10,
                rule("CONTAINS", "pack_name", "3.6空心钉", null, null, 10));
        insertProduct(fixedId, "7.3空心钉", "FIXED-7.3空心钉", 20,
                rule("CONTAINS", "pack_name", "7.3空心钉", null, null, 10));
        insertProduct(fixedId, "空心钉工具包", "FIXED-空心钉工具包", 30,
                rule("CONTAINS", "pack_name", "空心钉工具包", null, null, 10));

        insertProduct(dressingCottonId, "敷料包（纸塑袋）", "DRESSING-COTTON", 10,
                rule("COMPOSITE", null, null, null,
                        List.of(cond("type", "CONTAINS", "敷料"), cond("package_material", "CONTAINS", "纸塑袋")), 10));
        insertProduct(dressingNonwovenId, "敷料包（无纺布）", "DRESSING-NONWOVEN", 10,
                rule("COMPOSITE", null, null, null,
                        List.of(cond("type", "CONTAINS", "敷料"), cond("package_material", "CONTAINS", "无纺布")), 10));

        insertProduct(htPaperId, "高温纸塑袋器械包", "HT-PAPER", 10,
                rule("COMPOSITE", null, null, null,
                        List.of(cond("type", "CONTAINS", "高温"), cond("package_material", "CONTAINS", "纸塑袋")), 10));
        insertProduct(ltPaperId, "低温纸塑袋器械包", "LT-PAPER", 10,
                rule("COMPOSITE", null, null, null,
                        List.of(cond("type", "CONTAINS", "低温"), cond("package_material", "CONTAINS", "纸塑袋")), 10));
        insertProduct(htNonWovenId, "高温无纺布器械包", "HT-NONWOVEN", 10,
                rule("COMPOSITE", null, null, null,
                        List.of(cond("type", "CONTAINS", "高温"), cond("package_material", "CONTAINS", "无纺布")), 10));
        insertProduct(ltNonWovenId, "低温无纺布器械包", "LT-NONWOVEN", 10,
                rule("COMPOSITE", null, null, null,
                        List.of(cond("type", "CONTAINS", "低温"), cond("package_material", "CONTAINS", "无纺布")), 10));

        log.info("Product seed data created.");
    }

    private void seedCustomers() {
        log.info("Ensuring customer seed data from bokang analysis + PricingEngine hardcoded...");

        ensureCustomer("HRB-WY", "哈尔滨市第五医院", 53L, null, false,
                List.of(alias("市五院", "bokang_job"), alias("哈尔滨市第五医院", "bokang_job")),
                List.of(), List.of());
        ensureCustomer("HRB-HIT", "哈尔滨工业大学医院", 58L, null, false,
                List.of(alias("哈尔滨工业大学", "bokang_job"), alias("哈工程", "bokang_job")),
                List.of(), List.of());
        ensureCustomer("HRB-XK", "哈尔滨市胸科医院", 57L, null, false, true,
                List.of(), List.of(), List.of());
        ensureCustomer("NEAU-YY", "东北农业大学医院", 36L, null, false, true,
                List.of(alias("东北农业大学", "bokang_job")),
                List.of(), List.of());
        ensureCustomer("HRB-SD-MB", "哈尔滨道外区松电慢性病专科门诊部", 37L, null, false, true,
                List.of(alias("松电慢性病专科门诊部", "bokang_job")),
                List.of(), List.of());
        ensureCustomer("HRB-AM", "哈尔滨奥美医疗美容整形医院", 55L, null, false, true,
                List.of(alias("奥美", "bokang_job")),
                List.of(), List.of());
        ensureCustomer("HRB-ASM", "嫒尚美医疗美容诊所", 34L, null, false, true,
                List.of(), List.of(), List.of());
        ensureCustomer("HRB-BY", "北一医院", 60L, null, false, true,
                List.of(alias("北一", "bokang_job")),
                List.of(), List.of());
        ensureCustomer("HRB-CY", "春语医疗美容医院", 61L, null, false, true,
                List.of(alias("春雨", "bokang_job")),
                List.of(), List.of());
        ensureCustomer("HRB-BNXS", "哈尔滨百年夏氏中医门诊部", 59L, null, false, true,
                List.of(alias("百年夏氏", "bokang_job")),
                List.of(), List.of());
        ensureCustomer("HRB-CJ", "哈尔滨长健医院", 8L, null, false, true,
                List.of(), List.of(), List.of());

        ensureCustomer("ERYY-NG", "黑龙江省第二医院（南岗区）", null, null, false,
                List.of(alias("省二院南岗", "engine"), alias("黑龙江省第二医院（南岗区）", "engine")),
                List.of(discount("省二院 0.7 折扣", new BigDecimal("0.7000"))),
                List.of());
        ensureCustomer("ERYY-SB", "黑龙江省第二医院（松北区）", null, null, false,
                List.of(alias("省二院松北", "engine"), alias("黑龙江省第二医院（松北区）", "engine")),
                List.of(discount("省二院 0.7 折扣", new BigDecimal("0.7000"))),
                List.of());
        ensureCustomer("HULAN-RM", "呼兰区第一人民医院", null, null, false,
                List.of(alias("呼兰区第一人民医院", "engine")),
                List.of(discount("呼兰 0.7 折扣", new BigDecimal("0.7000"))),
                List.of());
        ensureCustomer("WCSRMYY", "五常市人民医院", null, "none", false, true,
                List.of(alias("五常市人民医院", "engine")),
                List.of(), List.of());
        ensureCustomer("YMYXZX", "予美医疗整形医院", null, null, true, true,
                List.of(alias("予美医疗整形医院", "engine")),
                List.of(), List.of());
        ensureCustomer("HY-HYY", "黑龙江省海员总医院（松北）", null, null, false, true,
                List.of(alias("黑龙江省海员总医院（松北）", "engine")),
                List.of(), List.of());
        ensureCustomer("ZYY-DSFY", "黑龙江省中医药大学附属第四医院", null, null, false, true,
                List.of(alias("黑龙江省中医药大学附属第四医院", "engine")),
                List.of(), List.of());

        log.info("Customer seed data ensured.");
    }

    private void ensureCustomer(String code, String name, Long defaultRuleId, String capMode,
                                boolean chargeDoubleBag,
                                List<AliasSeed> aliases,
                                List<DiscountSeed> discounts,
                                List<ProductRuleSeed> productRules) {
        ensureCustomer(code, name, defaultRuleId, capMode, chargeDoubleBag, false,
                aliases, discounts, productRules);
    }

    private void ensureCustomer(String code, String name, Long defaultRuleId, String capMode,
                                boolean chargeDoubleBag, boolean inactive,
                                List<AliasSeed> aliases,
                                List<DiscountSeed> discounts,
                                List<ProductRuleSeed> productRules) {
        if (customerMapper.selectByCode(code) != null) {
            return;
        }
        insertCustomer(code, name, defaultRuleId, capMode, chargeDoubleBag, inactive,
                aliases, discounts, productRules);
    }

    private void insertCustomer(String code, String name, Long defaultRuleId, String capMode,
                                boolean chargeDoubleBag, boolean inactive,
                                List<AliasSeed> aliases,
                                List<DiscountSeed> discounts,
                                List<ProductRuleSeed> productRules) {
        Customer customer = new Customer();
        customer.setCode(code);
        customer.setCanonicalName(name);
        customer.setStatus(inactive ? "inactive" : "active");
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

        for (ProductRuleSeed r : productRules) {
            CustomerProductRule rule = new CustomerProductRule();
            rule.setCustomerId(customer.getId());
            rule.setRuleType(r.ruleType);
            rule.setName(r.name);
            rule.setPriority(r.priority);
            rule.setPrice(r.price);
            rule.setKeywords(r.keywords != null ? JsonUtils.toJson(r.keywords) : null);
            rule.setIsActive(true);
            customerProductRuleMapper.insert(rule);
        }
    }

    private AliasSeed alias(String alias, String source) {
        return new AliasSeed(alias, source);
    }

    private DiscountSeed discount(String name, BigDecimal rate) {
        return new DiscountSeed(name, rate);
    }

    private record AliasSeed(String alias, String source) {}
    private record DiscountSeed(String name, BigDecimal rate) {}
    private record ProductRuleSeed(String ruleType, String name, int priority, BigDecimal price, List<String> keywords) {}

    private Long insertCategory(String code, String name, Long parentId, String pricingPath, int sortOrder) {
        ProductCategory category = new ProductCategory();
        category.setCode(code);
        category.setName(name);
        category.setParentId(parentId);
        category.setPricingPath(pricingPath);
        category.setSortOrder(sortOrder);
        category.setIsActive(true);
        categoryMapper.insert(category);
        return category.getId();
    }

    private void insertProduct(Long categoryId, String name, String sku, int priority, ProductMatchRule matchRule) {
        Product product = new Product();
        product.setCategoryId(categoryId);
        product.setName(name);
        product.setSkuCode(sku);
        product.setPriority(priority);
        product.setIsActive(true);
        productMapper.insert(product);

        matchRule.setProductId(product.getId());
        matchRule.setIsActive(true);
        matchRuleMapper.insert(matchRule);
    }

    private ProductMatchRule rule(String matchType, String targetField, String pattern,
                                  List<String> matchFields, List<java.util.Map<String, String>> conditions, int priority) {
        ProductMatchRule rule = new ProductMatchRule();
        rule.setMatchType(matchType);
        rule.setTargetField(targetField);
        rule.setPatternValue(pattern);
        rule.setMatchFields(matchFields != null ? JsonUtils.toJson(matchFields) : null);
        rule.setConditionsJson(conditions != null ? JsonUtils.toJson(conditions) : null);
        rule.setPriority(priority);
        return rule;
    }

    private java.util.Map<String, String> cond(String field, String operator, String value) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("field", field);
        map.put("operator", operator);
        map.put("value", value);
        return map;
    }
}
