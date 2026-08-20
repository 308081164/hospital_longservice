package com.hospital.backend.service;

import com.hospital.backend.imports.bokang.PackNameSpecParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 从未命中对账行推断建议产品族（pack_name + needle 关键词）。
 */
@Service
@RequiredArgsConstructor
public class UnmatchedProductAnalyzer {

    public Suggestion analyze(String packName, String type, String packageMaterial, List<String> needleKeywords) {
        PackNameSpecParser.ParsedPack parsed = PackNameSpecParser.parse(packName, type, packageMaterial);
        String suggestedFamily = parsed.familyName;
        if (suggestedFamily == null || suggestedFamily.isBlank()) {
            suggestedFamily = packName != null ? packName.trim() : "";
        }

        List<String> matchedNeedleKeywords = new ArrayList<>();
        String combined = (packName == null ? "" : packName) + " " + (type == null ? "" : type);
        for (String kw : needleKeywords) {
            if (kw != null && !kw.isBlank()
                    && BillingConditionEvaluator.matchesKeywordExactToken(combined, kw)) {
                matchedNeedleKeywords.add(kw);
            }
        }

        boolean likelySmallItem = !matchedNeedleKeywords.isEmpty();
        String suggestedCategory = likelySmallItem ? "SMALL_ITEM"
                : (type != null && type.contains("敷料") ? "DRESSING_NONWOVEN" : "HT_PAPER_PLASTIC");

        return new Suggestion(
                suggestedFamily,
                parsed.specFingerprint,
                parsed.displayName,
                suggestedCategory,
                likelySmallItem,
                matchedNeedleKeywords,
                parsed.orderNoPattern,
                parsed.instrumentCountHint
        );
    }

    public List<String> defaultNeedleKeywords() {
        return List.of("针", "小件", "探针", "穿刺针", "缝合针", "车针", "拔髓针",
                "成型片", "根管针", "根管锉", "支抗钉", "洁牙机尖", "球钻", "挖勺", "手术针", "机扩针", "镍钛锉");
    }

    public record Suggestion(
            String suggestedFamily,
            String specFingerprint,
            String displayName,
            String suggestedCategoryCode,
            boolean likelySmallItem,
            List<String> matchedNeedleKeywords,
            String orderNoPattern,
            Integer instrumentCountHint
    ) {}
}
