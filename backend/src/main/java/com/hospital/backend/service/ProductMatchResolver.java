package com.hospital.backend.service;

import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;

/**
 * Hook for PricingEngine to resolve products via structured match rules.
 * Implemented by {@link ProductMatchService}.
 */
@FunctionalInterface
public interface ProductMatchResolver {

    record StructuredProductMatch(
            Long productId,
            String productName,
            String categoryCode,
            String categoryName,
            String pricingPath,
            BigDecimal publicPrice
    ) {}

    Optional<StructuredProductMatch> resolve(Map<String, Object> row);
}
