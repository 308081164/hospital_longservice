package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.customer.SaveCustomerBillingPolicyRequest;
import com.hospital.backend.dto.request.customer.SaveCustomerProductRuleRequest;
import com.hospital.backend.dto.request.customer.SaveCustomerRequest;
import com.hospital.backend.dto.response.customer.CustomerBillingPolicyResponse;
import com.hospital.backend.dto.response.customer.CustomerProductRuleResponse;
import com.hospital.backend.dto.response.customer.CustomerResponse;
import com.hospital.backend.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public Result<List<CustomerResponse>> listCustomers() {
        return customerService.listCustomers();
    }

    @GetMapping("/{id}")
    public Result<CustomerResponse> getCustomer(@PathVariable Long id) {
        return customerService.getCustomer(id);
    }

    @PostMapping
    public Result<CustomerResponse> createCustomer(@Valid @RequestBody SaveCustomerRequest request) {
        return customerService.createCustomer(request);
    }

    @PutMapping("/{id}")
    public Result<CustomerResponse> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody SaveCustomerRequest request) {
        return customerService.updateCustomer(id, request);
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, Boolean>> deleteCustomer(@PathVariable Long id) {
        return customerService.deleteCustomer(id);
    }

    @GetMapping("/{id}/product-rules")
    public Result<List<CustomerProductRuleResponse>> listProductRules(@PathVariable Long id) {
        return customerService.listProductRules(id);
    }

    @PostMapping("/{id}/product-rules")
    public Result<CustomerProductRuleResponse> createProductRule(
            @PathVariable Long id,
            @Valid @RequestBody SaveCustomerProductRuleRequest request) {
        return customerService.createProductRule(id, request);
    }

    @PutMapping("/{id}/product-rules/{ruleId}")
    public Result<CustomerProductRuleResponse> updateProductRule(
            @PathVariable Long id,
            @PathVariable Long ruleId,
            @Valid @RequestBody SaveCustomerProductRuleRequest request) {
        return customerService.updateProductRule(id, ruleId, request);
    }

    @DeleteMapping("/{id}/product-rules/{ruleId}")
    public Result<Map<String, Boolean>> deleteProductRule(
            @PathVariable Long id,
            @PathVariable Long ruleId) {
        return customerService.deleteProductRule(id, ruleId);
    }

    @GetMapping("/{id}/billing-policies")
    public Result<List<CustomerBillingPolicyResponse>> listBillingPolicies(@PathVariable Long id) {
        return customerService.listBillingPolicies(id);
    }

    @PostMapping("/{id}/billing-policies")
    public Result<CustomerBillingPolicyResponse> createBillingPolicy(
            @PathVariable Long id,
            @Valid @RequestBody SaveCustomerBillingPolicyRequest request) {
        return customerService.createBillingPolicy(id, request);
    }

    @PutMapping("/{id}/billing-policies/{policyId}")
    public Result<CustomerBillingPolicyResponse> updateBillingPolicy(
            @PathVariable Long id,
            @PathVariable Long policyId,
            @Valid @RequestBody SaveCustomerBillingPolicyRequest request) {
        return customerService.updateBillingPolicy(id, policyId, request);
    }

    @DeleteMapping("/{id}/billing-policies/{policyId}")
    public Result<Map<String, Boolean>> deleteBillingPolicy(
            @PathVariable Long id,
            @PathVariable Long policyId) {
        return customerService.deleteBillingPolicy(id, policyId);
    }
}
