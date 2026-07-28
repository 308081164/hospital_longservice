package com.hospital.backend.export.impl;

import com.hospital.backend.dto.request.export.ExportV2Request;
import com.hospital.backend.dto.request.hospital.HospitalBillTemplateExportRequest;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.BillExportLayoutResolver;
import com.hospital.backend.export.BillExportRequestMapper;
import com.hospital.backend.export.ColumnTransformPipeline;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportFixedPriceApplier;
import com.hospital.backend.export.ExportStageDiscountApplier;
import com.hospital.backend.export.ExportType;
import com.hospital.backend.export.ReconciliationExportDataLoader;
import com.hospital.backend.export.ReconciliationLegacyExportBridge;
import com.hospital.backend.export.SettlementJobEnricher;
import com.hospital.backend.export.SettlementTemplateFiller;
import com.hospital.backend.export.model.ResolvedExportTemplate;
import com.hospital.backend.export.strategy.ExportStrategyRegistry;
import com.hospital.backend.mapper.HospitalReconciliationExportLogMapper;
import com.hospital.backend.mapper.HospitalPricingRuleMapper;
import com.hospital.backend.service.CustomerResolver;
import com.hospital.backend.service.PricingRuleCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportEngineServiceImplLegacyRoutingTest {

    @Mock
    private ReconciliationExportDataLoader dataLoader;
    @Mock
    private ExportStrategyRegistry strategyRegistry;
    @Mock
    private ColumnTransformPipeline columnTransformPipeline;
    @Mock
    private CustomerResolver customerResolver;
    @Mock
    private ExportEngineServiceImpl.ExportTemplateResolverHelper templateResolverHelper;
    @Mock
    private HospitalReconciliationExportLogMapper exportLogMapper;
    @Mock
    private ExportFixedPriceApplier exportFixedPriceApplier;
    @Mock
    private ExportStageDiscountApplier exportStageDiscountApplier;
    @Mock
    private PricingRuleCompiler pricingRuleCompiler;
    @Mock
    private HospitalPricingRuleMapper pricingRuleMapper;
    @Mock
    private SettlementTemplateFiller settlementTemplateFiller;
    @Mock
    private BillExportRequestMapper billExportRequestMapper;
    @Mock
    private SettlementJobEnricher settlementJobEnricher;
    @Mock
    private BillExportLayoutResolver billExportLayoutResolver;
    @Mock
    private ReconciliationLegacyExportBridge legacyExportBridge;

    private ExportEngineServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ExportEngineServiceImpl(
                dataLoader,
                strategyRegistry,
                columnTransformPipeline,
                customerResolver,
                templateResolverHelper,
                exportLogMapper,
                exportFixedPriceApplier,
                exportStageDiscountApplier,
                pricingRuleCompiler,
                pricingRuleMapper,
                settlementTemplateFiller,
                billExportRequestMapper,
                settlementJobEnricher,
                billExportLayoutResolver);
        ReflectionTestUtils.setField(service, "legacyExportBridge", legacyExportBridge);
    }

    @Test
    void exportV2BillUsesLegacyTemplatePipeline() throws IOException {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("香坊中医院");
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setSheetName("灭菌");
        row.setPackName("测试包");
        ExportContext context = ExportContext.builder()
                .jobId(99L)
                .exportType(ExportType.BILL)
                .job(job)
                .rows(List.of(row))
                .hospitalName("香坊中医院")
                .template(ResolvedExportTemplate.builder()
                        .strategyKey("standard_bill")
                        .name("default")
                        .build())
                .build();

        HospitalBillTemplateExportRequest billRequest = new HospitalBillTemplateExportRequest();
        billRequest.setHospitalName("香坊中医院");
        billRequest.setTemplateId("99");

        when(dataLoader.loadContext(eq(99L), eq(ExportType.BILL), eq(null))).thenReturn(context);
        when(billExportRequestMapper.fromContext(context)).thenReturn(billRequest);
        when(legacyExportBridge.generateBillExportBytes(billRequest)).thenReturn(new byte[] {1, 2, 3});
        when(legacyExportBridge.postProcessBillExport(any(), anyString())).thenReturn(new byte[] {4, 5, 6});
        when(customerResolver.resolveByName("香坊中医院")).thenReturn(java.util.Optional.empty());
        when(templateResolverHelper.resolve(null, ExportType.BILL, null))
                .thenReturn(ResolvedExportTemplate.builder()
                        .strategyKey("standard_bill")
                        .name("default")
                        .build());
        when(columnTransformPipeline.apply(any(), any())).thenReturn(new byte[] {4, 5, 6});
        when(legacyExportBridge.buildExcelDownloadResponse(any(), anyString()))
                .thenReturn(ResponseEntity.ok(new byte[] {4, 5, 6}));

        ExportV2Request request = new ExportV2Request();
        request.setExportType("bill");
        request.setUseStrategyEngine(true);

        ResponseEntity<byte[]> response = service.exportV2(99L, request);

        assertThat(response.getBody()).containsExactly(4, 5, 6);
        verify(legacyExportBridge).generateBillExportBytes(billRequest);
        verify(strategyRegistry, org.mockito.Mockito.never()).require(anyString());
    }
}
