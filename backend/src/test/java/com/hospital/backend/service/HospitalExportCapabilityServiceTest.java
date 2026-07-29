package com.hospital.backend.service;

import com.hospital.backend.dto.response.hospital.ReconciliationJobResponse;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.export.BillExportLayoutResolver;
import com.hospital.backend.export.ExportTemplateResolver;
import com.hospital.backend.export.ExportType;
import com.hospital.backend.export.model.ColumnMappingConfig;
import com.hospital.backend.export.model.ResolvedExportTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HospitalExportCapabilityServiceTest {

    @Mock
    private CustomerResolver customerResolver;

    @Mock
    private ExportTemplateResolver exportTemplateResolver;

    @Mock
    private BillExportLayoutResolver billExportLayoutResolver;

    @InjectMocks
    private HospitalExportCapabilityService service;

    @BeforeEach
    void setUp() {
        service.loadCapabilities();
        when(billExportLayoutResolver.resolveBillLayout(any())).thenReturn(BillExportLayoutResolver.LAYOUT_AUTO);
        when(billExportLayoutResolver.buildExportProfileLabel(true, BillExportLayoutResolver.LAYOUT_AUTO))
                .thenReturn("特色导出·自动布局");
        when(billExportLayoutResolver.buildExportProfileLabel(false, BillExportLayoutResolver.LAYOUT_AUTO))
                .thenReturn("常规导出");
    }

    @Test
    void fuyiHasDeptSummaryAndLogisticsAllocation() {
        List<String> types = service.getExportTypes("黑龙江中医药大学附属第一医院");
        assertTrue(types.contains("dept_summary"));
        assertTrue(types.contains("logistics_allocation"));
        assertEquals(4, types.size());
    }

    @Test
    void unknownHospitalDefaultsToBillAndSettlement() {
        assertEquals(List.of("bill", "settlement"), service.getExportTypes("未知测试医院"));
    }

    @Test
    void enrichJobResponseMarksSpecialExportWhenBillingEnabled() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setBillingEnabled(true);
        when(customerResolver.resolveByName("测试特色医院")).thenReturn(Optional.of(customer));
        when(exportTemplateResolver.resolve(eq(1L), eq(ExportType.BILL)))
                .thenReturn(ResolvedExportTemplate.builder()
                        .exportType(ExportType.BILL)
                        .name("default")
                        .strategyKey("standard_bill")
                        .columnMapping(new ColumnMappingConfig())
                        .build());

        ReconciliationJobResponse response = new ReconciliationJobResponse(
                1L, "测试特色医院", "a.xlsx", null, null,
                null, null, null, 1, 0, 0, 0, 0, 0, 0.0,
                "approved", null, "op", null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        service.enrichJobResponse(response, "测试特色医院");

        assertTrue(response.getHasSpecialExport());
        assertTrue(response.getBillingEnabled());
        assertEquals(List.of("bill", "settlement"), response.getExportTypes());
        assertEquals("特色导出·自动布局", response.getExportProfileLabel());
    }

    @Test
    void enrichJobResponseMarksSpecialExportForExtraTypesWithoutBilling() {
        ReconciliationJobResponse response = new ReconciliationJobResponse(
                2L, "哈尔滨市第五医院", "a.xlsx", null, null,
                null, null, null, 1, 0, 0, 0, 0, 0, 0.0,
                "approved", null, "op", null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        service.enrichJobResponse(response, "哈尔滨市第五医院");

        assertTrue(response.getHasSpecialExport());
        assertFalse(response.getBillingEnabled());
        assertEquals(6, response.getExportTypes().size());
    }
}
