package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Menu extends BaseEntity {

    private String name;

    private String menuType;

    private String icon;

    private String path;

    private Integer order;

    private Long parentId = 0L;

    private Boolean isHidden = false;

    private String component;

    private Boolean keepalive = true;

    private String redirect;
}
