package com.hospital.backend.mapper;

import com.hospital.backend.entity.CustomerDiscount;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CustomerDiscountMapper {

    void insert(CustomerDiscount discount);

    void deleteByCustomerId(Long customerId);

    List<CustomerDiscount> selectByCustomerId(Long customerId);

    void updateById(CustomerDiscount discount);
}
