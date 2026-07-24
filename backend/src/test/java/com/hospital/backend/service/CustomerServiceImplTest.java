package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.customer.CustomerProductRuleDto;
import com.hospital.backend.dto.request.customer.SaveCustomerRequest;
import com.hospital.backend.dto.response.customer.CustomerResponse;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.mapper.CustomerAliasMapper;
import com.hospital.backend.mapper.CustomerBillingPolicyMapper;
import com.hospital.backend.mapper.CustomerDiscountMapper;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.CustomerProductRuleMapper;
import com.hospital.backend.mapper.DepartmentEntryMapper;
import com.hospital.backend.mapper.PhysicianEntryMapper;
import com.hospital.backend.mapper.ProductMapper;
import com.hospital.backend.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private CustomerAliasMapper aliasMapper;
    @Mock
    private CustomerDiscountMapper discountMapper;
    @Mock
    private CustomerBillingPolicyMapper billingPolicyMapper;
    @Mock
    private CustomerProductRuleMapper productRuleMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private BillingRuleGroupSyncService billingRuleGroupSyncService;
    @Mock
    private DepartmentEntryMapper departmentEntryMapper;
    @Mock
    private PhysicianEntryMapper physicianEntryMapper;

    private CustomerServiceImpl customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerServiceImpl(
                customerMapper,
                aliasMapper,
                discountMapper,
                billingPolicyMapper,
                productRuleMapper,
                productMapper,
                billingRuleGroupSyncService,
                departmentEntryMapper,
                physicianEntryMapper);
        lenient().when(departmentEntryMapper.countActiveByCustomerId(anyLong())).thenReturn(0);
        lenient().when(physicianEntryMapper.countActiveByCustomerId(anyLong())).thenReturn(0);
    }

    @Test
    void updateCustomerPersistsStandardPricingOverride() {
        Customer existing = customer("ZYY-D1", "中医附一");
        when(customerMapper.selectById(1L)).thenReturn(existing);
        when(customerMapper.selectByCode("ZYY-D1")).thenReturn(existing);
        stubEmptyCustomerRelations();

        SaveCustomerRequest request = saveRequest("ZYY-D1", "中医附一", foldRuleWithoutProductId());
        request.setStandardPricingOverride("{\"highTemperature\":{\"paperPlastic\":{\"minCharge\":13.2}}}");

        Result<CustomerResponse> result = customerService.updateCustomer(1L, request);

        assertThat(result.getCode()).isEqualTo(200);
        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStandardPricingOverride()).contains("highTemperature");
        assertThat(result.getData().getStandardPricingOverride()).contains("highTemperature");
    }

    @Test
    void updateCustomerRejectsInvalidStandardPricingOverride() {
        Customer existing = customer("C003", "测试");
        when(customerMapper.selectById(1L)).thenReturn(existing);
        when(customerMapper.selectByCode("C003")).thenReturn(existing);

        SaveCustomerRequest request = saveRequest("C003", "测试", foldRuleWithoutProductId());
        request.setStandardPricingOverride("not-json");

        Result<CustomerResponse> result = customerService.updateCustomer(1L, request);

        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    void updateCustomerPersistsFoldRuleWithoutProductId() {
        Customer existing = customer("C001", "折算测试医院");
        when(customerMapper.selectById(1L)).thenReturn(existing);
        when(customerMapper.selectByCode("C001")).thenReturn(existing);
        stubEmptyCustomerRelations();

        SaveCustomerRequest request = saveRequest("C001", "折算测试医院", foldRuleWithoutProductId());

        Result<CustomerResponse> result = customerService.updateCustomer(1L, request);

        assertThat(result.getCode()).isEqualTo(200);
        ArgumentCaptor<CustomerProductRule> captor = ArgumentCaptor.forClass(CustomerProductRule.class);
        verify(productRuleMapper).insert(captor.capture());
        CustomerProductRule saved = captor.getValue();
        assertThat(saved.getRuleType()).isEqualTo("FOLD");
        assertThat(saved.getProductId()).isNull();
        assertThat(saved.getName()).isEqualTo("客户小件");
        assertThat(saved.getThreshold()).isEqualTo(5);
        assertThat(saved.getFoldRatio()).isEqualByComparingTo(BigDecimal.valueOf(5));
    }

    @Test
    void createCustomerPersistsExtraFeeRuleWithoutProductId() {
        when(customerMapper.selectByCode("C002")).thenReturn(null);
        doAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(2L);
            return null;
        }).when(customerMapper).insert(any(Customer.class));
        Customer created = customer("C002", "加收测试医院");
        created.setId(2L);
        when(customerMapper.selectById(2L)).thenReturn(created);
        when(aliasMapper.selectByCustomerId(2L)).thenReturn(List.of());
        when(billingPolicyMapper.selectByCustomerIdAndType(2L, "DISCOUNT")).thenReturn(List.of());
        when(discountMapper.selectByCustomerId(2L)).thenReturn(List.of());
        when(productRuleMapper.selectByCustomerId(2L)).thenReturn(List.of());

        CustomerProductRuleDto extraFee = new CustomerProductRuleDto();
        extraFee.setRuleType("EXTRA_FEE");
        extraFee.setName("镜头租借公司筐加收");
        extraFee.setKeywords(List.of("镜头"));
        extraFee.setFee(BigDecimal.valueOf(8));

        SaveCustomerRequest request = saveRequest("C002", "加收测试医院", extraFee);

        Result<CustomerResponse> result = customerService.createCustomer(request);

        assertThat(result.getCode()).isEqualTo(200);
        ArgumentCaptor<CustomerProductRule> captor = ArgumentCaptor.forClass(CustomerProductRule.class);
        verify(productRuleMapper).insert(captor.capture());
        CustomerProductRule saved = captor.getValue();
        assertThat(saved.getRuleType()).isEqualTo("EXTRA_FEE");
        assertThat(saved.getProductId()).isNull();
        assertThat(saved.getFee()).isEqualByComparingTo(BigDecimal.valueOf(8));
    }

    @Test
    void createCustomerPersistsFixedPriceRuleWithKeywordsOnly() {
        when(customerMapper.selectByCode("C003-FK")).thenReturn(null);
        doAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(3L);
            return null;
        }).when(customerMapper).insert(any(Customer.class));
        Customer created = customer("C003-FK", "关键词固定价医院");
        created.setId(3L);
        when(customerMapper.selectById(3L)).thenReturn(created);
        when(aliasMapper.selectByCustomerId(3L)).thenReturn(List.of());
        when(billingPolicyMapper.selectByCustomerIdAndType(3L, "DISCOUNT")).thenReturn(List.of());
        when(discountMapper.selectByCustomerId(3L)).thenReturn(List.of());
        when(productRuleMapper.selectByCustomerId(3L)).thenReturn(List.of());

        CustomerProductRuleDto fixedPrice = new CustomerProductRuleDto();
        fixedPrice.setRuleType("FIXED_PRICE");
        fixedPrice.setKeywords(List.of("小缝合包"));
        fixedPrice.setPrice(BigDecimal.valueOf(38.5));

        SaveCustomerRequest request = saveRequest("C003-FK", "关键词固定价医院", fixedPrice);

        Result<CustomerResponse> result = customerService.createCustomer(request);

        assertThat(result.getCode()).isEqualTo(200);
        ArgumentCaptor<CustomerProductRule> captor = ArgumentCaptor.forClass(CustomerProductRule.class);
        verify(productRuleMapper).insert(captor.capture());
        CustomerProductRule saved = captor.getValue();
        assertThat(saved.getRuleType()).isEqualTo("FIXED_PRICE");
        assertThat(saved.getProductId()).isNull();
        assertThat(saved.getKeywords()).contains("小缝合包");
        assertThat(saved.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(38.5));
    }

    private Customer customer(String code, String name) {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCode(code);
        customer.setCanonicalName(name);
        return customer;
    }

    private CustomerProductRuleDto foldRuleWithoutProductId() {
        CustomerProductRuleDto foldRule = new CustomerProductRuleDto();
        foldRule.setRuleType("FOLD");
        foldRule.setName("客户小件折算");
        foldRule.setThreshold(5);
        foldRule.setFoldRatio(BigDecimal.valueOf(5));
        return foldRule;
    }

    private SaveCustomerRequest saveRequest(String code, String name, CustomerProductRuleDto rule) {
        SaveCustomerRequest request = new SaveCustomerRequest();
        request.setCode(code);
        request.setCanonicalName(name);
        request.setProductRules(List.of(rule));
        return request;
    }

    private void stubEmptyCustomerRelations() {
        when(aliasMapper.selectByCustomerId(1L)).thenReturn(List.of());
        when(billingPolicyMapper.selectByCustomerIdAndType(1L, "DISCOUNT")).thenReturn(List.of());
        when(discountMapper.selectByCustomerId(1L)).thenReturn(List.of());
        when(productRuleMapper.selectByCustomerId(1L)).thenReturn(List.of());
    }
}
