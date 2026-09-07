package com.hospital.backend.service;

import com.hospital.backend.dto.response.billing.RuleVerificationResult;

import java.util.List;
import java.util.Map;

public interface RulesVerificationService {

    RuleVerificationResult verifyCustomer(String customerCode);

    RuleVerificationResult verifyCustomerId(Long customerId);

    Map<String, Object> verifyAll();

    void persistLastVerifySummary(Map<String, Object> summary);
}
