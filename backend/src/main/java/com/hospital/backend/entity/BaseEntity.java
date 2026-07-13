package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public abstract class BaseEntity {

    private Long id;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
