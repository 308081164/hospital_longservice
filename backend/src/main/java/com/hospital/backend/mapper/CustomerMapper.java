package com.hospital.backend.mapper;

import com.hospital.backend.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerMapper {

    void insert(Customer customer);

    void updateById(Customer customer);

    Customer selectById(Long id);

    Customer selectByCode(String code);

    List<Customer> selectAll();

    void deleteById(Long id);
}
