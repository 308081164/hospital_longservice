package com.hospital.backend.service;

import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerAlias;
import com.hospital.backend.mapper.CustomerAliasMapper;
import com.hospital.backend.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

        for (Customer customer : customerMapper.selectAll()) {
            if (trimmed.equals(customer.getCanonicalName())) {
                return Optional.of(customer);
            }
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
}
