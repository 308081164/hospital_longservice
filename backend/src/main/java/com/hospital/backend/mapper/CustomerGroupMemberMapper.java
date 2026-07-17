package com.hospital.backend.mapper;

import com.hospital.backend.entity.CustomerGroupMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerGroupMemberMapper {

    void insert(CustomerGroupMember member);

    void deleteByGroupId(@Param("groupId") Long groupId);

    void deleteById(Long id);

    List<CustomerGroupMember> selectByGroupId(@Param("groupId") Long groupId);

    List<CustomerGroupMember> selectByCustomerId(@Param("customerId") Long customerId);

    CustomerGroupMember selectByGroupAndCustomer(
            @Param("groupId") Long groupId,
            @Param("customerId") Long customerId);
}
