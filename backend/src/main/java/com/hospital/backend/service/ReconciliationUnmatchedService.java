package com.hospital.backend.service;

import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.product.MatchPreviewRequest;
import com.hospital.backend.dto.response.product.MatchPreviewResponse;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.mapper.HospitalReconciliationJobMapper;
import com.hospital.backend.service.UnmatchedProductAnalyzer.Suggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ReconciliationUnmatchedService {

    private final HospitalReconciliationJobMapper jobMapper;
    private final ProductMatchService productMatchService;
    private final UnmatchedProductAnalyzer unmatchedProductAnalyzer;

    public Result<Map<String, Object>> listUnmatchedProducts(Long jobId) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail(404, "对账任务不存在");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = job.getRowsJson() == null
                ? new ArrayList<>()
                : (List<Map<String, Object>>) (List<?>) JsonUtils.parseToList(job.getRowsJson(), Map.class);
        if (rows == null) {
            rows = new ArrayList<>();
        }

        Map<String, AggregatedUnmatched> aggregated = new LinkedHashMap<>();
        List<String> needleKeywords = unmatchedProductAnalyzer.defaultNeedleKeywords();

        for (Map<String, Object> row : rows) {
            if (row.get("matchedProductId") != null || row.get("matched_product_id") != null) {
                continue;
            }

            MatchPreviewRequest previewReq = new MatchPreviewRequest();
            previewReq.setType(str(row, "type"));
            previewReq.setPackName(str(row, "packName"));
            previewReq.setPackageMaterial(str(row, "packageMaterial"));
            previewReq.setCategoryNo(str(row, "categoryNo"));
            previewReq.setInstrumentCount(intVal(row, "instrumentCount"));

            Optional<MatchPreviewResponse> match = productMatchService.matchRow(previewReq);
            if (match.isPresent() && match.get().isMatched()) {
                continue;
            }

            String key = previewReq.getPackName() + "|" + previewReq.getType() + "|" + previewReq.getPackageMaterial();
            AggregatedUnmatched item = aggregated.computeIfAbsent(key, k -> {
                AggregatedUnmatched agg = new AggregatedUnmatched();
                agg.packName = previewReq.getPackName();
                agg.type = previewReq.getType();
                agg.packageMaterial = previewReq.getPackageMaterial();
                Suggestion suggestion = unmatchedProductAnalyzer.analyze(
                        agg.packName, agg.type, agg.packageMaterial, needleKeywords);
                agg.suggestedFamily = suggestion.suggestedFamily();
                agg.specFingerprint = suggestion.specFingerprint();
                agg.suggestedCategoryCode = suggestion.suggestedCategoryCode();
                agg.likelySmallItem = suggestion.likelySmallItem();
                agg.matchedNeedleKeywords = suggestion.matchedNeedleKeywords();
                return agg;
            });
            item.rowCount++;
            Double diff = doubleVal(row, "difference");
            if (diff != null) {
                item.totalDifference += diff;
            }
        }

        List<Map<String, Object>> items = aggregated.values().stream()
                .sorted(Comparator.comparingInt((AggregatedUnmatched a) -> a.rowCount).reversed())
                .map(this::toMap)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("job_id", jobId);
        result.put("unmatched_count", items.size());
        result.put("items", items);
        return Result.success(result);
    }

    private Map<String, Object> toMap(AggregatedUnmatched agg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pack_name", agg.packName);
        m.put("type", agg.type);
        m.put("package_material", agg.packageMaterial);
        m.put("row_count", agg.rowCount);
        m.put("total_difference", agg.totalDifference);
        m.put("suggested_family", agg.suggestedFamily);
        m.put("spec_fingerprint", agg.specFingerprint);
        m.put("suggested_category_code", agg.suggestedCategoryCode);
        m.put("likely_small_item", agg.likelySmallItem);
        m.put("matched_needle_keywords", agg.matchedNeedleKeywords);
        return m;
    }

    private String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private Integer intVal(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private Double doubleVal(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    private static class AggregatedUnmatched {
        String packName;
        String type;
        String packageMaterial;
        int rowCount;
        double totalDifference;
        String suggestedFamily;
        String specFingerprint;
        String suggestedCategoryCode;
        boolean likelySmallItem;
        List<String> matchedNeedleKeywords = List.of();
    }
}
