package com.hospital.backend.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则变更后失效客户级编译缓存（预留扩展点；当前无持久缓存时调用无害）。
 */
@Component
public class PricingRuleCompileCache {

    private final Set<Long> invalidatedCustomers = ConcurrentHashMap.newKeySet();

    public void invalidateCustomer(Long customerId) {
        if (customerId != null) {
            invalidatedCustomers.add(customerId);
        }
    }

    public boolean consumeInvalidation(Long customerId) {
        if (customerId == null) {
            return false;
        }
        return invalidatedCustomers.remove(customerId);
    }
}
