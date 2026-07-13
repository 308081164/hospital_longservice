package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.customer.SaveCustomerBillingPolicyRequest;
import com.hospital.backend.dto.request.customer.SaveCustomerProductRuleRequest;
import com.hospital.backend.dto.request.customer.SaveCustomerRequest;
import com.hospital.backend.dto.response.customer.CustomerBillingPolicyResponse;
import com.hospital.backend.dto.response.customer.CustomerProductRuleResponse;
import com.hospital.backend.dto.response.customer.CustomerResponse;

import java.util.List;
import java.util.Map;

public interface CustomerService {

    Result<List<CustomerResponse>> listCustomers();

    Result<CustomerResponse> getCustomer(Long id);

    Result<CustomerResponse> createCustomer(SaveCustomerRequest request);

    Result<CustomerResponse> updateCustomer(Long id, SaveCustomerRequest request);

    Result<Map<String, Boolean>> deleteCustomer(Long id);

    Result<List<CustomerProductRuleResponse>> listProductRules(Long customerId);

    Result<CustomerProductRuleResponse> createProductRule(Long customerId, SaveCustomerProductRuleRequest request);

    Result<CustomerProductRuleResponse> updateProductRule(
            Long customerId, Long ruleId, SaveCustomerProductRuleRequest request);

    Result<Map<String, Boolean>> deleteProductRule(Long customerId, Long ruleId);

    Result<List<CustomerBillingPolicyResponse>> listBillingPolicies(Long customerId);

    Result<CustomerBillingPolicyResponse> createBillingPolicy(
            Long customerId, SaveCustomerBillingPolicyRequest request);

    Result<CustomerBillingPolicyResponse> updateBillingPolicy(
            Long customerId, Long policyId, SaveCustomerBillingPolicyRequest request);

    Result<Map<String, Boolean>> deleteBillingPolicy(Long customerId, Long policyId);
}
