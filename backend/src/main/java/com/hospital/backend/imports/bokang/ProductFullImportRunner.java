package com.hospital.backend.imports.bokang;

import com.hospital.backend.entity.Product;
import com.hospital.backend.entity.ProductCategory;
import com.hospital.backend.entity.ProductMatchRule;
import com.hospital.backend.entity.ProductVariant;
import com.hospital.backend.mapper.ProductCategoryMapper;
import com.hospital.backend.mapper.ProductMapper;
import com.hospital.backend.mapper.ProductMatchRuleMapper;
import com.hospital.backend.mapper.ProductVariantMapper;
import com.hospital.backend.mapper.SysSettingMapper;
import com.hospital.backend.entity.SysSetting;
import com.hospital.backend.service.ProductMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 全量导入 888 个 product_variant（无 Top-N 截断）。
 * 触发：IMPORT_BOKANG_FULL_PRODUCTS=1 或 app.import.full-products.enabled=true
 */
@Slf4j
@Component
@Order(130)
@RequiredArgsConstructor
public class ProductFullImportRunner implements CommandLineRunner {

    private static final String MARKER_KEY = "product_full_import_v1";
    private static final List<String> NEEDLE_KEYWORDS = List.of(
            "针", "小件", "探针", "穿刺针", "缝合针", "车针", "拔髓针",
            "成型片", "根管针", "根管锉", "支抗钉", "洁牙机尖", "球钻", "挖勺", "手术针", "机扩针", "镍钛锉"
    );

    private final ProductFullImportProperties properties;
    private final SysSettingMapper sysSettingMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final ProductVariantMapper variantMapper;
    private final ProductMatchRuleMapper matchRuleMapper;
    private final ProductMatchService productMatchService;

    @Override
    public void run(String... args) {
        if (!properties.isEnabled()) {
            log.debug("Full product import disabled (set IMPORT_BOKANG_FULL_PRODUCTS=1 to enable)");
            return;
        }

        Path sqlFile = Path.of(properties.getDataDir(), properties.getRowSqlFile());
        if (!Files.isRegularFile(sqlFile)) {
            log.warn("Full product import SQL not found: {} — skipping", sqlFile.toAbsolutePath());
            return;
        }

        SysSetting marker = sysSettingMapper.selectByKey(MARKER_KEY);
        if (marker != null && variantMapper.countAll() >= 800) {
            log.info("Full product import already completed ({} variants), skipping", variantMapper.countAll());
            return;
        }

        log.info("Starting full product variant import from {}", sqlFile);
        ImportStats stats = importVariants(sqlFile);
        log.info("Full import done: scanned={}, variants={}, families={}, rules={}",
                stats.rowsScanned, stats.variantsUpserted, stats.familiesCreated, stats.matchRulesCreated);

        SysSetting done = new SysSetting();
        done.setSettingKey(MARKER_KEY);
        done.setSettingValue(String.format(
                "{\"variants\":%d,\"families\":%d,\"scanned\":%d}",
                stats.variantsUpserted, stats.familiesCreated, stats.rowsScanned));
        done.setDescription("Product full import marker");
        if (marker == null) {
            sysSettingMapper.insert(done);
        } else {
            sysSettingMapper.updateByKey(done);
        }

        productMatchService.refreshCache();
    }

    ImportStats importVariants(Path sqlFile) {
        ImportStats stats = new ImportStats();
        Map<String, VariantAggregate> aggregates = new LinkedHashMap<>();
        AtomicLong rowsScanned = new AtomicLong();

        try (BufferedReader reader = Files.newBufferedReader(sqlFile, StandardCharsets.UTF_8)) {
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
                String categoryNo = v.size() > 7 ? v.get(7) : null;
                BigDecimal expectedPrice = parseDecimal(v.get(14));
                Integer instrumentCount = parseInt(v.get(12));

                if (packName == null || packName.isBlank()) {
                    continue;
                }

                PackNameSpecParser.ParsedPack parsed = PackNameSpecParser.parse(packName, type, material);
                String fp = parsed.specFingerprint;
                aggregates.computeIfAbsent(fp, k -> new VariantAggregate(parsed, categoryNo))
                        .record(expectedPrice, instrumentCount);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to stream row dump: " + sqlFile, e);
        }

        stats.rowsScanned = rowsScanned.get();
        Map<String, Long> familyProductIds = new HashMap<>();
        Map<String, Long> variantRuleIds = new HashMap<>();

        for (VariantAggregate agg : aggregates.values()) {
            Long productId = ensureFamilyProduct(agg, familyProductIds, stats);
            upsertVariant(agg, productId, stats);
            ensureVariantMatchRule(agg, productId, variantRuleIds, stats);
        }

        return stats;
    }

    private Long ensureFamilyProduct(VariantAggregate agg, Map<String, Long> cache, ImportStats stats) {
        String familyName = agg.parsed.familyName;
        if (familyName == null || familyName.isBlank()) {
            familyName = agg.parsed.packName;
        }
        if (cache.containsKey(familyName)) {
            return cache.get(familyName);
        }

        Product existing = findProductByName(familyName);
        if (existing != null) {
            cache.put(familyName, existing.getId());
            return existing.getId();
        }

        Long categoryId = resolveCategoryId(agg.parsed.type, familyName);
        Product product = new Product();
        product.setCategoryId(categoryId);
        product.setName(familyName);
        product.setSkuCode(PackNameSpecParser.familyCode(familyName));
        product.setPriority(200);
        product.setIsActive(true);
        BigDecimal median = agg.medianPrice();
        if (median != null) {
            product.setPublicPrice(median);
            product.setOriginalPrice(median);
        }
        productMapper.insert(product);
        cache.put(familyName, product.getId());
        stats.familiesCreated++;

        ProductMatchRule familyRule = new ProductMatchRule();
        familyRule.setProductId(product.getId());
        familyRule.setMatchType("CONTAINS");
        familyRule.setTargetField("pack_name");
        familyRule.setPatternValue(familyName);
        familyRule.setPriority(300);
        familyRule.setIsActive(true);
        matchRuleMapper.insert(familyRule);
        stats.matchRulesCreated++;

        return product.getId();
    }

    private void upsertVariant(VariantAggregate agg, Long productId, ImportStats stats) {
        PackNameSpecParser.ParsedPack p = agg.parsed;
        ProductVariant existing = variantMapper.selectBySpecFingerprint(p.specFingerprint);
        BigDecimal median = agg.medianPrice();

        ProductVariant variant = existing != null ? existing : new ProductVariant();
        variant.setProductId(productId);
        variant.setSkuCode(PackNameSpecParser.variantSku(p.specFingerprint));
        variant.setSpecFingerprint(p.specFingerprint);
        variant.setPackName(p.packName);
        variant.setType(p.type);
        variant.setPackageMaterial(p.packageMaterial);
        variant.setCategoryNo(agg.categoryNo);
        variant.setFamilyNameParsed(p.familyName);
        variant.setSpecSuffix(p.specSuffix);
        variant.setInstrumentCountHint(p.instrumentCountHint);
        variant.setOrderNoPattern(p.orderNoPattern);
        variant.setBagMaterialClass(p.bagInfo.materialClass);
        variant.setBagTempClass(p.bagInfo.tempClass);
        variant.setBagWidthMm(p.bagInfo.widthMm);
        variant.setBagHeightMm(p.bagInfo.heightMm);
        variant.setBagSizeLabel(p.bagInfo.sizeLabel);
        variant.setDisplayName(p.displayName);
        if (median != null) {
            variant.setPublicPrice(median);
            variant.setOriginalPrice(median);
        }
        variant.setPriceSampleCount(agg.prices.size());
        variant.setOccurrenceCount(agg.count);
        variant.setIsActive(true);

        if (existing == null) {
            variantMapper.insert(variant);
        } else {
            variantMapper.updateById(variant);
        }
        agg.variantId = variant.getId();
        stats.variantsUpserted++;
    }

    private void ensureVariantMatchRule(VariantAggregate agg, Long productId,
                                        Map<String, Long> cache, ImportStats stats) {
        if (agg.variantId == null) {
            return;
        }
        String key = agg.parsed.specFingerprint;
        if (cache.containsKey(key)) {
            return;
        }

        String conditionsJson = buildCompositeConditions(agg.parsed);
        ProductMatchRule rule = new ProductMatchRule();
        rule.setProductId(productId);
        rule.setVariantId(agg.variantId);
        rule.setMatchType("COMPOSITE");
        rule.setConditionsJson(conditionsJson);
        rule.setPriority(100);
        rule.setIsActive(true);
        matchRuleMapper.insert(rule);
        cache.put(key, rule.getId());
        stats.matchRulesCreated++;
    }

    private String buildCompositeConditions(PackNameSpecParser.ParsedPack parsed) {
        List<Map<String, String>> conditions = new ArrayList<>();
        conditions.add(Map.of("field", "pack_name", "operator", "EQ", "value", parsed.packName));
        if (parsed.type != null && !parsed.type.isBlank()) {
            conditions.add(Map.of("field", "type", "operator", "EQ", "value", parsed.type));
        }
        if (parsed.packageMaterial != null && !parsed.packageMaterial.isBlank()) {
            conditions.add(Map.of("field", "package_material", "operator", "EQ", "value", parsed.packageMaterial));
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(conditions);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Long resolveCategoryId(String type, String familyName) {
        String code = mapTypeToCategory(type, familyName);
        ProductCategory category = categoryMapper.selectByCode(code);
        if (category != null) {
            return category.getId();
        }
        ProductCategory fallback = categoryMapper.selectByCode("SMALL_ITEM");
        return fallback != null ? fallback.getId() : 1L;
    }

    private String mapTypeToCategory(String type, String familyName) {
        if (type == null) {
            type = "";
        }
        if (type.contains("敷料") || type.contains("无纺布")) {
            return "DRESSING_NONWOVEN";
        }
        if (type.contains("器械包") || type.contains("器械包装")) {
            return "INSTRUMENT_PACK";
        }
        if (type.contains("纸塑袋") && isNeedleFamily(familyName)) {
            return "SMALL_ITEM";
        }
        if (type.contains("纸塑袋")) {
            return "HT_PAPER_PLASTIC";
        }
        return isNeedleFamily(familyName) ? "SMALL_ITEM" : "HT_PAPER_PLASTIC";
    }

    private boolean isNeedleFamily(String familyName) {
        if (familyName == null) {
            return false;
        }
        return NEEDLE_KEYWORDS.stream().anyMatch(familyName::contains);
    }

    private Product findProductByName(String name) {
        for (Product p : productMapper.selectAllActive()) {
            if (name.equals(p.getName())) {
                return p;
            }
        }
        return null;
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank() || "NULL".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank() || "NULL".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static class ImportStats {
        long rowsScanned;
        int variantsUpserted;
        int familiesCreated;
        int matchRulesCreated;
    }

    static class VariantAggregate {
        final PackNameSpecParser.ParsedPack parsed;
        final String categoryNo;
        int count;
        final List<BigDecimal> prices = new ArrayList<>();
        Long variantId;

        VariantAggregate(PackNameSpecParser.ParsedPack parsed, String categoryNo) {
            this.parsed = parsed;
            this.categoryNo = categoryNo;
        }

        void record(BigDecimal price, Integer instrumentCount) {
            count++;
            if (price != null) {
                prices.add(price);
            }
        }

        BigDecimal medianPrice() {
            if (prices.isEmpty()) {
                return null;
            }
            List<BigDecimal> sorted = new ArrayList<>(prices);
            sorted.sort(Comparator.naturalOrder());
            return sorted.get(sorted.size() / 2);
        }
    }
}
