package com.hospital.backend.mapper;

import com.hospital.backend.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    void insert(User user);

    User selectById(Long id);

    User selectByUsername(String username);

    List<User> selectAll();

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    void insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    void deleteUserRoles(Long userId);

    void updateById(User user);

    long count();
}
