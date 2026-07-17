package com.hospital.backend.export.strategy;

import com.hospital.backend.export.ExportTemplateResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ExportStrategyRegistry {

    private final Map<String, ExportStrategy> strategiesByKey;

    public ExportStrategyRegistry(List<ExportStrategy> strategies) {
        this.strategiesByKey = strategies.stream()
                .collect(Collectors.toMap(ExportStrategy::strategyKey, Function.identity(), (a, b) -> a));
    }

    public ExportStrategy require(String strategyKey) {
        ExportStrategy strategy = strategiesByKey.get(strategyKey);
        if (strategy == null) {
            strategy = strategiesByKey.get(ExportTemplateResolver.DEFAULT_BILL_STRATEGY);
        }
        if (strategy == null) {
            throw new IllegalStateException("No export strategy registered for key: " + strategyKey);
        }
        return strategy;
    }
}
