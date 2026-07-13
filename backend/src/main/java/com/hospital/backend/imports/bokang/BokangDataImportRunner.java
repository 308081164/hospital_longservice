package com.hospital.backend.imports.bokang;

import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.*;
import com.hospital.backend.mapper.*;
import com.hospital.backend.service.ProductMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Idempotent import of铂康 SQL dump master data (customers, products, pricing rules).
 * Trigger: IMPORT_BOKANG_DATA=1 or app.import.bokang.enabled=true (docker/dev).
 */
@Slf4j
@Component
@Order(120)
@RequiredArgsConstructor
public class BokangDataImportRunner implements CommandLineRunner {

    private static final String MARKER_KEY = "bokang_data_import_v1";
    private static final Set<String> TEST_HOSPITAL_KEYWORDS = Set.of(
            "测试副本", "测试医院", "东北农业大学测试", "20260415034753", "口腔科", "哈工程"
    );

    /** Known hospital_name variants → existing customer code (from bokang-legacy-analysis) */
    private static final Map<String, String> KNOWN_HOSPITAL_CODES = Map.ofEntries(
            Map.entry("哈尔滨市第五医院", "HRB-WY"),
            Map.entry("市五院", "HRB-WY"),
            Map.entry("哈尔滨工业大学医院", "HRB-HIT"),
            Map.entry("哈尔滨工业大学", "HRB-HIT"),
            Map.entry("哈尔滨市胸科医院", "HRB-XK"),
            Map.entry("东北农业大学医院", "NEAU-YY"),
            Map.entry("东北农业大学", "NEAU-YY"),
            Map.entry("哈尔滨道外区松电慢性病专科门诊部", "HRB-SD-MB"),
            Map.entry("道外松电慢性病4月账单", "HRB-SD-MB"),
            Map.entry("松电慢性病专科门诊部", "HRB-SD-MB"),
            Map.entry("哈尔滨奥美医疗美容整形医院", "HRB-AM"),
            Map.entry("奥美", "HRB-AM"),
            Map.entry("嫒尚美医疗美容诊所", "HRB-ASM"),
            Map.entry("北一医院", "HRB-BY"),
            Map.entry("北一", "HRB-BY"),
            Map.entry("春语医疗美容医院", "HRB-CY"),
            Map.entry("春雨", "HRB-CY"),
            Map.entry("哈尔滨百年夏氏中医门诊部", "HRB-BNXS"),
            Map.entry("百年夏氏", "HRB-BNXS"),
            Map.entry("哈尔滨长健医院", "HRB-CJ")
    );

    /** Rule name in dump → customer code for default_rule_id linkage */
    private static final Map<String, String> RULE_NAME_TO_CUSTOMER = Map.of(
            "哈尔滨市第五医院", "HRB-WY",
            "哈尔滨工业大学", "HRB-HIT",
            "哈尔滨市胸科医院", "HRB-XK",
            "奥美", "HRB-AM",
            "北一", "HRB-BY",
            "百年夏氏", "HRB-BNXS",
            "春雨", "HRB-CY"
    );

    private final BokangImportProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final SysSettingMapper sysSettingMapper;
    private final HospitalPricingRuleMapper pricingRuleMapper;
    private final CustomerMapper customerMapper;
    private final CustomerAliasMapper customerAliasMapper;
    private final CustomerProductRuleMapper customerProductRuleMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final ProductMatchRuleMapper matchRuleMapper;
    private final ProductMatchService productMatchService;

    private final BokangImportStats stats = new BokangImportStats();

    @Override
    public void run(String... args) {
        if (!properties.isEnabled()) {
            log.debug("Bokang data import disabled (set IMPORT_BOKANG_DATA=1 to enable)");
            return;
        }

        Path dataDir = Path.of(properties.getDataDir());
        if (!Files.isDirectory(dataDir)) {
            log.warn("Bokang data directory not found: {} — skipping import", dataDir.toAbsolutePath());
            return;
        }

        log.info("Starting铂康 master data import from {}", dataDir.toAbsolutePath());

        importPricingRules(dataDir.resolve("hospital_pricing_rule.sql"));
        importCustomersFromJobs(dataDir.resolve("hospital_reconciliation_job.sql"));
        importProductsFromRows(dataDir.resolve("hospital_reconciliation_row.sql"));
        linkCustomerDefaultRules();

        markComplete();
        productMatchService.refreshCache();

        log.info("铂康 import complete: {}", stats.summary());
    }

    private void importPricingRules(Path file) {
        if (!Files.isRegularFile(file)) {
            log.warn("Missing pricing rule dump: {}", file);
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("INSERT INTO")) {
                    continue;
                }
                List<String> v = BokangSqlInsertParser.parseValues(line);
                if (v.size() < 8) {
                    continue;
                }
                String name = v.get(5);
                if (name == null || name.isBlank()) {
                    continue;
                }
                List<HospitalPricingRule> existing = pricingRuleMapper
                        .selectByNameContainingOrderByIsActiveDescUpdatedAtDesc(name);
                if (!existing.isEmpty()) {
                    stats.pricingRulesSkipped++;
                    continue;
                }

                HospitalPricingRule rule = new HospitalPricingRule();
                rule.setDescription(v.get(3));
                rule.setIsActive("1".equals(v.get(4)));
                rule.setName(name);
                rule.setRulesJson(unescapeJson(v.get(6)));
                rule.setVersion(v.get(7));
                rule.setHospitalName(v.size() > 8 ? v.get(8) : null);
                rule.setPlanName(v.size() > 9 ? v.get(9) : null);
                pricingRuleMapper.insert(rule);
                stats.pricingRulesInserted++;

                String customerCode = RULE_NAME_TO_CUSTOMER.get(name);
                if (customerCode != null) {
                    stats.ruleLinks.put(name, rule.getId());
                }
            }
        } catch (Exception e) {
            log.error("Failed to import pricing rules from {}", file, e);
        }
    }

    private void importCustomersFromJobs(Path file) {
        if (!Files.isRegularFile(file)) {
            log.warn("Missing job dump: {}", file);
            return;
        }
        Set<String> hospitalNames = new LinkedHashSet<>();
        Map<String, Long> ruleIdByHospital = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("INSERT INTO")) {
                    continue;
                }
                List<String> v = BokangSqlInsertParser.parseValues(line);
                // id, created_at, updated_at, corrected_rows, hospital_name, ..., rule_id (index 10)
                if (v.size() < 10) {
                    continue;
                }
                String hospitalName = v.get(4);
                if (hospitalName == null || hospitalName.isBlank()) {
                    continue;
                }
                if (properties.isSkipTestHospitals() && isTestHospital(hospitalName)) {
                    stats.testHospitalsSkipped++;
                    continue;
                }
                hospitalNames.add(hospitalName);
                Long ruleId = parseLong(v.get(9));
                if (ruleId != null) {
                    ruleIdByHospital.putIfAbsent(hospitalName, ruleId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse jobs from {}", file, e);
            return;
        }

        Set<String> existingAliases = customerAliasMapper.selectAllActive().stream()
                .map(CustomerAlias::getAlias)
                .collect(Collectors.toSet());

        for (String hospitalName : hospitalNames) {
            if (existingAliases.contains(hospitalName)) {
                stats.customerAliasesSkipped++;
                continue;
            }

            String knownCode = KNOWN_HOSPITAL_CODES.get(hospitalName);
            Customer customer = knownCode != null ? customerMapper.selectByCode(knownCode) : null;

            if (customer != null) {
                insertAlias(customer.getId(), hospitalName);
                existingAliases.add(hospitalName);
                stats.customerAliasesInserted++;
                continue;
            }

            // Try fuzzy match via known codes partial name
            Optional<Map.Entry<String, String>> partial = KNOWN_HOSPITAL_CODES.entrySet().stream()
                    .filter(e -> hospitalName.contains(e.getKey()) || e.getKey().contains(hospitalName))
                    .findFirst();
            if (partial.isPresent()) {
                customer = customerMapper.selectByCode(partial.get().getValue());
                if (customer != null) {
                    insertAlias(customer.getId(), hospitalName);
                    existingAliases.add(hospitalName);
                    stats.customerAliasesInserted++;
                    continue;
                }
            }

            String code = generateCustomerCode(hospitalName);
            if (customerMapper.selectByCode(code) != null) {
                customer = customerMapper.selectByCode(code);
                insertAlias(customer.getId(), hospitalName);
                existingAliases.add(hospitalName);
                stats.customerAliasesInserted++;
                continue;
            }

            Customer newCustomer = new Customer();
            newCustomer.setCode(code);
            newCustomer.setCanonicalName(hospitalName);
            newCustomer.setStatus("active");
            Long ruleId = ruleIdByHospital.get(hospitalName);
            newCustomer.setDefaultRuleId(ruleId);
            newCustomer.setChargeDoubleBagWhenCapped(false);
            customerMapper.insert(newCustomer);
            insertAlias(newCustomer.getId(), hospitalName);
            existingAliases.add(hospitalName);
            stats.customersInserted++;
            stats.customerAliasesInserted++;
        }
    }

    private void importProductsFromRows(Path file) {
        if (!Files.isRegularFile(file)) {
            log.warn("Missing row dump: {}", file);
            return;
        }

        Long smallItemCategoryId = Optional.ofNullable(categoryMapper.selectByCode("SMALL_ITEM"))
                .map(ProductCategory::getId)
                .orElse(null);
        if (smallItemCategoryId == null) {
            log.warn("SMALL_ITEM category missing — skip product import");
            return;
        }

        Map<String, ProductAggregate> aggregates = new HashMap<>();
        AtomicLong rowsScanned = new AtomicLong();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("INSERT INTO")) {
                    continue;
                }
                rowsScanned.incrementAndGet();
                List<String> v = BokangSqlInsertParser.parseValues(line);
                if (v.size() < 16) {
                    continue;
                }
                String type = v.get(6);
                String packName = v.get(8);
                String material = v.get(9);
                BigDecimal expectedPrice = parseDecimal(v.get(14));

                if (packName == null || packName.isBlank()) {
                    continue;
                }

                String stem = BokangSqlInsertParser.extractPackNameStem(packName);
                if (stem == null || stem.length() < 2) {
                    continue;
                }

                String key = stem + "|" + nullToEmpty(type) + "|" + nullToEmpty(material);
                aggregates.computeIfAbsent(key, k -> new ProductAggregate(stem, type, material))
                        .record(expectedPrice, packName);
            }
        } catch (Exception e) {
            log.error("Failed to stream row dump {}", file, e);
            return;
        }

        stats.rowsScanned = rowsScanned.get();

        Set<String> existingKeywords = productMapper.selectAllActive().stream()
                .map(Product::getName)
                .collect(Collectors.toSet());

        List<ProductAggregate> top = aggregates.values().stream()
                .sorted(Comparator.comparingInt(ProductAggregate::count).reversed())
                .limit(properties.getMaxProducts())
                .toList();

        for (ProductAggregate agg : top) {
            if (existingKeywords.contains(agg.stem)) {
                updateProductPriceIfMissing(agg.stem, agg.medianPrice());
                stats.productsSkipped++;
                continue;
            }

            Product product = new Product();
            product.setCategoryId(smallItemCategoryId);
            product.setName(agg.stem);
            product.setSkuCode("BK-" + agg.stem.hashCode());
            product.setPriority(200);
            product.setIsActive(true);
            BigDecimal price = agg.medianPrice();
            if (price != null) {
                product.setPublicPrice(price);
                product.setOriginalPrice(price);
            }
            productMapper.insert(product);

            ProductMatchRule rule = new ProductMatchRule();
            rule.setProductId(product.getId());
            rule.setMatchType("CONTAINS");
            rule.setTargetField("pack_name");
            rule.setPatternValue(agg.stem);
            rule.setPriority(200);
            rule.setIsActive(true);
            matchRuleMapper.insert(rule);

            existingKeywords.add(agg.stem);
            stats.productsInserted++;
        }
    }

    private void updateProductPriceIfMissing(String productName, BigDecimal price) {
        if (price == null) {
            return;
        }
        for (Product p : productMapper.selectAllActive()) {
            if (productName.equals(p.getName()) && p.getPublicPrice() == null) {
                p.setPublicPrice(price);
                p.setOriginalPrice(price);
                productMapper.updateById(p);
                stats.productPricesUpdated++;
                break;
            }
        }
    }

    private void linkCustomerDefaultRules() {
        for (Map.Entry<String, String> entry : RULE_NAME_TO_CUSTOMER.entrySet()) {
            String ruleName = entry.getKey();
            String customerCode = entry.getValue();
            Customer customer = customerMapper.selectByCode(customerCode);
            if (customer == null || customer.getDefaultRuleId() != null) {
                continue;
            }
            Long linkedId = stats.ruleLinks.get(ruleName);
            if (linkedId == null) {
                List<HospitalPricingRule> rules = pricingRuleMapper
                        .selectByNameContainingOrderByIsActiveDescUpdatedAtDesc(ruleName);
                if (!rules.isEmpty()) {
                    linkedId = rules.get(0).getId();
                }
            }
            if (linkedId != null) {
                customer.setDefaultRuleId(linkedId);
                customerMapper.updateById(customer);
                stats.customerRuleLinksUpdated++;
            }
        }
    }

    private void insertAlias(Long customerId, String alias) {
        CustomerAlias customerAlias = new CustomerAlias();
        customerAlias.setCustomerId(customerId);
        customerAlias.setAlias(alias);
        customerAlias.setMatchType("contains");
        customerAlias.setSource("bokang_job");
        customerAlias.setPriority(100);
        customerAlias.setIsActive(true);
        customerAliasMapper.insert(customerAlias);
    }

    private boolean isTestHospital(String name) {
        return TEST_HOSPITAL_KEYWORDS.stream().anyMatch(name::contains);
    }

    private String generateCustomerCode(String hospitalName) {
        String sanitized = hospitalName.replaceAll("[^\\u4e00-\\u9fa5A-Za-z0-9]", "");
        String suffix = sanitized.length() > 6 ? sanitized.substring(0, 6) : sanitized;
        return "BK-" + Math.abs(suffix.hashCode() % 100000);
    }

    private void markComplete() {
        if (sysSettingMapper.countByKey(MARKER_KEY) > 0) {
            jdbcTemplate.update(
                    "UPDATE sys_setting SET setting_value = ?, updated_at = NOW() WHERE setting_key = ?",
                    JsonUtils.toJson(stats.toMap()), MARKER_KEY);
        } else {
            SysSetting marker = new SysSetting();
            marker.setSettingKey(MARKER_KEY);
            marker.setSettingValue(JsonUtils.toJson(stats.toMap()));
            marker.setDescription("铂康 master data import stats (re-runnable, idempotent upsert)");
            sysSettingMapper.insert(marker);
        }
    }

    private static String unescapeJson(String json) {
        if (json == null) {
            return null;
        }
        return json.replace("\\\"", "\"");
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static class ProductAggregate {
        final String stem;
        final String type;
        final String material;
        int count;
        final List<BigDecimal> prices = new ArrayList<>();
        String samplePackName;

        ProductAggregate(String stem, String type, String material) {
            this.stem = stem;
            this.type = type;
            this.material = material;
        }

        void record(BigDecimal price, String packName) {
            count++;
            if (samplePackName == null) {
                samplePackName = packName;
            }
            if (price != null) {
                prices.add(price);
            }
        }

        int count() {
            return count;
        }

        BigDecimal medianPrice() {
            if (prices.isEmpty()) {
                return null;
            }
            List<BigDecimal> sorted = new ArrayList<>(prices);
            sorted.sort(Comparator.naturalOrder());
            return sorted.get(sorted.size() / 2).setScale(2, RoundingMode.HALF_UP);
        }
    }

    static class BokangImportStats {
        int pricingRulesInserted;
        int pricingRulesSkipped;
        int customersInserted;
        int customerAliasesInserted;
        int customerAliasesSkipped;
        int testHospitalsSkipped;
        int customerRuleLinksUpdated;
        long rowsScanned;
        int productsInserted;
        int productsSkipped;
        int productPricesUpdated;
        final Map<String, Long> ruleLinks = new HashMap<>();

        String summary() {
            return String.format(
                    "rules +%d/skipped %d, customers +%d, aliases +%d/skipped %d, products +%d/skipped %d, "
                            + "prices updated %d, rows scanned %d, test hospitals skipped %d",
                    pricingRulesInserted, pricingRulesSkipped, customersInserted,
                    customerAliasesInserted, customerAliasesSkipped,
                    productsInserted, productsSkipped, productPricesUpdated,
                    rowsScanned, testHospitalsSkipped);
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pricingRulesInserted", pricingRulesInserted);
            m.put("pricingRulesSkipped", pricingRulesSkipped);
            m.put("customersInserted", customersInserted);
            m.put("customerAliasesInserted", customerAliasesInserted);
            m.put("customerAliasesSkipped", customerAliasesSkipped);
            m.put("testHospitalsSkipped", testHospitalsSkipped);
            m.put("customerRuleLinksUpdated", customerRuleLinksUpdated);
            m.put("rowsScanned", rowsScanned);
            m.put("productsInserted", productsInserted);
            m.put("productsSkipped", productsSkipped);
            m.put("productPricesUpdated", productPricesUpdated);
            m.put("ruleLinks", ruleLinks);
            m.put("completedAt", java.time.LocalDateTime.now().toString());
            return m;
        }
    }
}
