package com.hospital.backend.service.impl;

import com.hospital.backend.entity.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetiredPlantingBoxProductTest {

    @Test
    void plantingBoxProductIsRetiredAndNeedlesStay() {
        Product planting = new Product();
        planting.setName("种植盒");
        planting.setSkuCode("SMALL-种植盒");
        assertThat(ProductMatchServiceImpl.isRetiredPlantingBox(planting)).isTrue();

        Product bySku = new Product();
        bySku.setName("其他");
        bySku.setSkuCode("SMALL-种植盒");
        assertThat(ProductMatchServiceImpl.isRetiredPlantingBox(bySku)).isTrue();

        Product needle = new Product();
        needle.setName("克氏针");
        needle.setSkuCode("SMALL-克氏针");
        assertThat(ProductMatchServiceImpl.isRetiredPlantingBox(needle)).isFalse();
    }
}
