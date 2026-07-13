package com.hospital.backend.mapper;

import com.hospital.backend.entity.SysSetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysSettingMapper {

    void insert(SysSetting setting);

    void updateByKey(SysSetting setting);

    SysSetting selectByKey(String settingKey);

    long countByKey(String settingKey);
}
