package com.hospital.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class User extends BaseEntity {

    private String username;

    private String email;

    @JsonIgnore
    private String password;

    @JsonProperty("is_active")
    private Boolean isActive = true;

    @JsonProperty("is_superuser")
    private Boolean isSuperuser = false;

    private Set<Role> roles = new HashSet<>();
}
