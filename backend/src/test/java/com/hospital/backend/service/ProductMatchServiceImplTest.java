package com.hospital.backend.service;

import com.hospital.backend.dto.request.product.MatchPreviewRequest;
import com.hospital.backend.entity.Product;
import com.hospital.backend.entity.ProductCategory;
import com.hospital.backend.entity.ProductMatchRule;
import com.hospital.backend.mapper.ProductAliasMapper;
import com.hospital.backend.mapper.ProductCategoryMapper;
import com.hospital.backend.mapper.ProductMapper;
import com.hospital.backend.mapper.ProductMatchRuleMapper;
import com.hospital.backend.mapper.ProductVariantMapper;
import com.hospital.backend.service.impl.ProductMatchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductMatchServiceImplTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductCategoryMapper categoryMapper;
    @Mock
    private ProductMatchRuleMapper matchRuleMapper;
    @Mock
    private ProductAliasMapper aliasMapper;
    @Mock
    private ProductVariantMapper variantMapper;

    @InjectMocks
    private ProductMatchServiceImpl productMatchService;

    @BeforeEach
    void setUp() {
        ProductCategory smallItem = new ProductCategory();
        smallItem.setId(1L);
        smallItem.setCode("SMALL_ITEM");
        smallItem.setName("小件器械");
        smallItem.setPricingPath("standard");
        smallItem.setIsActive(true);

        Product product = new Product();
        product.setId(10L);
        product.setCategoryId(1L);
        product.setName("洁牙机尖");
        product.setPriority(10);
        product.setIsActive(true);

        ProductMatchRule rule = new ProductMatchRule();
        rule.setId(100L);
        rule.setProductId(10L);
        rule.setMatchType("CONTAINS");
        rule.setTargetField("pack_name");
        rule.setPatternValue("洁牙机尖");
        rule.setPriority(10);
        rule.setIsActive(true);

        when(productMapper.selectAllActive()).thenReturn(List.of(product));
        when(categoryMapper.selectAllActive()).thenReturn(List.of(smallItem));
        when(matchRuleMapper.selectByProductId(10L)).thenReturn(List.of(rule));
        when(aliasMapper.selectByProductId(10L)).thenReturn(List.of());
        when(variantMapper.selectAllActive()).thenReturn(List.of());
        when(matchRuleMapper.selectAllActiveVariantRules()).thenReturn(List.of());

        productMatchService.refreshCache();
    }

    @Test
    void matchesProductByContainsRule() {
        MatchPreviewRequest request = new MatchPreviewRequest();
        request.setType("额外包(纸塑袋)");
        request.setPackName("洁牙机尖-4/Z7526");
        request.setPackageMaterial("高温纸塑袋75*200");
        request.setInstrumentCount(4);

        Optional<com.hospital.backend.dto.response.product.MatchPreviewResponse> result =
                productMatchService.matchRow(request);

        assertThat(result).isPresent();
        assertThat(result.get().getProductName()).isEqualTo("洁牙机尖");
        assertThat(result.get().getCategoryCode()).isEqualTo("SMALL_ITEM");
        assertThat(result.get().getPricingPath()).isEqualTo("standard");
    }

    @Test
    void returnsEmptyWhenNoMatch() {
        MatchPreviewRequest request = new MatchPreviewRequest();
        request.setPackName("未知器械包");

        assertThat(productMatchService.matchRow(request)).isEmpty();
    }
}
