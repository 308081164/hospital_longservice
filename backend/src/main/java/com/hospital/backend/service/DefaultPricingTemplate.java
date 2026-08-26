package com.hospital.backend.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标准灭菌计费规则 v2.0 模板 —— 原 PricingEngine / 铂康 id=8 默认值的数据库单一来源。
 */
public final class DefaultPricingTemplate {

    private DefaultPricingTemplate() {}

    public static Map<String, Object> buildRulesMap() {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("version", "v2.0");
        rules.put("highTemperature", Map.of(
                "paperPlastic", new LinkedHashMap<>(Map.of(
                        "bagSizes", List.of(
                                bag(25, 10.5, "25cm", "25", "特大"),
                                bag(20, 7.5, "20cm", "20", "大"),
                                bag(15, 5.5, "15cm", "15", "中"),
                                bag(10, 2.5, "10cm", "10", "小")
                        ),
                        "perPackagePrice", 5.5,
                        "minCharge", 16.5,
                        "freeBagFeeThreshold", 16.5,
                        "capMode", "standard",
                        "chargeDoubleBagWhenCapped", false
                )),
                "nonWoven", Map.of(
                        "minCharge", 16.5,
                        "flatPerPackagePrice", 5.5,
                        "flatRateThreshold", 3
                )
        ));
        rules.put("lowTemperature", Map.of(
                "paperPlastic", Map.of(
                        "bagSizes", List.of(
                                bag(30, 35, "30cm", "30"),
                                bag(25, 30, "25cm", "25"),
                                bag(20, 28, "20cm", "20"),
                                bag(15, 25, "15cm", "15"),
                                bag(10, 22, "10cm", "10")
                        ),
                        "tierPrices", List.of(
                                Map.of("count", 20, "price", 300),
                                Map.of("count", 10, "price", 165),
                                Map.of("count", 5, "price", 88)
                        ),
                        "remainderPerPiecePrice", 22
                ),
                "nonWoven", Map.of(
                        "tierPrices", List.of(
                                Map.of("count", 20, "price", 300),
                                Map.of("count", 10, "price", 165),
                                Map.of("count", 5, "price", 88)
                        ),
                        "remainderPerPiecePrice", 22,
                        "minSingleCharge", 35
                )
        ));
        rules.put("packaging", Map.of(
                "enabled", false,
                "items", List.of(),
                "selfPackedKeywords", List.of()
        ));
        rules.put("needle", Map.of(
                "threshold", 5,
                "foldRatio", 5,
                "keywords", List.of("克氏针", "银质针", "内热针", "车针", "拔髓针", "扩大针", "根扩针", "卷棉子")
        ));
        rules.put("cleaning", Map.of(
                "removeFirstRow", false,
                "dropSummaryRows", true,
                "summaryKeywords", List.of("合计", "小计", "总计"),
                "trimPackagingMaterial", true,
                "clearInstrumentColumnFormatting", false,
                "recomputeTotalsWhenPriceChanges", true
        ));
        rules.put("logistics", Map.of(
                "enabled", true,
                "feePerTrip", 50,
                "dayBoundaryHour", 20,
                "mergeAdjacentDays", false,
                "mergeWindowDays", 1
        ));
        rules.put("dressingPack", Map.of(
                "cottonPaperPlastic", Map.of(
                        "20", 4.0,
                        "15", 2.5
                ),
                "nonWoven", Map.of(
                        "below90", 25,
                        "equals90", 30,
                        "range12to15", 35
                )
        ));
        rules.put("settlementLetter", Map.of(
                "companyName", "黑龙江省铂康医疗灭菌有限公司",
                "rowHeight", 20,
                "dateRangeTextTemplate", "{start} 至 {end}",
                "uppercaseTotalLabel", "大写金额",
                "templates", List.of(Map.of(
                        "id", "default_template",
                        "name", "默认结款函模板",
                        "hospitalName", "",
                        "templateSheetName", "结款函",
                        "titleText", "货款结算单",
                        "matchKeywords", List.of(),
                        "templateRef", "default"
                )),
                "defaultTemplateId", "default_template",
                "feeItems", List.of(
                        Map.of("key", "sterilize", "label", "灭菌费", "remark", "", "enabled", true, "sortOrder", 1),
                        Map.of("key", "logistics", "label", "物流费", "remark", "", "enabled", true, "sortOrder", 2)
                )
        ));
        rules.put("exportOptions", Map.of(
                "billFilePrefix", "账单_",
                "warningFilePrefix", "异常_",
                "settlementFilePrefix", "结款函_",
                "includeWarningSheet", true,
                "defaultPageMargin", "1cm"
        ));
        rules.put("specialRules", Map.of(
                "fixedPrices", List.of(
                        Map.of(
                                "name", "软镜固定300元",
                                "keywords", List.of("软镜"),
                                "price", 300.0,
                                "skipPackaging", true,
                                "skipHospitalDiscount", true
                        )
                ),
                "foldRules", genericSmallItemFoldRules(),
                "extraFees", List.of()
        ));
        return rules;
    }

    /** 特殊收费 Excel「通用特殊收费」8 项 5合1 FOLD（SC11-T04/T05）。 */
    private static List<String> genericSmallItemFoldKeywords() {
        return List.of("克氏针", "银质针", "内热针", "车针", "拔髓针", "扩大针", "根扩针", "卷棉子");
    }

    private static List<Map<String, Object>> genericSmallItemFoldRules() {
        List<String> keywords = genericSmallItemFoldKeywords();
        Map<String, Object> withBag = new LinkedHashMap<>();
        withBag.put("name", "通用小件5合1含包材");
        withBag.put("priority", 50);
        withBag.put("keywords", keywords);
        withBag.put("threshold", 5);
        withBag.put("foldRatio", 5);
        withBag.put("maxInstrumentCount", 10);
        withBag.put("skipPackaging", false);
        withBag.put("unitPrice", 5.5);

        Map<String, Object> noBag = new LinkedHashMap<>();
        noBag.put("name", "通用小件5合1免包材");
        noBag.put("priority", 51);
        noBag.put("keywords", keywords);
        noBag.put("threshold", 5);
        noBag.put("foldRatio", 5);
        noBag.put("minInstrumentCount", 11);
        noBag.put("skipPackaging", true);
        noBag.put("unitPrice", 5.5);

        return List.of(withBag, noBag);
    }

    private static Map<String, Object> bag(int size, double price, String... keywords) {
        return Map.of("size", size, "price", price, "keywords", List.of(keywords));
    }
}
