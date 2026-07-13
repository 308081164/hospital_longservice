package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SysSetting {

    private Long id;

    private String settingKey;

    private String settingValue;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
