package com.hospital.backend.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 启动时 baseline sync/verify 健康态（供 /health 与运维巡检）。
 */
@Component
public class BillingRulesBaselineSyncHealth {

    private final AtomicReference<Map<String, Object>> lastFailure = new AtomicReference<>();

    public boolean isHealthy() {
        return lastFailure.get() == null;
    }

    public Map<String, Object> lastFailureDetail() {
        return lastFailure.get();
    }

    public void markHealthy() {
        lastFailure.set(null);
    }

    public void markUnhealthy(Map<String, Object> verifySummary) {
        lastFailure.set(verifySummary);
    }
}
