package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        packageMaterial = inferPricingPackageMaterial(type, packageMaterial, notes);
        row.put("packageMaterial", packageMaterial);
        int instrumentCount = intVal(row, "instrumentCount");
        int packCount = Math.max(1, intVal(row, "packCount"));
        Double unitPrice = doubleOrNull(row, "unitPrice");
        Double totalPrice = doubleOrNull(row, "totalPrice");

        // 低温类型判定：类型含"低温"、"ETO"、"EO"均为低温处理
        JsonNode billingProfile = rules.path("billingProfile");
        if (billingProfile.has("enabled") && !billingProfile.path("enabled").asBoolean(true)) {
            ProcessedResult disabled = new ProcessedResult();
            disabled.expectedUnitPrice = unitPrice;
            disabled.correctedTotalPrice = totalPrice;
            disabled.difference = 0.0;
            disabled.status = "unchanged";
            disabled.pricingRule = "特色账单已关闭";
            disabled.notes = List.of("客户未启用特色账单，保留原始价格。");
            return disabled;
        }
        JsonNode pathOverride = billingProfile.path("pathOverride");
        SpecialPriceResult zeroPriceOverride = resolveZeroPriceOverride(row, pathOverride, unitPrice);
        boolean disableLowTemp = pathOverride.path("disableLowTemp").asBoolean(false);
        double forceHighTempPerItem = pathOverride.path("forceHighTempUnitPrice").asDouble(Double.NaN);
        String pricingMode = billingProfile.path("pricingMode").asText("standard");
        boolean specialOnly = "special_only".equalsIgnoreCase(pricingMode);

        boolean isLowTempType = !disableLowTemp
                && (type.contains("低温") || type.contains("ETO") || type.contains("EO"));
        boolean isZsdInstrumentPack = isZsdInstrumentPackType(type);

        int effectiveCount = instrumentCount;
        if (packCount > 1 && !isZsdInstrumentPack) {
            effectiveCount = Math.max(1, (int) Math.round((double) effectiveCount / packCount));
        }
        if (effectiveCount == 0) effectiveCount = 1;
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

        // 低温预处理在单包件数计算之后执行。
        // 低温包装类型 + 包名含"盒" + 单包件数不为1 → 单包件数减1（盒不收费）
        if (isLowTempType && packName.contains("盒") && effectiveCount > 0 && effectiveCount != 1) {
            effectiveCount = Math.max(1, effectiveCount - 1);
            notes.add("包装类型含低温标识且包名含\"盒\"，单包件数减 1 件（盒不收费）。");
        }

        // 低温包装类型 + 包名不含"盒" → 按包名中"件"前数字取器械数（单包）
        if (isLowTempType && !packName.contains("盒")) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+)\\s*件");
            java.util.regex.Matcher m = p.matcher(packName);
            if (m.find()) {
                int extracted = Integer.parseInt(m.group(1));
                if (extracted > 0 && extracted != effectiveCount) {
                    effectiveCount = extracted;
                    notes.add("包装类型含低温标识且包名不含\"盒\"，按包名中\"件\"前数字取器械数为 " + effectiveCount + "。");
                }
            }
        }


        // 袋尺寸检测（带缓存）。部分特例规则需要先知道袋型，例如“20cm 以下 5 件算 1 件”。
        int bagSize = detectBagSize(packageMaterial + packName);
        boolean isPaperPlastic = packageMaterial.contains("纸塑袋")
                || type.contains("纸塑袋")
                || packageMaterial.contains("低温灭菌");
        boolean isNonWoven = packageMaterial.contains("无纺布") || type.contains("无纺布");
        boolean isLowTemp = !disableLowTemp && ((type + packName + packageMaterial).contains("低温")
                || type.contains("ETO") || type.contains("EO")
                || packageMaterial.contains("低温灭菌"));
        boolean isDouble = packName.contains("双");
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
        if (preMatchedSpecialPrice == null && !isZsdInstrumentPack) {
            int countBeforeSpecialFold = effectiveCount;
            effectiveCount = applySpecialFoldRules(row, bagSize, effectiveCount, notes);
            appliedSpecialFoldRule = effectiveCount != countBeforeSpecialFold;
        }

        // 针数量规则 + 小件器械折算（针数量规则优先：包名含"针+数字"时按公式拆分）
        JsonNode needle = rules.path("needle");
        java.util.regex.Pattern needleQtyPattern = java.util.regex.Pattern.compile("针(\\d+)");
        java.util.regex.Matcher needleQtyMatcher = needleQtyPattern.matcher(packName);
        boolean appliedNeedleRule = false;
        if (preMatchedSpecialPrice == null && !appliedSpecialFoldRule && !isZsdInstrumentPack && needleQtyMatcher.find()) {
            String[] parts = packName.split("针\\d+", 2);
            String beforeNeedle = parts[0];
            String afterNeedle = parts.length > 1 ? parts[1] : "";
            // "针N"后是否还有器械名（如"钢丝4"），用于区分纯小件与混合器械
            boolean hasOtherItems = java.util.regex.Pattern.compile("[\\u4e00-\\u9fff]+\\d+").matcher(afterNeedle).find();
            boolean isSmallItemKeyword = matchesKeywordsBoundary(packName, needle.path("keywords"));
            // 若"针"是小件关键词的一部分（如"克氏针"）且针后无其他器械，跳过拆分
            if (isSmallItemKeyword && !hasOtherItems) {
                // 不应用针数量拆分，交给下方小件关键词规则处理
            } else {
                int needleQty = Integer.parseInt(needleQtyMatcher.group(1));
                double foldRatio = needle.path("foldRatio").asDouble(5.0);
                int nonNeedleCount = extractLastNumber(beforeNeedle);
                if (hasOtherItems) {
                    nonNeedleCount += extractFirstNumber(afterNeedle);
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
        if (preMatchedSpecialPrice == null && !appliedSpecialFoldRule && !appliedNeedleRule
                && !isLiposuctionNeedleLongVariant(packName) && !isZsdInstrumentPack) {
            SmallItemSplit smallSplit = findSmallItemSplit(packName, needle.path("keywords"));
            if (smallSplit != null) {
                int threshold = needle.path("threshold").asInt(5);
                double foldRatio = needle.path("foldRatio").asDouble(5.0);
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
        boolean skipPackaging = false;
        boolean skipHospitalDiscount = false;
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
        } else if (type.contains("纸塑袋") && packName.contains("棉球")) {
            int bagSize2 = detectBagSize(str(row, "packageMaterial") + str(row, "packName"));
            Double cottonPrice = resolveCottonPaperPlasticUnitPrice(bagSize2);
            if (cottonPrice != null) {
                expectedUnitPrice = cottonPrice;
                pricingRule = "敷料包(纸塑袋)+棉球——" + bagSize2 + "cm";
                notes.add("敷料包(纸塑袋)+棉球，纸塑袋规格 " + bagSize2 + "cm，单价为 "
                        + fmt(expectedUnitPrice) + " 元。");
            } else {
                pricingRule = "敷料包(纸塑袋)+棉球——未识别规格";
                notes.add("敷料包(纸塑袋)+棉球未能识别纸塑袋规格，保留原始价格。");
                requiresReview = true;
            }
            skipPackaging = true;
        } else if (type.contains("敷料包") && type.contains("纸塑袋")) {
            // 敷料包(纸塑袋)（棉球已在上方单独定价）：按账单原单价，勿把纸塑袋 75*200 尺寸误当无纺布敷料包
            pricingRule = "敷料包(纸塑袋)——保留原单价";
            notes.add("敷料包(纸塑袋)按账单原单价计费，不套用无纺布敷料包尺寸价或高温纸塑袋阶梯。");
            skipPackaging = true;
        } else if (type.contains("敷料包")) {
            // 敷料包(无纺布包)
            Double measure = extractDressingPackMeasure(packageMaterial);
            if (measure != null) {
                double dressPrice = computeDressingPackPrice(measure);
                if (dressPrice > 0) {
                    expectedUnitPrice = dressPrice;
                    pricingRule = "敷料包(无纺布包)——" + measure;
                    notes.add("敷料包规格 " + measure + "，按敷料包定价表计算单价为 " + fmt(expectedUnitPrice) + " 元。");
                    if (unitPrice != null && Math.abs(dressPrice - unitPrice) <= 0.05) {
                        expectedUnitPrice = unitPrice;
                    }
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
        } else if (specialOnly) {
            Double forcedPrice = computeForceHighTempUnitPrice(forceHighTempPerItem, materialBillingCount);
            if (forcedPrice != null) {
                expectedUnitPrice = forcedPrice;
                pricingRule = "路径覆盖：高温固定单价";
                notes.add("计价模式为仅特色规则，按路径覆盖高温单价 "
                        + fmt(forceHighTempPerItem) + " 元/件 × " + materialBillingCount + " 件 = "
                        + fmt(expectedUnitPrice) + " 元。");
            } else {
                pricingRule = "special_only 未命中特色规则";
                notes.add("计价模式为仅特色规则，未命中固定价且非敷料，保留原始价格。");
                requiresReview = true;
            }
        } else if (isPaperPlastic) {
            if (isLowTemp) {
                String ltPrefix = isDouble ? "低温纸塑袋(双)" : "低温纸塑袋";
                pricingRule = ltPrefix + (bagSize > 0 ? bagSize + "cm" : "") + "阶梯计费";
                expectedUnitPrice = computeLowTempPaperPlastic(materialBillingCount, bagSize, zBagSize, notes, isDouble);
            } else {
                Double forcedPrice = computeForceHighTempUnitPrice(forceHighTempPerItem, materialBillingCount);
                if (forcedPrice != null) {
                    expectedUnitPrice = forcedPrice;
                    pricingRule = "路径覆盖：高温固定单价";
                    notes.add("按路径覆盖高温单价 " + fmt(forceHighTempPerItem) + " 元/件 × "
                            + materialBillingCount + " 件 = " + fmt(expectedUnitPrice) + " 元。");
                } else {
                    int displaySize = bagSize > 25 ? 25 : bagSize;
                    String prefix = isDouble ? "高温纸塑袋(双)" : "高温纸塑袋";
                    pricingRule = prefix + (displaySize > 0 ? displaySize + "cm" : "") + "计费";
                    expectedUnitPrice = computeHighTempPaperPlastic(materialBillingCount, bagSize, zBagSize, notes, isDouble, hospitalName);
                }
            }
        } else if (isNonWoven) {
            if (isLowTemp) {
                pricingRule = "低温无纺布阶梯计费";
                expectedUnitPrice = computeLowTempNonWoven(materialBillingCount, notes);
            } else {
                Double forcedPrice = computeForceHighTempUnitPrice(forceHighTempPerItem, materialBillingCount);
                if (forcedPrice != null) {
                    expectedUnitPrice = forcedPrice;
                    pricingRule = "路径覆盖：高温固定单价";
                    notes.add("按路径覆盖高温单价 " + fmt(forceHighTempPerItem) + " 元/件 × "
                            + materialBillingCount + " 件 = " + fmt(expectedUnitPrice) + " 元。");
                } else {
                    pricingRule = "高温无纺布计费";
                    expectedUnitPrice = computeHighTempNonWoven(materialBillingCount);
                }
            }
        } else {
            pricingRule = "未识别包装类型，保留原价";
            notes.add("包装材料\"" + packageMaterial + "\"未能识别为纸塑袋或无纺布，已保留原始价格，请检查包装材料列填写是否正确，或手动调整单价。");
            if (unitPrice == null || isZeroImport(unitPrice, totalPrice)) {
                requiresReview = true;
            }
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
            }
        }

        SpecialFeeResult specialFee = computeSpecialExtraFee(row, bagSize, effectiveCount);
        if (specialFee != null && expectedUnitPrice != null) {
            expectedUnitPrice = round(expectedUnitPrice + specialFee.fee);
            pricingRule = pricingRule + " + " + specialFee.ruleName;
            notes.add(specialFee.note);
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
        boolean recomputeTotals = rules.path("cleaning").path("recomputeTotalsWhenPriceChanges").asBoolean(false);
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
        } else if (difference != null && Math.abs(difference) > 0.001) {
            status = "warning";
        } else if (requiresReview || isUnpricedZeroImport(unitPrice, totalPrice, expectedUnitPrice, specialPrice)) {
            status = "warning";
        } else {
            status = "unchanged";
        }

        ProcessedResult result = new ProcessedResult();
        result.expectedUnitPrice = expectedUnitPrice;
        result.correctedTotalPrice = correctedTotalPrice;
        result.difference = difference;
        result.status = status;
        result.pricingRule = pricingRule;
        result.notes = notes;
        if (specialPrice != null) {
            result.matchedRuleId = specialPrice.ruleId;
            result.matchedPriceOption = specialPrice.matchedPriceOption;
            if (specialPrice.anyPriceMode) {
                result.billingNotes = buildAnyPriceBillingNotes(
                        specialPrice, anyPriceAccepted, unitPrice, notes);
            }
        }
        return result;
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

    private int applySpecialFoldRules(Map<String, Object> row, int bagSize, int effectiveCount, List<String> notes) {
        String combined = combinedText(row);
        JsonNode foldRules = rules.path("specialRules").path("foldRules");
        return applyFoldRuleList(row, foldRules, combined, bagSize, effectiveCount, notes);
    }

    private int applyFoldRuleList(Map<String, Object> row, JsonNode foldRules, String combined, int bagSize,
                                  int effectiveCount, List<String> notes) {
        if (!foldRules.isArray()) return effectiveCount;
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
        for (JsonNode rule : foldRules) {
            if (!matchesRuleKeywords(combined, rule.path("keywords"))) continue;
            if (!BillingConditionEvaluator.matchesRule(rule, ctx)) continue;
            int threshold = rule.path("threshold").asInt(5);
            double foldRatio = rule.path("foldRatio").asDouble(5.0);
            int result = foldCount(effectiveCount, threshold, foldRatio);
            String name = rule.path("name").asText("特殊小件折算");
            notes.add(name + "，原器械数 " + effectiveCount + " 件，折算为 " + result + " 件。");
            return result;
        }
        return effectiveCount;
    }

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
        boolean pricePerInstrument = rule.path("pricePerInstrument").asBoolean(false);
        int billingCount;
        if (pricePerInstrument) {
            String rowType = str(row, "type");
            int rowPackCount = Math.max(1, intVal(row, "packCount"));
            int rowInstrumentCount = intVal(row, "instrumentCount");
            if (isZsdInstrumentPackType(rowType) && rowPackCount > 1) {
                billingCount = Math.max(1, (int) Math.round((double) rowInstrumentCount / rowPackCount));
            } else {
                billingCount = resolvePricePerInstrumentCount(
                        rule, str(row, "packName"), combined, effectiveCount);
            }
        } else {
            billingCount = effectiveCount;
        }
        result.price = pricePerInstrument ? round(basePrice * Math.max(1, billingCount)) : basePrice;
        result.ruleName = rule.path("name").asText("特殊固定单价");
        result.ruleId = rule.has("ruleId") ? rule.path("ruleId").asLong() : null;
        result.anyPriceMode = "any_price".equalsIgnoreCase(matchMode) && !acceptedPrices.isEmpty();
        result.acceptedPrices = acceptedPrices;
        result.skipPackaging = rule.path("skipPackaging").asBoolean(false);
        result.skipHospitalDiscount = rule.path("skipHospitalDiscount").asBoolean(false);
        result.note = pricePerInstrument
                ? result.ruleName + "，按每件 " + fmt(basePrice) + " 元，单包计费件数 "
                + Math.max(1, billingCount) + " 件，单价按 " + fmt(result.price) + " 元。"
                : result.ruleName + "，单价按 " + fmt(result.price) + " 元。";
        if (result.anyPriceMode) {
            result.note += "（多报价候选：" + formatPriceList(acceptedPrices) + "）";
        }
        return result;
    }

    /** 刮勺探针x：按包名后缀 x 作为按件计价件数（5.5×x） */
    private int resolvePricePerInstrumentCount(JsonNode rule, String packName, String combined, int effectiveCount) {
        if (packName == null || !rule.path("keywords").isArray()) {
            return effectiveCount;
        }
        for (JsonNode kwNode : rule.path("keywords")) {
            if (!"刮勺探针".equals(kwNode.asText())) {
                continue;
            }
            if (!combined.contains("刮勺探针")) {
                return effectiveCount;
            }
            int suffix = extractLastNumber(packName.split("/")[0]);
            return suffix > 0 ? suffix : effectiveCount;
        }
        return effectiveCount;
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
        return result;
    }

    private SpecialFeeResult computeSpecialExtraFee(Map<String, Object> row, int bagSize, int effectiveCount) {
        String combined = combinedText(row);
        String hospitalName = str(row, "hospitalName");
        JsonNode extraFees = rules.path("specialRules").path("extraFees");
        if (extraFees.isArray()) {
            for (JsonNode rule : extraFees) {
                SpecialFeeResult matched = matchExtraFeeRule(rule, combined, hospitalName, bagSize, effectiveCount);
                if (matched != null) return matched;
            }
        }
        return null;
    }

    private SpecialFeeResult matchExtraFeeRule(JsonNode rule, String combined, String hospitalName, int bagSize, int effectiveCount) {
        if (!hospitalMatches(rule, hospitalName)) return null;
        if (!matchesRuleKeywords(combined, rule.path("keywords"))) return null;
        if (!bagSizeMatches(rule, bagSize)) return null;
        int minCount = rule.path("minInstrumentCount").asInt(Integer.MIN_VALUE);
        int maxCount = rule.path("maxInstrumentCount").asInt(Integer.MAX_VALUE);
        if (effectiveCount < minCount || effectiveCount > maxCount) return null;

        SpecialFeeResult result = new SpecialFeeResult();
        result.fee = rule.path("fee").asDouble(Double.NaN);
        if (Double.isNaN(result.fee)) return null;
        result.ruleName = rule.path("name").asText("特殊加收");
        result.note = result.ruleName + "，加收 " + fmt(result.fee) + " 元。";
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
        if (threshold > 0 && count <= threshold) return 1;
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

    /** 棉球缸等：优先 dressingPack.cottonPaperPlastic，否则回落高温纸塑袋价表（含附一 fuyi 25cm=12.79）。 */
    private Double resolveCottonPaperPlasticUnitPrice(int bagSizeCm) {
        if (bagSizeCm <= 0) {
            return null;
        }
        JsonNode cottonPricing = rules.path("dressingPack").path("cottonPaperPlastic");
        String sizeKey = String.valueOf(bagSizeCm);
        if (cottonPricing.has(sizeKey)) {
            return cottonPricing.path(sizeKey).asDouble();
        }
        JsonNode bagConfig = findBagConfig(
                bagSizeCm, rules.path("highTemperature").path("paperPlastic").path("bagSizes"));
        if (bagConfig != null && bagConfig.has("price")) {
            return bagConfig.path("price").asDouble();
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

    /** 从"双"之后提取前两位数字作为纸塑袋尺寸（如 双15 → 15，双75 → 10）；"双"后紧跟汉字则不追加袋费 */
    private int extractSizeAfterDouble(String input) {
        if (input == null || input.isEmpty()) return 0;
        int idx = input.indexOf("双");
        if (idx < 0 || idx + 1 >= input.length()) return 0;

        char next = input.charAt(idx + 1);
        // "双"后紧跟汉字 → 不需要额外纸塑袋
        if (Character.UnicodeBlock.of(next) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) return 0;

        String after = input.substring(idx + 1);
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

        if (instrumentCount >= 3 && packageFee > capPrice) {
            boolean chargeDoubleBagWhenCapped = config.path("chargeDoubleBagWhenCapped").asBoolean(false);
            if (chargeDoubleBagWhenCapped && isDouble && bagFee2 > 0) {
                double total = round(packageFee + bagFee2);
                notes.add("大于等于 3 件且件数总价 " + fmt(packageFee) + " > " + fmt(capPrice)
                        + " 元，基础袋费免收，但双层纸塑袋追加第二层袋费 " + fmt(bagFee2)
                        + " 元，合计 " + fmt(total) + " 元。");
                return total;
            }
            String doubleNote = isDouble && bagFee2 > 0
                ? "（包名含\"双\"，但 ≥3 件且件数总价超封顶，袋费免收）"
                : "";
            notes.add("大于等于 3 件且件数总价 " + fmt(packageFee) + " > " + fmt(capPrice) +
                    " 元，按 " + fmt(perPackagePrice) + " 元/件计费，不再计袋费。" + doubleNote);
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

    private Double computeLowTempPaperPlastic(int instrumentCount, int bagSize, int zBagSize, List<String> notes, boolean isDouble) {
        JsonNode config = rules.path("lowTemperature").path("paperPlastic");
        double remainderPrice = config.path("remainderPerPiecePrice").asDouble(22.0);

        if (instrumentCount == 1) {
            JsonNode bagConfig = findBagConfig(bagSize, config.path("bagSizes"));
            if (bagConfig == null) {
                notes.add("未识别低温纸塑袋尺寸，按最低 22 元计费。");
                return 22.0;
            }
            double price = bagConfig.path("price").asDouble();
            if (isDouble && zBagSize > 0) {
                JsonNode zBagConfig = findBagConfig(zBagSize > 25 ? 25 : zBagSize, config.path("bagSizes"));
                double zFee = zBagConfig != null ? zBagConfig.path("price").asDouble() : 0;
                double doublePrice = round(price + zFee);
                notes.add("低温单件纸塑袋 " + bagConfig.path("size").asInt() + "cm，包名含\"双\"追加 " + zBagSize + "cm袋费，单价 " +
                        fmt(doublePrice) + " 元（袋费 " + fmt(price) + " + " + fmt(zFee) + "）。");
                return doublePrice;
            }
            notes.add("低温单件纸塑袋 " + bagConfig.path("size").asInt() + "cm，单价 " +
                    fmt(price) + " 元。");
            return price;
        }

        JsonNode tiers = config.path("tierPrices");
        if (instrumentCount <= 5) {
            double total = round(instrumentCount * remainderPrice);
            double cap = findTierCap(tiers, 5, 88);
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
        double remainderPrice = config.path("remainderPerPiecePrice").asDouble(22.0);

        if (instrumentCount == 1) {
            notes.add("低温单件无纺布，单价 " + fmt(config.path("minSingleCharge").asDouble()) + " 元。");
            return config.path("minSingleCharge").asDouble();
        }

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
            notes.add("命中包装收费项目\"" + matchedItem.path("name").asText() + "\"，但该项目未配置具体选项价格，请先在计费规则中配置价格，或手动核定包装费。");
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

    private Double extractDressingPackMeasure(String material) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[×x*]\\s*\\d+")
                .matcher(material);
        if (m.find()) {
            double measure = Double.parseDouble(m.group(1));
            // 150×150 等填写为 cm，定价表 1.2~1.5 为米
            if (measure >= 100) {
                measure = measure / 100.0;
            }
            return measure;
        }
        return null;
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

    private boolean matchesKeywordsBoundary(String text, JsonNode keywords) {
        if (!keywords.isArray()) return false;
        String normalized = text.replaceAll("\\s+", "").toLowerCase();
        for (JsonNode kw : keywords) {
            String raw = kw.asText().replaceAll("\\s+", "").toLowerCase();
            if (raw.isEmpty()) continue;
            for (String k : raw.split("[，,]")) {
                if (k.isEmpty()) continue;
                int idx = 0;
                while ((idx = normalized.indexOf(k, idx)) != -1) {
                    char prevChar = idx > 0 ? normalized.charAt(idx - 1) : 0;
                    char nextChar = idx + k.length() < normalized.length() ? normalized.charAt(idx + k.length()) : 0;
                    boolean leftBoundary = prevChar == 0 || !isCJK(prevChar);
                    boolean rightBoundary = nextChar == 0 || !isCJK(nextChar);
                    if (rightBoundary) return true;
                    idx += k.length();
                }
            }
        }
        return false;
    }

    /** 查找包名中匹配的小件关键词及其位置，用于区分大件/小件混合包 */
    private SmallItemSplit findSmallItemSplit(String text, JsonNode keywords) {
        if (!keywords.isArray()) return null;
        String compact = text.replaceAll("\\s+", "");
        String compactLower = compact.toLowerCase();
        for (JsonNode kw : keywords) {
            String raw = kw.asText().replaceAll("\\s+", "").toLowerCase();
            if (raw.isEmpty()) continue;
            for (String k : raw.split("[，,]")) {
                if (k.isEmpty()) continue;
                int idx = 0;
                while ((idx = compactLower.indexOf(k, idx)) != -1) {
                    char nextChar = idx + k.length() < compact.length() ? compact.charAt(idx + k.length()) : 0;
                    boolean rightBoundary = nextChar == 0 || !isCJK(nextChar);
                    if (rightBoundary) {
                        SmallItemSplit split = new SmallItemSplit();
                        split.keyword = compact.substring(idx, idx + k.length());
                        split.position = idx;
                        split.compactText = compact;
                        return split;
                    }
                    idx += k.length();
                }
            }
        }
        return null;
    }

    private boolean isCJK(char ch) {
        return (ch >= 0x4e00 && ch <= 0x9fff) || (ch >= 0x3400 && ch <= 0x4dbf);
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

    /** 从字符串中提取第一个数字，未找到时返回 0 */
    private int extractFirstNumber(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
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
    }

    private static class MultiplierResult {
        double multiplier;
        String ruleName;
        String note;
        boolean skipHospitalDiscount;
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
        String normalized = type.replaceAll("\\s+", "");
        if (isZsdInstrumentPackType(type)) {
            notes.add("器械包(ZSD)未填写包装材料，按高温无纺布标准阶梯计费。");
            return "无纺布";
        }
        return packageMaterial == null ? "" : packageMaterial;
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
