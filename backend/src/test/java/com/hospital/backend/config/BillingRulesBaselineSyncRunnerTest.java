package com.hospital.backend.config;

import com.hospital.backend.mapper.SysSettingMapper;
import com.hospital.backend.service.BaselineRuleSyncService;
import com.hospital.backend.service.RulesVerificationService;
import com.hospital.backend.service.SystemVersionInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingRulesBaselineSyncRunnerTest {

    @Mock
    private SysSettingMapper sysSettingMapper;
    @Mock
    private BaselineRuleIndex baselineRuleIndex;
    @Mock
    private BaselineRuleSyncService baselineRuleSyncService;
    @Mock
    private RulesVerificationService rulesVerificationService;
    @Mock
    private BillingRulesBaselineSyncHealth syncHealth;

    @InjectMocks
    private BillingRulesBaselineSyncRunner runner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(runner, "failOnVerifyError", false);
    }

    @Test
    void skipsImportWhenHashUnchanged() throws Exception {
        when(baselineRuleIndex.baselineHash()).thenReturn("abc123");
        when(sysSettingMapper.selectByKey(SystemVersionInfoService.BASELINE_HASH_KEY))
                .thenReturn(setting(SystemVersionInfoService.BASELINE_HASH_KEY, "abc123"));
        when(rulesVerificationService.verifyAll()).thenReturn(Map.of("ok", true));

        runner.run();

        verify(baselineRuleSyncService, never()).importAllBaselines(false);
        verify(syncHealth).markHealthy();
    }

    @Test
    void importsWhenHashChanged() throws Exception {
        when(baselineRuleIndex.baselineHash()).thenReturn("new-hash");
        when(sysSettingMapper.selectByKey(SystemVersionInfoService.BASELINE_HASH_KEY))
                .thenReturn(setting(SystemVersionInfoService.BASELINE_HASH_KEY, "old-hash"));
        when(baselineRuleSyncService.importAllBaselines(false)).thenReturn(42);
        when(rulesVerificationService.verifyAll()).thenReturn(Map.of("ok", true));

        runner.run();

        verify(baselineRuleSyncService).importAllBaselines(false);
        verify(sysSettingMapper, org.mockito.Mockito.atLeastOnce()).insert(any());
        verify(syncHealth).markHealthy();
    }

    @Test
    void marksUnhealthyWhenVerifyFails() throws Exception {
        when(baselineRuleIndex.baselineHash()).thenReturn("same");
        when(sysSettingMapper.selectByKey(SystemVersionInfoService.BASELINE_HASH_KEY))
                .thenReturn(setting(SystemVersionInfoService.BASELINE_HASH_KEY, "same"));
        when(baselineRuleSyncService.importAllBaselines(false)).thenReturn(10);
        when(rulesVerificationService.verifyAll())
                .thenReturn(Map.of("ok", false, "failedCustomers", List.of("HRB-WY")))
                .thenReturn(Map.of("ok", false, "failedCustomers", List.of("HRB-WY")));

        runner.run();

        verify(baselineRuleSyncService).importAllBaselines(false);
        verify(syncHealth).markUnhealthy(any());
    }

    @Test
    void reimportsWhenHashUnchangedButVerifyFailsThenRecovers() throws Exception {
        when(baselineRuleIndex.baselineHash()).thenReturn("same");
        when(sysSettingMapper.selectByKey(SystemVersionInfoService.BASELINE_HASH_KEY))
                .thenReturn(setting(SystemVersionInfoService.BASELINE_HASH_KEY, "same"));
        when(baselineRuleSyncService.importAllBaselines(false)).thenReturn(12);
        when(rulesVerificationService.verifyAll())
                .thenReturn(Map.of("ok", false, "totalExtra", 3))
                .thenReturn(Map.of("ok", true));

        runner.run();

        verify(baselineRuleSyncService).importAllBaselines(false);
        verify(syncHealth).markHealthy();
    }

    @Test
    void throwsWhenVerifyFailsAndFailOnVerifyError() throws Exception {
        ReflectionTestUtils.setField(runner, "failOnVerifyError", true);
        when(baselineRuleIndex.baselineHash()).thenReturn("same");
        when(sysSettingMapper.selectByKey(SystemVersionInfoService.BASELINE_HASH_KEY))
                .thenReturn(setting(SystemVersionInfoService.BASELINE_HASH_KEY, "same"));
        when(rulesVerificationService.verifyAll()).thenReturn(Map.of("ok", false));

        assertThatThrownBy(() -> runner.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verify failed");
    }

    private static com.hospital.backend.entity.SysSetting setting(String key, String value) {
        var row = new com.hospital.backend.entity.SysSetting();
        row.setSettingKey(key);
        row.setSettingValue(value);
        return row;
    }
}
