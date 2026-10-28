package com.hospital.backend.service;

import com.hospital.backend.common.JsonUtils;
import org.junit.jupiter.api.Test;

class RegexKeywordProbeTest {

    @Test
    void probe() {
        var kw = JsonUtils.getObjectMapper().createArrayNode();
        kw.add("克氏针.*(0\\.8|1\\.0|1\\.2).*双|双.*克氏针.*(0\\.8|1\\.0|1\\.2)@regex");
        boolean hit1 = BillingConditionEvaluator.matchesKeywordsByMode("克氏针(0.8)-3件/双", kw, "exact_token");
        var kw2 = JsonUtils.getObjectMapper().createArrayNode();
        kw2.add("克氏针.*双@regex");
        boolean hit2 = BillingConditionEvaluator.matchesKeywordsByMode("克氏针(0.8)-3件/双", kw2, "exact_token");
        System.out.println("PROBE hit1=" + hit1 + " hit2=" + hit2);
    }
}
