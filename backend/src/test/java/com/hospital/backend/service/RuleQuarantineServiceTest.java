package com.hospital.backend.service;

import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.mapper.CustomerProductRuleMapper;
import com.hospital.backend.mapper.CustomerProductRuleTombstoneMapper;
import com.hospital.backend.service.impl.RuleQuarantineServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RuleQuarantineServiceTest {

    @Mock
    private CustomerProductRuleMapper productRuleMapper;
    @Mock
    private CustomerProductRuleTombstoneMapper tombstoneMapper;
    @Mock
    private RuleChangeAuditService ruleChangeAuditService;

    @InjectMocks
    private RuleQuarantineServiceImpl service;

    @Test
    void quarantineRule_deactivatesAndWritesTombstone() {
        Customer customer = new Customer();
        customer.setCode("TEST-YY");
        CustomerProductRule rule = new CustomerProductRule();
        rule.setId(1L);
        rule.setCustomerId(10L);
        rule.setName("测试规则");
        rule.setIsActive(true);

        boolean ok = service.quarantineRule(customer, rule, "hash123", "不在 manifest", "test");

        assertTrue(ok);
        assertFalse(rule.getIsActive());
        verify(productRuleMapper).updateById(rule);
        verify(tombstoneMapper).insert(any());
        verify(ruleChangeAuditService).logChange(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void quarantineRule_skipsAlreadyInactive() {
        CustomerProductRule rule = new CustomerProductRule();
        rule.setId(2L);
        rule.setIsActive(false);
        assertFalse(service.quarantineRule(null, rule, "h", "r", "op"));
    }
}
