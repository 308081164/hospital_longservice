package com.hospital.backend.mapper;

import com.hospital.backend.entity.CustomerAlias;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CustomerAliasMapper {

    void insert(CustomerAlias alias);

    void deleteByCustomerId(Long customerId);

    List<CustomerAlias> selectByCustomerId(Long customerId);

    List<CustomerAlias> selectAllActive();

    CustomerAlias selectByAlias(String alias);
}
