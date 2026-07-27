package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationJob;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class D8DisplayNameResolverTest {

    private final D8DisplayNameResolver resolver = new D8DisplayNameResolver();

    @Test
    void hospitalNameSource_usesHospitalName() {
        HospitalReconciliationJob job = job("黑龙江省医院（南岗院区）", "标准灭菌计费规则", null);
        assertEquals(
                "黑龙江省医院（南岗院区）",
                resolver.resolve(job, BillExportLayoutResolver.D8_HOSPITAL_NAME));
    }

    @Test
    void auto_prefersHospitalOverDefaultRuleName() {
        HospitalReconciliationJob job = job("黑龙江省医院（南岗院区）", "标准灭菌计费规则", null);
        assertEquals(
                "黑龙江省医院（南岗院区）",
                resolver.resolve(job, BillExportLayoutResolver.D8_AUTO));
    }

    @Test
    void auto_usesCustomRuleNameWhenHospitalBlank() {
        HospitalReconciliationJob job = job("", "2024年Q1计费规则", null);
        assertEquals("2024年Q1计费规则", resolver.resolve(job, BillExportLayoutResolver.D8_AUTO));
    }

    @Test
    void ruleNameSource_usesPlanNameFirst() {
        HospitalReconciliationJob job = job("测试医院", "标准灭菌计费规则", "附一专项方案");
        assertEquals("附一专项方案", resolver.resolve(job, BillExportLayoutResolver.D8_RULE_NAME));
    }

    private static HospitalReconciliationJob job(String hospital, String ruleName, String planName) {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName(hospital);
        job.setRuleName(ruleName);
        job.setPlanName(planName);
        return job;
    }
}
