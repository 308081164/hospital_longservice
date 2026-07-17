package com.hospital.backend.mapper;

import com.hospital.backend.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper {

    void insert(Role role);

    Role selectById(Long id);

    Role selectByName(String name);

    List<Role> selectAll();

    boolean existsByName(String name);

    List<Role> selectAllByIds(@Param("ids") List<Long> ids);

    List<Role> selectByUserId(@Param("userId") Long userId);

    void insertRoleMenu(@Param("roleId") Long roleId, @Param("menuId") Long menuId);

    boolean existsRoleMenu(@Param("roleId") Long roleId, @Param("menuId") Long menuId);

    void deleteRoleMenus(Long roleId);
}
