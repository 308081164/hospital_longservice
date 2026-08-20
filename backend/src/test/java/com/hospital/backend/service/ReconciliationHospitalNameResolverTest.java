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
class ReconciliationHospitalNameResolverTest {

    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private CustomerAliasMapper customerAliasMapper;

    @InjectMocks
    private CustomerResolver customerResolver;

    private ReconciliationHospitalNameResolver resolver;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        resolver = new ReconciliationHospitalNameResolver(customerResolver);
    }

    @Test
    void infersHospitalNameFromDongdaFileName() {
        Customer customer = new Customer();
        customer.setId(99L);
        customer.setCanonicalName("黑龙江东大肛肠");

        when(customerMapper.selectAll()).thenReturn(List.of());
        when(customerAliasMapper.selectAllActive()).thenReturn(List.of());

        CustomerAlias alias = new CustomerAlias();
        alias.setCustomerId(99L);
        alias.setAlias("东大肛肠");
        alias.setMatchType("contains");
        alias.setPriority(100);
        alias.setIsActive(true);
        when(customerAliasMapper.selectAllActive()).thenReturn(List.of(alias));
        when(customerMapper.selectById(99L)).thenReturn(customer);

        String resolved = resolver.resolve("门诊部", "东大肛肠3月账单.xlsx");

        assertThat(resolved).isEqualTo("黑龙江东大肛肠");
    }

    @Test
    void rejectsDepartmentNameAndUsesFileName() {
        when(customerMapper.selectAll()).thenReturn(List.of());
        when(customerAliasMapper.selectAllActive()).thenReturn(List.of());

        String resolved = resolver.resolve("手术室", "东大肛肠2月账单.xlsx");

        assertThat(resolved).isEqualTo("东大肛肠");
    }

    @Test
    void inferFromFileNameStripsMonthAndBillSuffix() {
        assertThat(resolver.inferFromFileName("东大肛肠3月账单.xlsx")).isEqualTo("东大肛肠");
        assertThat(resolver.inferFromFileName("2026_东大肛肠4月结款函.xlsx")).isEqualTo("东大肛肠");
    }

    @Test
    void detectsCommonDepartmentNames() {
        assertThat(resolver.isLikelyDepartmentName("门诊部")).isTrue();
        assertThat(resolver.isLikelyDepartmentName("手术室")).isTrue();
        assertThat(resolver.isLikelyDepartmentName("黑龙江东大肛肠")).isFalse();
    }
}
