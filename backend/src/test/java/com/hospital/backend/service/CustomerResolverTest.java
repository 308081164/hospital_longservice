package com.hospital.backend.service;

import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerAlias;
import com.hospital.backend.mapper.CustomerAliasMapper;
import com.hospital.backend.mapper.CustomerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerResolverTest {

    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private CustomerAliasMapper customerAliasMapper;

    @InjectMocks
    private CustomerResolver customerResolver;

    @Test
    void prefersHrbCjOverLegacyChangjianWhenCanonicalNamesDuplicate() {
        Customer legacy = new Customer();
        legacy.setId(65L);
        legacy.setCode("CHANGJIAN");
        legacy.setCanonicalName("哈尔滨长健医院");
        legacy.setBillingEnabled(true);
        legacy.setStatus("active");

        Customer current = new Customer();
        current.setId(11L);
        current.setCode("HRB-CJ");
        current.setCanonicalName("哈尔滨长健医院");
        current.setBillingEnabled(true);
        current.setStatus("active");

        when(customerMapper.selectAll()).thenReturn(List.of(legacy, current));

        Optional<Customer> resolved = customerResolver.resolveByName("哈尔滨长健医院");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getCode()).isEqualTo("HRB-CJ");
    }

    @Test
    void skipsInactiveDuplicateBeforeLegacyCode() {
        Customer inactive = new Customer();
        inactive.setId(65L);
        inactive.setCode("CHANGJIAN");
        inactive.setCanonicalName("哈尔滨长健医院");
        inactive.setStatus("inactive");

        Customer current = new Customer();
        current.setId(11L);
        current.setCode("HRB-CJ");
        current.setCanonicalName("哈尔滨长健医院");
        current.setBillingEnabled(true);
        current.setStatus("active");

        when(customerMapper.selectAll()).thenReturn(List.of(inactive, current));

        Optional<Customer> resolved = customerResolver.resolveByName("哈尔滨长健医院");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo(11L);
    }

    @Test
    void resolvesByAliasWhenCanonicalNameMisses() {
        when(customerMapper.selectAll()).thenReturn(List.of());

        CustomerAlias alias = new CustomerAlias();
        alias.setCustomerId(11L);
        alias.setAlias("哈尔滨长健医院");
        alias.setMatchType("exact");
        alias.setPriority(100);
        alias.setIsActive(true);

        Customer customer = new Customer();
        customer.setId(11L);
        customer.setCode("HRB-CJ");
        customer.setCanonicalName("哈尔滨长健医院");

        when(customerAliasMapper.selectAllActive()).thenReturn(List.of(alias));
        when(customerMapper.selectById(11L)).thenReturn(customer);

        Optional<Customer> resolved = customerResolver.resolveByName("哈尔滨长健医院");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getCode()).isEqualTo("HRB-CJ");
    }
}
