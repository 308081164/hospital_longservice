package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 医院灭菌计费规则引擎 —— 后端唯一的定价逻辑源。
 *
 * 每个定价请求处理一行 Excel 数据，返回校正后的单价、总价、差异和状态。
 * 支持：高温/低温纸塑袋、高温/低温无纺布、敷料包、器械包(ZSD)、
 * 小件器械折算、包装耗材收费、袋尺寸自动检测等全部规则。
 *
 * 线程安全：bagSizeCache 使用 ConcurrentHashMap，并发处理 Excel 行时安全。
 * 缓存策略：仅缓存最近 2000 个袋尺寸检测结果（LRU 近似），防止无界增长。
 */
@Slf4j
public class PricingEngine {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_CACHE_SIZE = 2000;
    /** 账单展示价与规则价在标准路径下的容差（元），与 S4 TOLERANCE 一致 */
    private static final double DISPLAY_PRICE_TOLERANCE = 0.05;
    /** 双层包标记：兼容 /双、/(双)、/（双） 三种写法，后接可选右括号。 */
    private static final java.util.regex.Pattern DOUBLE_BAG_MARK =
            java.util.regex.Pattern.compile("/[(（]?双[)）]?");

    // ---- 缓存：袋尺寸检测器（线程安全，有限容量） ----
    private final JsonNode rules;
    private final Map<String, Integer> bagSizeCache = new ConcurrentHashMap<>();
    private final List<BagConfig> allBagConfigs;
    private final List<BagConfig> sortedBagConfigs;
    private final Map<String, Integer> keywordToBagSize;
    private ProductMatchResolver productMatchResolver;
    private boolean structuredProductMatchEnabled;

    public PricingEngine(JsonNode rules) {
        this.rules = rules;
        this.allBagConfigs = buildAllBagConfigs();
        this.sortedBagConfigs = new ArrayList<>(allBagConfigs);
        sortedBagConfigs.sort(Comparator.comparingInt(a -> a.size));
        this.keywordToBagSize = buildKeywordMap();
    }

    /**
     * Optional parallel path: resolve product/category from structured DB rules.
     * Disabled by default; enable via {@link #enableStructuredProductMatch(ProductMatchResolver)}.
     */
    public void enableStructuredProductMatch(ProductMatchResolver resolver) {
        this.structuredProductMatchEnabled = resolver != null;
        this.productMatchResolver = resolver;
    }

    // ================================================================
    //  公开入口
    // ================================================================

    /**
     * 处理单行数据，返回校正结果。
     * row 必须包含：type, packName, packageMaterial, instrumentCount, packCount,
     *              unitPrice, totalPrice
     */
    public ProcessedResult processRow(Map<String, Object> row) {
        row = new HashMap<>(row);
        List<String> notes = new ArrayList<>();
        String status = "unchanged";
        String pricingRule = "未命中规则";
        boolean requiresReview = false;

        String type = str(row, "type");
        String packName = str(row, "packName");
        String packageMaterial = str(row, "packageMaterial");
        String hospitalName = str(row, "hospitalName");
        int instrumentCount = intVal(row, "instrumentCount");
        int packCount = Math.max(1, intVal(row, "packCount"));
        List<BillRowFieldConsistencyValidator.Violation> consistencyViolations =
                BillRowFieldConsistencyValidator.validate(
                        type, packName, packageMaterial, instrumentCount, packCount);
        boolean forceConsistencyWarning = !consistencyViolations.isEmpty();
        for (BillRowFieldConsistencyValidator.Violation violation : consistencyViolations) {
            notes.add("【字段核对】" + violation.message());
        }
        Map<String, Object> consistencyBillingNotes =
                BillRowFieldConsistencyValidator.toBillingNotes(consistencyViolations);
        // error 级字段校验须在包材推断补全之前执行：核对的是账单原始列，而非引擎补全后的值。
        List<BillRowBillingValidator.Violation> billingViolations =
                BillRowBillingValidator.validate(type, packageMaterial, instrumentCount);
        boolean forceBillingValidationWarning = !billingViolations.isEmpty();
        for (BillRowBillingValidator.Violation violation : billingViolations) {
            notes.add("【字段核对错误】" + violation.message());
        }
        Map<String, Object> billingValidationNotes =
                BillRowBillingValidator.toBillingNotes(billingViolations);
        packageMaterial = inferPricingPackageMaterial(type, packageMaterial, notes);
        packageMaterial = normalizeFuyiImportMaterial(type, packName, packageMaterial, notes);
        row.put("packageMaterial", packageMaterial);
        PackPricingCategoryResolver.Resolution packCategoryResolution =
                PackPricingCategoryResolver.resolve(
                        type, packName, packageMaterial, instrumentCount, packCount);
        PackPricingCategory packCategory = packCategoryResolution.category();
        Double unitPrice = doubleOrNull(row, "unitPrice");
        Double totalPrice = doubleOrNull(row, "totalPrice");

        // 未解析/未启用特色账单统一走通用计价规则：不再早退保留原价、不再产生「特色账单已关闭」告警。
        // 未启用客户在编译阶段（PricingRuleCompiler）本就不合并 customer_product_rule，
        // 这里直接落到标准计价路径（高温/低温阶梯、纸塑袋、无纺布、敷料包等通用规则）。
        JsonNode billingProfile = rules.path("billingProfile");
        JsonNode pathOverride = billingProfile.path("pathOverride");
        SpecialPriceResult zeroPriceOverride = resolveZeroPriceOverride(row, pathOverride, unitPrice);
        boolean disableLowTemp = pathOverride.path("disableLowTemp").asBoolean(false);
        double forceHighTempPerItem = pathOverride.path("forceHighTempUnitPrice").asDouble(Double.NaN);
        String pricingMode = billingProfile.path("pricingMode").asText("standard");
        boolean specialOnly = "special_only".equalsIgnoreCase(pricingMode);
        boolean hybrid = "hybrid".equalsIgnoreCase(pricingMode);
        boolean preserveOriginalOnMiss = specialOnly;

        boolean isZsdInstrumentPack = isZsdInstrumentPackType(type);

        boolean dressingPerPack = isDressingPerPackRow(packName, type, instrumentCount, packCategory);
        int effectiveCount = instrumentCount;
        if (packCount > 1 && !isZsdInstrumentPack) {
            int divided = (int) Math.round((double) effectiveCount / packCount);
            effectiveCount = dressingPerPack ? Math.max(0, divided) : Math.max(1, divided);
        }
        if (effectiveCount == 0 && !dressingPerPack) {
            effectiveCount = 1;
        }
        int perPackInstrumentCount = packCount > 1
                ? Math.max(1, (int) Math.round((double) instrumentCount / packCount))
                : Math.max(1, instrumentCount);

        // Structured product match hook (feature-flagged, parallel path for future PricingEngine integration)
        java.util.Optional<ProductMatchResolver.StructuredProductMatch> structuredMatch = java.util.Optional.empty();
        Long matchedProductId = null;
        Long matchedVariantId = longOrNull(row, "matchedVariantId", "matched_variant_id");
        if (structuredProductMatchEnabled && productMatchResolver != null) {
            structuredMatch = productMatchResolver.resolve(row);
            if (structuredMatch.isPresent()) {
                matchedProductId = structuredMatch.get().productId();
                ProductMatchResolver.StructuredProductMatch match = structuredMatch.get();
                notes.add("结构化产品匹配: " + match.productName()
                        + " [" + match.categoryCode() + "] → " + match.pricingPath());
            }
        }

 // 计价始终使用Excel器械数（effectiveCount），不从包名覆盖。
 // 包名解析数量仅用于字段一致性核对（BillRowFieldConsistencyValidator），不影响计价。


        // 袋尺寸检测（带缓存）。部分特例规则需要先知道袋型，例如“20cm 以下 5 件算 1 件”。
        int bagSize = detectBagSize(packageMaterial + packName);
        boolean isPaperPlastic = packageMaterial.contains("纸塑袋")
                || type.contains("纸塑袋")
                || packageMaterial.contains("低温灭菌")
                || packageMaterial.contains("双层袋");
        boolean isNonWoven = packageMaterial.contains("无纺布") || type.contains("无纺布");
        // 包材优先于类型标签：账单 type 误标纸塑袋但包材为无纺布时仍走无纺布计价
        if (packageMaterial.contains("无纺布") && !packageMaterial.contains("纸塑袋")) {
            isNonWoven = true;
            isPaperPlastic = false;
        } else if (packageMaterial.contains("纸塑袋") || packageMaterial.contains("低温灭菌")
                || packageMaterial.contains("双层袋")) {
            isPaperPlastic = true;
        }
        int perPackRawInstrumentCount = packCount > 1
                ? (int) Math.round((double) instrumentCount / Math.max(1, packCount))
                : instrumentCount;
        if (perPackRawInstrumentCount <= 0 && !dressingPerPack) {
            perPackRawInstrumentCount = Math.max(1, instrumentCount);
        }
        boolean highTempPaperPlasticRow = isPaperPlastic && !isNonWoven;
        boolean isLowTemp = !disableLowTemp && ((type + packName + packageMaterial).contains("低温")
                || type.contains("ETO") || type.contains("EO")
                || packageMaterial.contains("低温灭菌"));
        boolean isDouble = DOUBLE_BAG_MARK.matcher(packName).find()
                || packageMaterial.contains("双层袋");
        int zBagSize = isDouble ? extractSizeAfterDouble(packName) : 0;
        SpecialPriceResult preMatchedSpecialPrice = zeroPriceOverride != null
                ? zeroPriceOverride
                : findSpecialFixedPrice(row, bagSize, effectiveCount, matchedProductId, matchedVariantId);
        if (preMatchedSpecialPrice == null) {
            preMatchedSpecialPrice = resolveProductPublicPrice(structuredMatch, unitPrice);
        }

        // 甲方测试中补充的小件折算规则，例如：机扩针/镍钛锉 5 件算 1 件。
        // 器械包(ZSD) 按单包器械总数阶梯计费，包名含「克氏针」等小件词也不折算。
        boolean appliedSpecialFoldRule = false;
        boolean foldSkipPackaging = false;
        boolean foldHasExtraCount = false;
        Double foldUnitPriceOverride = null;
        Long foldMatchedRuleId = null;
        String foldMatchedRuleName = null;
        if (preMatchedSpecialPrice == null && !isZsdInstrumentPack) {
            int countBeforeSpecialFold = effectiveCount;
            FoldApplyResult foldResult = applySpecialFoldRules(row, bagSize, effectiveCount, notes);
            effectiveCount = foldResult.effectiveCount();
            appliedSpecialFoldRule = foldResult.matched();
            foldSkipPackaging = foldResult.skipPackaging();
            foldHasExtraCount = foldResult.hasExtraCount();
            foldUnitPriceOverride = foldResult.unitPriceOverride();
            foldMatchedRuleId = foldResult.ruleId();
            foldMatchedRuleName = foldResult.ruleName();
        }
        if (foldUnitPriceOverride != null && !Double.isNaN(foldUnitPriceOverride)) {
            forceHighTempPerItem = foldUnitPriceOverride;
        }

        // 针数量规则 + 小件器械折算（针数量规则优先：包名含"针+数字"时按公式拆分）
        JsonNode needle = rules.path("needle");
        String needleMatchMode = BillingConditionEvaluator.resolveKeywordMatchMode(needle);
        // 有效关键词 = keywordConfigs 独立配置词（含逐词匹配模式） ∪ 普通 keywords
        JsonNode needleKeywords = effectiveNeedleKeywords(needle);

        // 高温纸塑 ≥3 件：按账单器械数×5.5，不应用全局针数拆分/小件折算（院级 FOLD 特色规则仍保留）。
        // 包名命中小件关键词（车针/克氏针等）时仍须走折算，不可因单包≥3件而按实件×5.5。
        boolean matchesSmallItemKeyword = matchesKeywordsBoundary(
                packName, needleKeywords, needleMatchMode);
        boolean skipGlobalNeedleAndSmallFold = highTempPaperPlasticRow && !isLowTemp
                && perPackRawInstrumentCount >= 3
                && !matchesSmallItemKeyword;
        if (skipGlobalNeedleAndSmallFold && !appliedSpecialFoldRule) {
            effectiveCount = Math.max(1, perPackRawInstrumentCount);
        }
        java.util.regex.Pattern needleQtyPattern = java.util.regex.Pattern.compile("针(\\d+)");
        java.util.regex.Matcher needleQtyMatcher = needleQtyPattern.matcher(packName);
        boolean appliedNeedleRule = false;
        boolean skipNeedleRuleForFuyiW9050 = packName.toLowerCase().contains("w9050");
        // 气腹针是腹腔镜全价器械（非 5 合 1 小件，全局小件关键词亦未收录），
        // 「气腹针N」的 N 为实件数，不参与针数量拆分（同 吸脂针长型号/W9050 的既有排除先例）
        boolean foundNeedleQty = false;
        while (needleQtyMatcher.find()) {
            if (isVeressNeedleAt(packName, needleQtyMatcher.start())) {
                continue;
            }
            foundNeedleQty = true;
            break;
        }
        if (!skipGlobalNeedleAndSmallFold && preMatchedSpecialPrice == null && !appliedSpecialFoldRule
                && !isZsdInstrumentPack && !skipNeedleRuleForFuyiW9050 && foundNeedleQty) {
            String beforeNeedle = packName.substring(0, needleQtyMatcher.start());
            String afterNeedle = packName.substring(needleQtyMatcher.end());
            // "针N"后是否还有器械名（如"钢丝4"），用于区分纯小件与混合器械
            boolean hasOtherItems = java.util.regex.Pattern.compile("[\\u4e00-\\u9fff]+\\d+").matcher(afterNeedle).find();
            boolean isSmallItemKeyword = matchesKeywordsBoundary(packName, needleKeywords, needleMatchMode);
            // 若"针"是小件关键词的一部分（如"克氏针"）且针后无其他器械，跳过拆分
            if (isSmallItemKeyword && !hasOtherItems) {
                // 不应用针数量拆分，交给下方小件关键词规则处理
            } else {
                int needleQty = Integer.parseInt(needleQtyMatcher.group(1));
                // 命中带独立配置的小件关键词时，针折算沿用该关键词的折算比例
                BillingConditionEvaluator.ExactTokenKeywordMatch needleKwMatch =
                        BillingConditionEvaluator.findKeywordByMode(packName, needleKeywords, needleMatchMode);
                double foldRatio = resolveNeedleFoldParams(needle,
                        needleKwMatch != null ? needleKwMatch.keyword() : null).foldRatio();
                // 非针器械数按包名全部「器械名+数字」段求和（如 剪刀2止血钳1探针1 → 2+1=3），
                // 避免只取末位数字丢失前段件数；无「汉字+数字」段时退回末位数字语义（兼容 （5号） 等写法）
                int nonNeedleCount = sumAllNumbers(beforeNeedle);
                if (nonNeedleCount == 0) {
                    nonNeedleCount = extractLastNumber(beforeNeedle);
                }
                if (hasOtherItems) {
                    nonNeedleCount += sumAllNumbers(afterNeedle);
                }
                if (nonNeedleCount == 0) {
                    nonNeedleCount = Math.max(1, effectiveCount - needleQty);
                }
                int needleEquivalent = (int) Math.ceil(needleQty / foldRatio);
                int newEffectiveCount = Math.max(1, nonNeedleCount + needleEquivalent);
                notes.add("包名含\"针" + needleQty + "\"，非针器械 " + nonNeedleCount + " 件 + 针折算 " + needleEquivalent
                        + " 件（" + needleQty + "÷" + (int) foldRatio + "=" + needleEquivalent + "） = "
                        + newEffectiveCount + " 件。");
                effectiveCount = newEffectiveCount;
                appliedNeedleRule = true;
            }
        }
        if (!skipGlobalNeedleAndSmallFold && preMatchedSpecialPrice == null && !appliedSpecialFoldRule
                && !appliedNeedleRule && !isLiposuctionNeedleLongVariant(packName) && !isZsdInstrumentPack) {
            SmallItemSplit smallSplit = findSmallItemSplit(packName, needleKeywords, needleMatchMode);
            if (smallSplit != null) {
                // 命中关键词带独立配置（keywordConfigs）时，触发件数/折算比例按该关键词覆盖全局默认
                NeedleFoldParams needleFoldParams = resolveNeedleFoldParams(needle, smallSplit.keyword);
                int threshold = needleFoldParams.threshold();
                double foldRatio = needleFoldParams.foldRatio();
                int originalCount = effectiveCount;

                String beforeKw = smallSplit.compactText.substring(0, smallSplit.position);
                String kwAndAfter = smallSplit.compactText.substring(smallSplit.position);

                int largeCount = sumAllNumbers(beforeKw);
                int smallCount = sumAllNumbers(kwAndAfter);

                if (largeCount > 0 && smallCount > 0) {
                    int foldedSmall = smallCount <= threshold ? 1
                            : Math.max(1, (int) Math.ceil(smallCount / foldRatio));
                    effectiveCount = largeCount + foldedSmall;
                    notes.add("名称含小件关键词\"" + smallSplit.keyword + "\"，大件 " + largeCount + " 件（按实计） + 小件 " + smallCount
                            + " 件（折 " + foldedSmall + " 件） = " + effectiveCount + " 件。");
                } else {
                    if (effectiveCount <= threshold) {
                        effectiveCount = 1;
                        notes.add("名称含小件关键词，单包器械数 " + originalCount + " 件 ≤ " + threshold + "，按 1 件计费。");
                    } else {
                        effectiveCount = Math.max(1, (int) Math.ceil(effectiveCount / foldRatio));
                        notes.add("名称含小件关键词，单包器械数 " + originalCount + " 件 > " + threshold + "，按每 " + foldRatio + " 件折算为 " + effectiveCount + " 件计费。");
                    }
                }
            }
        }

        int materialBillingCount = isZsdInstrumentPack && packCount > 1
                ? perPackInstrumentCount
                : effectiveCount;

        Double expectedUnitPrice = unitPrice;
        boolean skipPackaging = foldSkipPackaging;
        boolean skipHospitalDiscount = false;
        Long matchedRuleId = foldMatchedRuleId;
        SpecialPriceResult specialPrice = preMatchedSpecialPrice != null
                ? preMatchedSpecialPrice
                : findSpecialFixedPrice(row, bagSize, effectiveCount, matchedProductId, matchedVariantId);

        // ---- 特殊类型优先处理 ----
        if (specialPrice != null) {
            expectedUnitPrice = specialPrice.price;
            pricingRule = specialPrice.ruleName;
            notes.add(specialPrice.note);
            skipPackaging = specialPrice.skipPackaging;
            skipHospitalDiscount = specialPrice.skipHospitalDiscount;
            if (specialPrice.ruleId != null) {
                matchedRuleId = specialPrice.ruleId;
            }
        } else if (preserveOriginalOnMiss) {
            Double forcedPrice = computeForceHighTempUnitPrice(forceHighTempPerItem, materialBillingCount);
            if (forcedPrice != null) {
                expectedUnitPrice = forcedPrice;
                pricingRule = "路径覆盖：高温固定单价";
                notes.add("计价模式为仅特色规则，按路径覆盖高温单价 "
                        + fmt(forceHighTempPerItem) + " 元/件 × " + materialBillingCount + " 件 = "
                        + fmt(expectedUnitPrice) + " 元。");
            } else {
                pricingRule = "special_only 未命中特色规则";
                notes.add("计价模式为仅特色规则，未命中客户特色规则，保留原始价格。");
                requiresReview = true;
            }
        } else {
            if (hybrid && !appliedSpecialFoldRule) {
                notes.add("混合模式未命中特色规则，走标准灭菌计价。");
            }
            if (packCategoryResolution.note() != null) {
                notes.add(packCategoryResolution.note());
            }
            switch (packCategory) {
            case DRESSING_PAPER -> {
                if (type.contains("纸塑袋") && isCottonDressingPackName(packName)
                        && shouldUseDressingCottonPaperPlasticPrice(
                                packName, type, instrumentCount, packCount)) {
                    int bagSize2 = detectBagSize(str(row, "packageMaterial") + str(row, "packName"));
                    Double cottonPrice = resolveCottonPaperPlasticUnitPrice(bagSize2);
                    if (cottonPrice != null) {
                        expectedUnitPrice = cottonPrice;
                        pricingRule = "敷料包(纸塑袋)+棉球——" + bagSize2 + "cm";
                        notes.add("敷料包(纸塑袋)+棉球，纸塑袋规格 " + bagSize2 + "cm，按包计价 "
                                + fmt(expectedUnitPrice) + " 元/包，总价=单价×包数(" + packCount + ")。");
                    } else {
                        pricingRule = "敷料包(纸塑袋)+棉球——未识别规格";
                        notes.add("敷料包(纸塑袋)+棉球未能识别纸塑袋规格，保留原始价格。");
                        requiresReview = true;
                    }
                    skipPackaging = true;
                }
            }
            case DRESSING_NONWOVEN -> {
                if (packName.contains("驱血带")) {
                    Double measure = extractDressingPackMeasure(packageMaterial, packName);
                    double dressPrice = measure != null ? computeDressingPackPrice(measure) : 0;
                    if (dressPrice <= 0 && measure != null) {
                        Double materialMeasure = extractDressingPackMeasure(packageMaterial, "");
                        if (materialMeasure != null) {
                            double materialPrice = computeDressingPackPrice(materialMeasure);
                            if (materialPrice > 0) {
                                measure = materialMeasure;
                                dressPrice = materialPrice;
                            }
                        }
                    }
                    if (dressPrice > 0) {
                        expectedUnitPrice = dressPrice;
                        pricingRule = "敷料包(无纺布包)驱血带——" + measure;
                        notes.add("驱血带按无纺布敷料包规格 " + measure + " 计价，单价 "
                                + fmt(expectedUnitPrice) + " 元。");
                    } else if (isZeroImport(unitPrice, totalPrice)) {
                        double defaultPrice = defaultDressingPackPrice();
                        expectedUnitPrice = defaultPrice;
                        pricingRule = "敷料包(无纺布包)驱血带——默认价";
                        notes.add("驱血带未能识别 W 码规格，0 元导入按默认小敷料包 "
                                + fmt(defaultPrice) + " 元。");
                        requiresReview = true;
                    } else {
                        pricingRule = measure != null
                                ? "敷料包(无纺布包)驱血带——未匹配定价"
                                : "敷料包(无纺布包)驱血带——未识别规格";
                        notes.add(measure != null
                                ? "驱血带未能匹配无纺布敷料分档，保留原始价格。"
                                : "驱血带未能识别无纺布 W 码规格，保留原始价格。");
                        requiresReview = true;
                    }
                    skipPackaging = true;
                } else if (type.contains("敷料包")) {
                    Double measure = extractDressingPackMeasure(packageMaterial, packName);
                    if (measure != null) {
                        double dressPrice = computeDressingPackPrice(measure);
                        if (dressPrice > 0) {
                            expectedUnitPrice = dressPrice;
                            pricingRule = "敷料包(无纺布包)——" + measure;
                            notes.add("敷料包规格 " + measure + "，按敷料包定价表计算单价为 "
                                    + fmt(expectedUnitPrice) + " 元。");
                        } else {
                            pricingRule = "敷料包(无纺布包)——未匹配定价";
                            notes.add("敷料包规格 " + measure + " 未命中定价表（<90→25, =90→30, 1.2~1.5→35），保留原始价格。");
                            requiresReview = true;
                        }
                    } else if (isZeroImport(unitPrice, totalPrice)) {
                        double defaultPrice = defaultDressingPackPrice();
                        expectedUnitPrice = defaultPrice;
                        pricingRule = "敷料包(无纺布包)——0元导入默认价";
                        notes.add("敷料包未能识别规格尺寸，0 元导入按标准小敷料包默认单价 "
                                + fmt(defaultPrice) + " 元。");
                        requiresReview = true;
                    } else {
                        pricingRule = "敷料包(无纺布包)——未识别规格";
                        notes.add("敷料包(无纺布包)未能识别到规格尺寸，保留原始价格。");
                        requiresReview = true;
                    }
                    skipPackaging = true;
                }
            }
            case INSTRUMENT_PAPER -> {
            if (isLowTemp) {
                Double forcedLt = computeForceHighTempUnitPrice(forceHighTempPerItem, materialBillingCount);
                if (forcedLt != null && appliedSpecialFoldRule) {
                    expectedUnitPrice = forcedLt;
                    pricingRule = "低温特色折算单价";
                    notes.add("按特色规则低温折算单价 " + fmt(forceHighTempPerItem) + " 元/件 × "
                            + materialBillingCount + " 件 = " + fmt(expectedUnitPrice) + " 元。");
                    if (!skipPackaging) {
                        double bagFee = (bagSize > 0 && bagSize < 20) ? 2.5 : 4.0;
                        expectedUnitPrice = round(expectedUnitPrice + bagFee);
                        pricingRule = pricingRule + " + 标准包材费";
                        notes.add("含包材规则，叠加标准包材费 " + fmt(bagFee) + " 元。");
                    }
                } else {
                    String ltPrefix = isDouble ? "低温纸塑袋(双)" : "低温纸塑袋";
                    pricingRule = ltPrefix + (bagSize > 0 ? bagSize + "cm" : "") + "阶梯计费";
                    expectedUnitPrice = computeLowTempPaperPlastic(
                            materialBillingCount, bagSize, zBagSize, notes, isDouble, hospitalName);
                }
            } else {
                Double forcedPrice = computeForceHighTempUnitPrice(forceHighTempPerItem, materialBillingCount);
                if (forcedPrice != null) {
                    expectedUnitPrice = forcedPrice;
                    pricingRule = "路径覆盖：高温固定单价";
                    notes.add("按路径覆盖高温单价 " + fmt(forceHighTempPerItem) + " 元/件 × "
                            + materialBillingCount + " 件 = " + fmt(expectedUnitPrice) + " 元。");
                    if (appliedSpecialFoldRule && !skipPackaging) {
                        double bagFee = (bagSize > 0 && bagSize < 20) ? 2.5 : 4.0;
                        expectedUnitPrice = round(expectedUnitPrice + bagFee);
                        pricingRule = pricingRule + " + 标准包材费";
                        notes.add("含包材规则，叠加标准包材费 " + fmt(bagFee) + " 元。");
                    }
                } else if (appliedSpecialFoldRule && (foldSkipPackaging || foldHasExtraCount)) {
                    // 针盒针（extraCount）或免包材 FOLD：按折算件数×把价计件费；含包材叠加标准袋费。
                    // 不可走 computeHighTempPaperPlastic（免包材会重复计袋费，针盒含包材袋规价≠标准2.5）。
                    double perItem = rules.path("highTemperature").path("paperPlastic").path("perPackagePrice").asDouble(5.5);
                    expectedUnitPrice = computeForceHighTempUnitPrice(perItem, materialBillingCount);
                    notes.add("按特色折算单价 " + fmt(perItem) + " 元/件 × "
                            + materialBillingCount + " 件 = " + fmt(expectedUnitPrice) + " 元。");
                    if (!skipPackaging) {
                        double bagFee = (bagSize > 0 && bagSize < 20) ? 2.5 : 4.0;
                        expectedUnitPrice = round(expectedUnitPrice + bagFee);
                        notes.add("含包材规则，叠加标准包材费 " + fmt(bagFee) + " 元。");
                        skipPackaging = true;
                    }
                } else {
                    int displaySize = bagSize > 25 ? 25 : bagSize;
                    String prefix = isDouble ? "高温纸塑袋(双)" : "高温纸塑袋";
                    pricingRule = prefix + (displaySize > 0 ? displaySize + "cm" : "") + "计费";
                    expectedUnitPrice = computeHighTempPaperPlastic(materialBillingCount, bagSize, zBagSize, notes, isDouble, hospitalName);
                }
            }
            }
            case INSTRUMENT_NONWOVEN -> {
            if (isLowTemp) {
                Double forcedLt = computeForceHighTempUnitPrice(forceHighTempPerItem, materialBillingCount);
                if (forcedLt != null && appliedSpecialFoldRule) {
                    expectedUnitPrice = forcedLt;
                    pricingRule = "低温特色折算单价";
                    notes.add("按特色规则低温折算单价 " + fmt(forceHighTempPerItem) + " 元/件 × "
                            + materialBillingCount + " 件 = " + fmt(expectedUnitPrice) + " 元。");
                } else {
                    pricingRule = "低温无纺布阶梯计费";
                    expectedUnitPrice = computeLowTempNonWoven(materialBillingCount, notes);
                }
            } else {
                Double forcedPrice = computeForceHighTempUnitPrice(forceHighTempPerItem, materialBillingCount);
                if (forcedPrice != null) {
                    expectedUnitPrice = forcedPrice;
                    pricingRule = "路径覆盖：高温固定单价";
                    notes.add("按路径覆盖高温单价 " + fmt(forceHighTempPerItem) + " 元/件 × "
                            + materialBillingCount + " 件 = " + fmt(expectedUnitPrice) + " 元。");
                } else {
                    Double dressingMeasure = extractDressingPackMeasure(packageMaterial, packName);
                    boolean dressingPackRow = type.contains("敷料包") || packName.contains("敷料");
                    if (dressingMeasure != null && materialBillingCount <= 1 && dressingPackRow) {
                        double dressPrice = computeDressingPackPrice(dressingMeasure);
                        if (dressPrice > 0) {
                            expectedUnitPrice = dressPrice;
                            pricingRule = "高温无纺布(敷料规格)——" + dressingMeasure;
                            notes.add("无纺布材料识别为敷料规格 " + dressingMeasure
                                    + "，单件按敷料包定价 " + fmt(dressPrice) + " 元。");
                        } else {
                            pricingRule = "高温无纺布计费";
                            expectedUnitPrice = computeHighTempNonWoven(materialBillingCount);
                        }
                    } else {
                        pricingRule = "高温无纺布计费";
                        expectedUnitPrice = computeHighTempNonWoven(materialBillingCount);
                    }
                }
            }
            }
            case UNKNOWN -> {
            pricingRule = "未识别包装类型，保留原价";
            notes.add("包装材料\"" + packageMaterial + "\"未能识别为纸塑袋或无纺布，已保留原始价格，请检查包装材料列填写是否正确，或手动调整单价。");
            requiresReview = true;
            }
            }
        }
        if (appliedSpecialFoldRule && foldMatchedRuleName != null && !foldMatchedRuleName.isBlank()) {
            pricingRule = foldMatchedRuleName;
        }

        if (specialPrice == null && expectedUnitPrice != null) {
            MultiplierResult multiplierResult = findCustomerMultiplier(
                    row, bagSize, effectiveCount, matchedProductId, matchedVariantId);
            if (multiplierResult != null) {
                double baseUnitPrice = expectedUnitPrice;
                expectedUnitPrice = round(baseUnitPrice * multiplierResult.multiplier);
                pricingRule = pricingRule + " + " + multiplierResult.ruleName;
                notes.add(multiplierResult.note);
                if (multiplierResult.skipHospitalDiscount) {
                    skipHospitalDiscount = true;
                }
                if (matchedRuleId == null && multiplierResult.ruleId != null) {
                    matchedRuleId = multiplierResult.ruleId;
                }
            }
        }

        List<SpecialFeeResult> specialFees = computeSpecialExtraFees(row, bagSize, materialBillingCount);
        for (SpecialFeeResult specialFee : specialFees) {
            if (expectedUnitPrice != null) {
                expectedUnitPrice = round(expectedUnitPrice + specialFee.fee);
                pricingRule = pricingRule + " + " + specialFee.ruleName;
                notes.add(specialFee.note);
                if (matchedRuleId == null && specialFee.ruleId != null) {
                    matchedRuleId = specialFee.ruleId;
                }
            }
        }

        if (!skipPackaging && instrumentCount > 10
                && matchesKeywordsBoundary(packName, needleKeywords, needleMatchMode)
                && !appliedSpecialFoldRule) {
            skipPackaging = true;
            notes.add("小件器械超过 10 件，按客户标准不加袋子钱。");
        }

        if (specialPrice != null && !skipPackaging && isPaperPlastic && !isLowTemp && expectedUnitPrice != null) {
            Double bagAddon = computeHighTempBagAddon(bagSize, zBagSize, isDouble, instrumentCount);
            if (bagAddon != null && bagAddon > 0) {
                expectedUnitPrice = round(expectedUnitPrice + bagAddon);
                pricingRule = pricingRule + " + 纸塑袋费";
                notes.add("按件计价叠加纸塑袋费 " + fmt(bagAddon) + " 元。");
            }
        }

        // 标准纸塑袋器械包：袋费已在高温/低温纸塑阶梯或 FOLD 内联包材中计取，不走 packaging 模块。
        // 院级 FOLD 含包材规则仍须走 packaging 模块（如方南南小件5合1含包材）。
        if (packCategory == PackPricingCategory.INSTRUMENT_PAPER && isPaperPlastic
                && !(appliedSpecialFoldRule && !foldSkipPackaging)) {
            skipPackaging = true;
        }

        // 包装收费
        if (!skipPackaging) {
            PackagingResult pkg = computePackagingCharge(row, materialBillingCount, notes);
            if (expectedUnitPrice != null) {
                expectedUnitPrice = round(expectedUnitPrice + pkg.fee);
                if (pkg.fee > 0) pricingRule = pricingRule + " + 包装收费";
            }
            if (pkg.warning) requiresReview = true;
        }
        // 客户级折扣（billingPolicies 分温策略，兼容 customerOverrides；跳过 export_only / settlement_only）
        int billingPieces = Math.max(1, materialBillingCount);
        if (expectedUnitPrice != null && !skipHospitalDiscount) {
            BillingPolicyApplier.BillDetailDiscount appliedDiscount = BillingPolicyApplier.applyBillDetailDiscounts(
                    rules, type, packName, packageMaterial, hospitalName,
                    expectedUnitPrice, billingPieces, skipHospitalDiscount, specialPrice != null);
            if (appliedDiscount != null) {
                expectedUnitPrice = appliedDiscount.price();
                pricingRule = pricingRule + appliedDiscount.ruleSuffix();
                notes.add(appliedDiscount.note());
            }
        }

        // 校正总价
        boolean recomputeTotals = rules.path("cleaning").path("recomputeTotalsWhenPriceChanges").asBoolean(true);
        Double correctedTotalPrice = expectedUnitPrice == null
            ? totalPrice
            : recomputeTotals
                ? round(expectedUnitPrice * Math.max(packCount, 1))
                : totalPrice;

        double originalTotal = totalPrice != null ? totalPrice : 0;
        Double difference = (correctedTotalPrice == null || totalPrice == null)
            ? null
            : round(correctedTotalPrice - originalTotal);

        if (!recomputeTotals && expectedUnitPrice != null && unitPrice != null &&
                Math.abs(expectedUnitPrice - unitPrice) > 0.001) {
            notes.add("规则建议单价为 " + fmt(expectedUnitPrice) + " 元，但当前配置保留原总价不自动重算，如需同步更新总价请手动调整。");
        }

        // 最终状态判定：有差额的行统一标为 warning（人工复核），用户点"一键修正"后才变为 corrected
        boolean anyPriceAccepted = false;
        if (specialPrice != null && specialPrice.anyPriceMode) {
            String candidates = formatPriceList(specialPrice.acceptedPrices);
            if (unitPrice != null) {
                Double matchedOption = findMatchingAcceptedPrice(unitPrice, specialPrice.acceptedPrices);
                if (matchedOption != null) {
                    anyPriceAccepted = true;
                    specialPrice.matchedPriceOption = matchedOption;
                    notes.add("多报价命中：规则「" + specialPrice.ruleName + "」/ 报价 "
                            + fmt(matchedOption) + " 元（候选：" + candidates + "）");
                } else {
                    notes.add("多报价未命中：规则「" + specialPrice.ruleName + "」/ 账单价 "
                            + fmt(unitPrice) + " 元不在候选报价（" + candidates + "）内");
                }
            }
        }

        if (expectedUnitPrice == null || correctedTotalPrice == null) {
            status = "skipped";
            notes.add("无法根据当前规则自动计算价格（可能因包装类型或袋尺寸未识别），请人工核定单价和总价。");
        } else if (anyPriceAccepted) {
            status = "unchanged";
            difference = 0.0;
        } else if (requiresReview) {
            status = "warning";
            if (unitPrice != null && expectedUnitPrice != null
                    && Math.abs(expectedUnitPrice - unitPrice) <= 0.001) {
                difference = 0.0;
            }
        } else if (difference != null && Math.abs(difference) > 0.001) {
            if (specialPrice == null && unitPrice != null && expectedUnitPrice != null
                    && Math.abs(expectedUnitPrice - unitPrice) <= DISPLAY_PRICE_TOLERANCE) {
                status = "unchanged";
                difference = 0.0;
                notes.add("账单单价与规则价相差不超过 "
                        + fmt(DISPLAY_PRICE_TOLERANCE) + " 元，按展示精度视为一致。");
            } else {
                status = "warning";
            }
        } else if (isUnpricedZeroImport(unitPrice, totalPrice, expectedUnitPrice, specialPrice)) {
            status = "warning";
        } else {
            status = "unchanged";
        }

        if (forceConsistencyWarning) {
            status = "warning";
        }
        if (forceBillingValidationWarning) {
            status = "warning";
        }

        ProcessedResult result = new ProcessedResult();
        result.expectedUnitPrice = expectedUnitPrice;
        result.correctedTotalPrice = correctedTotalPrice;
        result.difference = difference;
        result.status = status;
        result.pricingRule = pricingRule;
        result.notes = notes;
        result.matchedRuleId = matchedRuleId;
        result.pricingPath = resolveEffectivePricingPath(specialPrice, pricingRule);
        if (specialPrice != null) {
            result.matchedPriceOption = specialPrice.matchedPriceOption;
            if (specialPrice.anyPriceMode) {
                result.billingNotes = buildAnyPriceBillingNotes(
                        specialPrice, anyPriceAccepted, unitPrice, notes);
            } else {
                result.billingNotes = buildRowBillingNotes(
                        notes, type, packName, packageMaterial, hospitalName, skipHospitalDiscount,
                        matchedRuleId, specialPrice.ruleName, result.pricingPath);
            }
        } else {
            result.billingNotes = buildRowBillingNotes(
                    notes, type, packName, packageMaterial, hospitalName, skipHospitalDiscount,
                    matchedRuleId, null, result.pricingPath);
        }
        result.billingNotes = mergeBillingNotes(
                result.billingNotes, consistencyBillingNotes, billingValidationNotes);
        return result;
    }

    private Map<String, Object> mergeBillingNotes(
            Map<String, Object> primary,
            Map<String, Object> consistency,
            Map<String, Object> billingValidation) {
        Map<String, Object> merged = primary == null || primary.isEmpty()
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(primary);
        if (consistency != null && !consistency.isEmpty()) {
            if (merged.isEmpty()) {
                merged.putAll(consistency);
            } else {
                merged.put("fieldConsistency", consistency);
                merged.put("field_consistency", consistency);
                if ("field_consistency".equals(consistency.get("type"))) {
                    merged.put("consistencyViolations", consistency.get("violations"));
                }
            }
        }
        if (billingValidation != null && !billingValidation.isEmpty()) {
            if (merged.isEmpty()) {
                merged.putAll(billingValidation);
            } else {
                merged.put("billingValidation", billingValidation);
                merged.put("billing_validation", billingValidation);
            }
        }
        return merged.isEmpty() ? null : merged;
    }

    private String resolveEffectivePricingPath(SpecialPriceResult specialPrice, String pricingRule) {
        if (specialPrice != null) {
            return "fixed";
        }
        if ("special_only 未命中特色规则".equals(pricingRule)) {
            return "preserve";
        }
        if (pricingRule != null && isStandardSterilizationPricingRule(pricingRule)) {
            return "standard";
        }
        return null;
    }

    private boolean isStandardSterilizationPricingRule(String pricingRule) {
        if (pricingRule == null || pricingRule.isBlank()) {
            return false;
        }
        return pricingRule.contains("高温")
                || pricingRule.contains("低温")
                || pricingRule.contains("敷料")
                || pricingRule.contains("路径覆盖")
                || pricingRule.contains("阶梯")
                || pricingRule.contains("纸塑")
                || pricingRule.contains("无纺布")
                || pricingRule.contains("产品主数据公开价格");
    }

    private Map<String, Object> buildRowBillingNotes(
            List<String> notes,
            String type,
            String packName,
            String packageMaterial,
            String hospitalName,
            boolean skipHospitalDiscount,
            Long matchedRuleId,
            String ruleName,
            String effectivePricingPath) {
        Map<String, Object> billingNotes = new LinkedHashMap<>();
        if (matchedRuleId != null) {
            billingNotes.put("matchedRuleId", matchedRuleId);
            billingNotes.put("matched_rule_id", matchedRuleId);
        }
        if (ruleName != null && !ruleName.isBlank()) {
            billingNotes.put("ruleName", ruleName);
        }
        if (effectivePricingPath != null && !effectivePricingPath.isBlank()) {
            billingNotes.put("effectivePricingPath", effectivePricingPath);
            billingNotes.put("effective_pricing_path", effectivePricingPath);
        }
        List<Map<String, Object>> discountChain = buildDiscountChain(notes);
        if (!discountChain.isEmpty()) {
            billingNotes.put("discountChain", discountChain);
        }
        BillingPolicyApplier.AppliedDiscount discount = BillingPolicyApplier.resolveBestDiscount(
                rules, type, packName, packageMaterial, hospitalName, skipHospitalDiscount);
        if (discount.trace() != null && !discount.trace().isEmpty()) {
            List<Map<String, Object>> policyTraces = new ArrayList<>();
            for (String traceLine : discount.trace()) {
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("label", traceLine);
                policyTraces.add(step);
            }
            billingNotes.put("policyTraces", policyTraces);
        }
        return billingNotes.isEmpty() ? null : billingNotes;
    }

    private Map<String, Object> buildAnyPriceBillingNotes(
            SpecialPriceResult specialPrice,
            boolean anyPriceAccepted,
            Double unitPrice,
            List<String> notes) {
        Map<String, Object> billingNotes = new LinkedHashMap<>();
        billingNotes.put("type", anyPriceAccepted ? "any_price_match" : "any_price_mismatch");
        if (specialPrice.ruleId != null) {
            billingNotes.put("matchedRuleId", specialPrice.ruleId);
            billingNotes.put("matched_rule_id", specialPrice.ruleId);
        }
        billingNotes.put("ruleName", specialPrice.ruleName);
        billingNotes.put("candidatePrices", specialPrice.acceptedPrices);
        billingNotes.put("candidates", specialPrice.acceptedPrices);
        if (unitPrice != null) {
            billingNotes.put("billUnitPrice", unitPrice);
        }
        if (anyPriceAccepted) {
            billingNotes.put("verifiedByRule", false);
        }
        if (anyPriceAccepted && specialPrice.matchedPriceOption != null) {
            billingNotes.put("matchedPrice", specialPrice.matchedPriceOption);
            billingNotes.put("matchedPriceOption", specialPrice.matchedPriceOption);
        }
        List<Map<String, Object>> discountChain = buildDiscountChain(notes);
        if (!discountChain.isEmpty()) {
            billingNotes.put("discountChain", discountChain);
        }
        return billingNotes;
    }

    private List<Map<String, Object>> buildDiscountChain(List<String> notes) {
        List<Map<String, Object>> chain = new ArrayList<>();
        for (String note : notes) {
            if (!isDiscountNote(note)) {
                continue;
            }
            Map<String, Object> step = new LinkedHashMap<>();
            String label = note.length() > 48 ? note.substring(0, 48) + "…" : note;
            step.put("label", label);
            if (note.length() > 48) {
                step.put("detail", note);
            }
            chain.add(step);
        }
        return chain;
    }

    private boolean isDiscountNote(String note) {
        if (note == null || note.contains("多报价命中")) {
            return false;
        }
        return (note.contains("命中") && (note.contains("折扣") || note.contains("折")))
                || note.contains("×")
                || note.contains("→")
                || note.contains("倍计费")
                || note.contains("包装收费")
                || note.contains("倍率");
    }

    private String formatPriceList(List<Double> prices) {
        if (prices == null || prices.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < prices.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(fmt(prices.get(i)));
        }
        return sb.toString();
    }

    private Double findMatchingAcceptedPrice(Double unitPrice, List<Double> acceptedPrices) {
        if (unitPrice == null || acceptedPrices == null) {
            return null;
        }
        for (Double candidate : acceptedPrices) {
            if (candidate != null && Math.abs(unitPrice - candidate) <= 0.001) {
                return candidate;
            }
        }
        return null;
    }

    private FoldApplyResult applySpecialFoldRules(Map<String, Object> row, int bagSize, int effectiveCount, List<String> notes) {
        String combined = combinedText(row);
        JsonNode foldRules = rules.path("specialRules").path("foldRules");
        return applyFoldRuleList(row, foldRules, combined, bagSize, effectiveCount, notes);
    }

    private FoldApplyResult applyFoldRuleList(Map<String, Object> row, JsonNode foldRules, String combined, int bagSize,
                                              int effectiveCount, List<String> notes) {
        if (!foldRules.isArray()) {
            return new FoldApplyResult(effectiveCount, false, false, null, null, null, false);
        }
        BillingConditionEvaluator.RowContext ctx = new BillingConditionEvaluator.RowContext(
                str(row, "type"),
                str(row, "packName"),
                str(row, "packageMaterial"),
                str(row, "hospitalName"),
                str(row, "department"),
                doubleOrNull(row, "unitPrice"),
                bagSize,
                effectiveCount,
                null,
                null,
                combined
        );
        List<JsonNode> sortedRules = new ArrayList<>();
        foldRules.forEach(sortedRules::add);
        sortedRules.sort(java.util.Comparator.comparingInt(r -> r.path("priority").asInt(100)));
        for (JsonNode rule : sortedRules) {
            // 折算规则门控默认 exact_token（2026-08-27 基线行为：包名分词匹配，
            // "针"类模式词命中"针5盒1"而不误伤"车针排"）；规则显式 contains 或关键词带 @contains 后缀时走包含
            String foldMatchMode = BillingConditionEvaluator.resolveKeywordMatchMode(rule, BillingConditionEvaluator.KEYWORD_MATCH_EXACT_TOKEN);
            if (!BillingConditionEvaluator.matchesKeywordsByMode(str(row, "packName"), rule.path("keywords"), foldMatchMode)) {
                continue;
            }
            if (!BillingConditionEvaluator.matchesRule(rule, ctx)) continue;
            int threshold = rule.path("threshold").asInt(5);
            double foldRatio = rule.path("foldRatio").asDouble(5.0);
            // 折算后额外加计件数（如"针N盒1"的盒固定计 1 件，不参与 5 合 1 折算）
            int extraCount = rule.path("extraCount").asInt(0);
            int foldInput = effectiveCount;
            if (extraCount > 0 && effectiveCount > extraCount) {
                // 器械总数含盒时，仅针对针数做 5 合 1 折算，盒以 extraCount 固定追加
                foldInput = effectiveCount - extraCount;
            }
            int result = foldCount(foldInput, threshold, foldRatio);
            if (extraCount > 0) {
                result += extraCount;
            }
            String name = rule.path("name").asText("特殊小件折算");
            notes.add(name + "，原器械数 " + effectiveCount + " 件，折算为 " + result + " 件"
                    + (extraCount > 0 ? "（含额外 " + extraCount + " 件）" : "") + "。");
            boolean skipPackaging = rule.path("skipPackaging").asBoolean(false);
            Double unitPriceOverride = rule.has("unitPrice")
                    ? rule.path("unitPrice").asDouble(Double.NaN)
                    : Double.NaN;
            if (Double.isNaN(unitPriceOverride)) {
                unitPriceOverride = null;
            }
            Long ruleId = rule.has("ruleId") ? rule.path("ruleId").asLong() : null;
            return new FoldApplyResult(result, skipPackaging, true, unitPriceOverride, ruleId, name, extraCount > 0);
        }
        return new FoldApplyResult(effectiveCount, false, false, null, null, null, false);
    }

    private record FoldApplyResult(int effectiveCount, boolean skipPackaging, boolean matched,
                                   Double unitPriceOverride, Long ruleId, String ruleName,
                                   boolean hasExtraCount) {}

    private SpecialPriceResult findSpecialFixedPrice(
            Map<String, Object> row, int bagSize, int effectiveCount,
            Long matchedProductId, Long matchedVariantId) {
        String combined = combinedText(row);
        String hospitalName = str(row, "hospitalName");
        Double unitPrice = doubleOrNull(row, "unitPrice");
        JsonNode fixedPrices = rules.path("specialRules").path("fixedPrices");
        if (!fixedPrices.isArray()) {
            return null;
        }
        SpecialPriceResult firstMatch = null;
        SpecialPriceResult unitPriceMatch = null;
        SpecialPriceResult anyPriceAcceptedMatch = null;
        for (JsonNode rule : fixedPrices) {
            SpecialPriceResult matched = matchFixedPriceRule(
                    rule, row, combined, hospitalName, bagSize, effectiveCount,
                    matchedProductId, matchedVariantId);
            if (matched == null) {
                continue;
            }
            if (firstMatch == null) {
                firstMatch = matched;
            }
            if (unitPrice == null) {
                continue;
            }
            if (matched.anyPriceMode) {
                if (findMatchingAcceptedPrice(unitPrice, matched.acceptedPrices) != null) {
                    anyPriceAcceptedMatch = matched;
                }
            } else if (Math.abs(matched.price - unitPrice) <= 0.001) {
                unitPriceMatch = matched;
            }
        }
        if (anyPriceAcceptedMatch != null) {
            if (unitPrice != null) {
                Double accepted = findMatchingAcceptedPrice(unitPrice, anyPriceAcceptedMatch.acceptedPrices);
                if (accepted != null) {
                    anyPriceAcceptedMatch.matchedPriceOption = accepted;
                    anyPriceAcceptedMatch.price = accepted;
                }
            }
            return anyPriceAcceptedMatch;
        }
        if (unitPriceMatch != null) {
            return unitPriceMatch;
        }
        return firstMatch;
    }

    /**
     * 无客户专属固定价规则时，使用产品主数据 public_price 作为默认单价（计价路径 fixed）。
     */
    private SpecialPriceResult resolveProductPublicPrice(
            java.util.Optional<ProductMatchResolver.StructuredProductMatch> structuredMatch,
            Double unitPrice) {
        if (structuredMatch.isEmpty()) {
            return null;
        }
        ProductMatchResolver.StructuredProductMatch match = structuredMatch.get();
        if (match.publicPrice() == null || !"fixed".equals(match.pricingPath())) {
            return null;
        }
        // 器械包走标准阶梯（件数×5.5），不用 variant 中位数公开价锁定单价
        if ("INSTRUMENT_PACK".equals(match.categoryCode())) {
            return null;
        }
        if (unitPrice != null
                && Math.abs(match.publicPrice().doubleValue() - unitPrice) > 0.001) {
            return null;
        }
        SpecialPriceResult result = new SpecialPriceResult();
        result.price = match.publicPrice().doubleValue();
        result.ruleName = "产品主数据公开价格";
        result.skipPackaging = true;
        result.skipHospitalDiscount = false;
        result.note = "命中产品「" + match.productName() + "」，使用公开价格 " + fmt(result.price) + " 元。";
        return result;
    }

    private SpecialPriceResult matchFixedPriceRule(
            JsonNode rule, Map<String, Object> row, String combined, String hospitalName,
            int bagSize, int effectiveCount, Long matchedProductId, Long matchedVariantId) {
        BillingConditionEvaluator.RowContext ctx = BillingConditionEvaluator.RowContext.fromRow(
                row, bagSize, effectiveCount, matchedProductId, matchedVariantId);
        if (!BillingConditionEvaluator.matchesRule(rule, ctx)) {
            return null;
        }

        SpecialPriceResult result = new SpecialPriceResult();
        String matchMode = rule.path("matchMode").asText("first");
        List<Double> acceptedPrices = parseAcceptedPrices(rule.path("acceptedPrices"));
        double basePrice = rule.path("price").asDouble(Double.NaN);
        if (Double.isNaN(basePrice) && !acceptedPrices.isEmpty()) {
            basePrice = acceptedPrices.get(0);
        }
        if (Double.isNaN(basePrice)) return null;
        FixedPriceBillingCountResolver.RowInput rowInput = new FixedPriceBillingCountResolver.RowInput(
                str(row, "type"),
                str(row, "packName"),
                combined,
                Math.max(1, intVal(row, "packCount")),
                intVal(row, "instrumentCount"));
        FixedPriceBillingCountResolver.FixedPriceComputation computation =
                FixedPriceBillingCountResolver.compute(rule, rowInput, effectiveCount);
        if (computation == null) {
            return null;
        }
        result.price = computation.unitPrice();
        result.ruleName = rule.path("name").asText("特殊固定单价");
        result.ruleId = rule.has("ruleId") ? rule.path("ruleId").asLong() : null;
        result.anyPriceMode = "any_price".equalsIgnoreCase(matchMode) && !acceptedPrices.isEmpty();
        result.acceptedPrices = acceptedPrices;
        result.skipPackaging = rule.path("skipPackaging").asBoolean(false);
        result.skipHospitalDiscount = rule.path("skipHospitalDiscount").asBoolean(false);
        result.note = result.ruleName + computation.noteSuffix();
        if (result.anyPriceMode) {
            result.note += "（多报价候选：" + formatPriceList(acceptedPrices) + "）";
        }
        return result;
    }

    private List<Double> parseAcceptedPrices(JsonNode acceptedPricesNode) {
        List<Double> prices = new ArrayList<>();
        if (!acceptedPricesNode.isArray()) {
            return prices;
        }
        for (JsonNode node : acceptedPricesNode) {
            if (node.isNumber()) {
                prices.add(node.asDouble());
            }
        }
        return prices;
    }

    private MultiplierResult findCustomerMultiplier(
            Map<String, Object> row, int bagSize, int effectiveCount,
            Long matchedProductId, Long matchedVariantId) {
        String combined = combinedText(row);
        String hospitalName = str(row, "hospitalName");
        JsonNode multipliers = rules.path("specialRules").path("priceMultipliers");
        if (!multipliers.isArray()) {
            return null;
        }
        for (JsonNode rule : multipliers) {
            MultiplierResult matched = matchMultiplierRule(
                    rule, row, combined, hospitalName, bagSize, effectiveCount,
                    matchedProductId, matchedVariantId);
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    private MultiplierResult matchMultiplierRule(
            JsonNode rule, Map<String, Object> row, String combined, String hospitalName,
            int bagSize, int effectiveCount, Long matchedProductId, Long matchedVariantId) {
        BillingConditionEvaluator.RowContext ctx = BillingConditionEvaluator.RowContext.fromRow(
                row, bagSize, effectiveCount, matchedProductId, matchedVariantId);
        if (!BillingConditionEvaluator.matchesRule(rule, ctx)) {
            return null;
        }

        double multiplier = rule.path("multiplier").asDouble(Double.NaN);
        if (Double.isNaN(multiplier) || multiplier <= 0) return null;

        MultiplierResult result = new MultiplierResult();
        result.multiplier = multiplier;
        result.ruleName = rule.path("name").asText("客户商品倍率");
        result.skipHospitalDiscount = rule.path("skipHospitalDiscount").asBoolean(false);
        result.note = result.ruleName + "，基础单价 × " + multiplier + " 倍。";
        result.ruleId = rule.has("ruleId") ? rule.path("ruleId").asLong() : null;
        return result;
    }

    private List<SpecialFeeResult> computeSpecialExtraFees(
            Map<String, Object> row, int bagSize, int billingCount) {
        String combined = combinedText(row);
        String hospitalName = str(row, "hospitalName");
        JsonNode extraFees = rules.path("specialRules").path("extraFees");
        List<SpecialFeeResult> matchedFees = new ArrayList<>();
        if (extraFees.isArray()) {
            for (JsonNode rule : extraFees) {
                SpecialFeeResult matched = matchExtraFeeRule(rule, str(row, "packName"), combined, hospitalName, bagSize, billingCount);
                if (matched != null) {
                    matchedFees.add(matched);
                }
            }
        }
        return matchedFees;
    }

    private SpecialFeeResult matchExtraFeeRule(JsonNode rule, String packName, String combined, String hospitalName, int bagSize, int effectiveCount) {
        if (!hospitalMatches(rule, hospitalName)) return null;
        if (!BillingConditionEvaluator.matchesRuleKeywords(rule, packName, combined)) return null;
        if (!bagSizeMatches(rule, bagSize)) return null;
        int minCount = rule.path("minInstrumentCount").asInt(Integer.MIN_VALUE);
        int maxCount = rule.path("maxInstrumentCount").asInt(Integer.MAX_VALUE);
        if (effectiveCount < minCount || effectiveCount > maxCount) return null;

        SpecialFeeResult result = new SpecialFeeResult();
        result.fee = rule.path("fee").asDouble(Double.NaN);
        if (Double.isNaN(result.fee)) return null;
        result.ruleName = rule.path("name").asText("特殊加收");
        result.note = result.ruleName + "，加收 " + fmt(result.fee) + " 元。";
        result.ruleId = rule.has("ruleId") ? rule.path("ruleId").asLong() : null;
        return result;
    }

    /**
     * 0 元导入覆盖：pathOverride.zeroPriceMode=packaging_type 时按包装/包名规则计价（武警总队）。
     */
    private SpecialPriceResult resolveZeroPriceOverride(
            Map<String, Object> row, JsonNode pathOverride, Double unitPrice) {
        if (unitPrice != null && Math.abs(unitPrice) > 0.001) {
            return null;
        }
        String combined = combinedText(row);
        JsonNode zeroRules = rules.path("specialRules").path("zeroPriceOverrides");
        if (zeroRules.isArray()) {
            for (JsonNode rule : zeroRules) {
                if (!BillingConditionEvaluator.matchesRule(rule,
                        BillingConditionEvaluator.RowContext.fromRow(row, 0, 1, null, null))) {
                    continue;
                }
                double price = rule.path("price").asDouble(Double.NaN);
                if (Double.isNaN(price)) {
                    continue;
                }
                SpecialPriceResult result = new SpecialPriceResult();
                result.price = price;
                result.ruleName = rule.path("name").asText("0元覆盖价");
                result.skipPackaging = rule.path("skipPackaging").asBoolean(true);
                result.skipHospitalDiscount = rule.path("skipHospitalDiscount").asBoolean(false);
                result.note = result.ruleName + "，0 元导入按规则单价 " + fmt(price) + " 元。";
                return result;
            }
        }
        JsonNode pathOverrideSafe = pathOverride == null || pathOverride.isMissingNode()
                ? mapper.createObjectNode()
                : pathOverride;
        String zeroPriceMode = pathOverrideSafe.path("zeroPriceMode").asText("");
        if ("packaging_type".equalsIgnoreCase(zeroPriceMode)) {
            return resolvePackagingTypeZeroPrice(row, combined);
        }
        return null;
    }

    private SpecialPriceResult resolvePackagingTypeZeroPrice(Map<String, Object> row, String combined) {
        String packageMaterial = str(row, "packageMaterial");
        String packName = str(row, "packName");
        String type = str(row, "type");
        String normalized = BillingConditionEvaluator.normalizeMatchText(
                type + packName + packageMaterial + combined).toLowerCase();
        Double price = null;
        String label = null;
        if (containsPackagingKeyword(normalized, "无纺布")) {
            price = 20.0;
            label = "无纺布";
        } else if (containsPackagingKeyword(normalized, "纸塑袋")) {
            price = 8.0;
            label = "纸塑袋";
        } else if (containsPackagingKeyword(normalized, "环氧乙烷")
                || normalized.contains("过氧化氢")
                || containsLowTempSterilantKeyword(normalized)) {
            price = 35.0;
            label = "环氧乙烷";
        }
        if (price == null) {
            return null;
        }
        SpecialPriceResult result = new SpecialPriceResult();
        result.price = price;
        result.ruleName = "0元覆盖（" + label + "）";
        result.skipPackaging = true;
        result.skipHospitalDiscount = false;
        result.note = "0 元导入，按" + label + "规则单价 " + fmt(price) + " 元。";
        return result;
    }

    private boolean containsPackagingKeyword(String normalizedText, String keyword) {
        return normalizedText.contains(BillingConditionEvaluator.normalizeMatchText(keyword).toLowerCase());
    }

    private boolean containsLowTempSterilantKeyword(String normalizedText) {
        if (normalizedText.contains("eto")) {
            return true;
        }
        int idx = 0;
        while ((idx = normalizedText.indexOf("eo", idx)) != -1) {
            char prev = idx > 0 ? normalizedText.charAt(idx - 1) : 0;
            char next = idx + 2 < normalizedText.length() ? normalizedText.charAt(idx + 2) : 0;
            if ((prev == 0 || !Character.isLetter(prev))
                    && (next == 0 || !Character.isLetter(next))) {
                return true;
            }
            idx += 2;
        }
        return false;
    }

    private AppliedDiscount applyScopedDiscounts(
            String type,
            String packName,
            String packageMaterial,
            String hospitalName,
            Double expectedUnitPrice,
            boolean skipHospitalDiscount,
            boolean hitFixedPrice) {
        if (skipHospitalDiscount || expectedUnitPrice == null) {
            return null;
        }

        JsonNode billingPolicies = rules.path("billingPolicies");
        if (billingPolicies.isArray() && billingPolicies.size() > 0) {
            String rowTemp = resolveRowTemperature(type, packName, packageMaterial);
            JsonNode matched = findScopedDiscountPolicy(billingPolicies, rowTemp, hitFixedPrice);
            if (matched != null) {
                return buildAppliedDiscount(matched, hospitalName, expectedUnitPrice);
            }
            return null;
        }

        JsonNode customerOverrides = rules.path("customerOverrides");
        if (!customerOverrides.has("discountRate")) {
            return null;
        }
        if (hitFixedPrice && customerOverrides.path("skipWhenFixedPrice").asBoolean(true)) {
            return null;
        }
        double rate = customerOverrides.path("discountRate").asDouble(1.0);
        if (rate <= 0 || rate >= 1.0) {
            return null;
        }
        AppliedDiscount result = new AppliedDiscount();
        result.price = round(expectedUnitPrice * rate);
        String label = customerOverrides.path("discountLabel").asText("客户折扣");
        String displayName = customerOverrides.path("displayName").asText(hospitalName);
        result.ruleSuffix = " + " + displayName + " " + rate + "倍计费";
        result.note = "命中" + label + "，基础规则单价 "
                + fmt(expectedUnitPrice) + " 元 × " + rate + " = " + fmt(result.price) + " 元。";
        return result;
    }

    private AppliedDiscount buildAppliedDiscount(JsonNode policy, String hospitalName, double baseUnitPrice) {
        double rate = policy.path("params").path("rate").asDouble(1.0);
        if (rate <= 0 || rate >= 1.0) {
            return null;
        }
        AppliedDiscount result = new AppliedDiscount();
        result.price = round(baseUnitPrice * rate);
        String label = policy.path("name").asText("客户折扣");
        String tempScope = policy.path("scope").path("temperature").asText("ANY");
        String tempNote = "ANY".equalsIgnoreCase(tempScope) ? "" : "（" + tempScope + "）";
        result.ruleSuffix = " + " + hospitalName + " " + rate + "倍计费";
        result.note = "命中" + label + tempNote + "，基础规则单价 "
                + fmt(baseUnitPrice) + " 元 × " + rate + " = " + fmt(result.price) + " 元。";
        return result;
    }

    private JsonNode findScopedDiscountPolicy(JsonNode policies, String rowTemp, boolean hitFixedPrice) {
        List<JsonNode> discounts = new ArrayList<>();
        for (JsonNode policy : policies) {
            if ("DISCOUNT".equalsIgnoreCase(policy.path("policyType").asText())) {
                discounts.add(policy);
            }
        }
        discounts.sort(Comparator.comparingInt(p -> p.path("priority").asInt(100)));
        JsonNode anyFallback = null;
        for (JsonNode policy : discounts) {
            String scopeTemp = policy.path("scope").path("temperature").asText("ANY");
            if (!temperatureScopeMatches(scopeTemp, rowTemp)) {
                continue;
            }
            if (hitFixedPrice && policy.path("params").path("skipWhenFixedPrice").asBoolean(true)) {
                continue;
            }
            if ("ANY".equalsIgnoreCase(scopeTemp)) {
                anyFallback = policy;
                continue;
            }
            return policy;
        }
        return anyFallback;
    }

    private boolean temperatureScopeMatches(String scopeTemp, String rowTemp) {
        if (scopeTemp == null || scopeTemp.isBlank() || "ANY".equalsIgnoreCase(scopeTemp)) {
            return true;
        }
        return scopeTemp.equalsIgnoreCase(rowTemp);
    }

    private String resolveRowTemperature(String type, String packName, String packageMaterial) {
        return resolveRowTemperature(type + packName + packageMaterial);
    }

    private String resolveRowTemperature(String combined) {
        if (combined.contains("低温") || combined.contains("ETO") || combined.contains("EO")) {
            return "LT";
        }
        return "HT";
    }

    private boolean bagSizeMatches(JsonNode rule, int bagSize) {
        if (rule.has("bagSizeEquals") && bagSize != rule.path("bagSizeEquals").asInt()) return false;
        if (rule.has("minBagSizeInclusive") && bagSize < rule.path("minBagSizeInclusive").asInt()) return false;
        if (rule.has("maxBagSizeInclusive") && bagSize > rule.path("maxBagSizeInclusive").asInt()) return false;
        if (rule.has("maxBagSizeExclusive") && bagSize >= rule.path("maxBagSizeExclusive").asInt()) return false;
        return true;
    }

    private boolean hospitalMatches(JsonNode rule, String hospitalName) {
        JsonNode hospitals = rule.path("hospitals");
        if (!hospitals.isArray() || hospitals.isEmpty()) return true;
        String normalizedName = hospitalName.replaceAll("\\s+", "");
        for (JsonNode h : hospitals) {
            String candidate = h.asText("").replaceAll("\\s+", "");
            if (candidate.isEmpty()) continue;
            if (normalizedName.equals(candidate)
                    || normalizedName.contains(candidate)
                    || candidate.contains(normalizedName)) {
                return true;
            }
        }
        return false;
    }

    private int foldCount(int count, int threshold, double foldRatio) {
        if (count <= 0) return 1;
        return Math.max(1, (int) Math.ceil(count / Math.max(1.0, foldRatio)));
    }

    /**
     * 吸脂针按包名区分型号长度：20cm 及以上按实件计费，不参与全局「针」小件 5 合 1 折算。
     */
    private boolean isLiposuctionNeedleLongVariant(String packName) {
        if (packName == null || !packName.contains("吸脂针")) {
            return false;
        }
        String normalized = packName.replaceAll("\\s+", "").toLowerCase();
        return normalized.contains("型号20cm以上") || normalized.contains("20cm以上");
    }

    private String combinedText(Map<String, Object> row) {
        return str(row, "type") + " " + str(row, "packName") + " " + str(row, "packageMaterial");
    }

    private boolean matchesRuleKeywords(String text, JsonNode keywords) {
        return !keywords.isArray() || keywords.size() == 0 || matchesKeywords(text, keywords);
    }

    // ================================================================
    //  袋尺寸检测（带缓存）
    // ================================================================

    /** 棉球/棉球包敷料名；棉球缸是容器，走高温纸塑件费+袋费。 */
    private boolean isCottonDressingPackName(String packName) {
        return packName.contains("棉球") && !packName.contains("棉球缸");
    }

    /** 器械数为 0 的敷料按包行：不要把 0 件强制当成 1 件。 */
    private boolean isDressingPerPackRow(
            String packName, String type, int instrumentCount, PackPricingCategory category) {
        if (instrumentCount > 0) {
            return false;
        }
        if (packName.contains("棉球缸")) {
            return false;
        }
        if (category == PackPricingCategory.DRESSING_PAPER) {
            if (isCottonDressingPackName(packName) && type.contains("纸塑袋")) {
                return true;
            }
            return type.contains("敷料包") && type.contains("纸塑袋");
        }
        if (category == PackPricingCategory.DRESSING_NONWOVEN && packName.contains("驱血带")) {
            return true;
        }
        return false;
    }

    /** 棉球/纱布纸塑袋敷料价：纯敷料棉球；棉球缸等容器走高温纸塑「件费+袋费」。 */
    private boolean shouldUseDressingCottonPaperPlasticPrice(
            String packName, String type, int instrumentCount, int packCount) {
        if (!isCottonDressingPackName(packName)) {
            return false;
        }
        if (type.contains("敷料包") && type.contains("纸塑袋")) {
            return true;
        }
        int perPack = packCount > 1
                ? (int) Math.round((double) instrumentCount / Math.max(1, packCount))
                : instrumentCount;
        return perPack <= 0;
    }

    /**
     * 棉球敷料单价：精确 key → 档位 ≤15=2.5 / ≥20=4.0。
     * 禁止回落到高温纸塑袋材费（15cm=5.5），避免按包棉球被当成按件器械费。
     */
    private Double resolveCottonPaperPlasticUnitPriceByTier(int bagSizeCm) {
        if (bagSizeCm <= 0) {
            return null;
        }
        JsonNode cottonPricing = rules.path("dressingPack").path("cottonPaperPlastic");
        String sizeKey = String.valueOf(bagSizeCm);
        if (cottonPricing.has(sizeKey) && cottonPricing.path(sizeKey).isNumber()) {
            return cottonPricing.path(sizeKey).asDouble();
        }
        if (bagSizeCm >= 20) {
            return 4.0;
        }
        return 2.5;
    }

    private Double resolveCottonPaperPlasticUnitPrice(int bagSizeCm) {
        return resolveCottonPaperPlasticUnitPriceByTier(bagSizeCm);
    }

    /** 按件计价叠加纸塑袋费：优先 cottonPaperPlastic 分档，否则客户确认 <20cm=2.5 / ≥20cm=4。 */
    private Double resolvePerPiecePaperPlasticBagAddon(int bagSizeCm) {
        if (bagSizeCm <= 0) {
            return null;
        }
        JsonNode cottonPricing = rules.path("dressingPack").path("cottonPaperPlastic");
        String sizeKey = String.valueOf(bagSizeCm);
        if (cottonPricing.has(sizeKey)) {
            return cottonPricing.path(sizeKey).asDouble();
        }
        if (bagSizeCm >= 20) {
            return 4.0;
        }
        if (bagSizeCm >= 15) {
            return 2.5;
        }
        return null;
    }

    private int detectBagSize(String input) {
        if (input == null || input.isEmpty()) return 0;
        String key = input.replaceAll("\\s+", "");
        Integer cached = bagSizeCache.get(key);
        if (cached != null) return cached;

        int result = 0;
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("(\\d+)\\s*[×x*]\\s*\\d+")
                .matcher(key);
        if (mm.find()) {
            int firstNum = Integer.parseInt(mm.group(1));
            if (firstNum >= 50) {
                int cmSize = firstNum / 10;
                for (BagConfig bag : allBagConfigs) {
                    if (bag.size == cmSize) { result = bag.size; break; }
                }
                if (result == 0) {
                    for (BagConfig bag : sortedBagConfigs) {
                        if (bag.size >= cmSize) { result = bag.size; break; }
                    }
                }
                if (result == 0 && !sortedBagConfigs.isEmpty()) {
                    result = sortedBagConfigs.get(sortedBagConfigs.size() - 1).size;
                }
            }
        }

        if (result == 0) {
            for (Map.Entry<String, Integer> entry : keywordToBagSize.entrySet()) {
                if (key.contains(entry.getKey())) {
                    result = entry.getValue();
                    break;
                }
            }
        }

        // 从包名中大写字母 Z 后前两位数字提取尺寸（如 Z1530 → 15）
        if (result == 0) {
            result = extractSizeFromZPattern(key);
        }

        // 容量保护：超过上限时清空缓存，防止长期运行内存泄漏
        if (bagSizeCache.size() >= MAX_CACHE_SIZE) {
            bagSizeCache.clear();
        }
        bagSizeCache.put(key, result);
        return result;
    }

    /** 从"双"标记之后提取前两位数字作为纸塑袋尺寸（如 /双15 → 15，/(双)Z1526 → 15，/双75 → 10）；"双"标记后紧跟汉字则不追加袋费 */
    private int extractSizeAfterDouble(String input) {
        if (input == null || input.isEmpty()) return 0;
        java.util.regex.Matcher mark = DOUBLE_BAG_MARK.matcher(input);
        if (!mark.find()) return 0;
        int afterIdx = mark.end(); // 跳过"双"标记（含可选右括号）
        if (afterIdx >= input.length()) return 0;

        char next = input.charAt(afterIdx);
        // "双"标记后紧跟汉字 → 不需要额外纸塑袋
        if (Character.UnicodeBlock.of(next) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) return 0;

        String after = input.substring(afterIdx);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{2})").matcher(after);
        if (m.find()) {
            int rawSize = Integer.parseInt(m.group(1));
            // 特殊映射：75 → 10
            int size = rawSize == 75 ? 10 : rawSize;
            for (BagConfig bag : allBagConfigs) {
                if (bag.size == size) return bag.size;
            }
            for (BagConfig bag : sortedBagConfigs) {
                if (bag.size >= size) return bag.size;
            }
            if (!sortedBagConfigs.isEmpty()) {
                return sortedBagConfigs.get(sortedBagConfigs.size() - 1).size;
            }
            return size;
        }
        return 0;
    }

    private int extractSizeFromZPattern(String input) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Z(\\d{2})").matcher(input);
        if (m.find()) {
            int size = Integer.parseInt(m.group(1));
            for (BagConfig bag : allBagConfigs) {
                if (bag.size == size) return bag.size;
            }
            for (BagConfig bag : sortedBagConfigs) {
                if (bag.size >= size) return bag.size;
            }
            if (!sortedBagConfigs.isEmpty()) {
                return sortedBagConfigs.get(sortedBagConfigs.size() - 1).size;
            }
        }
        return 0;
    }

    private List<BagConfig> buildAllBagConfigs() {
        List<BagConfig> list = new ArrayList<>();
        for (JsonNode bag : rules.path("highTemperature").path("paperPlastic").path("bagSizes")) {
            BagConfig bc = new BagConfig();
            bc.size = bag.path("size").asInt();
            bc.price = bag.path("price").asDouble();
            bc.keywords = jsonArrayToStringList(bag.path("keywords"));
            list.add(bc);
        }
        for (JsonNode bag : rules.path("lowTemperature").path("paperPlastic").path("bagSizes")) {
            BagConfig bc = new BagConfig();
            bc.size = bag.path("size").asInt();
            bc.price = bag.path("price").asDouble();
            bc.keywords = jsonArrayToStringList(bag.path("keywords"));
            list.add(bc);
        }
        return list;
    }

    private Map<String, Integer> buildKeywordMap() {
        Map<String, Integer> map = new HashMap<>();
        for (BagConfig bag : allBagConfigs) {
            for (String kw : bag.keywords) {
                map.put(kw, bag.size);
            }
        }
        return map;
    }

    // ================================================================
    //  高温纸塑袋
    // ================================================================

    private Double computeHighTempPaperPlastic(int instrumentCount, int bagSize, int zBagSize, List<String> notes, boolean isDouble, String hospitalName) {
        // 尺寸大于 25 时，按 25 标准收费
        int effectiveSize = bagSize;
        if (bagSize > 25) {
            effectiveSize = 25;
            notes.add("纸塑袋尺寸 " + bagSize + "cm > 25cm，按 25cm 标准计费。");
        }
        JsonNode config = rules.path("highTemperature").path("paperPlastic");
        JsonNode bagConfig = findBagConfig(effectiveSize, config.path("bagSizes"));
        if (bagConfig == null) {
            notes.add("未识别高温纸塑袋尺寸，无法精确匹配袋费，请人工复核。");
            return null;
        }
        double perPackagePrice = config.path("perPackagePrice").asDouble();
        double packageFee = round(perPackagePrice * instrumentCount);
        double bagFee1 = bagConfig.path("price").asDouble();

        // 包名含"双"：额外加一个 Z 尺寸纸塑袋费
        double bagFee2 = 0;
        if (isDouble && zBagSize > 0) {
            int effZSize = zBagSize > 25 ? 25 : zBagSize;
            JsonNode zBagConfig = findBagConfig(effZSize, config.path("bagSizes"));
            if (zBagConfig != null) {
                bagFee2 = zBagConfig.path("price").asDouble();
            }
        }

        double totalWithBag = round(packageFee + bagFee1 + bagFee2);
        double capPrice = config.path("minCharge").asDouble();
        String capMode = config.path("capMode").asText("standard");
        if ("fuyi".equalsIgnoreCase(capMode)) {
            if (instrumentCount == 1) {
                double total = round(bagFee1 + bagFee2);
                notes.add("附一高温纸塑：单件按袋规 " + fmt(bagFee1 + bagFee2) + " 元计费。");
                return total;
            }
            if (instrumentCount == 2) {
                double total = round(bagFee1 + bagFee2 + perPackagePrice);
                notes.add("附一高温纸塑：2 件 = 袋规 " + fmt(bagFee1 + bagFee2) + " + 最低把价 "
                        + fmt(perPackagePrice) + " = " + fmt(total) + " 元。");
                return total;
            }
            double total = round(perPackagePrice * instrumentCount);
            notes.add("附一高温纸塑：" + instrumentCount + " 件 × 最低把价 " + fmt(perPackagePrice)
                    + " = " + fmt(total) + " 元。");
            return total;
        }
        if ("none".equalsIgnoreCase(capMode)) {
            notes.add("高温纸塑袋按件费 + 袋费计费，不启用 " + fmt(capPrice) + " 元封顶：件费 "
                    + fmt(packageFee) + " + 袋费 " + fmt(bagFee1 + bagFee2) + " = " + fmt(totalWithBag) + " 元。");
            return totalWithBag;
        }

        if (instrumentCount >= 3) {
            boolean chargeDoubleBagWhenCapped = config.path("chargeDoubleBagWhenCapped").asBoolean(false);
            if (chargeDoubleBagWhenCapped && isDouble && bagFee2 > 0) {
                double total = round(packageFee + bagFee2);
                notes.add("大于等于 3 件，按 " + fmt(perPackagePrice) + " 元/件 × " + instrumentCount
                        + " = " + fmt(packageFee) + " 元计费，基础袋费免收，但双层纸塑袋追加第二层袋费 "
                        + fmt(bagFee2) + " 元，合计 " + fmt(total) + " 元。");
                return total;
            }
            String doubleNote = isDouble && bagFee2 > 0
                ? "（包名含\"双\"，但 ≥3 件袋费免收）"
                : "";
            notes.add("大于等于 3 件，按 " + fmt(perPackagePrice) + " 元/件 × " + instrumentCount
                    + " = " + fmt(packageFee) + " 元计费，不再计袋费。" + doubleNote);
            return packageFee;
        }

        String doubleNote = isDouble && bagFee2 > 0
            ? "，含双袋费(" + zBagSize + "cm) " + fmt(bagFee2) + " 元"
            : "";
        notes.add("高温纸塑袋件数 " + instrumentCount + " 件，含袋费 " + fmt(totalWithBag) +
                " 元" + doubleNote + "，封顶 " + fmt(capPrice) + " 元。");
        return Math.min(totalWithBag, capPrice);
    }

    // ================================================================
    //  高温无纺布
    // ================================================================

    private double computeHighTempNonWoven(int instrumentCount) {
        JsonNode config = rules.path("highTemperature").path("nonWoven");
        if (instrumentCount > config.path("flatRateThreshold").asInt(Integer.MAX_VALUE)) {
            return round(config.path("flatPerPackagePrice").asDouble() * instrumentCount);
        }
        return config.path("minCharge").asDouble();
    }

    // ================================================================
    //  低温纸塑袋
    // ================================================================

    private Double computeLowTempPaperPlastic(int instrumentCount, int bagSize, int zBagSize, List<String> notes,
                                              boolean isDouble, String hospitalName) {
        if (instrumentCount == 1 && isDouble && !isHrb2ndHospital(hospitalName)) {
            notes.add("低温单件纸塑袋包名含「双」，按客户标准固定 35 元计费（市二院除外）。");
            return 35.0;
        }
        JsonNode config = rules.path("lowTemperature").path("paperPlastic");

        if (instrumentCount == 1) {
            JsonNode bagConfig = findBagConfig(bagSize, config.path("bagSizes"));
            if (bagConfig == null) {
                notes.add("未识别低温纸塑袋尺寸，按最低 22 元计费。");
                return 22.0;
            }
            double price = bagConfig.path("price").asDouble();
            if (isDouble && zBagSize > 0) {
                notes.add("低温单件纸塑袋 " + bagConfig.path("size").asInt() + "cm，单件计费忽略包名「双」追加袋费，单价 "
                        + fmt(price) + " 元。");
            } else {
                notes.add("低温单件纸塑袋 " + bagConfig.path("size").asInt() + "cm，单价 "
                        + fmt(price) + " 元。");
            }
            return price;
        }

        // 优先查表：2件及以上使用 priceTable 精确匹配（与 Excel 价格表完全一致）
        JsonNode priceTable = config.path("priceTable");
        String countKey = String.valueOf(instrumentCount);
        if (priceTable.has(countKey)) {
            double tablePrice = priceTable.path(countKey).asDouble();
            notes.add("低温纸塑袋 " + instrumentCount + " 件按价格表精确匹配 " + fmt(tablePrice) + " 元。");
            return tablePrice;
        }

        // 超出价格表范围时，回退到阶梯计算 + 封顶逻辑
        double remainderPrice = config.path("remainderPerPiecePrice").asDouble(22.0);
        JsonNode tiers = config.path("tierPrices");
        if (instrumentCount <= 5) {
            double cap = findTierCap(tiers, 5, 88);
            if (instrumentCount >= 4) {
                notes.add("低温纸塑袋 " + instrumentCount + " 件按 " + fmt(cap) + " 元（≤5 件 tier）计费。");
                return cap;
            }
            double total = round(instrumentCount * remainderPrice);
            if (total > cap) {
                notes.add("低温纸塑袋 " + instrumentCount + " 件封顶 " + fmt(cap) + " 元（原计 " + fmt(total) + " 元）。");
                total = cap;
            } else {
                notes.add("低温纸塑袋 " + instrumentCount + " 件 × " + fmt(remainderPrice) + " 元 = " + fmt(total) + " 元。");
            }
            return total;
        }

        TierSplit split = findTierSplit(instrumentCount, tiers);
        double tierTotal = split.tierTotal;
        double remainderTotal = split.remainder * remainderPrice;
        double total = round(tierTotal + remainderTotal);

        if (!split.chunks.isEmpty()) {
            StringBuilder sb = new StringBuilder("低温纸塑袋阶梯：");
            for (int i = 0; i < split.chunks.size(); i++) {
                if (i > 0) sb.append(" + ");
                TierChunk c = split.chunks.get(i);
                sb.append(c.count).append("件=").append(fmt(c.price)).append("");
            }
            notes.add(sb.toString());
        }
        if (split.remainder > 0) {
            notes.add("余 " + split.remainder + " 件按 " + fmt(remainderPrice) + " 元/件计费。");
        }

        // 封顶
        List<JsonNode> sortedTiers = jsonArrayToList(tiers);
        sortedTiers.sort(Comparator.comparingInt(a -> a.path("count").asInt()));
        for (JsonNode tier : sortedTiers) {
            int capCount = tier.path("count").asInt();
            double capPrice = tier.path("price").asDouble();
            if (instrumentCount <= capCount && total > capPrice) {
                notes.add(instrumentCount + " 件封顶 " + fmt(capPrice) + " 元（原计 " + fmt(total) + " 元）。");
                total = capPrice;
                break;
            }
        }
        return total;
    }

    // ================================================================
    //  低温无纺布
    // ================================================================

    private double computeLowTempNonWoven(int instrumentCount, List<String> notes) {
        JsonNode config = rules.path("lowTemperature").path("nonWoven");

        if (instrumentCount == 1) {
            notes.add("低温单件无纺布，单价 " + fmt(config.path("minSingleCharge").asDouble()) + " 元。");
            return config.path("minSingleCharge").asDouble();
        }

        // 优先查表：2件及以上使用 priceTable 精确匹配（与 Excel 价格表完全一致）
        JsonNode priceTable = config.path("priceTable");
        String countKey = String.valueOf(instrumentCount);
        if (priceTable.has(countKey)) {
            double tablePrice = priceTable.path(countKey).asDouble();
            notes.add("低温无纺布 " + instrumentCount + " 件按价格表精确匹配 " + fmt(tablePrice) + " 元。");
            return tablePrice;
        }

        // 超出价格表范围时，回退到阶梯计算 + 封顶逻辑
        double remainderPrice = config.path("remainderPerPiecePrice").asDouble(22.0);
        JsonNode tiers = config.path("tierPrices");
        if (instrumentCount <= 5) {
            double total = round(instrumentCount * remainderPrice);
            double cap = findTierCap(tiers, 5, 88);
            if (total > cap) {
                notes.add("低温无纺布 " + instrumentCount + " 件封顶 " + fmt(cap) + " 元（原计 " + fmt(total) + " 元）。");
                total = cap;
            } else {
                notes.add("低温无纺布 " + instrumentCount + " 件 × " + fmt(remainderPrice) + " 元 = " + fmt(total) + " 元。");
            }
            return total;
        }

        TierSplit split = findTierSplit(instrumentCount, tiers);
        double tierTotal = split.tierTotal;
        double remainderTotal = split.remainder * remainderPrice;
        double total = round(tierTotal + remainderTotal);

        if (!split.chunks.isEmpty()) {
            StringBuilder sb = new StringBuilder("低温无纺布阶梯：");
            for (int i = 0; i < split.chunks.size(); i++) {
                if (i > 0) sb.append(" + ");
                TierChunk c = split.chunks.get(i);
                sb.append(c.count).append("件=").append(fmt(c.price));
            }
            notes.add(sb.toString());
        }
        if (split.remainder > 0) {
            notes.add("余 " + split.remainder + " 件按 " + fmt(remainderPrice) + " 元/件计费。");
        }

        List<JsonNode> sortedTiers = jsonArrayToList(tiers);
        sortedTiers.sort(Comparator.comparingInt(a -> a.path("count").asInt()));
        for (JsonNode tier : sortedTiers) {
            int capCount = tier.path("count").asInt();
            double capPrice = tier.path("price").asDouble();
            if (instrumentCount <= capCount && total > capPrice) {
                notes.add(instrumentCount + " 件封顶 " + fmt(capPrice) + " 元（原计 " + fmt(total) + " 元）。");
                total = capPrice;
                break;
            }
        }
        return total;
    }

    // ================================================================
    //  包装收费
    // ================================================================

    private PackagingResult computePackagingCharge(Map<String, Object> row, int effectiveCount, List<String> notes) {
        if (!rules.path("packaging").path("enabled").asBoolean(false)) {
            return new PackagingResult();
        }

        String combined = str(row, "type") + " " + str(row, "packName") + " " + str(row, "packageMaterial");
        JsonNode selfPacked = rules.path("packaging").path("selfPackedKeywords");
        if (matchesKeywords(combined, selfPacked)) {
            notes.add("命中医院自行打包规则，仅收灭菌费，不收包装费。");
            return new PackagingResult();
        }

        JsonNode matchedItem = null;
        for (JsonNode item : rules.path("packaging").path("items")) {
            if (matchesKeywords(combined, item.path("keywords"))) {
                matchedItem = item;
                break;
            }
        }
        if (matchedItem == null) return new PackagingResult();

        JsonNode options = matchedItem.path("options");
        if (!options.isArray() || options.size() == 0) {
            String itemName = matchedItem.path("name").asText("");
            // 纸塑袋费已并入标准灭菌阶梯；rules_json 中「纸塑袋」占位项无 options 时不应误告警。
            if ("纸塑袋".equals(itemName) && containsPackagingKeyword(combined, "纸塑袋")) {
                return new PackagingResult();
            }
            notes.add("命中包装收费项目\"" + itemName + "\"，但该项目未配置具体选项价格，请先在计费规则中配置价格，或手动核定包装费。");
            PackagingResult pr = new PackagingResult();
            pr.warning = true;
            return pr;
        }

        JsonNode matchedOption = null;
        for (JsonNode opt : options) {
            if (matchesKeywords(combined, opt.path("keywords"))) {
                matchedOption = opt;
                break;
            }
        }
        if (matchedOption == null) {
            notes.add("命中包装收费项目\"" + matchedItem.path("name").asText() + "\"，但未识别到具体包装规格大小，请人工选择对应规格或手动调整包装费。");
            PackagingResult pr = new PackagingResult();
            pr.warning = true;
            return pr;
        }

        double fee = matchedItem.path("chargePerPack").asBoolean(false)
            ? matchedOption.path("price").asDouble()
            : round(matchedOption.path("price").asDouble() * Math.max(effectiveCount, 1));
        notes.add("包装收费：" + matchedItem.path("name").asText() + "-" +
                matchedOption.path("label").asText() + " = " + fmt(fee) + " 元。");

        PackagingResult pr = new PackagingResult();
        pr.fee = fee;
        return pr;
    }

    // ================================================================
    //  敷料包
    // ================================================================

    /** 无纺布敷料 W 码规格前缀（最长优先），如 W5050→50、W15050→150。 */
    private static final int[] DRESSING_W_SIZE_PREFIXES = {150, 120, 90, 70, 60, 50};

    private Double extractDressingPackMeasure(String material, String packName) {
        String combined = (material == null ? "" : material) + " " + (packName == null ? "" : packName);
        java.util.regex.Matcher wMarker = java.util.regex.Pattern
                .compile("[/＿_]?W\\s*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(combined);
        if (wMarker.find()) {
            String digits = wMarker.group(1);
            for (int prefix : DRESSING_W_SIZE_PREFIXES) {
                String prefixStr = String.valueOf(prefix);
                if (digits.startsWith(prefixStr)) {
                    if (prefix >= 120) {
                        return prefix / 100.0;
                    }
                    return (double) prefix;
                }
            }
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[×x*]\\s*\\d+")
                .matcher(material == null ? "" : material);
        if (m.find()) {
            double measure = Double.parseDouble(m.group(1));
            if (measure >= 100) {
                measure = measure / 100.0;
            }
            return measure;
        }
        return null;
    }

    private Double extractDressingPackMeasure(String material) {
        return extractDressingPackMeasure(material, "");
    }

    private Double computeHighTempBagAddon(int bagSize, int zBagSize, boolean isDouble, int rawInstrumentCount) {
        int effectiveSize = bagSize > 25 ? 25 : bagSize;
        JsonNode config = rules.path("highTemperature").path("paperPlastic");
        JsonNode bagConfig = findBagConfig(effectiveSize, config.path("bagSizes"));
        if (bagConfig == null) {
            return null;
        }
        double bagFee1 = bagConfig.path("price").asDouble();
        double bagFee2 = 0;
        int innerBagSize = 0;
        if (isDouble && zBagSize > 0) {
            int effZSize = zBagSize > 25 ? 25 : zBagSize;
            innerBagSize = effZSize;
            JsonNode zBagConfig = findBagConfig(effZSize, config.path("bagSizes"));
            if (zBagConfig != null) {
                bagFee2 = zBagConfig.path("price").asDouble();
            }
        }
        if (rawInstrumentCount >= 3 && isDouble) {
            Double cottonInner = resolvePerPiecePaperPlasticBagAddon(innerBagSize > 0 ? innerBagSize : effectiveSize);
            if (cottonInner != null) {
                return cottonInner;
            }
            if (bagFee2 > 0) {
                return bagFee2;
            }
            Double cottonOuter = resolvePerPiecePaperPlasticBagAddon(effectiveSize);
            return cottonOuter != null ? cottonOuter : bagFee1;
        }
        Double cottonOuter = resolvePerPiecePaperPlasticBagAddon(effectiveSize);
        return cottonOuter != null ? cottonOuter : bagFee1;
    }

    private static boolean isHrb2ndHospital(String hospitalName) {
        if (hospitalName == null || hospitalName.isBlank()) {
            return false;
        }
        return hospitalName.contains("第二医院") || hospitalName.contains("市二院");
    }

    private double defaultDressingPackPrice() {
        JsonNode nonWoven = rules.path("dressingPack").path("nonWoven");
        if (nonWoven.has("below90")) {
            return nonWoven.path("below90").asDouble(25);
        }
        return 25;
    }

    private static boolean isZeroImport(Double unitPrice, Double totalPrice) {
        if (unitPrice == null || Math.abs(unitPrice) > 0.001) {
            return false;
        }
        return totalPrice == null || Math.abs(totalPrice) <= 0.001;
    }

    private double computeDressingPackPrice(double measure) {
        JsonNode nonWoven = rules.path("dressingPack").path("nonWoven");
        if (nonWoven.isMissingNode() || nonWoven.isEmpty()) {
            if (measure == Math.floor(measure) && !Double.isInfinite(measure)) {
                if (measure == 90) return 30;
                if (measure < 90) return 25;
            }
            if (measure >= 1.2 && measure <= 1.5) return 35;
            return 0;
        }
        if (measure == Math.floor(measure) && !Double.isInfinite(measure)) {
            if (measure == 90 && nonWoven.has("equals90")) {
                return nonWoven.path("equals90").asDouble();
            }
            if (measure < 90 && nonWoven.has("below90")) {
                return nonWoven.path("below90").asDouble();
            }
        }
        if (measure >= 1.2 && measure <= 1.5 && nonWoven.has("range12to15")) {
            return nonWoven.path("range12to15").asDouble();
        }
        return 0;
    }

    // ================================================================
    //  阶梯定价
    // ================================================================

    private TierSplit findTierSplit(int totalCount, JsonNode tiersArray) {
        List<JsonNode> sorted = jsonArrayToList(tiersArray);
        sorted.sort(Comparator.comparingInt((JsonNode a) -> a.path("count").asInt()).reversed());
        int remaining = Math.max(0, totalCount);
        List<TierChunk> chunks = new ArrayList<>();

        for (JsonNode tier : sorted) {
            int count = tier.path("count").asInt();
            double price = tier.path("price").asDouble();
            while (remaining >= count) {
                TierChunk c = new TierChunk();
                c.count = count;
                c.price = price;
                chunks.add(c);
                remaining -= count;
            }
        }

        TierSplit split = new TierSplit();
        split.chunks = chunks;
        split.remainder = remaining;
        split.tierTotal = chunks.stream().mapToDouble(c -> c.price).sum();
        return split;
    }

    private double findTierCap(JsonNode tiers, int count, double defaultVal) {
        for (JsonNode tier : tiers) {
            if (tier.path("count").asInt() == count) {
                return tier.path("price").asDouble();
            }
        }
        return defaultVal;
    }

    private JsonNode findBagConfig(int size, JsonNode bagSizes) {
        if (size <= 0) return null;
        for (JsonNode bag : bagSizes) {
            if (bag.path("size").asInt() == size) return bag;
        }
        return null;
    }

    // ================================================================
    //  工具方法
    // ================================================================

    private boolean matchesKeywords(String text, JsonNode keywords) {
        if (!keywords.isArray()) return false;
        String normalized = BillingConditionEvaluator.normalizeMatchText(text).toLowerCase();
        for (JsonNode kw : keywords) {
            String raw = BillingConditionEvaluator.normalizeMatchText(kw.asText()).toLowerCase();
            if (raw.isEmpty()) continue;
            for (String k : raw.split("[，,]")) {
                if (!k.isEmpty() && normalized.contains(k)) return true;
            }
        }
        return false;
    }

    private boolean matchesKeywordList(String text, List<?> keywords) {
        if (keywords == null || keywords.isEmpty()) return true;
        String normalized = text.replaceAll("\\s+", "").toLowerCase();
        for (Object kw : keywords) {
            String raw = String.valueOf(kw).replaceAll("\\s+", "").toLowerCase();
            if (!raw.isEmpty() && normalized.contains(raw)) return true;
        }
        return false;
    }

    private boolean matchesKeywordsBoundary(String text, JsonNode keywords, String mode) {
        return BillingConditionEvaluator.matchesKeywordsByMode(text, keywords, mode);
    }

    /** 查找包名中命中（按匹配模式）的小件关键词及其位置，用于区分大件/小件混合包 */
    private SmallItemSplit findSmallItemSplit(String text, JsonNode keywords, String mode) {
        BillingConditionEvaluator.ExactTokenKeywordMatch match =
                BillingConditionEvaluator.findKeywordByMode(text, keywords, mode);
        if (match == null) {
            return null;
        }
        SmallItemSplit split = new SmallItemSplit();
        split.keyword = match.keyword();
        split.position = match.position();
        split.compactText = match.compactText();
        return split;
    }

    /** 小件折算生效参数：命中关键词带 keywordConfigs 独立配置时覆盖全局触发件数/折算比例。 */
    record NeedleFoldParams(int threshold, double foldRatio) {}

    /**
     * 合并小件识别有效关键词：keywordConfigs 独立配置关键词优先（其 matchMode 转为 @contains/@exact
     * 后缀，复用逐词模式解析），普通 keywords 中未被配置覆盖的词原样保留（词级 @后缀 仍然有效）。
     * 未配置 keywordConfigs 时等价于原 keywords 数组，保持既有行为不变。
     */
    static ArrayNode effectiveNeedleKeywords(JsonNode needle) {
        ArrayNode result = mapper.createArrayNode();
        Set<String> covered = new HashSet<>();
        JsonNode configs = needle.path("keywordConfigs");
        if (configs.isArray()) {
            for (JsonNode cfg : configs) {
                String kw = cfg.path("keyword").asText("").trim();
                if (kw.isEmpty()) {
                    continue;
                }
                result.add(kw + needleMatchModeSuffix(cfg.path("matchMode").asText("")));
                covered.add(BillingConditionEvaluator.normalizeMatchText(kw).toLowerCase());
            }
        }
        JsonNode keywords = needle.path("keywords");
        if (keywords.isArray()) {
            for (JsonNode kwNode : keywords) {
                for (BillingConditionEvaluator.ParsedKeyword pk
                        : BillingConditionEvaluator.parseKeywordList(kwNode.asText(""))) {
                    if (pk.keyword().isBlank()) {
                        continue;
                    }
                    if (covered.add(BillingConditionEvaluator.normalizeMatchText(pk.keyword()).toLowerCase())) {
                        result.add(pk.mode() != null ? pk.keyword() + "@" + pk.mode() : pk.keyword());
                    }
                }
            }
        }
        return result;
    }

    /** 独立配置 matchMode 转关键词 @后缀；未设置或非法值返回空串（沿用全局默认匹配模式）。 */
    private static String needleMatchModeSuffix(String matchMode) {
        if (matchMode == null) {
            return "";
        }
        return switch (matchMode.trim().toLowerCase()) {
            case "contains" -> "@contains";
            case "exact", "exact_token", "exacttoken" -> "@exact";
            default -> "";
        };
    }

    /** 解析命中关键词的触发件数/折算比例：命中 keywordConfigs 独立配置时覆盖全局默认值。 */
    static NeedleFoldParams resolveNeedleFoldParams(JsonNode needle, String matchedKeyword) {
        int threshold = needle.path("threshold").asInt(5);
        double foldRatio = needle.path("foldRatio").asDouble(5.0);
        if (matchedKeyword == null || matchedKeyword.isBlank()) {
            return new NeedleFoldParams(threshold, foldRatio);
        }
        JsonNode configs = needle.path("keywordConfigs");
        if (!configs.isArray()) {
            return new NeedleFoldParams(threshold, foldRatio);
        }
        String target = BillingConditionEvaluator.normalizeMatchText(matchedKeyword).toLowerCase();
        for (JsonNode cfg : configs) {
            String kw = cfg.path("keyword").asText("").trim();
            if (kw.isEmpty()
                    || !BillingConditionEvaluator.normalizeMatchText(kw).toLowerCase().equals(target)) {
                continue;
            }
            int cfgThreshold = cfg.path("threshold").asInt(-1);
            if (cfgThreshold >= 0) {
                threshold = cfgThreshold;
            }
            double cfgFoldRatio = cfg.path("foldRatio").asDouble(-1);
            if (cfgFoldRatio > 0) {
                foldRatio = cfgFoldRatio;
            }
            break;
        }
        return new NeedleFoldParams(threshold, foldRatio);
    }

    private Double computeForceHighTempUnitPrice(double forceHighTempPerItem, int effectiveCount) {
        if (Double.isNaN(forceHighTempPerItem) || forceHighTempPerItem <= 0) {
            return null;
        }
        return round(forceHighTempPerItem * Math.max(1, effectiveCount));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String fmt(Double value) {
        if (value == null) return "0";
        return value == Math.floor(value) && !Double.isInfinite(value)
            ? String.format("%.0f", value)
            : String.format("%.2f", value);
    }

    private static String str(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? "" : val.toString();
    }

    private static int intVal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt(((String) val).replace(",", "").trim()); } catch (Exception e) { }
        }
        return 0;
    }

    private static Double doubleOrNull(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try {
                String s = ((String) val).replace(",", "").replace("￥", "").trim();
                if (s.isEmpty()) return null;
                return Double.parseDouble(s);
            } catch (Exception e) { return null; }
        }
        return null;
    }

    private static Long longOrNull(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val == null) {
                continue;
            }
            if (val instanceof Number n) {
                return n.longValue();
            }
            if (val instanceof String s && !s.isBlank()) {
                try {
                    return Long.parseLong(s.trim());
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return null;
    }

    private static List<JsonNode> jsonArrayToList(JsonNode array) {
        List<JsonNode> list = new ArrayList<>();
        if (array.isArray()) array.forEach(list::add);
        return list;
    }

    /** 从字符串中提取最后一个数字，未找到时返回 0 */
    private int extractLastNumber(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)(?!.*\\d)").matcher(s);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    /** 「针N」匹配是否气腹针：「气腹」与「针」之间允许空白分隔（如 气腹 针1，含全角空格）。 */
    private static boolean isVeressNeedleAt(String packName, int needleStart) {
        int i = needleStart - 1;
        while (i >= 0 && Character.isSpaceChar(packName.charAt(i))) {
            i--;
        }
        return i >= 1 && "气腹".equals(packName.substring(i - 1, i + 1));
    }

    /** 从字符串中提取所有"器械名+数字"模式的数字求和（只计中文紧接的数字，排除 Z7537 等编码） */
    private int sumAllNumbers(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\\u4e00-\\u9fff]+(\\d+)").matcher(s);
        int sum = 0;
        while (m.find()) {
            sum += Integer.parseInt(m.group(1));
        }
        return sum;
    }

    private static List<String> jsonArrayToStringList(JsonNode array) {
        List<String> list = new ArrayList<>();
        if (array.isArray()) array.forEach(n -> list.add(n.asText()));
        return list;
    }

    // ================================================================
    //  内部数据类
    // ================================================================

    public static class ProcessedResult {
        public Double expectedUnitPrice;
        public Double correctedTotalPrice;
        public Double difference;
        public String status = "unchanged";
        public String pricingRule = "";
        public List<String> notes = new ArrayList<>();
        public Long matchedRuleId;
        public Double matchedPriceOption;
        /** 实际计价路径：fixed=客户校正/固定价，standard=标准灭菌阶梯，preserve=保留原价 */
        public String pricingPath;
        public Map<String, Object> billingNotes;
    }

    private static class AppliedDiscount {
        double price;
        String ruleSuffix;
        String note;
    }

    private static class BagConfig {
        int size;
        double price;
        List<String> keywords = new ArrayList<>();
    }

    private static class PackagingResult {
        double fee;
        boolean warning;
    }

    private static class SpecialPriceResult {
        double price;
        String ruleName;
        String note;
        boolean skipPackaging;
        boolean skipHospitalDiscount;
        Long ruleId;
        boolean anyPriceMode;
        List<Double> acceptedPrices = List.of();
        Double matchedPriceOption;
    }

    private static class SpecialFeeResult {
        double fee;
        String ruleName;
        String note;
        Long ruleId;
    }

    private static class MultiplierResult {
        double multiplier;
        String ruleName;
        String note;
        boolean skipHospitalDiscount;
        Long ruleId;
    }

    private static class TierSplit {
        List<TierChunk> chunks = new ArrayList<>();
        int remainder;
        double tierTotal;
    }

    private static class TierChunk {
        int count;
        double price;
    }

    private static class SmallItemSplit {
        String keyword;
        int position;
        String compactText;
    }

    /**
     * 账单 0 元且规则侧仍为 0（未命中 0 元覆盖/固定价），不应因差额为 0 而视为 unchanged。
     */
    private static boolean isUnpricedZeroImport(
            Double unitPrice,
            Double totalPrice,
            Double expectedUnitPrice,
            SpecialPriceResult specialPrice) {
        if (specialPrice != null) {
            return false;
        }
        if (unitPrice == null || Math.abs(unitPrice) > 0.001) {
            return false;
        }
        if (totalPrice != null && Math.abs(totalPrice) > 0.001) {
            return false;
        }
        return expectedUnitPrice != null && Math.abs(expectedUnitPrice) <= 0.001;
    }

    /**
     * 源账单「器械包(ZSD)」常无包装材料列；按铂康惯例视为高温无纺布阶梯计费。
     */
    private String inferPricingPackageMaterial(String type, String packageMaterial, List<String> notes) {
        if (packageMaterial != null && !packageMaterial.isBlank()) {
            return packageMaterial;
        }
        if (type == null || type.isBlank()) {
            return packageMaterial == null ? "" : packageMaterial;
        }
        if (isZsdInstrumentPackType(type)) {
            notes.add("器械包(ZSD)未填写包装材料，按高温无纺布标准阶梯计费。");
            return "无纺布";
        }
        return packageMaterial == null ? "" : packageMaterial;
    }

    /**
     * 附一睿思 export 导入：包材列为「无纺布-60×60」「高温纸塑袋200*440」等编码，
     * 人工核对版使用「无纺布」「低温灭菌 30cm」等计费语义。
     */
    private String normalizeFuyiImportMaterial(String type, String packName, String packageMaterial,
                                               List<String> notes) {
        if (!"fuyi".equalsIgnoreCase(
                rules.path("highTemperature").path("paperPlastic").path("capMode").asText(""))) {
            return packageMaterial;
        }
        String mat = packageMaterial == null ? "" : packageMaterial.trim();
        String packLower = packName == null ? "" : packName.toLowerCase();
        String typeNorm = type == null ? "" : type;

        if (typeNorm.contains("额外包") && typeNorm.contains("无纺布") && !typeNorm.contains("敷料")) {
            Double measure = extractDressingPackMeasure(mat);
            if (measure != null && measure >= 50) {
                notes.add("附一睿思 export：额外包(无纺布)大包材归一化为「无纺布」，按高温无纺布最低收费。");
                return "无纺布";
            }
        }

        if (typeNorm.contains("敷料包") && packLower.contains("w15050") && mat.isEmpty()) {
            notes.add("附一 W15050 敷料包补全包材为敷料大（30cm*30cm*50cm）。");
            return "敷料大（30cm*30cm*50cm）";
        }

        boolean lowTempRow = typeNorm.contains("ETO") || typeNorm.contains("低温") || typeNorm.contains("EO");
        if (lowTempRow && packLower.contains("w12050")
                && mat.contains("无纺布") && mat.contains("×")) {
            notes.add("附一 w12050 睿思无纺布包材归一化为低温灭菌 30cm。");
            return "低温灭菌 30cm";
        }

        if (lowTempRow) {
            String inferred = inferFuyiLowTempSterilizeMaterial(packName, mat);
            if (inferred != null && !inferred.equals(mat)) {
                notes.add("附一睿思 export 包材「" + mat + "」归一化为「" + inferred + "」。");
                return inferred;
            }
        }

        return packageMaterial;
    }

    /** 附一：睿思纸塑袋编码 + 包名语义 → 人工核对版「低温灭菌 Ncm」包材（非泛匹配 z2044）。 */
    private String inferFuyiLowTempSterilizeMaterial(String packName, String material) {
        if (packName == null || packName.isBlank() || material == null || material.isBlank()) {
            return null;
        }
        if (!material.contains("纸塑袋") && !material.contains("无纺布")) {
            return null;
        }
        String packLower = packName.toLowerCase();
        if (packLower.contains("保温杯") || packLower.contains("保温瓶")) {
            return "低温灭菌 20cm";
        }
        if (packLower.contains("宫腔镜") && packLower.contains("z2044")) {
            return "低温灭菌 10cm";
        }
        if (packLower.contains("特殊钳") && packLower.contains("z2044")) {
            return "低温灭菌 20cm";
        }
        if (packLower.contains("w12050")) {
            return "低温灭菌 30cm";
        }
        if (packLower.contains("z3095")) {
            return "低温灭菌 10cm";
        }
        return null;
    }

    private boolean isZsdInstrumentPackType(String type) {
        if (type == null || type.isBlank()) {
            return false;
        }
        String normalized = type.replaceAll("\\s+", "");
        return normalized.contains("器械包(ZSD)")
                || (normalized.contains("器械包") && normalized.toUpperCase().contains("ZSD"));
    }
}
