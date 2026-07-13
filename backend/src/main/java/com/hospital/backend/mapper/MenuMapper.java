package com.hospital.backend.mapper;

import com.hospital.backend.entity.Menu;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MenuMapper {

    void insert(Menu menu);

    Menu selectById(Long id);

    List<Menu> selectAllOrderByOrder();

    long count();

    List<Menu> selectAll();

    Menu selectByPath(String path);

    void update(Menu menu);
}
