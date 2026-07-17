package com.hospital.backend.mapper;

import com.hospital.backend.entity.CustomerGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerGroupMapper {

    void insert(CustomerGroup group);

    void updateById(CustomerGroup group);

    CustomerGroup selectById(Long id);

    List<CustomerGroup> selectAll(@Param("groupType") String groupType);

    void deleteById(Long id);
}
