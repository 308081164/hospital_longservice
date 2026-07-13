package com.hospital.backend.service;

import com.hospital.backend.dto.request.product.MatchPreviewRequest;
import com.hospital.backend.dto.response.product.MatchPreviewResponse;

import java.util.Map;
import java.util.Optional;

public interface ProductMatchService extends ProductMatchResolver {

    Optional<MatchPreviewResponse> matchRow(MatchPreviewRequest request);

    Optional<MatchPreviewResponse> matchRow(Map<String, Object> row);

    @Override
    default Optional<StructuredProductMatch> resolve(Map<String, Object> row) {
        return matchRow(row).map(r -> new StructuredProductMatch(
                r.getProductId(),
                r.getProductName(),
                r.getCategoryCode(),
                r.getCategoryName(),
                r.getPricingPath(),
                r.getPublicPrice()
        ));
    }

    void refreshCache();
}
