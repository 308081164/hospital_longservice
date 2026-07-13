package com.hospital.backend.util;

import com.hospital.backend.entity.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRuleNameUtilsTest {

    @Test
    void sanitizeLegacyRuleName_stripsHospitalPrefixAndFixedPriceSuffix() {
        assertThat(ProductRuleNameUtils.sanitizeLegacyRuleName(
                "黑龙江省第二医院（松北区）3.6空心钉工具包固定单价"))
                .isEqualTo("3.6空心钉工具包");
    }

    @Test
    void sanitizeLegacyRuleName_stripsPerItemPriceSuffix() {
        assertThat(ProductRuleNameUtils.sanitizeLegacyRuleName(
                "东北农业大学医院洁牙机尖每件 5.5 元"))
                .isEqualTo("洁牙机尖");
    }

    @Test
    void resolveProductRuleName_prefersProductName() {
        Product product = new Product();
        product.setName("3.6空心钉工具包");

        assertThat(ProductRuleNameUtils.resolveProductRuleName(
                "黑龙江省第二医院（松北区）3.6空心钉工具包固定单价",
                product,
                List.of("3.6空心钉工具包")))
                .isEqualTo("3.6空心钉工具包");
    }

    @Test
    void resolveProductRuleName_fallsBackToKeyword() {
        assertThat(ProductRuleNameUtils.resolveProductRuleName(
                "黑龙江省第二医院（松北区）3.6空心钉工具包固定单价",
                null,
                List.of("3.6空心钉工具包")))
                .isEqualTo("3.6空心钉工具包");
    }
}
