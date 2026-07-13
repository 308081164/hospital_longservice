package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class Role extends BaseEntity {

    private String name;

    private String description;

    private Set<Menu> menus = new HashSet<>();

}
