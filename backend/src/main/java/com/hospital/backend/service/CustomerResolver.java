package com.hospital.backend.service;

import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerAlias;
import com.hospital.backend.mapper.CustomerAliasMapper;
import com.hospital.backend.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerResolver {

    private final CustomerMapper customerMapper;
    private final CustomerAliasMapper customerAliasMapper;

    /**
     * 按规范名精确匹配，或按别名 contains/exact 匹配（优先级升序）。
     */
    public Optional<Customer> resolveByName(String hospitalName) {
        if (hospitalName == null || hospitalName.isBlank()) {
            return Optional.empty();
        }
        String trimmed = hospitalName.trim();

        List<Customer> exactMatches = new ArrayList<>();
        for (Customer customer : customerMapper.selectAll()) {
            if (trimmed.equals(customer.getCanonicalName())) {
                exactMatches.add(customer);
            }
        }
        if (!exactMatches.isEmpty()) {
            return Optional.of(selectPreferredCustomer(exactMatches));
        }

        List<CustomerAlias> aliases = customerAliasMapper.selectAllActive();
        return aliases.stream()
                .filter(alias -> matchesAlias(trimmed, alias))
                .min(Comparator.comparingInt(CustomerAlias::getPriority))
                .flatMap(alias -> Optional.ofNullable(customerMapper.selectById(alias.getCustomerId())));
    }

    public List<String> hospitalNamesForCustomer(Customer customer) {
        List<String> names = new java.util.ArrayList<>();
        names.add(customer.getCanonicalName());
        for (CustomerAlias alias : customerAliasMapper.selectByCustomerId(customer.getId())) {
            if (Boolean.TRUE.equals(alias.getIsActive())) {
                names.add(alias.getAlias());
            }
        }
        return names;
    }

    private boolean matchesAlias(String hospitalName, CustomerAlias alias) {
        if (!Boolean.TRUE.equals(alias.getIsActive())) {
            return false;
        }
        String matchType = alias.getMatchType() != null ? alias.getMatchType() : "contains";
        return switch (matchType) {
            case "exact" -> hospitalName.equals(alias.getAlias());
            default -> hospitalName.contains(alias.getAlias()) || alias.getAlias().contains(hospitalName);
        };
    }

    /**
     * 规范名重复时（如 CHANGJIAN 与 HRB-CJ 并存），优先启用特色账单的非 legacy 客户。
     */
    private Customer selectPreferredCustomer(List<Customer> matches) {
        if (matches.size() == 1) {
            return matches.get(0);
        }
        return matches.stream()
                .min(Comparator
                        .comparing((Customer c) -> isInactive(c) ? 1 : 0)
                        .thenComparing(c -> "CHANGJIAN".equals(c.getCode()) ? 1 : 0)
                        .thenComparing(c -> Boolean.TRUE.equals(c.getBillingEnabled()) ? 0 : 1)
                        .thenComparing(Customer::getId))
                .orElse(matches.get(0));
    }

    private static boolean isInactive(Customer customer) {
        return customer.getStatus() != null && "inactive".equalsIgnoreCase(customer.getStatus().trim());
    }
}
