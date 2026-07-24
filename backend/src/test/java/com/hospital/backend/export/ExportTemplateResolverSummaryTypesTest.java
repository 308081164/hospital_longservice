package com.hospital.backend.export;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportTemplateResolverSummaryTypesTest {

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
    void syntheticDefaultForInstrumentAudit() {
        when(exportTemplateMapper.selectGlobalByType("instrument_audit")).thenReturn(List.of());

        ResolvedExportTemplate resolved = resolver.resolve(null, ExportType.INSTRUMENT_AUDIT, null);

        assertThat(resolved.getStrategyKey()).isEqualTo(ExportTemplateResolverKeys.INSTRUMENT_AUDIT);
    }

    @Test
    void syntheticDefaultForPriceSummary() {
        when(exportTemplateMapper.selectGlobalByType("price_summary")).thenReturn(List.of());

        ResolvedExportTemplate resolved = resolver.resolve(null, ExportType.PRICE_SUMMARY, null);

        assertThat(resolved.getStrategyKey()).isEqualTo(ExportTemplateResolverKeys.STANDARD_PRICE_SUMMARY);
    }

    @Test
    void syntheticDefaultForLogisticsAllocation() {
        when(exportTemplateMapper.selectGlobalByType("logistics_allocation")).thenReturn(List.of());

        ResolvedExportTemplate resolved = resolver.resolve(null, ExportType.LOGISTICS_ALLOCATION, null);

        assertThat(resolved.getStrategyKey()).isEqualTo(ExportTemplateResolverKeys.LOGISTICS_ALLOCATION);
    }

    @Test
    void syntheticDefaultForGrandSummary() {
        when(exportTemplateMapper.selectGlobalByType("grand_summary")).thenReturn(List.of());

        ResolvedExportTemplate resolved = resolver.resolve(null, ExportType.GRAND_SUMMARY, null);

        assertThat(resolved.getStrategyKey()).isEqualTo(ExportTemplateResolverKeys.GRAND_SUMMARY);
    }
}
