package com.hospital.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.CustomerProductRuleMapper;
import com.hospital.backend.mapper.SysSettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BillingRulesManifestReconcilerTest {

    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private CustomerProductRuleMapper customerProductRuleMapper;
    @Mock
    private SysSettingMapper sysSettingMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private BillingRulesManifestReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new BillingRulesManifestReconciler(
                customerMapper, customerProductRuleMapper, sysSettingMapper, jdbcTemplate);
    }

    @Test
    void applyCustomerManifestFields_syncsBillingEnabledAndStatus() throws Exception {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCode("HLFB-SF");
        customer.setBillingEnabled(true);
        customer.setStatus("active");

        ObjectNode node = new ObjectMapper().createObjectNode()
                .put("billingEnabled", false)
                .put("status", "inactive");

        boolean changed = ReflectionTestUtils.invokeMethod(
                reconciler, "applyCustomerManifestFields", customer, node);

        assertTrue(changed);
        assertFalse(customer.getBillingEnabled());
        assertEquals("inactive", customer.getStatus());
        verify(customerMapper).updateById(customer);
    }

    @Test
    void applyCustomerManifestFields_noOpWhenAlreadyAligned() throws Exception {
        Customer customer = new Customer();
        customer.setId(2L);
        customer.setCode("HLJ-FY-RK");
        customer.setBillingEnabled(true);
        customer.setStatus("active");

        ObjectNode node = new ObjectMapper().createObjectNode()
                .put("billingEnabled", true)
                .put("status", "active");

        boolean changed = ReflectionTestUtils.invokeMethod(
                reconciler, "applyCustomerManifestFields", customer, node);

        assertFalse(changed);
    }
}
