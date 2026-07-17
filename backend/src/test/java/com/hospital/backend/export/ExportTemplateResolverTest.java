package com.hospital.backend.export;

import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.ExportTemplate;
import com.hospital.backend.export.model.ResolvedExportTemplate;
import com.hospital.backend.export.strategy.ExportTemplateResolverKeys;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.ExportTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportTemplateResolverTest {

    @Mock
    private ExportTemplateMapper exportTemplateMapper;

    @Mock
    private CustomerMapper customerMapper;

    private ExportTemplateResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ExportTemplateResolver(exportTemplateMapper, customerMapper);
    }

    @Test
    void prefersCustomerBoundTemplateOverGlobal() {
        ExportTemplate customerTemplate = template(10L, 5L, "bill", "客户账单",
                "{}", "{\"strategyKey\":\"standard_bill\"}");
        when(exportTemplateMapper.selectByCustomerAndType(5L, "bill"))
                .thenReturn(List.of(customerTemplate));

        ResolvedExportTemplate resolved = resolver.resolve(5L, ExportType.BILL, null);

        assertThat(resolved.getTemplateId()).isEqualTo(10L);
        assertThat(resolved.isCustomerOverride()).isTrue();
    }

    @Test
    void matchesGlobalSkeletonByCustomerCode() {
        when(exportTemplateMapper.selectByCustomerAndType(7L, "bill")).thenReturn(List.of());

        ExportTemplate daowai = template(2L, null, "bill", "道外骨架",
                "{\"removeColumns\":[\"器械数\"]}",
                "{\"strategyKey\":\"daowai_bill\",\"customerCode\":\"DAOWAI\"}");
        ExportTemplate standard = template(1L, null, "bill", "默认",
                "{}", "{\"strategyKey\":\"standard_bill\"}");
        when(exportTemplateMapper.selectGlobalByType("bill")).thenReturn(List.of(standard, daowai));

        Customer customer = new Customer();
        customer.setId(7L);
        customer.setCode("DAOWAI");
        when(customerMapper.selectById(7L)).thenReturn(customer);

        ResolvedExportTemplate resolved = resolver.resolve(7L, ExportType.BILL, null);

        assertThat(resolved.getStrategyKey()).isEqualTo(ExportTemplateResolverKeys.DAOWAI_BILL);
        assertThat(resolved.getColumnMapping().getRemoveColumns()).contains("器械数");
    }

    @Test
    void fallsBackToSyntheticDefaultWhenNoTemplates() {
        when(exportTemplateMapper.selectGlobalByType("settlement")).thenReturn(List.of());

        ResolvedExportTemplate resolved = resolver.resolve(null, ExportType.SETTLEMENT, null);

        assertThat(resolved.getStrategyKey()).isEqualTo(ExportTemplateResolver.DEFAULT_SETTLEMENT_STRATEGY);
        assertThat(resolved.getName()).contains("系统默认");
    }

    private static ExportTemplate template(
            Long id, Long customerId, String type, String name, String columnMapping, String sheetConfig) {
        ExportTemplate template = new ExportTemplate();
        template.setId(id);
        template.setCustomerId(customerId);
        template.setTemplateType(type);
        template.setName(name);
        template.setColumnMapping(columnMapping);
        template.setSheetConfig(sheetConfig);
        template.setIsActive(true);
        return template;
    }
}
